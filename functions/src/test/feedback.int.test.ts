/**
 * Feedback becoming affinity, against the real Firestore emulator (BUILD_PROMPT.md section 8,
 * onFeedbackWrite): only the partner who reacted moves, once per reaction however often the
 * trigger fires, and the engine reads what it learned.
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
import { loadCatalogue, resetCatalogueCache } from '../engine/catalogue';
import { applyFeedback, dimensionsOf } from '../feedback/affinity';
import { createPairingCode, requestPairing, respondToPairing } from '../pairing/pairing';

const PROJECT = 'afterhours-int-test';
const db = getFirestore(initializeApp({ projectId: PROJECT }, 'feedback-int-test'));
const NOW = Date.UTC(2026, 8, 22, 23, 0, 0);

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
  const a = `loves_${counter}`;
  const b = `other_${counter}`;
  await db.doc(`users/${a}`).set({ contentLevel: 3 });
  await db.doc(`users/${b}`).set({ contentLevel: 3 });
  const { code } = await createPairingCode(db, a, NOW);
  await requestPairing(db, b, code, NOW);
  const { coupleId } = await respondToPairing(db, a, true, NOW);
  assert.ok(coupleId);
  await recomputeCouple(db, coupleId, NOW);
  return { a, b, coupleId };
}

async function react(coupleId: string, byUid: string, contentId: string, reaction: string, chapterIndex = 0) {
  const id = `night1_${chapterIndex}_${byUid}`;
  await db.doc(`couples/${coupleId}/feedback/${id}`).set({
    experienceId: 'night1',
    chapterIndex,
    contentId,
    reaction,
    byUid,
    at: Timestamp.fromMillis(NOW),
  });
  return id;
}

describe('feedback into affinity', () => {
  beforeEach(async () => {
    await clearEmulator();
    resetCatalogueCache();
  });

  it("moves exactly the reacting partner's scores, on every dimension of the item", async () => {
    const { a, b, coupleId } = await couple();
    const item = (await loadCatalogue(db, NOW)).items[0];
    const id = await react(coupleId, a, item.id, 'LOVED');
    assert.equal(await applyFeedback(db, coupleId, id, NOW), 'applied');

    for (const dimension of dimensionsOf(item)) {
      const score = (await db.doc(`users/${a}/affinity/${dimension}`).get()).data()!;
      assert.equal(score.score, 1, dimension);
      assert.equal(score.samples, 1);
    }
    assert.equal((await db.collection(`users/${b}/affinity`).get()).size, 0, "the partner's scores never move");
    assert.ok((await db.doc(`couples/${coupleId}/feedback/${id}`).get()).get('processedAt'));
  });

  it('counts a reaction once, however often the trigger delivers it', async () => {
    const { a, coupleId } = await couple();
    const item = (await loadCatalogue(db, NOW)).items[1];
    const id = await react(coupleId, a, item.id, 'NOT_FOR_US');
    const results = await Promise.all([applyFeedback(db, coupleId, id, NOW), applyFeedback(db, coupleId, id, NOW)]);
    assert.deepEqual([...results].sort(), ['already-applied', 'applied']);
    assert.equal(await applyFeedback(db, coupleId, id, NOW), 'already-applied');
    const dimension = dimensionsOf(item)[0];
    const score = (await db.doc(`users/${a}/affinity/${dimension}`).get()).data()!;
    assert.equal(score.score, -1);
    assert.equal(score.samples, 1);
  });

  it('ignores feedback about an item it does not know, or in a shape it does not know', async () => {
    const { a, coupleId } = await couple();
    const unknown = await react(coupleId, a, 'no_such_item', 'LOVED');
    assert.equal(await applyFeedback(db, coupleId, unknown, NOW), 'ignored');
    const item = (await loadCatalogue(db, NOW)).items[2];
    const odd = await react(coupleId, a, item.id, 'toString', 1);
    assert.equal(await applyFeedback(db, coupleId, odd, NOW), 'ignored');
    assert.equal((await db.collection(`users/${a}/affinity`).get()).size, 0);
    assert.equal(await applyFeedback(db, coupleId, 'never_written', NOW), 'ignored');
  });
});
