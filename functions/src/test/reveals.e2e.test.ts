/**
 * Matching and reveals through the running Functions emulator (BUILD_PROMPT.md 5.2, 5.3):
 * answers written the way the app writes them are queued by the real triggers, and the
 * "release now" callable honours the minimum delay.
 *
 * Requires the full emulator suite, including functions, built from this code:
 *   npm --prefix functions run build
 *   firebase emulators:start --only auth,firestore,storage,functions --project afterhours-dev-emulator
 */
process.env.FIRESTORE_EMULATOR_HOST ??= '127.0.0.1:8080';

import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { initializeApp } from 'firebase-admin/app';
import { Timestamp, getFirestore } from 'firebase-admin/firestore';
import { mutualRef, queueRef } from '../discovery/reveals';
import { createPairingCode, requestPairing, respondToPairing } from '../pairing/pairing';

const PROJECT = 'afterhours-dev-emulator';
const FUNCTIONS = `http://127.0.0.1:5001/${PROJECT}/us-central1`;
const AUTH = 'http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1';
const db = getFirestore(initializeApp({ projectId: PROJECT }, 'reveals-e2e-test'));
const MINUTE = 60 * 1000;

async function signUp(): Promise<{ idToken: string; uid: string }> {
  const res = await fetch(`${AUTH}/accounts:signUp?key=emulator`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ returnSecureToken: true }),
  });
  const body = (await res.json()) as { idToken: string; localId: string };
  return { idToken: body.idToken, uid: body.localId };
}

async function releaseNow(idToken?: string): Promise<{ result?: { released: number }; error?: { status: string } }> {
  const res = await fetch(`${FUNCTIONS}/releaseRevealsNow`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...(idToken ? { Authorization: `Bearer ${idToken}` } : {}) },
    body: JSON.stringify({ data: null }),
  });
  return (await res.json()) as { result?: { released: number }; error?: { status: string } };
}

async function eventually<T>(read: () => Promise<T>, check: (value: T) => boolean, timeoutMs = 15_000): Promise<T> {
  const deadline = Date.now() + timeoutMs;
  let last = await read();
  while (!check(last) && Date.now() < deadline) {
    await new Promise((resolve) => setTimeout(resolve, 250));
    last = await read();
  }
  assert.ok(check(last), `condition never held; last value: ${JSON.stringify(last)}`);
  return last;
}

async function pairUsers(a: string, b: string): Promise<string> {
  const now = Date.now();
  const { code } = await createPairingCode(db, a, now);
  await requestPairing(db, b, code, now);
  const { coupleId } = await respondToPairing(db, a, true, now);
  assert.ok(coupleId);
  return coupleId;
}

const answer = (uid: string, itemId: string, value: string, secret = false) =>
  db.doc(`users/${uid}/preferences/${itemId}`).set({ value, secret, updatedAt: new Date(), taxonomyVersion: 1 });

describe('reveals, end to end', () => {
  it('refuses an unauthenticated release', async () => {
    assert.equal((await releaseNow()).error?.status, 'UNAUTHENTICATED');
  });

  it('queues a match from two answers, and reveals it only after the minimum delay', async () => {
    const alex = await signUp();
    const sam = await signUp();
    const coupleId = await pairUsers(alex.uid, sam.uid);

    await answer(alex.uid, 'sensory_massage', 'YES');
    await answer(sam.uid, 'sensory_massage', 'CURIOUS');

    // The trigger queues it; nothing a partner can read appears.
    await eventually(async () => (await queueRef(db, coupleId, 'sensory_massage').get()).data(), (entry) => entry?.op === 'REVEAL');
    assert.equal((await mutualRef(db, coupleId, 'sensory_massage').get()).exists, false);

    // Opening the app straight away releases nothing: the answer is too fresh to reveal.
    assert.equal((await releaseNow(alex.idToken)).result?.released, 0);
    assert.equal((await mutualRef(db, coupleId, 'sensory_massage').get()).exists, false);

    // Once it has waited the minimum delay, opening the app does release it.
    await queueRef(db, coupleId, 'sensory_massage').update({ queuedAt: Timestamp.fromMillis(Date.now() - 31 * MINUTE) });
    assert.equal((await releaseNow(sam.idToken)).result?.released, 1);
    assert.equal((await mutualRef(db, coupleId, 'sensory_massage').get()).get('matchLevel'), 'MIXED_POSITIVE');
  });

  it('never queues anything for a non-match', async () => {
    const alex = await signUp();
    const sam = await signUp();
    const coupleId = await pairUsers(alex.uid, sam.uid);

    await answer(alex.uid, 'mood_intense', 'YES');
    await answer(sam.uid, 'mood_intense', 'NEVER');
    // Let the triggers run, then look everywhere a trace could be.
    await new Promise((resolve) => setTimeout(resolve, 3_000));

    assert.equal((await queueRef(db, coupleId, 'mood_intense').get()).exists, false);
    assert.equal((await db.collection(`couples/${coupleId}/mutualPreferences`).get()).size, 0);
  });

  it('queues what a couple already shares the moment they pair', async () => {
    const alex = await signUp();
    const sam = await signUp();
    await answer(alex.uid, 'comm_voice', 'CURIOUS', true);
    await answer(sam.uid, 'comm_voice', 'CURIOUS', true);

    const coupleId = await pairUsers(alex.uid, sam.uid);

    await eventually(
      async () => (await queueRef(db, coupleId, 'comm_voice').get()).data(),
      (entry) => entry?.matchLevel === 'BOTH_SECRET',
    );
  });
});
