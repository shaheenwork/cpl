/**
 * The triggers really fire (BUILD_PROMPT.md section 8): a change to a partner's private
 * settings reaches the couple's server-only filters with no one calling anything.
 *
 * Writes as the server would see a client's write, then waits for the Functions emulator to
 * react. Requires the full emulator suite, including functions, built from this code:
 *   npm --prefix functions run build
 *   firebase emulators:start --only auth,firestore,storage,functions --project afterhours-dev-emulator
 */
process.env.FIRESTORE_EMULATOR_HOST ??= '127.0.0.1:8080';

import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { filtersRef } from '../couple/recompute';
import { createPairingCode, requestPairing, respondToPairing, unpairCouple } from '../pairing/pairing';

const PROJECT = 'afterhours-dev-emulator';
const db = getFirestore(initializeApp({ projectId: PROJECT }, 'triggers-e2e-test'));

/** Polls until [check] passes on the filters document, or fails after [timeoutMs]. */
async function eventually(
  coupleId: string,
  check: (filters: FirebaseFirestore.DocumentData | undefined) => boolean,
  timeoutMs = 15_000,
): Promise<FirebaseFirestore.DocumentData | undefined> {
  const deadline = Date.now() + timeoutMs;
  let last: FirebaseFirestore.DocumentData | undefined;
  while (Date.now() < deadline) {
    last = (await filtersRef(db, coupleId).get()).data();
    if (check(last)) return last;
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  assert.fail(`filters never matched; last seen: ${JSON.stringify(last)}`);
}

async function pairFresh(): Promise<{ a: string; b: string; coupleId: string }> {
  const suffix = `${Date.now()}_${Math.floor(Math.random() * 1e6)}`;
  const a = `e2e_alex_${suffix}`;
  const b = `e2e_sam_${suffix}`;
  const now = Date.now();
  const { code } = await createPairingCode(db, a, now);
  await requestPairing(db, b, code, now);
  const { coupleId } = await respondToPairing(db, a, true, now);
  assert.ok(coupleId);
  return { a, b, coupleId };
}

describe('filter triggers, end to end', () => {
  it('builds the filters when a couple forms', async () => {
    const { coupleId } = await pairFresh();

    const filters = await eventually(coupleId, (f) => f !== undefined);
    assert.deepEqual(filters?.excludedThemes, []);
  });

  it("applies a partner's new NEVER without anyone asking", async () => {
    const { b, coupleId } = await pairFresh();
    await eventually(coupleId, (f) => f !== undefined);

    await db.doc(`users/${b}/boundaries/sensory_blindfold`).set({ level: 'NEVER', updatedAt: new Date() });

    await eventually(coupleId, (f) => (f?.excludedThemes as string[] | undefined)?.includes('sensory_blindfold') === true);
  });

  it('lifts the exclusion when the boundary is relaxed', async () => {
    const { a, coupleId } = await pairFresh();
    await db.doc(`users/${a}/boundaries/roleplay_strangers`).set({ level: 'NOT_TONIGHT', updatedAt: new Date() });
    await eventually(coupleId, (f) => (f?.excludedThemes as string[] | undefined)?.includes('roleplay_strangers') === true);

    await db.doc(`users/${a}/boundaries/roleplay_strangers`).set({ level: 'ALWAYS_OK', updatedAt: new Date() });

    await eventually(coupleId, (f) => (f?.excludedThemes as string[] | undefined)?.length === 0);
  });

  it("applies a partner's NEVER answer, and a lowered content level", async () => {
    const { a, b, coupleId } = await pairFresh();
    await eventually(coupleId, (f) => f !== undefined);

    await db.doc(`users/${a}/preferences/mood_intense`).set({
      value: 'NEVER', secret: false, updatedAt: new Date(), taxonomyVersion: 1,
    });
    await db.doc(`users/${b}`).set({ contentLevel: 1 }, { merge: true });

    await eventually(
      coupleId,
      (f) => (f?.excludedItems as string[] | undefined)?.includes('mood_intense') === true && f?.maxIntensity === 1,
    );
    assert.equal((await db.doc(`couples/${coupleId}`).get()).get('contentLevelEffective'), 1);
  });

  it('deletes the filters when the couple unpairs', async () => {
    const { a, coupleId } = await pairFresh();
    await eventually(coupleId, (f) => f !== undefined);

    await unpairCouple(db, a, Date.now());

    await eventually(coupleId, (f) => f === undefined);
  });
});
