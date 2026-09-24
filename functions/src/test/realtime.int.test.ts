/**
 * Two phones in one session against the real Firestore emulator (BUILD_PROMPT.md section 12):
 * each partner's own answer, the reveal written once from the pair, the chapter complete only
 * when both have seen it, either partner moving it on — and a session nobody is in expiring.
 *
 * Requires a running emulator:
 *   firebase emulators:start --only auth,firestore,storage --project afterhours-dev-emulator
 */
process.env.FIRESTORE_EMULATOR_HOST ??= '127.0.0.1:8080';

import assert from 'node:assert/strict';
import { beforeEach, describe, it } from 'node:test';
import { initializeApp } from 'firebase-admin/app';
import { getFirestore, Timestamp, type DocumentData } from 'firebase-admin/firestore';
import { recomputeCouple } from '../couple/recompute';
import { grantPremium } from './support';
import { loadCatalogue, resetCatalogueCache } from '../engine/catalogue';
import { DEFAULT_WEIGHTS } from '../engine/types';
import { startGame } from '../experience/game';
import { type GenerateDeps } from '../experience/generate';
import { createPairingCode, requestPairing, respondToPairing } from '../pairing/pairing';
import { EXPIRE_AFTER_MS, expireStale } from '../session/expiry';
import { advanceSession, createSession, markSeen } from '../session/session';
import { applySubmission } from '../session/submissions';
import { ServiceError } from '../shared/errors';

const PROJECT = 'afterhours-int-test';
const db = getFirestore(initializeApp({ projectId: PROJECT }, 'realtime-int-test'));
const NOW = Date.UTC(2026, 8, 23, 21, 0, 0);

async function clearEmulator(): Promise<void> {
  const res = await fetch(
    `http://${process.env.FIRESTORE_EMULATOR_HOST}/emulator/v1/projects/${PROJECT}/databases/(default)/documents`,
    { method: 'DELETE' },
  );
  assert.ok(res.ok, `emulator clear failed: ${res.status}`);
}

const deps: GenerateDeps = { catalogue: loadCatalogue, weights: async () => DEFAULT_WEIGHTS, seed: () => 4242 };

let counter = 0;
async function couple() {
  counter += 1;
  const a = `rt_host_${counter}`;
  const b = `rt_partner_${counter}`;
  await db.doc(`users/${a}`).set({ contentLevel: 3 });
  await db.doc(`users/${b}`).set({ contentLevel: 3 });
  const { code } = await createPairingCode(db, a, NOW);
  await requestPairing(db, b, code, NOW);
  const { coupleId } = await respondToPairing(db, a, true, NOW);
  assert.ok(coupleId);
  await grantPremium(db, a, NOW);
  await recomputeCouple(db, coupleId, NOW);
  return { a, b, coupleId };
}

/** A game for the couple, its session opened for two phones and moved to its first round. */
async function playing(levels = 3) {
  const { a, b, coupleId } = await couple();
  const game = await startGame(db, a, { game: 'WOULD_YOU_RATHER', intensity: levels }, NOW, deps);
  const session = await createSession(db, a, { experienceId: game.experienceId, kind: 'TWO_DEVICE' }, NOW);
  await advanceSession(db, a, { sessionId: session.sessionId, fromChapter: -1 }, NOW, deps);
  return { a, b, coupleId, game, sessionId: session.sessionId };
}

/** What a phone writes for itself; the rules allow exactly this (rules tests D–F). */
async function submit(coupleId: string, sessionId: string, uid: string, chapter: number, answer: string | null) {
  const id = `${chapter}_${uid}`;
  await db.doc(`couples/${coupleId}/sessions/${sessionId}/submissions/${id}`).set({
    chapter,
    byUid: uid,
    answer,
    at: Timestamp.fromMillis(NOW),
  });
  await applySubmission(db, coupleId, sessionId, id, NOW);
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

describe('two phones, one session', () => {
  beforeEach(async () => {
    await clearEmulator();
    resetCatalogueCache();
  });

  it('waits for both answers, reveals once, and finishes the chapter when both have seen it', async () => {
    const { a, b, coupleId, sessionId } = await playing();
    const session = () => read(`couples/${coupleId}/sessions/${sessionId}`);

    await submit(coupleId, sessionId, a, 0, '0');
    assert.equal((await session())!.state, 'AWAITING_SUBMISSIONS');
    assert.equal(await read(`couples/${coupleId}/sessions/${sessionId}/rounds/0`), undefined, 'nothing to see yet');

    await submit(coupleId, sessionId, b, 0, '1');
    const revealed = await session();
    assert.equal(revealed!.state, 'REVEAL');
    const reveal = await read(`couples/${coupleId}/sessions/${sessionId}/rounds/0`);
    assert.deepEqual(reveal!.answers, { [a]: '0', [b]: '1' });

    assert.equal((await markSeen(db, a, { sessionId, chapter: 0 }, NOW)).state, 'REVEAL');
    assert.equal((await markSeen(db, b, { sessionId, chapter: 0 }, NOW)).state, 'CHAPTER_COMPLETE');
  });

  it('lets either phone move the chapter on, and starts the next one with nobody having answered', async () => {
    const { a, b, coupleId, sessionId } = await playing();
    await submit(coupleId, sessionId, a, 0, '0');
    await submit(coupleId, sessionId, b, 0, '0');
    await markSeen(db, a, { sessionId, chapter: 0 }, NOW);
    await markSeen(db, b, { sessionId, chapter: 0 }, NOW);

    // The partner who did not start it moves it on: on two phones neither is the host (D-074).
    const next = await advanceSession(db, b, { sessionId, fromChapter: 0 }, NOW, deps);
    assert.equal(next.state, 'CHAPTER_ACTIVE');
    assert.equal(next.currentChapter, 1);
    const stored = await read(`couples/${coupleId}/sessions/${sessionId}`);
    assert.deepEqual(stored!.submittedBy, {});
    assert.deepEqual(stored!.seenBy, {});
    const history = await read(`couples/${coupleId}/engineState/history`);
    assert.equal(Object.keys(history!.lastPlayed).length, 1, 'the chapter played is remembered');
  });

  it('two answers landing together reveal once', async () => {
    const { a, b, coupleId, sessionId } = await playing();
    await Promise.all([submit(coupleId, sessionId, a, 0, 'a'), submit(coupleId, sessionId, b, 0, 'b')]);
    const session = await read(`couples/${coupleId}/sessions/${sessionId}`);
    assert.equal(session!.state, 'REVEAL');
    const rounds = await db.collection(`couples/${coupleId}/sessions/${sessionId}/rounds`).get();
    assert.equal(rounds.size, 1);
    assert.deepEqual(rounds.docs[0].get('answers'), { [a]: 'a', [b]: 'b' });
  });

  it('a retried answer, or one for a chapter that has moved on, changes nothing', async () => {
    const { a, b, coupleId, sessionId } = await playing();
    await submit(coupleId, sessionId, a, 0, '0');
    const before = await read(`couples/${coupleId}/sessions/${sessionId}`);
    await applySubmission(db, coupleId, sessionId, `0_${a}`, NOW);
    const after = await read(`couples/${coupleId}/sessions/${sessionId}`);
    assert.deepEqual(after!.submittedBy, before!.submittedBy);
    assert.equal(after!.state, 'AWAITING_SUBMISSIONS');

    // An answer for a chapter nobody is on is ignored: the session is not moved by it.
    await submit(coupleId, sessionId, b, 3, 'late');
    assert.equal((await read(`couples/${coupleId}/sessions/${sessionId}`))!.state, 'AWAITING_SUBMISSIONS');
  });

  it('the chapter cannot be seen or moved on before its reveal', async () => {
    const { a, coupleId, sessionId } = await playing();
    await rejects(markSeen(db, a, { sessionId, chapter: 0 }, NOW), 'failed-precondition', 'illegal-transition');
    await submit(coupleId, sessionId, a, 0, '0');
    await rejects(markSeen(db, a, { sessionId, chapter: 0 }, NOW), 'failed-precondition', 'illegal-transition');
  });

  it('on one phone, only its host moves the night on', async () => {
    const { a, b, coupleId } = await couple();
    const game = await startGame(db, a, { game: 'WOULD_YOU_RATHER', intensity: 3 }, NOW, deps);
    const { sessionId } = await createSession(db, a, { experienceId: game.experienceId }, NOW);
    await advanceSession(db, a, { sessionId, fromChapter: -1 }, NOW, deps);
    await rejects(advanceSession(db, b, { sessionId, fromChapter: 0 }, NOW, deps), 'permission-denied', 'not-host');
    assert.equal((await read(`couples/${coupleId}/sessions/${sessionId}`))!.kind, 'ONE_DEVICE');
  });
});

describe('a session nobody is in', () => {
  beforeEach(async () => {
    await clearEmulator();
    resetCatalogueCache();
  });

  it('expires after ten minutes of silence, freeing the couple', async () => {
    const { coupleId, sessionId } = await playing();
    const later = NOW + EXPIRE_AFTER_MS + 1_000;

    assert.deepEqual(await expireStale(db, NOW + 1_000), { expired: 0, warned: 0 }, 'a session under way is left alone');
    assert.deepEqual(await expireStale(db, later), { expired: 1, warned: 0 });

    const session = await read(`couples/${coupleId}/sessions/${sessionId}`);
    assert.equal(session!.state, 'ABANDONED');
    assert.equal(session!.endReason, 'EXPIRED');
    assert.equal((await read(`couples/${coupleId}`))!.activeSessionId, undefined);
    assert.deepEqual(await expireStale(db, later), { expired: 0, warned: 0 }, 'and stays ended');
  });

  it('is left alone while either phone is still there', async () => {
    const { b, coupleId, sessionId } = await playing();
    const later = NOW + EXPIRE_AFTER_MS + 1_000;
    await db.doc(`couples/${coupleId}/sessions/${sessionId}/presence/${b}`).set({
      at: Timestamp.fromMillis(later - 20_000),
    });

    assert.deepEqual(await expireStale(db, later), { expired: 0, warned: 0 });
    assert.equal((await read(`couples/${coupleId}/sessions/${sessionId}`))!.state, 'CHAPTER_ACTIVE');
  });
});
