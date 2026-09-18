/**
 * Recomputing a couple's ceiling and server-only filters, against the real Firestore
 * emulator (BUILD_PROMPT.md sections 5.4, 8 and 10.3).
 *
 * Calls the core logic directly, as the triggers do. Whether the triggers themselves fire
 * is covered by triggers.e2e.test.ts.
 *
 * Requires a running emulator:
 *   firebase emulators:start --only auth,firestore,storage --project afterhours-dev-emulator
 */
process.env.FIRESTORE_EMULATOR_HOST ??= '127.0.0.1:8080';

import assert from 'node:assert/strict';
import { beforeEach, describe, it } from 'node:test';
import { initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { filtersRef, recomputeCouple } from '../couple/recompute';
import { createPairingCode, requestPairing, respondToPairing, unpairCouple } from '../pairing/pairing';

const PROJECT = 'afterhours-dev-emulator';
const db = getFirestore(initializeApp({ projectId: PROJECT }, 'couple-int-test'));
const NOW = Date.UTC(2026, 8, 18, 21, 0, 0);

async function clearEmulator(): Promise<void> {
  const res = await fetch(
    `http://${process.env.FIRESTORE_EMULATOR_HOST}/emulator/v1/projects/${PROJECT}/databases/(default)/documents`,
    { method: 'DELETE' },
  );
  assert.ok(res.ok, `emulator clear failed: ${res.status}`);
}

let uidCounter = 0;
/** Fresh uids per test, so a trigger still running from an earlier test cannot interfere. */
function uids(): [string, string] {
  uidCounter += 1;
  return [`alex_${uidCounter}`, `sam_${uidCounter}`];
}

async function pair(a: string, b: string): Promise<string> {
  const { code } = await createPairingCode(db, a, NOW);
  await requestPairing(db, b, code, NOW);
  const { coupleId } = await respondToPairing(db, a, true, NOW);
  assert.ok(coupleId);
  return coupleId;
}

const boundary = (uid: string, themeId: string, level: string) =>
  db.doc(`users/${uid}/boundaries/${themeId}`).set({ level, updatedAt: new Date(NOW) });

const answer = (uid: string, itemId: string, value: string) =>
  db.doc(`users/${uid}/preferences/${itemId}`).set({ value, secret: false, updatedAt: new Date(NOW), taxonomyVersion: 1 });

const filters = async (coupleId: string) => (await filtersRef(db, coupleId).get()).data();

describe('recomputeCouple', () => {
  beforeEach(clearEmulator);

  it('builds filters for a new couple, with the lower content level as the ceiling', async () => {
    const [a, b] = uids();
    await db.doc(`users/${a}`).set({ contentLevel: 5 });
    await db.doc(`users/${b}`).set({ contentLevel: 3 });
    const coupleId = await pair(a, b);

    assert.equal(await recomputeCouple(db, coupleId, NOW), 'updated');

    const result = await filters(coupleId);
    assert.equal(result?.maxIntensity, 3);
    assert.deepEqual(result?.excludedThemes, []);
    assert.equal((await db.doc(`couples/${coupleId}`).get()).get('contentLevelEffective'), 3);
  });

  it("applies either partner's NEVER and NOT_TONIGHT, and names who to ask first", async () => {
    const [a, b] = uids();
    const coupleId = await pair(a, b);
    await boundary(a, 'sensory_blindfold', 'NEVER');
    await boundary(b, 'sensory_blindfold', 'CURIOUS');
    await boundary(b, 'roleplay_strangers', 'NOT_TONIGHT');
    await boundary(a, 'teasing_instructions', 'ASK_FIRST');
    await boundary(a, 'roleplay_characters', 'CURIOUS');

    await recomputeCouple(db, coupleId, NOW);

    const result = await filters(coupleId);
    assert.deepEqual(result?.excludedThemes, ['roleplay_strangers', 'sensory_blindfold']);
    assert.deepEqual(result?.askFirstThemes, { teasing_instructions: [a] });
    assert.deepEqual(result?.curiousThemes, ['roleplay_characters']);
  });

  it('removes items either partner answered NEVER, and only those', async () => {
    const [a, b] = uids();
    const coupleId = await pair(a, b);
    await answer(a, 'mood_intense', 'NEVER');
    await answer(b, 'sensory_massage', 'NOT_FOR_ME');
    await answer(b, 'comm_voice', 'YES');

    await recomputeCouple(db, coupleId, NOW);

    assert.deepEqual((await filters(coupleId))?.excludedItems, ['mood_intense']);
  });

  it('follows a content level change down and back up', async () => {
    const [a, b] = uids();
    await db.doc(`users/${a}`).set({ contentLevel: 4 });
    await db.doc(`users/${b}`).set({ contentLevel: 4 });
    const coupleId = await pair(a, b);

    await db.doc(`users/${b}`).set({ contentLevel: 1 }, { merge: true });
    await recomputeCouple(db, coupleId, NOW);
    assert.equal((await filters(coupleId))?.maxIntensity, 1);

    await db.doc(`users/${b}`).set({ contentLevel: 4 }, { merge: true });
    await recomputeCouple(db, coupleId, NOW);
    assert.equal((await filters(coupleId))?.maxIntensity, 4);
    assert.equal((await db.doc(`couples/${coupleId}`).get()).get('contentLevelEffective'), 4);
  });

  it('deletes the filters once the couple unpairs', async () => {
    const [a, b] = uids();
    const coupleId = await pair(a, b);
    await boundary(a, 'power_negotiated', 'NEVER');
    await recomputeCouple(db, coupleId, NOW);
    assert.ok(await filters(coupleId));

    await unpairCouple(db, a, NOW);
    assert.equal(await recomputeCouple(db, coupleId, NOW), 'cleared');

    assert.equal(await filters(coupleId), undefined);
  });

  it('clears rather than fails for a couple that does not exist', async () => {
    assert.equal(await recomputeCouple(db, 'no-such-couple', NOW), 'cleared');
  });

  it('survives concurrent recomputes, and the next one settles on every write', async () => {
    const [a, b] = uids();
    const coupleId = await pair(a, b);

    // Two partners changing boundaries at once: the recomputes contend for the same
    // documents, and must retry rather than fail. Whatever order they commit in, the next
    // recompute is built from the current documents. (Phase 9 recomputes at generation
    // time, so a night is never built from filters older than the boundaries.)
    await Promise.all([
      boundary(a, 'mood_intense', 'NEVER').then(() => recomputeCouple(db, coupleId, NOW)),
      boundary(b, 'sensory_temperature', 'NEVER').then(() => recomputeCouple(db, coupleId, NOW)),
    ]);
    await recomputeCouple(db, coupleId, NOW);

    assert.deepEqual((await filters(coupleId))?.excludedThemes, ['mood_intense', 'sensory_temperature']);
  });
});
