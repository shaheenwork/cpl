/**
 * Games against the real Firestore emulator (BUILD_PROMPT.md sections 3.1, 3.2, 11): rounds
 * chosen behind both partners' boundaries, every ask-first theme left out of a phone both
 * partners hold, and a game played through the same session as a night — consent at BOLD and
 * WILD, a dare reported as what was played, a limit set mid-game holding for the rest of it.
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
import { grantPremium } from './support';
import { loadCatalogue, resetCatalogueCache } from '../engine/catalogue';
import { DEFAULT_WEIGHTS, type EnginePrompt } from '../engine/types';
import { GAME_LIMIT, startGame } from '../experience/game';
import { type GenerateDeps } from '../experience/generate';
import { createPairingCode, requestPairing, respondToPairing } from '../pairing/pairing';
import { advanceSession, createSession, markReady } from '../session/session';
import { ServiceError } from '../shared/errors';

const PROJECT = 'afterhours-int-test';
const db = getFirestore(initializeApp({ projectId: PROJECT }, 'game-int-test'));
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
  const a = `gamehost_${counter}`;
  const b = `gamepartner_${counter}`;
  await db.doc(`users/${a}`).set({ contentLevel: levels[0] });
  await db.doc(`users/${b}`).set({ contentLevel: levels[1] });
  const { code } = await createPairingCode(db, a, NOW);
  await requestPairing(db, b, code, NOW);
  const { coupleId } = await respondToPairing(db, a, true, NOW);
  assert.ok(coupleId);
  await grantPremium(db, a, NOW);
  await recomputeCouple(db, coupleId, NOW);
  return { a, b, coupleId };
}

async function prompts(): Promise<Map<string, EnginePrompt>> {
  return new Map((await loadCatalogue(db, NOW)).prompts.map((prompt) => [prompt.id, prompt]));
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

function released(game: { rounds: { contentIds: string[] }[]; alternates: string[] }): string[] {
  return [...game.rounds.flatMap((round) => round.contentIds), ...game.alternates];
}

const advance = (uid: string, sessionId: string, fromChapter: number, playedItemId?: string) =>
  advanceSession(db, uid, { sessionId, fromChapter, ...(playedItemId ? { playedItemId } : {}) }, NOW);

describe('starting a game', () => {
  beforeEach(async () => {
    await clearEmulator();
    resetCatalogueCache();
  });

  it('stores the rounds as an experience the couple plays through a session', async () => {
    const { a, coupleId } = await couple();
    const game = await startGame(db, a, { game: 'WOULD_YOU_RATHER', intensity: 3 }, NOW, deps());
    assert.equal(game.game, 'WOULD_YOU_RATHER');
    assert.equal(game.rounds.length, 8);
    const byId = await prompts();
    for (const id of released(game)) {
      assert.equal(byId.get(id)!.game, 'WOULD_YOU_RATHER');
      assert.ok(byId.get(id)!.intensity <= 3);
    }

    const stored = await read(`couples/${coupleId}/experiences/${game.experienceId}`);
    assert.equal(stored.type, 'GAME');
    assert.equal(stored.game, 'WOULD_YOU_RATHER');
    assert.equal(stored.status, 'READY');
    assert.deepEqual(stored.chapters.map((chapter: DocumentData) => chapter.contentIds), game.rounds.map((round) => round.contentIds));
    // What the couple's limits are is used, never stored with the game.
    for (const key of Object.keys(stored)) assert.ok(!/filter|boundar|exclud|askFirst/i.test(key), `stored ${key}`);
  });

  it("keeps a whole theme out on either partner's NEVER", async () => {
    const { a, b, coupleId } = await couple();
    const theme = 'comm_fantasies';
    await db.doc(`users/${b}/boundaries/${theme}`).set({ level: 'NEVER', updatedAt: new Date(NOW) });
    await recomputeCouple(db, coupleId, NOW);
    const byId = await prompts();
    for (const gameId of ['TRUTH_OR_DARE', 'DEEP_TALK', 'COUPLE_BATTLE']) {
      for (let seed = 1; seed <= 4; seed++) {
        const game = await startGame(db, a, { game: gameId, intensity: 5 }, NOW, deps(seed));
        for (const id of released(game)) assert.ok(!byId.get(id)!.excludedByBoundaries.includes(theme), `${id} carries ${theme}`);
      }
    }
  });

  it('leaves out every ask-first theme, the host\'s own too: both partners hold the phone', async () => {
    const { a, b, coupleId } = await couple();
    await db.doc(`users/${a}/boundaries/power_leading`).set({ level: 'ASK_FIRST', updatedAt: new Date(NOW) });
    await db.doc(`users/${b}/boundaries/teasing_words`).set({ level: 'ASK_FIRST', updatedAt: new Date(NOW) });
    await recomputeCouple(db, coupleId, NOW);
    const byId = await prompts();
    for (let seed = 1; seed <= 4; seed++) {
      const game = await startGame(db, a, { game: 'TRUTH_OR_DARE', intensity: 5 }, NOW, deps(seed));
      for (const id of released(game)) {
        const themes = byId.get(id)!.excludedByBoundaries;
        assert.ok(!themes.includes('power_leading') && !themes.includes('teasing_words'), `${id} asks first`);
      }
      const asks = await db.collection(`couples/${coupleId}/experiences/${game.experienceId}/askFirst`).get();
      assert.equal(asks.size, 0);
    }
  });

  it("the more careful partner's content level caps a game, whatever is asked", async () => {
    const { a } = await couple([5, 2]);
    const byId = await prompts();
    const game = await startGame(db, a, { game: 'ME_OR_YOU', intensity: 5 }, NOW, deps());
    assert.equal(game.effectiveIntensity, 2);
    for (const id of released(game)) assert.ok(byId.get(id)!.intensity <= 2, id);
  });

  it('refuses unknown games, fields the server decides, and anyone unpaired', async () => {
    const { a } = await couple();
    await rejects(startGame(db, a, { game: 'STRIP_POKER', intensity: 3 }, NOW, deps()), 'invalid-argument', 'game');
    await rejects(startGame(db, a, { game: 'ME_OR_YOU', intensity: 6 }, NOW, deps()), 'invalid-argument', 'intensity');
    await rejects(startGame(db, a, { game: 'ME_OR_YOU', intensity: 3, seed: 1 }, NOW, deps()), 'invalid-argument', 'unknown field seed');
    await db.doc('users/loner').set({ contentLevel: 5 });
    await rejects(startGame(db, 'loner', { game: 'ME_OR_YOU', intensity: 3 }, NOW, deps()), 'failed-precondition', 'not-paired');
  });

  it('is rate limited per user', async () => {
    const { a } = await couple([1, 1]);
    for (let i = 0; i < GAME_LIMIT; i++) await startGame(db, a, { game: 'DEEP_TALK', intensity: 1 }, NOW, deps(i));
    await rejects(startGame(db, a, { game: 'DEEP_TALK', intensity: 1 }, NOW, deps()), 'resource-exhausted');
  });
});

describe('playing a game', () => {
  beforeEach(async () => {
    await clearEmulator();
    resetCatalogueCache();
  });

  it('plays through a session, and counts as a game played', async () => {
    const { a, coupleId } = await couple();
    const game = await startGame(db, a, { game: 'DEEP_TALK', intensity: 2 }, NOW, deps());
    const { sessionId, state, chapterCount } = await createSession(db, a, { experienceId: game.experienceId }, NOW);
    assert.equal(state, 'READY');
    assert.equal(chapterCount, game.rounds.length);
    assert.equal((await read(`couples/${coupleId}/sessions/${sessionId}`)).experienceType, 'GAME');

    let view = await advance(a, sessionId, -1);
    for (let round = 0; round < chapterCount; round++) view = await advance(a, sessionId, round);
    assert.equal(view.state, 'COMPLETED');
    const history = await read(`couples/${coupleId}/engineState/history`);
    for (const round of game.rounds) assert.equal(history.lastPlayed[round.contentIds[0]], NOW);
    const after = await read(`couples/${coupleId}`);
    assert.equal(after.stats.gamesPlayed, 1);
    assert.equal(after.stats.nightsPlayed, undefined);
  });

  it('a BOLD or WILD game waits for both partners\' "I\'m in"', async () => {
    const { a, b } = await couple();
    const game = await startGame(db, a, { game: 'TRUTH_OR_DARE', intensity: 5 }, NOW, deps());
    const { sessionId, state, consentRequired } = await createSession(db, a, { experienceId: game.experienceId }, NOW);
    assert.equal(consentRequired, true);
    assert.equal(state, 'WAITING_FOR_PARTNER');
    await rejects(advance(a, sessionId, -1), 'failed-precondition', 'illegal-transition');
    await markReady(db, a, { sessionId }, NOW);
    assert.equal((await markReady(db, b, { sessionId }, NOW)).state, 'READY');
    assert.equal((await advance(a, sessionId, -1)).state, 'CHAPTER_ACTIVE');
  });

  it('records the dare when the dare was played, and refuses a prompt the game never released', async () => {
    const { a, coupleId } = await couple([3, 3]);
    const game = await startGame(db, a, { game: 'TRUTH_OR_DARE', intensity: 3 }, NOW, deps());
    const { sessionId } = await createSession(db, a, { experienceId: game.experienceId }, NOW);
    await advance(a, sessionId, -1);

    const [truth, dare] = game.rounds[0].contentIds;
    await advance(a, sessionId, 0, dare);
    const experience = await read(`couples/${coupleId}/experiences/${game.experienceId}`);
    assert.deepEqual(experience.chapters[0].contentIds, [dare, truth]);
    const history = await read(`couples/${coupleId}/engineState/history`);
    assert.equal(history.lastPlayed[dare], NOW);
    assert.equal(history.lastPlayed[truth], undefined, 'the truth was not played');

    // Another round's prompt, or one never released, is refused.
    const otherRound = game.rounds[2].contentIds[1];
    await rejects(advance(a, sessionId, 1, otherRound), 'invalid-argument', 'playedItemId');
    const byId = await prompts();
    const unreleased = [...byId.values()].find((prompt) => prompt.game === 'TRUTH_OR_DARE' && !released(game).includes(prompt.id))!;
    await rejects(advance(a, sessionId, 1, unreleased.id), 'invalid-argument', 'playedItemId');

    // An alternate swapped in ("Not this one") is what was played.
    const alternate = game.alternates[0];
    await advance(a, sessionId, 1, alternate);
    const after = await read(`couples/${coupleId}/experiences/${game.experienceId}`);
    assert.equal(after.chapters[1].contentIds[0], alternate);
  });

  it('a limit set halfway through a game holds for the rest of it', async () => {
    const { a, b, coupleId } = await couple();
    const game = await startGame(db, a, { game: 'WHO_KNOWS_WHO', intensity: 3 }, NOW, deps());
    const byId = await prompts();
    const { sessionId } = await createSession(db, a, { experienceId: game.experienceId }, NOW);
    await advance(a, sessionId, -1);

    const ahead = game.rounds[3].contentIds[0];
    const theme = byId.get(ahead)!.excludedByBoundaries[0];
    await db.doc(`users/${b}/boundaries/${theme}`).set({ level: 'NEVER', updatedAt: new Date(NOW) });
    await recomputeCouple(db, coupleId, NOW);

    const view = await advance(a, sessionId, 0);
    assert.ok(view.blocked.includes(ahead), 'the round coming up is blocked');
    const stillToCome = [...game.rounds.slice(1).flatMap((round) => round.contentIds), ...game.alternates];
    const expected = stillToCome.filter((id) => byId.get(id)!.excludedByBoundaries.includes(theme));
    assert.deepEqual([...view.blocked].sort(), [...new Set(expected)].sort());
  });

  it("a partner's ask-first set mid-game blocks what it covers, even the host's own", async () => {
    const { a, coupleId } = await couple();
    const game = await startGame(db, a, { game: 'TWO_TRUTHS_AND_A_LIE', intensity: 3 }, NOW, deps());
    const byId = await prompts();
    const { sessionId } = await createSession(db, a, { experienceId: game.experienceId }, NOW);
    await advance(a, sessionId, -1);
    const ahead = game.rounds[2].contentIds[0];
    const theme = byId.get(ahead)!.excludedByBoundaries[0];
    // The host's own ask-first: on a shared phone it cannot be asked either.
    await db.doc(`users/${a}/boundaries/${theme}`).set({ level: 'ASK_FIRST', updatedAt: new Date(NOW) });
    await recomputeCouple(db, coupleId, NOW);
    const view = await advance(a, sessionId, 0);
    assert.ok(view.blocked.includes(ahead));
  });
});
