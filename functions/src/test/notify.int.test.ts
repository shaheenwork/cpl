/**
 * Notifications against the real emulator (BUILD_PROMPT.md section 16): who is told, who is not,
 * and — driving the flows that actually send — that nothing anyone wrote ever reaches a payload.
 *
 * The sender is this test's own, so every message the server would have put on the wire is caught
 * and read. Nothing is mocked below it: these are the real callables, writing real documents.
 *
 * Requires a running emulator:
 *   firebase emulators:start --only auth,firestore,storage --project afterhours-dev-emulator
 */
process.env.FIRESTORE_EMULATOR_HOST ??= '127.0.0.1:8080';

import assert from 'node:assert/strict';
import { beforeEach, describe, it } from 'node:test';
import { initializeApp } from 'firebase-admin/app';
import { Timestamp, getFirestore } from 'firebase-admin/firestore';
import { recomputeCouple } from '../couple/recompute';
import { grantPremium } from './support';
import { loadCatalogue, resetCatalogueCache } from '../engine/catalogue';
import { DEFAULT_WEIGHTS } from '../engine/types';
import type { GenerateDeps } from '../experience/generate';
import { planNight } from '../experience/plan';
import { notifyPartner, notifyUser, type PushMessage, type Sender } from '../notify/notify';
import { createPairingCode, requestPairing, respondToPairing } from '../pairing/pairing';
import { QUIET_MS, onMessageWritten } from '../chat/chat';
import { openArcDay, startArc } from '../surprises/arcs';
import { createSurprise, deliverDue } from '../surprises/surprises';

const PROJECT = 'afterhours-int-test';
const db = getFirestore(initializeApp({ projectId: PROJECT }, 'notify-int-test'));
const NOW = Date.UTC(2026, 8, 24, 19, 0, 0);

const deps: GenerateDeps = {
  catalogue: loadCatalogue,
  weights: async () => DEFAULT_WEIGHTS,
  seed: () => 4242,
};

/** Everything a couple wrote in this test. None of it may appear in a payload. */
const SECRETS = ['blindfold', 'For Friday', 'meet me at the door with nothing on'];

/** A sender that keeps what it was given, and fails whichever tokens it was told to fail. */
class Recorder implements Sender {
  readonly sent: { tokens: string[]; message: PushMessage }[] = [];
  reject: string[] = [];

  async send(tokens: string[], message: PushMessage): Promise<{ failed: string[] }> {
    this.sent.push({ tokens, message });
    return { failed: tokens.filter((token) => this.reject.includes(token)) };
  }

  kinds(): string[] {
    return this.sent.map((one) => one.message.data.kind);
  }

  /** Every payload, as text, for reading back. */
  text(): string {
    return JSON.stringify(this.sent);
  }
}

async function clearEmulator(): Promise<void> {
  const res = await fetch(
    `http://${process.env.FIRESTORE_EMULATOR_HOST}/emulator/v1/projects/${PROJECT}/databases/(default)/documents`,
    { method: 'DELETE' },
  );
  assert.ok(res.ok, `emulator clear failed: ${res.status}`);
}

let counter = 0;
async function user(settings?: Record<string, unknown>, timezone = 'UTC'): Promise<string> {
  counter += 1;
  const uid = `notify_${counter}`;
  await db.doc(`users/${uid}`).set({ contentLevel: 5, timezone, ...(settings ? { notificationSettings: settings } : {}) });
  return uid;
}

async function withPhone(uid: string, token: string): Promise<void> {
  await db.doc(`users/${uid}/fcmTokens/${token}`).set({ token, platform: 'ANDROID', updatedAt: Timestamp.fromMillis(NOW) });
}

async function pair(a: string, b: string, recorder: Sender): Promise<string> {
  const { code } = await createPairingCode(db, a, NOW);
  await requestPairing(db, b, code, NOW, recorder);
  const { coupleId } = await respondToPairing(db, a, true, NOW, recorder);
  assert.ok(coupleId);
  await grantPremium(db, a, NOW);
  await recomputeCouple(db, coupleId, NOW);
  return coupleId;
}

describe('who gets told', () => {
  beforeEach(async () => {
    await clearEmulator();
    resetCatalogueCache();
  });

  it('sends to every phone a user has signed in on', async () => {
    const recorder = new Recorder();
    const uid = await user();
    await withPhone(uid, 'phone-one');
    await withPhone(uid, 'phone-two');

    assert.equal(await notifyUser(db, uid, 'SURPRISE_LEFT', 'HDjiwnxR8u9xqPsTMwuW', NOW, recorder), 'sent');

    assert.equal(recorder.sent.length, 1);
    assert.deepEqual(recorder.sent[0].tokens.sort(), ['phone-one', 'phone-two']);
    assert.deepEqual(recorder.sent[0].message.data, {
      kind: 'SURPRISE_LEFT',
      channel: 'surprises',
      ref: 'HDjiwnxR8u9xqPsTMwuW',
      at: String(NOW),
    });
  });

  it('says nothing to someone who turned notifications off, or is asleep', async () => {
    const recorder = new Recorder();
    const off = await user({ enabled: false });
    const asleep = await user({ quietHours: { from: '22:00', to: '07:00' } }, 'Asia/Kolkata');
    const awake = await user({ quietHours: { from: '22:00', to: '07:00' } }, 'America/New_York');
    for (const uid of [off, asleep, awake]) await withPhone(uid, `phone-${uid}`);

    // 19:00 UTC: half past midnight in Kolkata, three in the afternoon in New York.
    assert.equal(await notifyUser(db, off, 'BOTH_READY', null, NOW, recorder), 'disabled');
    assert.equal(await notifyUser(db, asleep, 'BOTH_READY', null, NOW, recorder), 'quiet');
    assert.equal(await notifyUser(db, awake, 'BOTH_READY', null, NOW, recorder), 'sent');

    assert.equal(recorder.sent.length, 1);
  });

  it('forgets a phone the service no longer knows', async () => {
    const recorder = new Recorder();
    const uid = await user();
    await withPhone(uid, 'gone');
    await withPhone(uid, 'here');
    recorder.reject = ['gone'];

    await notifyUser(db, uid, 'PARTNER_JOINED', null, NOW, recorder);

    const left = await db.collection(`users/${uid}/fcmTokens`).get();
    assert.deepEqual(left.docs.map((token) => token.id), ['here']);
    // And nothing is sent to a user with no phone at all.
    assert.equal(await notifyUser(db, await user(), 'PARTNER_JOINED', null, NOW, recorder), 'no-tokens');
  });

  it('tells the other one, never the one who did it', async () => {
    const recorder = new Recorder();
    const a = await user();
    const b = await user();
    await withPhone(a, 'phone-a');
    await withPhone(b, 'phone-b');

    await notifyPartner(db, [a, b], a, 'THEY_SENT_SOMETHING', null, NOW, recorder);

    assert.deepEqual(recorder.sent[0].tokens, ['phone-b']);
  });

  it('tells them something was said, says nothing about it, and not again for a while', async () => {
    const recorder = new Recorder();
    const a = await user();
    const b = await user();
    await withPhone(b, 'phone-b');
    const coupleId = await pair(a, b, recorder);
    recorder.sent.length = 0;

    await onMessageWritten(db, coupleId, a, NOW, recorder);
    assert.deepEqual(recorder.kinds(), ['THEY_SENT_SOMETHING']);
    assert.deepEqual(recorder.sent[0].tokens, ['phone-b']);
    // Not a word of it rides along: the payload is a kind, a channel and a time (D-088).
    assert.ok(!recorder.text().includes('did you do it'));

    // A conversation is not an event. Twenty messages in a minute is one notification.
    await onMessageWritten(db, coupleId, a, NOW + 1_000, recorder);
    await onMessageWritten(db, coupleId, a, NOW + 30_000, recorder);
    assert.equal(recorder.sent.length, 1);

    // Once the quiet is over, the next one is told.
    await onMessageWritten(db, coupleId, a, NOW + QUIET_MS + 1, recorder);
    assert.equal(recorder.sent.length, 2);
  });

  it('never fails the thing it was about', async () => {
    const angry: Sender = { send: async () => { throw new Error('no network'); } };
    const uid = await user();
    await withPhone(uid, 'phone');

    assert.equal(await notifyUser(db, uid, 'BOTH_READY', null, NOW, angry), 'failed');
  });
});

describe('what the real flows send', () => {
  beforeEach(async () => {
    await clearEmulator();
    resetCatalogueCache();
  });

  it('tells a partner that something happened, and never what', async () => {
    const recorder = new Recorder();
    const alex = await user();
    const sam = await user();
    await withPhone(alex, 'phone-alex');
    await withPhone(sam, 'phone-sam');
    const coupleId = await pair(alex, sam, recorder);

    // Everything below is something one of them wrote or chose.
    const surprise = await createSurprise(
      db,
      alex,
      { type: 'SCHEDULED', title: 'For Friday', text: SECRETS[2], scheduledFor: NOW + 60_000 },
      NOW,
      recorder,
    );
    await deliverDue(db, NOW + 120_000, recorder);
    await planNight(
      db,
      sam,
      { mode: 'TOGETHER', durationMin: 30, moods: ['naughty'], intensity: 3, leadership: 'RANDOM', novelty: 'MIXED', media: {} },
      NOW,
      deps,
      recorder,
    );
    const arc = await startArc(db, alex, { lengthDays: 3 }, NOW);
    await openArcDay(db, alex, { arcId: arc.arcId, dayIndex: 0 }, NOW, deps, recorder);

    assert.deepEqual(recorder.kinds(), [
      'PARTNER_INVITED',
      'PARTNER_JOINED',
      'SURPRISE_LEFT',
      'SURPRISE_UNLOCKED',
      'NIGHT_STARTING',
      'DAY_UNLOCKED',
    ]);
    // The payload inspection, on what the server really sent rather than on what it can build.
    const sent = recorder.text();
    for (const secret of SECRETS) {
      assert.ok(!sent.toLowerCase().includes(secret.toLowerCase()), `a payload leaked "${secret}"`);
    }
    for (const one of recorder.sent) {
      assert.ok(!('notification' in one.message));
      for (const [key, value] of Object.entries(one.message.data)) {
        assert.ok(['kind', 'channel', 'ref', 'at'].includes(key), `payload carried ${key}`);
        assert.match(value, /^[A-Za-z0-9_-]+$/);
      }
    }
    // What was left is pointed at by id — which is all the phone needs to go and ask for it.
    assert.equal(recorder.sent[2].message.data.ref, surprise.surpriseId);
    assert.deepEqual(recorder.sent[2].tokens, ['phone-sam']);
    assert.ok((await db.doc(`couples/${coupleId}/surprises/${surprise.surpriseId}`).get()).exists);
  });
});
