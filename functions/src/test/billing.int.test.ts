/**
 * Subscriptions against the real emulator (BUILD_PROMPT.md section 19): what free includes, what
 * premium lifts, and the rule that matters most — **a night that has started finishes**, whatever
 * anyone's subscription says.
 *
 * Requires a running emulator:
 *   firebase emulators:start --only auth,firestore,storage --project afterhours-dev-emulator
 */
process.env.FIRESTORE_EMULATOR_HOST ??= '127.0.0.1:8080';
process.env.FUNCTIONS_EMULATOR ??= 'true';

import assert from 'node:assert/strict';
import { beforeEach, describe, it } from 'node:test';
import { initializeApp } from 'firebase-admin/app';
import { Timestamp, getFirestore } from 'firebase-admin/firestore';
import {
  applyPlayNotification,
  currentEntitlement,
  expireEntitlements,
  verifyPurchase,
} from '../billing/billing';
import { coupleEntitlement } from '../billing/entitlement';
import { FREE_NIGHTS_PER_WEEK, FREE_SURPRISES_WAITING } from '../billing/limits';
import { recomputeCouple } from '../couple/recompute';
import { loadCatalogue, resetCatalogueCache } from '../engine/catalogue';
import { DEFAULT_WEIGHTS } from '../engine/types';
import { generateExperience, type GenerateDeps } from '../experience/generate';
import { surpriseExperience } from '../experience/surprise';
import { createPairingCode, requestPairing, respondToPairing } from '../pairing/pairing';
import { advanceSession, createSession, markReady } from '../session/session';
import { ServiceError } from '../shared/errors';
import { startArc } from '../surprises/arcs';
import { createSurprise } from '../surprises/surprises';

const PROJECT = 'afterhours-int-test';
const db = getFirestore(initializeApp({ projectId: PROJECT }, 'billing-int-test'));
const NOW = Date.UTC(2026, 8, 24, 20, 0, 0);
const HOUR = 60 * 60 * 1000;
const DAY = 24 * HOUR;

const deps: GenerateDeps = {
  catalogue: loadCatalogue,
  weights: async () => DEFAULT_WEIGHTS,
  seed: () => 11,
};

const NIGHT = {
  mode: 'TOGETHER',
  durationMin: 30,
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
/** A couple on the free tier: nothing is granted here, which is the point of this suite. */
async function couple() {
  counter += 1;
  const a = `bill_a_${counter}`;
  const b = `bill_b_${counter}`;
  await db.doc(`users/${a}`).set({ contentLevel: 5 });
  await db.doc(`users/${b}`).set({ contentLevel: 5 });
  const { code } = await createPairingCode(db, a, NOW);
  await requestPairing(db, b, code, NOW);
  const { coupleId } = await respondToPairing(db, a, true, NOW);
  assert.ok(coupleId);
  await recomputeCouple(db, coupleId, NOW);
  return { a, b, coupleId };
}

async function rejects(work: Promise<unknown>, code: string, reason?: string) {
  await assert.rejects(work, (error: unknown) => {
    assert.ok(error instanceof ServiceError, `expected a ServiceError, got ${error}`);
    assert.equal(error.code, code);
    if (reason) assert.equal(error.reason, reason);
    return true;
  });
}

describe('buying premium', () => {
  beforeEach(async () => {
    await clearEmulator();
    resetCatalogueCache();
  });

  it('takes a token, asks what it is worth, and writes the answer where the client cannot', async () => {
    const { a, b, coupleId } = await couple();

    const result = await verifyPurchase(db, a, { purchaseToken: 'dev-token-1' }, NOW);

    assert.equal(result.tier, 'PREMIUM');
    const user = (await db.doc(`users/${a}`).get()).data()!;
    assert.equal(user.entitlement.tier, 'PREMIUM');
    assert.equal(user.entitlement.source, 'DEV');
    assert.equal(user.entitlement.state, 'ACTIVE');
    // One subscription covers both partners, without anything being copied onto the other account.
    assert.equal((await coupleEntitlement(db, [a, b], NOW)).tier, 'PREMIUM');
    assert.equal((await db.doc(`users/${b}`).get()).get('entitlement'), undefined);
    // And the couple's own copy, which both may read — with no purchase token in it.
    const published = (await db.doc(`couples/${coupleId}`).get()).get('entitlement');
    assert.equal(published.tier, 'PREMIUM');
    assert.deepEqual(Object.keys(published).sort(), ['expiresAt', 'state', 'tier']);
  });

  it('refuses a token it was not given, and a payload it does not know', async () => {
    const { a } = await couple();

    await rejects(verifyPurchase(db, a, { purchaseToken: 'short' }, NOW), 'invalid-argument', 'purchaseToken');
    // A well-formed token Google would not recognise: the dev verifier takes its own, and no more.
    await rejects(verifyPurchase(db, a, { purchaseToken: 'not-a-dev-token' }, NOW), 'invalid-argument', 'purchaseToken');
    await rejects(verifyPurchase(db, a, { purchaseToken: 'dev-token-x', productId: 'other' }, NOW), 'invalid-argument', 'productId');
    await rejects(verifyPurchase(db, a, { purchaseToken: 'dev-token-x', tier: 'PREMIUM' }, NOW), 'invalid-argument', 'unknown field tier');
    assert.equal((await currentEntitlement(db, a, NOW)).tier, 'FREE');
  });

  it('reports what the server holds, for a restore or a reinstall', async () => {
    const { a } = await couple();
    assert.equal((await currentEntitlement(db, a, NOW)).tier, 'FREE');

    await verifyPurchase(db, a, { purchaseToken: 'dev-token-2' }, NOW);

    const current = await currentEntitlement(db, a, NOW);
    assert.equal(current.tier, 'PREMIUM');
    assert.equal(current.state, 'ACTIVE');
    assert.ok((current.expiresAtMillis ?? 0) > NOW);
  });
});

describe('what free includes', () => {
  beforeEach(async () => {
    await clearEmulator();
    resetCatalogueCache();
  });

  it('builds a few nights a week, and then says why not', async () => {
    const { a, coupleId } = await couple();

    for (let i = 0; i < FREE_NIGHTS_PER_WEEK; i += 1) {
      await generateExperience(db, a, NIGHT, NOW + i * 1000, deps);
    }
    await rejects(
      generateExperience(db, a, NIGHT, NOW + DAY, deps),
      'failed-precondition',
      'premium-required:NIGHTS_THIS_WEEK',
    );

    // A week later the count has rolled off, and they can build again.
    assert.ok((await generateExperience(db, a, NIGHT, NOW + 8 * DAY, deps)).experienceId);
    assert.equal((await db.collection(`couples/${coupleId}/experiences`).get()).size, FREE_NIGHTS_PER_WEEK + 1);
  });

  it('keeps Mystery Nights and multi-day arcs for premium, and says which', async () => {
    const { a } = await couple();

    await rejects(surpriseExperience(db, a, { mystery: true }, NOW, deps), 'failed-precondition', 'premium-required:MYSTERY_NIGHT');
    await rejects(startArc(db, a, { lengthDays: 3 }, NOW), 'failed-precondition', 'premium-required:MULTI_DAY_ARC');
    // An ordinary surprise night is not premium.
    assert.ok((await surpriseExperience(db, a, {}, NOW, deps)).experienceId);
  });

  it('lets a few things wait at once, and opening them is never limited', async () => {
    const { a, coupleId } = await couple();
    const leave = (title: string, at: number) =>
      createSurprise(db, a, { type: 'SCHEDULED', title, text: 'x', scheduledFor: at }, NOW);

    for (let i = 0; i < FREE_SURPRISES_WAITING; i += 1) {
      await leave(`one ${i}`, NOW + (i + 1) * HOUR);
    }
    await rejects(leave('one too many', NOW + HOUR), 'failed-precondition', 'premium-required:SURPRISES_WAITING');

    // Delivering one frees the slot: the limit is what is waiting, not what was ever left.
    const waiting = await db.collection(`couples/${coupleId}/surprises`).limit(1).get();
    await waiting.docs[0].ref.update({ state: 'DELIVERED' });
    assert.ok((await leave('room again', NOW + HOUR)).surpriseId);
  });

  it('lifts every one of them the moment they subscribe', async () => {
    const { a } = await couple();
    for (let i = 0; i < FREE_NIGHTS_PER_WEEK; i += 1) {
      await generateExperience(db, a, NIGHT, NOW + i * 1000, deps);
    }

    await verifyPurchase(db, a, { purchaseToken: 'dev-token-3' }, NOW);

    assert.ok((await generateExperience(db, a, NIGHT, NOW + DAY, deps)).experienceId);
    assert.ok((await surpriseExperience(db, a, { mystery: true }, NOW + DAY, deps)).experienceId);
    assert.ok((await startArc(db, a, { lengthDays: 3 }, NOW + DAY)).arcId);
  });
});

describe('a night that has started finishes', () => {
  beforeEach(async () => {
    await clearEmulator();
    resetCatalogueCache();
  });

  it('never refuses a session for a limit reached after it began (section 19)', async () => {
    const { a, b, coupleId } = await couple();
    // The couple's whole week, spent.
    const built = [];
    for (let i = 0; i < FREE_NIGHTS_PER_WEEK; i += 1) {
      built.push(await generateExperience(db, a, NIGHT, NOW + i * 1000, deps));
    }
    const night = built[built.length - 1];

    // Building another is refused...
    await rejects(generateExperience(db, a, NIGHT, NOW + DAY, deps), 'failed-precondition', 'premium-required:NIGHTS_THIS_WEEK');

    // ...and everything about playing the one they have is untouched.
    const session = await createSession(db, a, { experienceId: night.experienceId, kind: 'ONE_DEVICE' }, NOW + DAY);
    await markReady(db, a, { sessionId: session.sessionId }, NOW + DAY);
    await markReady(db, b, { sessionId: session.sessionId }, NOW + DAY);
    const advanced = await advanceSession(db, a, { sessionId: session.sessionId, fromChapter: -1 }, NOW + DAY, deps);

    assert.equal(advanced.state, 'CHAPTER_ACTIVE');
    assert.equal((await db.doc(`couples/${coupleId}/sessions/${session.sessionId}`).get()).get('state'), 'CHAPTER_ACTIVE');
  });
});

describe('what Play tells the server later', () => {
  beforeEach(async () => {
    await clearEmulator();
    resetCatalogueCache();
  });

  it('follows a subscription through grace, hold and back', async () => {
    const { a, b } = await couple();
    await verifyPurchase(db, a, { purchaseToken: 'dev-token-4' }, NOW);

    // The payment failed; they are still in.
    await applyPlayNotification(db, { purchaseToken: 'dev-token-4', notificationType: 6, expiresAtMillis: NOW + DAY }, NOW);
    assert.equal((await coupleEntitlement(db, [a, b], NOW)).tier, 'PREMIUM');

    // It kept failing; now they are not.
    await applyPlayNotification(db, { purchaseToken: 'dev-token-4', notificationType: 5, expiresAtMillis: NOW + DAY }, NOW);
    assert.equal((await coupleEntitlement(db, [a, b], NOW)).tier, 'FREE');

    // Recovered.
    await applyPlayNotification(db, { purchaseToken: 'dev-token-4', notificationType: 1, expiresAtMillis: NOW + 30 * DAY }, NOW);
    assert.equal((await coupleEntitlement(db, [a, b], NOW)).tier, 'PREMIUM');
  });

  it('ends a refunded subscription at once, and ignores a token it has never seen', async () => {
    const { a, b } = await couple();
    await verifyPurchase(db, a, { purchaseToken: 'dev-token-5' }, NOW);

    assert.equal(
      await applyPlayNotification(db, { purchaseToken: 'someone-elses', notificationType: 12, expiresAtMillis: null }, NOW),
      'unknown-token',
    );
    assert.equal((await coupleEntitlement(db, [a, b], NOW)).tier, 'PREMIUM');

    assert.equal(
      await applyPlayNotification(db, { purchaseToken: 'dev-token-5', notificationType: 12, expiresAtMillis: null }, NOW),
      'applied',
    );
    assert.equal((await coupleEntitlement(db, [a, b], NOW)).tier, 'FREE');
  });

  it('sweeps a term that ran out, so a missed notification cannot leave anyone premium', async () => {
    const { a, b, coupleId } = await couple();
    await verifyPurchase(db, a, { purchaseToken: 'dev-token-6' }, NOW);
    await db.doc(`users/${a}`).update({ 'entitlement.expiresAt': Timestamp.fromMillis(NOW + HOUR) });

    assert.deepEqual(await expireEntitlements(db, NOW + 2 * HOUR), { expired: 1 });

    assert.equal((await db.doc(`users/${a}`).get()).get('entitlement.state'), 'EXPIRED');
    assert.equal((await coupleEntitlement(db, [a, b], NOW + 2 * HOUR)).tier, 'FREE');
    assert.equal((await db.doc(`couples/${coupleId}`).get()).get('entitlement.tier'), 'FREE');
    // Twice is no worse than once.
    assert.deepEqual(await expireEntitlements(db, NOW + 3 * HOUR), { expired: 0 });
  });
});
