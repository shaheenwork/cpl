/**
 * Things left for later, against the real emulator (BUILD_PROMPT.md sections 5.7, 13): what is
 * left is guarded until its moment, the clock delivers it rather than a client, and "Open when…"
 * hands over exactly one.
 *
 * Requires a running emulator:
 *   firebase emulators:start --only auth,firestore,storage --project afterhours-dev-emulator
 */
process.env.FIRESTORE_EMULATOR_HOST ??= '127.0.0.1:8080';

import assert from 'node:assert/strict';
import { beforeEach, describe, it } from 'node:test';
import { initializeApp } from 'firebase-admin/app';
import { getFirestore, type DocumentData } from 'firebase-admin/firestore';
import { recomputeCouple } from '../couple/recompute';
import { createPairingCode, requestPairing, respondToPairing } from '../pairing/pairing';
import { ServiceError } from '../shared/errors';
import {
  MAX_AHEAD_MS,
  createSurprise,
  deliverDue,
  expireSurprises,
  openSurprise,
  openWhen,
} from '../surprises/surprises';

const PROJECT = 'afterhours-int-test';
const db = getFirestore(initializeApp({ projectId: PROJECT }, 'surprises-int-test'));
const NOW = Date.UTC(2026, 8, 24, 9, 0, 0);
const HOUR = 60 * 60 * 1000;

async function clearEmulator(): Promise<void> {
  const res = await fetch(
    `http://${process.env.FIRESTORE_EMULATOR_HOST}/emulator/v1/projects/${PROJECT}/databases/(default)/documents`,
    { method: 'DELETE' },
  );
  assert.ok(res.ok, `emulator clear failed: ${res.status}`);
}

let counter = 0;
async function couple() {
  counter += 1;
  const a = `keeper_${counter}`;
  const b = `finder_${counter}`;
  await db.doc(`users/${a}`).set({ contentLevel: 3 });
  await db.doc(`users/${b}`).set({ contentLevel: 3 });
  const { code } = await createPairingCode(db, a, NOW);
  await requestPairing(db, b, code, NOW);
  const { coupleId } = await respondToPairing(db, a, true, NOW);
  assert.ok(coupleId);
  await recomputeCouple(db, coupleId, NOW);
  return { a, b, coupleId };
}

async function read(path: string): Promise<DocumentData | undefined> {
  return (await db.doc(path).get()).data();
}

async function rejects(work: Promise<unknown>, code: string, reason?: string) {
  await assert.rejects(work, (error: unknown) => {
    assert.ok(error instanceof ServiceError, `expected a ServiceError, got ${error}`);
    assert.equal(error.code, code);
    if (reason) assert.equal(error.reason, reason);
    return true;
  });
}

describe('something left for later', () => {
  beforeEach(async () => {
    await clearEmulator();
  });

  it('is visible as a thing, and unreadable as a secret, until its moment', async () => {
    const { a, b, coupleId } = await couple();

    const left = await createSurprise(
      db,
      a,
      { type: 'SCHEDULED', title: 'For Friday', text: 'the secret', scheduledFor: NOW + 6 * HOUR },
      NOW,
    );

    const surprise = (await read(`couples/${coupleId}/surprises/${left.surpriseId}`))!;
    assert.equal(surprise.state, 'SCHEDULED');
    assert.equal(surprise.forUid, b);
    assert.equal(surprise.title, 'For Friday');
    // Nothing of what is in it is on the document both of them can read.
    for (const [key, value] of Object.entries(surprise)) {
      assert.ok(String(value) !== 'the secret', `the secret leaked into ${key}`);
    }
    const payload = (await read(`couples/${coupleId}/surprises/${left.surpriseId}/payload/content`))!;
    assert.equal(payload.text, 'the secret');
    assert.equal(payload.deliveredAt, null);
    assert.equal(payload.ownerUid, a);
  });

  it('is delivered by the clock, once, and not a moment early', async () => {
    const { a, coupleId } = await couple();
    const left = await createSurprise(
      db,
      a,
      { type: 'SCHEDULED', title: 'For Friday', text: 'the secret', scheduledFor: NOW + 6 * HOUR },
      NOW,
    );

    assert.deepEqual(await deliverDue(db, NOW + HOUR), { delivered: 0 });
    assert.equal((await read(`couples/${coupleId}/surprises/${left.surpriseId}`))!.state, 'SCHEDULED');

    assert.deepEqual(await deliverDue(db, NOW + 6 * HOUR), { delivered: 1 });
    const delivered = (await read(`couples/${coupleId}/surprises/${left.surpriseId}`))!;
    assert.equal(delivered.state, 'DELIVERED');
    assert.ok(delivered.deliveredAt);
    assert.ok((await read(`couples/${coupleId}/surprises/${left.surpriseId}/payload/content`))!.deliveredAt);

    // Running the sweep again finds nothing left to do.
    assert.deepEqual(await deliverDue(db, NOW + 7 * HOUR), { delivered: 0 });
  });

  it('is opened by the partner it was left for, and by nobody before it arrives', async () => {
    const { a, b, coupleId } = await couple();
    const left = await createSurprise(
      db,
      a,
      { type: 'SCHEDULED', title: 'For Friday', text: 'the secret', scheduledFor: NOW + HOUR },
      NOW,
    );

    await rejects(openSurprise(db, b, { surpriseId: left.surpriseId }, NOW), 'failed-precondition', 'not-yet');
    await deliverDue(db, NOW + HOUR);
    await rejects(openSurprise(db, a, { surpriseId: left.surpriseId }, NOW + HOUR), 'permission-denied', 'not-yours');

    const opened = await openSurprise(db, b, { surpriseId: left.surpriseId }, NOW + 2 * HOUR);

    assert.equal(opened.state, 'OPENED');
    const stored = (await read(`couples/${coupleId}/surprises/${left.surpriseId}`))!;
    assert.equal(stored.state, 'OPENED');
    assert.ok(stored.openedAt);
    // Opening it again is not an error, and changes nothing.
    assert.equal((await openSurprise(db, b, { surpriseId: left.surpriseId }, NOW + 3 * HOUR)).state, 'OPENED');
  });

  it('hands over one "Open when…" at a time, the oldest first', async () => {
    const { a, b, coupleId } = await couple();
    const first = await createSurprise(
      db,
      a,
      { type: 'OPEN_WHEN', title: 'When you miss me', text: 'the first one', trigger: 'MISS_ME' },
      NOW,
    );
    const second = await createSurprise(
      db,
      a,
      { type: 'OPEN_WHEN', title: 'When you miss me again', text: 'the second', trigger: 'MISS_ME' },
      NOW + 1000,
    );
    await createSurprise(
      db,
      a,
      { type: 'OPEN_WHEN', title: 'When you cannot sleep', text: 'for 3am', trigger: 'CANT_SLEEP' },
      NOW,
    );

    const pulled = await openWhen(db, b, { trigger: 'MISS_ME' }, NOW + HOUR);

    assert.equal(pulled.surpriseId, first.surpriseId);
    assert.equal((await read(`couples/${coupleId}/surprises/${first.surpriseId}`))!.state, 'DELIVERED');
    // The second one is still waiting for the next time.
    assert.equal((await read(`couples/${coupleId}/surprises/${second.surpriseId}`))!.state, 'SCHEDULED');

    const next = await openWhen(db, b, { trigger: 'MISS_ME' }, NOW + 2 * HOUR);
    assert.equal(next.surpriseId, second.surpriseId);
    await rejects(openWhen(db, b, { trigger: 'MISS_ME' }, NOW + 3 * HOUR), 'not-found', 'nothing-waiting');
    // The one kept for a different moment is untouched, and is not the keeper's to pull.
    await rejects(openWhen(db, a, { trigger: 'CANT_SLEEP' }, NOW + HOUR), 'not-found', 'nothing-waiting');
  });

  it('refuses a payload it cannot read, a time that has passed, and a stranger', async () => {
    const { a } = await couple();
    const base = { type: 'SCHEDULED', title: 'For Friday', text: 'the secret' };
    await rejects(createSurprise(db, a, { ...base, scheduledFor: NOW - HOUR }, NOW), 'invalid-argument', 'scheduledFor');
    await rejects(
      createSurprise(db, a, { ...base, scheduledFor: NOW + MAX_AHEAD_MS + HOUR }, NOW),
      'invalid-argument',
      'scheduledFor',
    );
    await rejects(createSurprise(db, a, { ...base, scheduledFor: NOW + HOUR, trigger: 'MISS_ME' }, NOW), 'invalid-argument', 'trigger');
    await rejects(createSurprise(db, a, { type: 'OPEN_WHEN', title: 'x', text: 'y' }, NOW), 'invalid-argument', 'trigger');
    await rejects(createSurprise(db, a, { type: 'CAPSULE', title: 'x', scheduledFor: NOW + HOUR }, NOW), 'invalid-argument', 'text');
    await rejects(createSurprise(db, a, { ...base, scheduledFor: NOW + HOUR, extra: 1 }, NOW), 'invalid-argument', 'unknown field extra');
    await rejects(
      createSurprise(db, a, { ...base, scheduledFor: NOW + HOUR, mediaId: 'not_theirs' }, NOW),
      'not-found',
      'media',
    );
    await rejects(
      createSurprise(db, 'nobody', { ...base, scheduledFor: NOW + HOUR }, NOW),
      'failed-precondition',
      'not-paired',
    );
  });

  it('marks what nobody opened as expired, long after its moment', async () => {
    const { a, coupleId } = await couple();
    const left = await createSurprise(
      db,
      a,
      { type: 'SCHEDULED', title: 'For Friday', text: 'the secret', scheduledFor: NOW + HOUR },
      NOW,
    );
    await deliverDue(db, NOW + HOUR);

    assert.deepEqual(await expireSurprises(db, NOW + 2 * HOUR, 30 * 24 * HOUR), { expired: 0 });
    assert.deepEqual(await expireSurprises(db, NOW + 31 * 24 * HOUR, 30 * 24 * HOUR), { expired: 1 });
    assert.equal((await read(`couples/${coupleId}/surprises/${left.surpriseId}`))!.state, 'EXPIRED');
  });
});
