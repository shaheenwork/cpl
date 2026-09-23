/**
 * Playing a night against the real Firestore emulator (BUILD_PROMPT.md sections 3.1, 8, 12):
 * consent from each partner, one-tap PAUSE and STOP from either phone, advancing that no
 * retry or race can double, what a night leaves behind for cooldowns, and the rest of a night
 * held to a limit set halfway through it.
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
import { loadCatalogue, resetCatalogueCache } from '../engine/catalogue';
import { DEFAULT_WEIGHTS, type EngineItem } from '../engine/types';
import { generateExperience, type GenerateDeps } from '../experience/generate';
import { openPlan, planNight } from '../experience/plan';
import { createPairingCode, requestPairing, respondToPairing, unpairCouple } from '../pairing/pairing';
import { expireStale } from '../session/expiry';
import { STALE_SESSION_MS, advanceSession, createSession, endSession, markReady } from '../session/session';
import { ServiceError } from '../shared/errors';

const PROJECT = 'afterhours-int-test';
const db = getFirestore(initializeApp({ projectId: PROJECT }, 'session-int-test'));
const NOW = Date.UTC(2026, 8, 22, 21, 0, 0);

async function clearEmulator(): Promise<void> {
  const res = await fetch(
    `http://${process.env.FIRESTORE_EMULATOR_HOST}/emulator/v1/projects/${PROJECT}/databases/(default)/documents`,
    { method: 'DELETE' },
  );
  assert.ok(res.ok, `emulator clear failed: ${res.status}`);
}

const deps = (seed = 2024): GenerateDeps => ({
  catalogue: loadCatalogue,
  weights: async () => DEFAULT_WEIGHTS,
  seed: () => seed,
});

let counter = 0;
async function couple(levels: [number, number] = [5, 5]) {
  counter += 1;
  const a = `host_${counter}`;
  const b = `partner_${counter}`;
  await db.doc(`users/${a}`).set({ contentLevel: levels[0] });
  await db.doc(`users/${b}`).set({ contentLevel: levels[1] });
  const { code } = await createPairingCode(db, a, NOW);
  await requestPairing(db, b, code, NOW);
  const { coupleId } = await respondToPairing(db, a, true, NOW);
  assert.ok(coupleId);
  await recomputeCouple(db, coupleId, NOW);
  return { a, b, coupleId };
}

async function night(uid: string, intensity = 3, seed = 2024, durationMin = 30) {
  return generateExperience(
    db,
    uid,
    { mode: 'TOGETHER', durationMin, moods: ['romantic'], intensity, leadership: 'ME', novelty: 'MIXED' },
    NOW,
    deps(seed),
  );
}

async function read(path: string): Promise<DocumentData> {
  return (await db.doc(path).get()).data()!;
}

async function rejects(work: Promise<unknown>, code: string, reason?: string) {
  await assert.rejects(work, (error: unknown) => {
    assert.ok(error instanceof ServiceError, `expected a ServiceError, got ${error}`);
    assert.equal(error.code, code);
    if (reason) assert.equal(error.reason, reason);
    return true;
  });
}

const advance = (uid: string, sessionId: string, fromChapter: number, playedItemId?: string, at = NOW) =>
  advanceSession(db, uid, { sessionId, fromChapter, ...(playedItemId ? { playedItemId } : {}) }, at);

describe('playing a night', () => {
  beforeEach(async () => {
    await clearEmulator();
    resetCatalogueCache();
  });

  it("plays a night from start to finish on the host's phone, and remembers what was played", async () => {
    const { a, coupleId } = await couple();
    const built = await night(a, 3);
    const { sessionId, state, consentRequired, chapterCount } = await createSession(db, a, { experienceId: built.experienceId }, NOW);
    assert.equal(state, 'READY');
    assert.equal(consentRequired, false);
    assert.equal(chapterCount, built.chapters.length);
    assert.equal((await read(`couples/${coupleId}`)).activeSessionId, sessionId);
    assert.equal((await read(`couples/${coupleId}/experiences/${built.experienceId}`)).status, 'IN_PROGRESS');

    let view = await advance(a, sessionId, -1);
    assert.equal(view.state, 'CHAPTER_ACTIVE');
    assert.equal(view.currentChapter, 0);
    assert.deepEqual(view.blocked, []);
    for (let chapter = 0; chapter < chapterCount; chapter++) view = await advance(a, sessionId, chapter);
    assert.equal(view.state, 'COMPLETED');

    const session = await read(`couples/${coupleId}/sessions/${sessionId}`);
    assert.equal(session.endReason, 'COMPLETED');
    assert.ok(session.startedAt && session.endedAt);
    const experience = await read(`couples/${coupleId}/experiences/${built.experienceId}`);
    assert.equal(experience.status, 'COMPLETED');
    assert.equal(experience.playedThrough, chapterCount);
    assert.ok(experience.chapters.every((chapter: DocumentData) => chapter.state === 'DONE'));
    const history = await read(`couples/${coupleId}/engineState/history`);
    for (const chapter of built.chapters) assert.equal(history.lastPlayed[chapter.contentId], NOW);
    const after = await read(`couples/${coupleId}`);
    assert.equal(after.activeSessionId, undefined, 'the couple is free for the next night');
    assert.equal(after.stats.nightsPlayed, 1);
  });

  it('a BOLD or WILD night waits for both partners\' "I\'m in", each from their own phone', async () => {
    const { a, b, coupleId } = await couple();
    const built = await night(a, 5);
    const expected = built.chapters.some((chapter) => chapter.intensity >= 4);
    assert.ok(expected, 'a WILD request for a 5/5 couple reaches BOLD at least');
    const { sessionId, state, consentRequired } = await createSession(db, a, { experienceId: built.experienceId }, NOW);
    assert.equal(consentRequired, true);
    assert.equal(state, 'WAITING_FOR_PARTNER');

    await rejects(advance(a, sessionId, -1), 'failed-precondition', 'illegal-transition');
    assert.equal((await markReady(db, a, { sessionId }, NOW)).state, 'WAITING_FOR_PARTNER');
    await rejects(advance(a, sessionId, -1), 'failed-precondition', 'illegal-transition');
    // Saying it twice is not saying it for someone else.
    assert.equal((await markReady(db, a, { sessionId }, NOW)).state, 'WAITING_FOR_PARTNER');
    assert.equal((await markReady(db, b, { sessionId }, NOW)).state, 'READY');

    const session = await read(`couples/${coupleId}/sessions/${sessionId}`);
    assert.deepEqual(Object.keys(session.consentAcks).sort(), [a, b].sort());
    assert.ok(session.participants[a].ready && session.participants[b].ready);
    assert.equal((await advance(a, sessionId, -1)).state, 'CHAPTER_ACTIVE');
  });

  it('either partner can pause, resume and stop from their own phone; stop names no one', async () => {
    const { a, b, coupleId } = await couple();
    const built = await night(a, 3);
    const { sessionId } = await createSession(db, a, { experienceId: built.experienceId }, NOW);
    await advance(a, sessionId, -1);
    await advance(a, sessionId, 0);

    assert.equal((await endSession(db, b, { sessionId, action: 'PAUSE' }, NOW)).state, 'PAUSED');
    await rejects(advance(a, sessionId, 1), 'failed-precondition', 'illegal-transition');
    const resumed = await endSession(db, a, { sessionId, action: 'RESUME' }, NOW);
    assert.equal(resumed.state, 'CHAPTER_ACTIVE');
    assert.equal(resumed.currentChapter, 1);

    assert.equal((await endSession(db, b, { sessionId, action: 'STOP' }, NOW)).state, 'ABANDONED');
    // A second tap, from either phone, is not an error.
    assert.equal((await endSession(db, a, { sessionId, action: 'STOP' }, NOW)).state, 'ABANDONED');

    const session = await read(`couples/${coupleId}/sessions/${sessionId}`);
    assert.equal(session.endReason, 'STOPPED');
    for (const key of Object.keys(session)) assert.ok(!/endedBy|stoppedBy/i.test(key), `stored ${key}`);
    const experience = await read(`couples/${coupleId}/experiences/${built.experienceId}`);
    assert.equal(experience.status, 'ABANDONED');
    assert.equal(experience.playedThrough, 2, 'the chapter under way counts as played');
    const history = await read(`couples/${coupleId}/engineState/history`);
    assert.deepEqual(Object.keys(history.lastPlayed).sort(), built.chapters.slice(0, 2).map((chapter) => chapter.contentId).sort());
    assert.equal((await read(`couples/${coupleId}`)).activeSessionId, undefined);
    await rejects(advance(a, sessionId, 1), 'failed-precondition', 'illegal-transition');
    await rejects(endSession(db, a, { sessionId, action: 'RESUME' }, NOW), 'failed-precondition', 'illegal-transition');
  });

  it('declining a night that waits for consent ends it, and records nothing as played', async () => {
    const { a, b, coupleId } = await couple();
    const built = await night(a, 5);
    const { sessionId } = await createSession(db, a, { experienceId: built.experienceId }, NOW);
    await markReady(db, a, { sessionId }, NOW);
    assert.equal((await endSession(db, b, { sessionId, action: 'DECLINE' }, NOW)).state, 'ABANDONED');
    assert.equal((await read(`couples/${coupleId}/sessions/${sessionId}`)).endReason, 'DECLINED');
    assert.equal((await db.doc(`couples/${coupleId}/engineState/history`).get()).exists, false);
  });

  it('a retried or doubled advance never skips a chapter', async () => {
    const { a, coupleId } = await couple();
    const built = await night(a, 3);
    const { sessionId } = await createSession(db, a, { experienceId: built.experienceId }, NOW);
    await advance(a, sessionId, -1);
    const [first, second] = await Promise.all([advance(a, sessionId, 0), advance(a, sessionId, 0)]);
    assert.equal(first.currentChapter, 1);
    assert.equal(second.currentChapter, 1);
    assert.equal((await advance(a, sessionId, 0)).currentChapter, 1, 'a late retry changes nothing');
    assert.equal((await advance(a, sessionId, -1)).currentChapter, 1, 'nor does a late start');
    assert.equal((await read(`couples/${coupleId}/sessions/${sessionId}`)).currentChapter, 1);
    await rejects(advance(a, sessionId, 3), 'failed-precondition', 'illegal-transition');
  });

  it('only the host moves the night on', async () => {
    const { a, b } = await couple();
    const built = await night(a, 3);
    const { sessionId } = await createSession(db, a, { experienceId: built.experienceId }, NOW);
    await rejects(advance(b, sessionId, -1), 'permission-denied', 'not-host');
  });

  it('a swapped chapter must be one the server released for this night', async () => {
    const { a, coupleId } = await couple();
    const built = await night(a, 3);
    const { sessionId } = await createSession(db, a, { experienceId: built.experienceId }, NOW);
    await advance(a, sessionId, -1);

    await rejects(advance(a, sessionId, 0, 'never_released_item'), 'invalid-argument', 'playedItemId');
    await rejects(advance(a, sessionId, 0, built.chapters[2].contentId), 'invalid-argument', 'playedItemId');
    const swapped = built.alternates[0];
    await advance(a, sessionId, 0, swapped);

    const experience = await read(`couples/${coupleId}/experiences/${built.experienceId}`);
    assert.deepEqual(experience.chapters[0].contentIds, [swapped]);
    const history = await read(`couples/${coupleId}/engineState/history`);
    assert.equal(history.lastPlayed[swapped], NOW);
    assert.equal(history.lastPlayed[built.chapters[0].contentId], undefined, 'the item swapped out was never played');
  });

  it('a limit set halfway through a night holds for the rest of it', async () => {
    const { a, b, coupleId } = await couple();
    const built = await night(a, 3, 2024, 45);
    const byId = new Map<string, EngineItem>((await loadCatalogue(db, NOW)).items.map((item) => [item.id, item]));
    const { sessionId } = await createSession(db, a, { experienceId: built.experienceId }, NOW);
    await advance(a, sessionId, -1);

    const ahead = built.chapters[2].contentId;
    const theme = byId.get(ahead)!.excludedByBoundaries[0];
    await db.doc(`users/${b}/boundaries/${theme}`).set({ level: 'NEVER', updatedAt: new Date(NOW) });
    await recomputeCouple(db, coupleId, NOW);

    const view = await advance(a, sessionId, 0);
    assert.ok(view.blocked.includes(ahead), 'the chapter coming up is blocked');
    const stillToCome = [...built.chapters.slice(1).map((chapter) => chapter.contentId), ...built.alternates];
    const expected = stillToCome.filter((id) => byId.get(id)!.excludedByBoundaries.includes(theme));
    assert.deepEqual([...view.blocked].sort(), [...new Set(expected)].sort());
    // Nothing already played is named: it is history, not a choice.
    assert.ok(!view.blocked.includes(built.chapters[0].contentId));
  });

  it('holds one night at a time, and lets a forgotten one expire', async () => {
    const { a, b, coupleId } = await couple();
    const first = await night(a, 3, 1);
    const second = await night(b, 3, 2);
    const { sessionId } = await createSession(db, a, { experienceId: first.experienceId }, NOW);
    await advance(a, sessionId, -1);

    await rejects(createSession(db, b, { experienceId: second.experienceId }, NOW), 'failed-precondition', 'session-in-progress');
    // Asking again for the same night is not a second night.
    assert.equal((await createSession(db, a, { experienceId: first.experienceId }, NOW)).sessionId, sessionId);

    const later = NOW + STALE_SESSION_MS + 60_000;
    const replacement = await createSession(db, b, { experienceId: second.experienceId }, later);
    assert.notEqual(replacement.sessionId, sessionId);
    const expired = await read(`couples/${coupleId}/sessions/${sessionId}`);
    assert.equal(expired.state, 'ABANDONED');
    assert.equal(expired.endReason, 'EXPIRED');
    assert.equal((await read(`couples/${coupleId}/experiences/${first.experienceId}`)).status, 'ABANDONED');
    assert.equal((await read(`couples/${coupleId}`)).activeSessionId, replacement.sessionId);
  });

  it('a night that ended cannot be started again', async () => {
    const { a } = await couple();
    const built = await night(a, 3);
    const { sessionId } = await createSession(db, a, { experienceId: built.experienceId }, NOW);
    await endSession(db, a, { sessionId, action: 'STOP' }, NOW);
    await rejects(createSession(db, a, { experienceId: built.experienceId }, NOW), 'failed-precondition', 'experience-not-ready');
  });

  it('refuses strangers, ended couples and payloads with fields it does not know', async () => {
    const { a } = await couple();
    const built = await night(a, 3);
    await rejects(createSession(db, a, { experienceId: built.experienceId, coupleId: 'x' }, NOW), 'invalid-argument', 'unknown field coupleId');
    await rejects(createSession(db, a, { experienceId: '../../users/x' }, NOW), 'invalid-argument', 'experienceId');
    await rejects(createSession(db, a, { experienceId: 'doesNotExist' }, NOW), 'not-found', 'experience');
    await rejects(createSession(db, 'stranger', { experienceId: built.experienceId }, NOW), 'failed-precondition', 'not-paired');
    const { sessionId } = await createSession(db, a, { experienceId: built.experienceId }, NOW);
    await rejects(endSession(db, a, { sessionId, action: 'EXPLODE' }, NOW), 'invalid-argument', 'action');
    await rejects(advanceSession(db, a, { sessionId, fromChapter: 'one' }, NOW), 'invalid-argument', 'fromChapter');
    await unpairCouple(db, a, NOW);
    await rejects(endSession(db, a, { sessionId, action: 'STOP' }, NOW), 'failed-precondition', 'not-paired');
  });
});

describe('a night planned for the partner, played later', () => {
  beforeEach(async () => {
    await clearEmulator();
    resetCatalogueCache();
  });

  it('completes with the two of them never in it at once (section 12.4)', async () => {
    const { a, b, coupleId } = await couple();

    // Alex plans it, at a level that would need both to say they are in, and goes away.
    const planned = await planNight(
      db,
      a,
      { mode: 'TOGETHER', durationMin: 30, moods: ['romantic'], intensity: 5, leadership: 'ME', novelty: 'MIXED' },
      NOW,
      deps(),
    );
    assert.equal((await read(`couples/${coupleId}/plans/${planned.experienceId}`)).state, 'WAITING');

    // Hours later, Sam finds it and opens it. Alex is not here, and is never asked to be.
    const later = NOW + 6 * 60 * 60 * 1000;
    await openPlan(db, b, { experienceId: planned.experienceId }, later);
    const session = await createSession(db, b, { experienceId: planned.experienceId }, later);
    assert.equal(session.kind, 'ASYNC');
    // Alex said how far it goes by planning it: their "I'm in" is already in, and only Sam's
    // is still to give — if this night reaches a level that needs one at all.
    const opened = await read(`couples/${coupleId}/sessions/${session.sessionId}`);
    assert.deepEqual(opened.consentAcks, { [a]: true });
    if (session.consentRequired) {
      assert.equal(session.state, 'WAITING_FOR_PARTNER');
      assert.equal((await markReady(db, b, { sessionId: session.sessionId }, later)).state, 'READY');
    }

    let view = await advance(b, session.sessionId, -1, undefined, later);
    for (let chapter = 0; chapter < session.chapterCount; chapter++) {
      view = await advance(b, session.sessionId, chapter, undefined, later);
    }
    assert.equal(view.state, 'COMPLETED');
    assert.equal((await read(`couples/${coupleId}/experiences/${planned.experienceId}`)).status, 'COMPLETED');
    // The couple's place is theirs again: the pointer is removed, not emptied.
    assert.equal((await read(`couples/${coupleId}`)).activeSessionId, undefined);
  });

  it('is the partner to open it, and nobody else', async () => {
    const { a, b } = await couple();
    const planned = await planNight(
      db,
      a,
      { mode: 'TOGETHER', durationMin: 30, moods: ['romantic'], intensity: 2, leadership: 'ME', novelty: 'MIXED' },
      NOW,
      deps(),
    );

    // The planner cannot play what they planned: it is not theirs to open.
    await rejects(createSession(db, a, { experienceId: planned.experienceId }, NOW), 'permission-denied', 'not-yours');
    const session = await createSession(db, b, { experienceId: planned.experienceId }, NOW);
    assert.equal(session.kind, 'ASYNC');
    assert.equal(session.consentRequired, false);
  });

  it('is given a week before it is swept away, rather than ten minutes', async () => {
    const { a, b, coupleId } = await couple();
    const planned = await planNight(
      db,
      a,
      { mode: 'TOGETHER', durationMin: 30, moods: ['romantic'], intensity: 2, leadership: 'ME', novelty: 'MIXED' },
      NOW,
      deps(),
    );
    const session = await createSession(db, b, { experienceId: planned.experienceId }, NOW);

    // A day later it is still theirs to come back to, exactly where they left it.
    assert.deepEqual(await expireStale(db, NOW + 24 * 60 * 60 * 1000), { expired: 0 });
    assert.equal((await read(`couples/${coupleId}/sessions/${session.sessionId}`)).state, session.state);

    // After a week, the couple gets their place back.
    assert.deepEqual(await expireStale(db, NOW + 8 * 24 * 60 * 60 * 1000), { expired: 1 });
    assert.equal((await read(`couples/${coupleId}/sessions/${session.sessionId}`)).endReason, 'EXPIRED');
  });
});
