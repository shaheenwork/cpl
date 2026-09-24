/**
 * The library, against the real emulator (BUILD_PROMPT.md sections 6.1, 14.8): a night they
 * played, kept in their own words — and never a content id.
 *
 * Requires a running emulator:
 *   firebase emulators:start --only auth,firestore,storage --project afterhours-dev-emulator
 */
process.env.FIRESTORE_EMULATOR_HOST ??= '127.0.0.1:8080';

import assert from 'node:assert/strict';
import { beforeEach, describe, it } from 'node:test';
import { initializeApp } from 'firebase-admin/app';
import { Timestamp, getFirestore, type DocumentData } from 'firebase-admin/firestore';
import { recomputeCouple } from '../couple/recompute';
import { grantPremium } from './support';
import { loadCatalogue, resetCatalogueCache } from '../engine/catalogue';
import { DEFAULT_WEIGHTS } from '../engine/types';
import { generateExperience, type GenerateDeps } from '../experience/generate';
import { forgetMemory, saveMemory } from '../memories/memories';
import { createPairingCode, requestPairing, respondToPairing } from '../pairing/pairing';
import { ServiceError } from '../shared/errors';

const PROJECT = 'afterhours-int-test';
const db = getFirestore(initializeApp({ projectId: PROJECT }, 'memories-int-test'));
const NOW = Date.UTC(2026, 8, 24, 21, 0, 0);

const deps: GenerateDeps = {
  catalogue: loadCatalogue,
  weights: async () => DEFAULT_WEIGHTS,
  seed: () => 7,
};

const NIGHT = {
  mode: 'TOGETHER',
  durationMin: 60,
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
  const a = `mem_a_${counter}`;
  const b = `mem_b_${counter}`;
  await db.doc(`users/${a}`).set({ contentLevel: 5 });
  await db.doc(`users/${b}`).set({ contentLevel: 5 });
  const { code } = await createPairingCode(db, a, NOW);
  await requestPairing(db, b, code, NOW);
  const { coupleId } = await respondToPairing(db, a, true, NOW);
  assert.ok(coupleId);
  await grantPremium(db, a, NOW);
  await recomputeCouple(db, coupleId, NOW);
  return { a, b, coupleId };
}

/** A night, played to the end. */
async function playedNight(uid: string, coupleId: string): Promise<string> {
  const built = await generateExperience(db, uid, NIGHT, NOW, deps);
  await db.doc(`couples/${coupleId}/experiences/${built.experienceId}`)
    .update({ status: 'COMPLETED', playedThrough: built.chapters.length });
  return built.experienceId;
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

describe('their own library', () => {
  beforeEach(async () => {
    await clearEmulator();
    resetCatalogueCache();
  });

  it('keeps a night in their words, and the night in its own', async () => {
    const { a, coupleId } = await couple();
    const experienceId = await playedNight(a, coupleId);

    const { memoryId } = await saveMemory(
      db,
      a,
      {
        experienceId,
        title: 'The Tuesday one',
        note: 'we talked until two',
        favoriteMoment: 'the bit where you laughed',
        rating: 5,
      },
      NOW,
    );

    const memory = (await read(`couples/${coupleId}/memories/${memoryId}`))!;
    assert.equal(memory.title, 'The Tuesday one');
    assert.equal(memory.favoriteMoment, 'the bit where you laughed');
    assert.equal(memory.rating, 5);
    assert.equal(memory.favorite, false);
    assert.equal(memory.createdBy, a);
    // The shape of the evening, read from the night itself rather than taken from the phone.
    assert.equal(memory.durationMin, 60);
    assert.deepEqual(memory.mood, ['naughty']);
    assert.equal(memory.experienceType, 'NIGHT');
    assert.ok(memory.chapterCount > 0);
    // And never what was in it (D-081, D-090).
    const asText = JSON.stringify(memory);
    const night = (await read(`couples/${coupleId}/experiences/${experienceId}`))!;
    for (const chapter of night.chapters as { contentIds: string[] }[]) {
      assert.ok(!asText.includes(chapter.contentIds[0]), `a memory carried ${chapter.contentIds[0]}`);
    }
  });

  it('keeps one memory per night, editing rather than piling up', async () => {
    const { a, b, coupleId } = await couple();
    const experienceId = await playedNight(a, coupleId);

    const first = await saveMemory(db, a, { experienceId, title: 'Ours' }, NOW);
    await db.doc(`couples/${coupleId}/memories/${first.memoryId}`).update({ favorite: true });
    const again = await saveMemory(db, b, { experienceId, title: 'Ours, actually' }, NOW + 60_000);

    assert.equal(again.memoryId, first.memoryId);
    const all = await db.collection(`couples/${coupleId}/memories`).get();
    assert.equal(all.size, 1);
    const memory = all.docs[0].data();
    assert.equal(memory.title, 'Ours, actually');
    // Neither the favourite mark nor who first kept it is lost to an edit.
    assert.equal(memory.favorite, true);
    assert.equal(memory.createdBy, a);
    assert.equal((memory.createdAt as Timestamp).toMillis(), NOW);
    assert.equal((memory.updatedAt as Timestamp).toMillis(), NOW + 60_000);
  });

  it('attaches only media of their own that was really sent', async () => {
    const { a, coupleId } = await couple();
    const experienceId = await playedNight(a, coupleId);
    await db.doc(`couples/${coupleId}/media/sent1`).set({ ownerUid: a, state: 'SENT', kind: 'PHOTO' });
    await db.doc(`couples/${coupleId}/media/pending1`).set({ ownerUid: a, state: 'PENDING', kind: 'PHOTO' });

    const { memoryId } = await saveMemory(db, a, { experienceId, title: 'With a photo', mediaIds: ['sent1'] }, NOW);

    assert.deepEqual((await read(`couples/${coupleId}/memories/${memoryId}`))!.mediaIds, ['sent1']);
    await rejects(saveMemory(db, a, { experienceId, title: 'x', mediaIds: ['pending1'] }, NOW), 'not-found', 'media');
    await rejects(saveMemory(db, a, { experienceId, title: 'x', mediaIds: ['nothing'] }, NOW), 'not-found', 'media');
  });

  it('refuses a night nobody played, one that is not theirs, and a payload it does not know', async () => {
    const { a, coupleId } = await couple();
    const other = await couple();
    const unplayed = (await generateExperience(db, a, NIGHT, NOW, deps)).experienceId;
    const theirs = await playedNight(other.a, other.coupleId);

    await rejects(saveMemory(db, a, { experienceId: unplayed, title: 'too soon' }, NOW), 'failed-precondition', 'not-played');
    await rejects(saveMemory(db, a, { experienceId: theirs, title: 'not ours' }, NOW), 'not-found', 'experience');
    await rejects(saveMemory(db, a, { experienceId: unplayed, title: '' }, NOW), 'invalid-argument', 'title');
    await rejects(saveMemory(db, a, { experienceId: unplayed, title: 'x', rating: 9 }, NOW), 'invalid-argument', 'rating');
    await rejects(
      saveMemory(db, a, { experienceId: unplayed, title: 'x', contentId: 'item_1' }, NOW),
      'invalid-argument',
      'unknown field contentId',
    );
    assert.equal((await db.collection(`couples/${coupleId}/memories`).get()).size, 0);
  });

  it('is forgotten by either of them, and takes nothing else with it', async () => {
    const { a, b, coupleId } = await couple();
    const experienceId = await playedNight(a, coupleId);
    await db.doc(`couples/${coupleId}/media/sent1`).set({ ownerUid: a, state: 'SENT', kind: 'VOICE' });
    const { memoryId } = await saveMemory(db, a, { experienceId, title: 'Ours', mediaIds: ['sent1'] }, NOW);

    await forgetMemory(db, b, { memoryId }, NOW);

    assert.equal((await db.collection(`couples/${coupleId}/memories`).get()).size, 0);
    // The voice note is still theirs, and the night still happened.
    assert.ok(await read(`couples/${coupleId}/media/sent1`));
    assert.ok(await read(`couples/${coupleId}/experiences/${experienceId}`));
    await rejects(forgetMemory(db, a, { memoryId }, NOW), 'not-found', 'memory');
  });
});
