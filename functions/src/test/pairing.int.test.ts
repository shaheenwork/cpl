/**
 * Pairing, against the real Firestore emulator (BUILD_PROMPT.md sections 8 and 21.1).
 *
 * These drive the actual transactions, including the races the spec calls out: two people
 * using one code at once, a double approval, and a partner pairing elsewhere mid-request.
 *
 * Requires a running emulator:
 *   firebase emulators:start --only auth,firestore,storage --project afterhours-dev-emulator
 */
process.env.FIRESTORE_EMULATOR_HOST ??= '127.0.0.1:8080';

import assert from 'node:assert/strict';
import { beforeEach, describe, it } from 'node:test';
import { initializeApp } from 'firebase-admin/app';
import { Timestamp, getFirestore } from 'firebase-admin/firestore';
import { CODE_TTL_MS } from '../pairing/code';
import {
  LIMITS,
  REQUEST_TTL_MS,
  cancelPairing,
  createPairingCode,
  requestPairing,
  respondToPairing,
  unpairCouple,
} from '../pairing/pairing';
import { PairingError } from '../shared/errors';

const PROJECT = 'afterhours-dev-emulator';
const db = getFirestore(initializeApp({ projectId: PROJECT }, 'pairing-int-test'));
const NOW = Date.UTC(2026, 8, 18, 20, 0, 0);

async function clearEmulator(): Promise<void> {
  const res = await fetch(
    `http://${process.env.FIRESTORE_EMULATOR_HOST}/emulator/v1/projects/${PROJECT}/databases/(default)/documents`,
    { method: 'DELETE' },
  );
  assert.ok(res.ok, `emulator clear failed: ${res.status}`);
}

async function rejectsWith(promise: Promise<unknown>, code: PairingError['code'], reason?: string) {
  await assert.rejects(promise, (error: unknown) => {
    assert.ok(error instanceof PairingError, `expected PairingError, got ${String(error)}`);
    assert.equal(error.code, code);
    if (reason) assert.equal(error.reason, reason);
    return true;
  });
}

const user = async (uid: string) => (await db.doc(`users/${uid}`).get()).data();
const code = async (c: string) => (await db.doc(`pairingCodes/${c}`).get()).data();

/** Runs the full happy path and returns the couple id. */
async function pair(creator: string, joiner: string, at = NOW): Promise<string> {
  const { code: c } = await createPairingCode(db, creator, at);
  await requestPairing(db, joiner, c, at);
  const { coupleId } = await respondToPairing(db, creator, true, at);
  assert.ok(coupleId);
  return coupleId;
}

describe('pairing', () => {
  beforeEach(clearEmulator);

  describe('creating a code', () => {
    it('issues a six-digit code that expires in 15 minutes', async () => {
      const { code: c, expiresAtMs } = await createPairingCode(db, 'alice', NOW);

      assert.match(c, /^[0-9]{6}$/);
      assert.equal(expiresAtMs, NOW + CODE_TTL_MS);
      assert.equal((await code(c))?.status, 'OPEN');
      assert.equal((await user('alice'))?.pairing.status, 'OPEN');
    });

    it('retires the previous code when a new one is made', async () => {
      const first = await createPairingCode(db, 'alice', NOW);
      const second = await createPairingCode(db, 'alice', NOW + 1000);

      assert.notEqual(first.code, second.code);
      assert.equal((await code(first.code))?.status, 'CANCELLED');
      assert.equal((await code(second.code))?.status, 'OPEN');
    });

    it('refuses a user who is already paired', async () => {
      await pair('alice', 'bob');
      await rejectsWith(createPairingCode(db, 'alice', NOW), 'failed-precondition', 'already-paired');
    });
  });

  describe('requesting to pair', () => {
    it('makes a pending request and shows both sides the same symbols', async () => {
      const { code: c } = await createPairingCode(db, 'alice', NOW);
      const { verification } = await requestPairing(db, 'bob', c, NOW);

      assert.equal(verification.length, 3);
      assert.equal((await code(c))?.status, 'PENDING');
      assert.deepEqual((await user('alice'))?.pairing.verification, verification);
      assert.deepEqual((await user('bob'))?.pairing.verification, verification);
      // A request is not a pairing: nobody has a couple yet.
      assert.equal((await user('alice'))?.coupleId, undefined);
      assert.equal((await user('bob'))?.coupleId, undefined);
    });

    it('rejects a malformed code before touching the database', async () => {
      await rejectsWith(requestPairing(db, 'bob', '12a456', NOW), 'invalid-argument');
      await rejectsWith(requestPairing(db, 'bob', 123456, NOW), 'invalid-argument');
    });

    it('rejects your own code', async () => {
      const { code: c } = await createPairingCode(db, 'alice', NOW);
      await rejectsWith(requestPairing(db, 'alice', c, NOW), 'failed-precondition', 'own-code');
    });

    it('rejects an expired code', async () => {
      const { code: c } = await createPairingCode(db, 'alice', NOW);
      await rejectsWith(requestPairing(db, 'bob', c, NOW + CODE_TTL_MS), 'not-found', 'invalid-code');
    });

    it('rejects a code that has already been used (7.3 test N)', async () => {
      const { code: c } = await createPairingCode(db, 'alice', NOW);
      await requestPairing(db, 'bob', c, NOW);
      await respondToPairing(db, 'alice', true, NOW);

      await rejectsWith(requestPairing(db, 'carol', c, NOW), 'not-found', 'invalid-code');
    });

    it('answers missing, expired and used codes identically, so guesses learn nothing', async () => {
      const reasons: string[] = [];
      const capture = async (p: Promise<unknown>) =>
        p.catch((e: PairingError) => reasons.push(`${e.code}/${e.reason}`));

      await capture(requestPairing(db, 'bob', '000000', NOW)); // never existed
      const expired = await createPairingCode(db, 'alice', NOW);
      await capture(requestPairing(db, 'bob', expired.code, NOW + CODE_TTL_MS));
      const used = await createPairingCode(db, 'dave', NOW);
      await requestPairing(db, 'erin', used.code, NOW);
      await respondToPairing(db, 'dave', true, NOW);
      await capture(requestPairing(db, 'bob', used.code, NOW));

      assert.equal(new Set(reasons).size, 1, reasons.join(', '));
    });

    it('rejects a joiner who is already paired', async () => {
      await pair('alice', 'bob');
      const { code: c } = await createPairingCode(db, 'carol', NOW);
      await rejectsWith(requestPairing(db, 'bob', c, NOW), 'failed-precondition', 'already-paired');
    });

    it('rate-limits guessing', async () => {
      for (let i = 0; i < LIMITS.requestPairing.limit; i++) {
        await requestPairing(db, 'mallory', '000000', NOW).catch(() => undefined);
      }
      await rejectsWith(requestPairing(db, 'mallory', '000000', NOW), 'resource-exhausted', 'rate-limited');
    });

    it('race: two people entering the same code at once — exactly one gets the request', async () => {
      const { code: c } = await createPairingCode(db, 'alice', NOW);

      const results = await Promise.allSettled([
        requestPairing(db, 'bob', c, NOW),
        requestPairing(db, 'carol', c, NOW),
      ]);

      assert.equal(results.filter((r) => r.status === 'fulfilled').length, 1);
      const rejected = results.find((r) => r.status === 'rejected') as PromiseRejectedResult;
      assert.equal((rejected.reason as PairingError).reason, 'busy');
      const joiner = (await code(c))?.joinerUid;
      assert.ok(joiner === 'bob' || joiner === 'carol');
    });
  });

  describe('responding', () => {
    it('approval creates a couple of exactly the two of them, atomically', async () => {
      const coupleId = await pair('alice', 'bob');

      const couple = (await db.doc(`couples/${coupleId}`).get()).data();
      assert.equal(couple?.status, 'ACTIVE');
      assert.deepEqual([...couple?.memberUids].sort(), ['alice', 'bob']);
      const members = await db.collection(`couples/${coupleId}/members`).get();
      assert.equal(members.size, 2);
      assert.equal((await user('alice'))?.coupleId, coupleId);
      assert.equal((await user('bob'))?.coupleId, coupleId);
      // The handshake state is cleaned up on both sides.
      assert.equal((await user('alice'))?.pairing, undefined);
      assert.equal((await user('bob'))?.pairing, undefined);
    });

    it('the shared content level is the lower of the two', async () => {
      await db.doc('users/alice').set({ contentLevel: 5 });
      await db.doc('users/bob').set({ contentLevel: 3 });
      const coupleId = await pair('alice', 'bob');
      assert.equal((await db.doc(`couples/${coupleId}`).get()).data()?.contentLevelEffective, 3);
    });

    it('declining cancels the code, tells the joiner, and creates nothing', async () => {
      const { code: c } = await createPairingCode(db, 'alice', NOW);
      await requestPairing(db, 'mallory', c, NOW);

      const { coupleId } = await respondToPairing(db, 'alice', false, NOW);

      assert.equal(coupleId, null);
      assert.equal((await code(c))?.status, 'CANCELLED');
      assert.equal((await user('mallory'))?.pairing.status, 'DECLINED');
      assert.equal((await db.collection('couples').get()).size, 0);
    });

    it('only the creator can respond', async () => {
      const { code: c } = await createPairingCode(db, 'alice', NOW);
      await requestPairing(db, 'bob', c, NOW);
      // The joiner cannot approve their own request.
      await rejectsWith(respondToPairing(db, 'bob', true, NOW), 'failed-precondition', 'nothing-to-respond-to');
    });

    it('a request can still be approved after the original code window, within its own', async () => {
      const { code: c } = await createPairingCode(db, 'alice', NOW);
      const lateRequest = NOW + CODE_TTL_MS - 1000;
      await requestPairing(db, 'bob', c, lateRequest);
      const { coupleId } = await respondToPairing(db, 'alice', true, lateRequest + 5 * 60 * 1000);
      assert.ok(coupleId);
    });

    it('a stale request cannot be approved', async () => {
      const { code: c } = await createPairingCode(db, 'alice', NOW);
      await requestPairing(db, 'bob', c, NOW);
      await rejectsWith(
        respondToPairing(db, 'alice', true, NOW + CODE_TTL_MS + REQUEST_TTL_MS),
        'failed-precondition',
        'expired',
      );
    });

    it('race: approving twice at once creates one couple', async () => {
      const { code: c } = await createPairingCode(db, 'alice', NOW);
      await requestPairing(db, 'bob', c, NOW);

      const results = await Promise.allSettled([
        respondToPairing(db, 'alice', true, NOW),
        respondToPairing(db, 'alice', true, NOW),
      ]);

      assert.equal(results.filter((r) => r.status === 'fulfilled').length, 1);
      assert.equal((await db.collection('couples').get()).size, 1);
    });

    it('race: the joiner pairs elsewhere before approval — no second couple', async () => {
      const { code: aliceCode } = await createPairingCode(db, 'alice', NOW);
      await requestPairing(db, 'bob', aliceCode, NOW);

      // Bob is simultaneously paired with Carol by the server (e.g. an earlier request).
      await db.doc('users/bob').set({ coupleId: 'some-other-couple' }, { merge: true });

      await rejectsWith(respondToPairing(db, 'alice', true, NOW), 'failed-precondition', 'already-paired');
      assert.equal((await db.collection('couples').get()).size, 0);
    });
  });

  describe('cancelling mid-handshake', () => {
    it('a joiner backing out hands the code back to the creator', async () => {
      const { code: c } = await createPairingCode(db, 'alice', NOW);
      await requestPairing(db, 'mallory', c, NOW);

      await cancelPairing(db, 'mallory');

      assert.equal((await code(c))?.status, 'OPEN');
      assert.equal((await user('alice'))?.pairing.status, 'OPEN');
      // The real partner can still use it.
      await requestPairing(db, 'bob', c, NOW);
      assert.equal((await code(c))?.joinerUid, 'bob');
    });

    it('a creator backing out releases a waiting joiner', async () => {
      const { code: c } = await createPairingCode(db, 'alice', NOW);
      await requestPairing(db, 'bob', c, NOW);

      await cancelPairing(db, 'alice');

      assert.equal((await code(c))?.status, 'CANCELLED');
      assert.equal((await user('bob'))?.pairing.status, 'DECLINED');
    });
  });

  describe('unpairing', () => {
    it('ends the couple for both partners', async () => {
      const coupleId = await pair('alice', 'bob');

      await unpairCouple(db, 'bob', NOW);

      assert.equal((await db.doc(`couples/${coupleId}`).get()).data()?.status, 'UNPAIRED');
      assert.equal((await user('alice'))?.coupleId, undefined);
      assert.equal((await user('bob'))?.coupleId, undefined);
    });

    it('does not record who ended it', async () => {
      const coupleId = await pair('alice', 'bob');
      await unpairCouple(db, 'alice', NOW);
      const couple = (await db.doc(`couples/${coupleId}`).get()).data() ?? {};
      assert.ok(!('unpairedBy' in couple));
    });

    it('both can pair again afterwards', async () => {
      await pair('alice', 'bob');
      await unpairCouple(db, 'alice', NOW);
      assert.ok(await pair('alice', 'carol', NOW + 1000));
    });

    it('refuses someone who is not paired', async () => {
      await rejectsWith(unpairCouple(db, 'alice', NOW), 'failed-precondition', 'not-paired');
    });
  });

  it('stores times as Firestore timestamps', async () => {
    const { code: c } = await createPairingCode(db, 'alice', NOW);
    assert.ok((await code(c))?.expiresAt instanceof Timestamp);
  });
});
