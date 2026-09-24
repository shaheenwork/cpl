/**
 * Multi-day arcs against the real emulator (BUILD_PROMPT.md section 13): a day opens on the
 * clock, and only once the day before it has been played.
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
import { DEFAULT_WEIGHTS } from '../engine/types';
import type { GenerateDeps } from '../experience/generate';
import { createPairingCode, requestPairing, respondToPairing } from '../pairing/pairing';
import { ServiceError } from '../shared/errors';
import { ARC_LENGTHS, DAY_MS, closeArc, openArcDay, planDays, startArc } from '../surprises/arcs';

const PROJECT = 'afterhours-int-test';
const db = getFirestore(initializeApp({ projectId: PROJECT }, 'arcs-int-test'));
const NOW = Date.UTC(2026, 8, 24, 19, 0, 0);

const deps = (seed = 4242): GenerateDeps => ({
  catalogue: loadCatalogue,
  weights: async () => DEFAULT_WEIGHTS,
  seed: () => seed,
});

async function clearEmulator(): Promise<void> {
  const res = await fetch(
    `http://${process.env.FIRESTORE_EMULATOR_HOST}/emulator/v1/projects/${PROJECT}/databases/(default)/documents`,
    { method: 'DELETE' },
  );
  assert.ok(res.ok, `emulator clear failed: ${res.status}`);
}

let counter = 0;
async function couple(levels: [number, number] = [5, 5]) {
  counter += 1;
  const a = `arc_a_${counter}`;
  const b = `arc_b_${counter}`;
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

/** What playing a night to the end looks like from the arc's side. */
async function finish(coupleId: string, experienceId: string): Promise<void> {
  await db.doc(`couples/${coupleId}/experiences/${experienceId}`).update({ status: 'COMPLETED' });
}

describe('a few days of this', () => {
  beforeEach(async () => {
    await clearEmulator();
    resetCatalogueCache();
  });

  it('plans a day for each beat, opening one a day', () => {
    for (const length of ARC_LENGTHS) {
      const days = planDays(length, NOW);
      assert.equal(days.length, length);
      assert.equal(days[0].state, 'READY');
      assert.ok(days.slice(1).every((day) => day.state === 'LOCKED'));
      assert.equal(days.at(-1)!.title, 'The last night');
      days.forEach((day, index) => assert.equal(day.unlockAt.toMillis(), NOW + index * DAY_MS));
    }
  });

  it('starts one arc at a time, and builds a night only when a day is opened', async () => {
    const { a, coupleId } = await couple();

    const arc = await startArc(db, a, { lengthDays: 3 }, NOW);

    assert.equal(arc.days.length, 3);
    assert.ok(arc.days.every((day) => day.experienceId === null));
    await rejects(startArc(db, a, { lengthDays: 7 }, NOW), 'failed-precondition', 'arc-in-progress');

    const opened = await openArcDay(db, a, { arcId: arc.arcId, dayIndex: 0 }, NOW, deps());

    assert.ok(opened.experienceId);
    const stored = (await read(`couples/${coupleId}/arcs/${arc.arcId}`))!;
    assert.equal(stored.days[0].experienceId, opened.experienceId);
    assert.equal(stored.days[0].state, 'READY');
    // Opening the same day again gives the same night rather than a second one.
    assert.equal((await openArcDay(db, a, { arcId: arc.arcId, dayIndex: 0 }, NOW, deps())).experienceId, opened.experienceId);
  });

  it('holds a day shut until the clock reaches it, and until the one before was played', async () => {
    const { a, coupleId } = await couple();
    const arc = await startArc(db, a, { lengthDays: 3 }, NOW);
    const first = await openArcDay(db, a, { arcId: arc.arcId, dayIndex: 0 }, NOW, deps());

    // Tomorrow's day, today.
    await rejects(openArcDay(db, a, { arcId: arc.arcId, dayIndex: 1 }, NOW, deps()), 'failed-precondition', 'not-yet');

    // Tomorrow, with last night unfinished.
    const tomorrow = NOW + DAY_MS;
    await rejects(
      openArcDay(db, a, { arcId: arc.arcId, dayIndex: 1 }, tomorrow, deps()),
      'failed-precondition',
      'finish-the-day-before',
    );

    await finish(coupleId, first.experienceId);
    const second = await openArcDay(db, a, { arcId: arc.arcId, dayIndex: 1 }, tomorrow, deps(99));

    assert.notEqual(second.experienceId, first.experienceId);
    const stored = (await read(`couples/${coupleId}/arcs/${arc.arcId}`))!;
    assert.equal(stored.days[0].state, 'DONE');
    assert.equal(stored.days[1].state, 'READY');
  });

  it('builds each day inside the couple\'s own ceiling', async () => {
    const { a, coupleId } = await couple([2, 4]);
    const arc = await startArc(db, a, { lengthDays: 1 }, NOW);

    const opened = await openArcDay(db, a, { arcId: arc.arcId, dayIndex: 0 }, NOW, deps());

    const night = (await read(`couples/${coupleId}/experiences/${opened.experienceId}`))!;
    assert.equal(night.intensity, 2);
    assert.ok(night.chapters.every((chapter: { intensity: number }) => chapter.intensity <= 2));
  });

  it('ends when they say so, and refuses what it cannot read', async () => {
    const { a } = await couple();
    const arc = await startArc(db, a, { lengthDays: 7 }, NOW);

    await rejects(startArc(db, a, { lengthDays: 5 }, NOW), 'invalid-argument', 'lengthDays');
    await rejects(startArc(db, a, { lengthDays: 7, extra: true }, NOW), 'invalid-argument', 'unknown field extra');
    await rejects(openArcDay(db, a, { arcId: arc.arcId, dayIndex: 99 }, NOW, deps()), 'not-found', 'day');
    await rejects(openArcDay(db, a, { arcId: 'nothing', dayIndex: 0 }, NOW, deps()), 'not-found', 'arc');

    await closeArc(db, a, { arcId: arc.arcId }, NOW);
    await rejects(openArcDay(db, a, { arcId: arc.arcId, dayIndex: 0 }, NOW, deps()), 'failed-precondition', 'arc-over');
    // And with that one over, another can begin.
    assert.ok((await startArc(db, a, { lengthDays: 1 }, NOW)).arcId);
  });
});
