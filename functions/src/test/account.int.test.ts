/**
 * The account itself, against the real emulator (BUILD_PROMPT.md sections 3.5, 8, 14.9):
 * reporting, an export that is a real file, and a deletion that leaves nothing behind.
 *
 * Requires a running emulator:
 *   firebase emulators:start --only auth,firestore,storage --project afterhours-dev-emulator
 */
process.env.FIRESTORE_EMULATOR_HOST ??= '127.0.0.1:8080';
process.env.FIREBASE_STORAGE_EMULATOR_HOST ??= '127.0.0.1:9199';
process.env.FIREBASE_AUTH_EMULATOR_HOST ??= '127.0.0.1:9099';
process.env.FUNCTIONS_EMULATOR ??= 'true';
process.env.GCLOUD_PROJECT ??= 'afterhours-int-test';

import assert from 'node:assert/strict';
import { beforeEach, describe, it } from 'node:test';
import { initializeApp } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { Timestamp, getFirestore } from 'firebase-admin/firestore';
import { getStorage } from 'firebase-admin/storage';
import { buildExport, requestDataExport } from '../account/export';
import { deleteAccount } from '../account/delete';
import { reportContent } from '../account/report';
import { recomputeCouple } from '../couple/recompute';
import { loadCatalogue, resetCatalogueCache } from '../engine/catalogue';
import { DEFAULT_WEIGHTS } from '../engine/types';
import { generateExperience, type GenerateDeps } from '../experience/generate';
import { bucketOf } from '../media/media';
import { saveMemory } from '../memories/memories';
import { createPairingCode, requestPairing, respondToPairing } from '../pairing/pairing';
import { ServiceError } from '../shared/errors';
import { createSurprise } from '../surprises/surprises';
import { grantPremium } from './support';

const PROJECT = 'afterhours-int-test';
const app = initializeApp({ projectId: PROJECT, storageBucket: `${PROJECT}.firebasestorage.app` }, 'account-int-test');
const db = getFirestore(app);
const auth = getAuth(app);
const storage = getStorage(app);
const NOW = Date.UTC(2026, 8, 25, 20, 0, 0);

const deps: GenerateDeps = {
  catalogue: loadCatalogue,
  weights: async () => DEFAULT_WEIGHTS,
  seed: () => 3,
};

const NIGHT = {
  mode: 'TOGETHER',
  durationMin: 30,
  moods: ['naughty'],
  intensity: 3,
  leadership: 'RANDOM',
  novelty: 'MIXED',
  media: {},
};

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
  const a = `acct_a_${counter}`;
  const b = `acct_b_${counter}`;
  for (const uid of [a, b]) {
    await db.doc(`users/${uid}`).set({ contentLevel: 5, displayName: uid });
    await auth.createUser({ uid, email: `${uid}@example.com`, password: 'correct-horse-43' }).catch(() => undefined);
  }
  const { code } = await createPairingCode(db, a, NOW);
  await requestPairing(db, b, code, NOW);
  const { coupleId } = await respondToPairing(db, a, true, NOW);
  assert.ok(coupleId);
  await grantPremium(db, a, NOW);
  await recomputeCouple(db, coupleId, NOW);
  return { a, b, coupleId };
}

async function rejects(work: Promise<unknown>, code: string, reason?: string) {
  await assert.rejects(work, (error: unknown) => {
    assert.ok(error instanceof ServiceError, `expected a ServiceError, got ${error}`);
    assert.equal(error.code, code);
    if (reason) assert.equal(error.reason, reason);
    return true;
  });
}

describe('reporting', () => {
  beforeEach(async () => {
    await clearEmulator();
    resetCatalogueCache();
  });

  it('files a report where neither of them can ever read it', async () => {
    const { a, b, coupleId } = await couple();

    const { reportId } = await reportContent(db, a, { target: 'CONTENT', id: 'flirt_tell_me_something_nice', reason: 'INAPPROPRIATE' }, NOW);

    const report = (await db.doc(`reports/${reportId}`).get()).data()!;
    assert.equal(report.target, 'CONTENT');
    assert.equal(report.reason, 'INAPPROPRIATE');
    assert.equal(report.reportedBy, a);
    assert.equal(report.coupleId, coupleId);
    assert.equal(report.state, 'OPEN');
    // Nothing about the report is written anywhere the partner could reach (section 3.5).
    assert.equal((await db.collection(`couples/${coupleId}/reports`).get()).size, 0);
    assert.equal((await db.collection(`users/${b}/reports`).get()).size, 0);
  });

  it('reports a partner media item, and refuses one that is not theirs', async () => {
    const { a, b, coupleId } = await couple();
    await db.doc(`couples/${coupleId}/media/m1`).set({ ownerUid: b, state: 'SENT', kind: 'PHOTO' });

    assert.ok((await reportContent(db, a, { target: 'MEDIA', id: 'm1', reason: 'UNSAFE' }, NOW)).reportId);
    await rejects(reportContent(db, a, { target: 'MEDIA', id: 'nothing', reason: 'UNSAFE' }, NOW), 'not-found', 'media');
    await rejects(reportContent(db, a, { target: 'CONTENT', id: 'x', reason: 'RUDE' }, NOW), 'invalid-argument', 'reason');
    await rejects(reportContent(db, a, { target: 'OTHER', id: 'x', reason: 'BROKEN' }, NOW), 'invalid-argument', 'target');
    await rejects(
      reportContent(db, a, { target: 'CONTENT', id: 'x', reason: 'BROKEN', note: 'a paragraph' }, NOW),
      'invalid-argument',
      'unknown field note',
    );
  });
});

describe('a data export', () => {
  beforeEach(async () => {
    await clearEmulator();
    resetCatalogueCache();
  });

  it('holds their own data, and not one field of their partner\'s', async () => {
    const { a, b, coupleId } = await couple();
    await db.doc(`users/${a}/preferences/sensory_blindfold`).set({ value: 'CURIOUS', secret: true });
    await db.doc(`users/${b}/preferences/roleplay_strangers`).set({ value: 'YES', secret: false });
    await db.doc(`users/${b}/boundaries/teasing_instructions`).set({ level: 'NEVER', note: "b's own note" });
    const night = await generateExperience(db, a, NIGHT, NOW, deps);
    await db.doc(`couples/${coupleId}/experiences/${night.experienceId}`).update({ status: 'COMPLETED' });
    await saveMemory(db, a, { experienceId: night.experienceId, title: 'The Tuesday one' }, NOW);

    const data = await buildExport(db, a, NOW);

    const asText = JSON.stringify(data);
    assert.equal((data.preferences as unknown[]).length, 1);
    assert.ok(asText.includes('sensory_blindfold'), 'their own answer is in it');
    // The partner's private documents are nowhere in it (section 5.1).
    assert.ok(!asText.includes('roleplay_strangers'), "the partner's answer leaked");
    assert.ok(!asText.includes("b's own note"), "the partner's limit leaked");
    // What they share is, because both of them can already see it.
    const shared = data.couple as Record<string, unknown>;
    assert.equal((shared.memories as unknown[]).length, 1);
    assert.equal((shared.nights as unknown[]).length, 1);
    // And no purchase token, which is a credential rather than a fact about a person.
    assert.ok(!asText.includes('purchaseToken'));
  });

  it('keeps a surprise still waiting closed, even in its author\'s own export', async () => {
    const { a, b, coupleId } = await couple();
    await createSurprise(db, b, { type: 'SCHEDULED', title: 'For Friday', text: 'the inside of it', scheduledFor: NOW + 60_000 }, NOW);

    const data = await buildExport(db, a, NOW);

    const asText = JSON.stringify(data);
    assert.ok(!asText.includes('the inside of it'), 'a payload leaked into an export');
    assert.ok(!asText.includes('For Friday'), 'a title leaked before it was delivered');
    const surprises = (data.couple as Record<string, unknown>).surprises as Record<string, unknown>[];
    assert.equal(surprises.length, 1);
    assert.equal(surprises[0].state, 'SCHEDULED');
    assert.equal(surprises[0].mine, false);
    assert.ok((await db.collection(`couples/${coupleId}/surprises`).get()).size === 1);
  });

  it('writes a real file, where only they can read it', async () => {
    const { a } = await couple();

    const result = await requestDataExport(db, storage, a, NOW);

    assert.equal(result.exportPath, `users/${a}/exports/afterhours-${NOW}.json`);
    assert.ok(result.bytes > 0);
    const [exists] = await bucketOf(storage).file(result.exportPath).exists();
    assert.ok(exists, 'the export is not in Storage');
    const [body] = await bucketOf(storage).file(result.exportPath).download();
    const parsed = JSON.parse(body.toString('utf8')) as Record<string, unknown>;
    assert.equal((parsed.account as Record<string, unknown>).uid, a);
    assert.equal(parsed.exportedAt, NOW);
    const [metadata] = await bucketOf(storage).file(result.exportPath).getMetadata();
    assert.equal(metadata.metadata?.ownerUid, a);
  });
});

describe('deleting an account', () => {
  beforeEach(async () => {
    await clearEmulator();
    resetCatalogueCache();
  });

  it('asks to be sure, and does nothing until it is', async () => {
    const { a } = await couple();

    await rejects(deleteAccount(db, auth, storage, a, {}, NOW), 'failed-precondition', 'not-confirmed');
    await rejects(deleteAccount(db, auth, storage, a, { confirm: false }, NOW), 'failed-precondition', 'not-confirmed');
    await rejects(deleteAccount(db, auth, storage, a, { confirm: true, also: 'them' }, NOW), 'invalid-argument', 'unknown field also');

    assert.ok((await db.doc(`users/${a}`).get()).exists);
    assert.ok(await auth.getUser(a));
  });

  it('cascades across Auth, Firestore, Storage and the couple', async () => {
    const { a, b, coupleId } = await couple();
    await db.doc(`users/${a}/preferences/sensory_blindfold`).set({ value: 'CURIOUS', secret: true });
    await db.doc(`users/${a}/fcmTokens/phone`).set({ token: 'phone', platform: 'ANDROID' });
    const night = await generateExperience(db, a, NIGHT, NOW, deps);
    await db.doc(`couples/${coupleId}/experiences/${night.experienceId}`).update({ status: 'COMPLETED' });
    await saveMemory(db, a, { experienceId: night.experienceId, title: 'Ours' }, NOW);
    await db.doc(`couples/${coupleId}/sessions/s1`).set({ state: 'COMPLETED', kind: 'ONE_DEVICE' });
    await db.doc(`couples/${coupleId}/sessions/s1/rounds/0`).set({ chapter: 0, answers: { [a]: 'mine', [b]: 'theirs' } });
    await bucketOf(storage).file(`couples/${coupleId}/media/m1/photo.jpg`).save(Buffer.from('bytes'));
    await bucketOf(storage).file(`users/${a}/exports/old.json`).save(Buffer.from('{}'));

    const result = await deleteAccount(db, auth, storage, a, { confirm: true }, NOW);

    assert.ok(result.coupleEnded);
    assert.ok(result.documents > 0);
    assert.ok(result.objects >= 2);
    // Nothing unreachable: a cascade that finishes leaves no failures behind (D-094).
    assert.equal(result.failures, 0);
    // Firestore: the account, everything under it, the couple and everything under it.
    assert.equal((await db.doc(`users/${a}`).get()).exists, false);
    assert.equal((await db.collection(`users/${a}/preferences`).get()).size, 0);
    assert.equal((await db.doc(`couples/${coupleId}`).get()).exists, false);
    assert.equal((await db.collection(`couples/${coupleId}/memories`).get()).size, 0);
    assert.equal((await db.collection(`couples/${coupleId}/sessions/s1/rounds`).get()).size, 0);
    // Storage: nothing of theirs and nothing of the couple's.
    const [left] = await bucketOf(storage).getFiles({ prefix: `couples/${coupleId}/` });
    assert.equal(left.length, 0);
    const [mine] = await bucketOf(storage).getFiles({ prefix: `users/${a}/` });
    assert.equal(mine.length, 0);
    // Auth: gone.
    await assert.rejects(auth.getUser(a));
    // The partner keeps their own account, and is no longer in a couple.
    assert.ok((await db.doc(`users/${b}`).get()).exists);
    assert.equal((await db.doc(`users/${b}`).get()).get('coupleId'), null);
    assert.ok(await auth.getUser(b));
  });

  it('is safe to run twice, and works for an account with no couple at all', async () => {
    const { a } = await couple();

    await deleteAccount(db, auth, storage, a, { confirm: true }, NOW);
    const again = await deleteAccount(db, auth, storage, a, { confirm: true }, NOW);

    assert.equal(again.documents, 0);
    assert.equal(again.coupleEnded, false);
    assert.equal(again.failures, 0);

    const lonely = `acct_lonely_${counter}`;
    await db.doc(`users/${lonely}`).set({ contentLevel: 3 });
    await auth.createUser({ uid: lonely, email: `${lonely}@example.com`, password: 'correct-horse-43' });
    const result = await deleteAccount(db, auth, storage, lonely, { confirm: true }, NOW);
    assert.equal(result.coupleEnded, false);
    assert.equal((await db.doc(`users/${lonely}`).get()).exists, false);
    await assert.rejects(auth.getUser(lonely));
  });

  it('takes the scheduled things with it, so nothing fires for an account that is gone', async () => {
    const { a, b, coupleId } = await couple();
    await createSurprise(db, b, { type: 'SCHEDULED', title: 'For Friday', text: 'x', scheduledFor: NOW + 60_000 }, NOW);
    await db.doc(`couples/${coupleId}/arcs/arc1`).set({ state: 'ACTIVE', lengthDays: 3, days: [] });
    await db.doc(`couples/${coupleId}/countdowns/c1`).set({ title: 'Until we meet', targetAt: Timestamp.fromMillis(NOW) });

    await deleteAccount(db, auth, storage, a, { confirm: true }, NOW);

    // The sweeps look at collections that no longer exist, which is the point.
    assert.equal((await db.collection(`couples/${coupleId}/surprises`).get()).size, 0);
    assert.equal((await db.collection(`couples/${coupleId}/arcs`).get()).size, 0);
    assert.equal((await db.collection(`couples/${coupleId}/countdowns`).get()).size, 0);
    const stray = await db.collectionGroup('payload').get();
    assert.equal(stray.docs.filter((doc) => doc.ref.path.includes(coupleId)).length, 0);
  });
});
