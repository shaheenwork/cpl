/**
 * Mutual matches and their delayed reveal, against the real Firestore emulator
 * (BUILD_PROMPT.md sections 5.2 and 5.3; Phase 7 exit: "two-user test yields a match;
 * non-match leaks nothing; timing jitter verified").
 *
 * Requires a running emulator:
 *   firebase emulators:start --only auth,firestore,storage --project afterhours-dev-emulator
 */
process.env.FIRESTORE_EMULATOR_HOST ??= '127.0.0.1:8080';

import assert from 'node:assert/strict';
import { beforeEach, describe, it } from 'node:test';
import { initializeApp } from 'firebase-admin/app';
import { Timestamp, getFirestore } from 'firebase-admin/firestore';
import { DEFAULT_TIMING } from '../discovery/match';
import {
  mutualRef,
  queueRef,
  releaseDue,
  releaseForCouple,
  releaseOnOpen,
  syncCoupleMatches,
  syncMatch,
} from '../discovery/reveals';
import { createPairingCode, requestPairing, respondToPairing, unpairCouple } from '../pairing/pairing';

// Its own project, so the triggers running in the Functions emulator (which serve
// afterhours-dev-emulator) never touch this data mid-test.
const PROJECT = 'afterhours-int-test';
const db = getFirestore(initializeApp({ projectId: PROJECT }, 'reveals-int-test'));
const NOW = Date.UTC(2026, 8, 18, 22, 0, 0);
const MINUTE = 60 * 1000;
const T = DEFAULT_TIMING;

async function clearEmulator(): Promise<void> {
  const res = await fetch(
    `http://${process.env.FIRESTORE_EMULATOR_HOST}/emulator/v1/projects/${PROJECT}/databases/(default)/documents`,
    { method: 'DELETE' },
  );
  assert.ok(res.ok, `emulator clear failed: ${res.status}`);
}

let counter = 0;
async function couple(): Promise<{ a: string; b: string; coupleId: string }> {
  counter += 1;
  const a = `alex_${counter}`;
  const b = `sam_${counter}`;
  const { code } = await createPairingCode(db, a, NOW);
  await requestPairing(db, b, code, NOW);
  const { coupleId } = await respondToPairing(db, a, true, NOW);
  assert.ok(coupleId);
  return { a, b, coupleId };
}

const answer = (uid: string, itemId: string, value: string, secret = false) =>
  db.doc(`users/${uid}/preferences/${itemId}`).set({ value, secret, updatedAt: new Date(NOW), taxonomyVersion: 1 });

const queued = async (coupleId: string, itemId: string) => (await queueRef(db, coupleId, itemId).get()).data();
const revealed = async (coupleId: string, itemId: string) => (await mutualRef(db, coupleId, itemId).get()).data();
const visible = async (coupleId: string) => (await db.collection(`couples/${coupleId}/mutualPreferences`).get()).docs;
const pending = async (coupleId: string) => (await db.collection(`couples/${coupleId}/revealQueue`).get()).docs;

/** Answers both partners and syncs, as the trigger would. */
async function both(c: { a: string; b: string; coupleId: string }, itemId: string, va: string, vb: string) {
  await answer(c.a, itemId, va);
  await answer(c.b, itemId, vb);
  return syncMatch(db, c.coupleId, itemId, NOW, T);
}

describe('mutual matching', () => {
  beforeEach(clearEmulator);

  it('two positives make a match that waits in the server-only queue, unseen', async () => {
    const c = await couple();
    assert.equal(await both(c, 'sensory_massage', 'YES', 'YES'), 'queued');

    const entry = await queued(c.coupleId, 'sensory_massage');
    assert.equal(entry?.op, 'REVEAL');
    assert.equal(entry?.matchLevel, 'BOTH_YES');
    // Nothing a partner can read exists yet.
    assert.equal((await visible(c.coupleId)).length, 0);
  });

  it('reveals exactly when its random time comes, and not a moment before', async () => {
    const c = await couple();
    await both(c, 'sensory_massage', 'YES', 'CURIOUS');
    const releaseAt = (await queued(c.coupleId, 'sensory_massage'))!.releaseAfter.toMillis();

    assert.ok(releaseAt >= NOW + T.minDelayMs && releaseAt <= NOW + T.maxDelayMs, 'inside the window');

    await releaseDue(db, releaseAt - 1);
    assert.equal((await visible(c.coupleId)).length, 0, 'not a millisecond early');

    await releaseDue(db, releaseAt);
    const match = await revealed(c.coupleId, 'sensory_massage');
    assert.equal(match?.matchLevel, 'MIXED_POSITIVE');
    assert.equal(match?.revealedAt.toMillis(), releaseAt);
    assert.equal((await pending(c.coupleId)).length, 0);
  });

  it('holds only what section 5.2 allows, never either answer', async () => {
    const c = await couple();
    await both(c, 'comm_voice', 'CURIOUS', 'CURIOUS');
    await releaseDue(db, NOW + T.maxDelayMs);

    const match = await revealed(c.coupleId, 'comm_voice');
    assert.deepEqual(Object.keys(match!).sort(), ['matchLevel', 'prefId', 'revealedAt', 'seenBy', 'sourceVersion']);
    assert.deepEqual(match?.seenBy, {});
  });

  it('a non-match leaves nothing, anywhere', async () => {
    const c = await couple();
    for (const [va, vb] of [['YES', 'NEVER'], ['CURIOUS', 'NOT_FOR_ME'], ['NEVER', 'NEVER'], ['MAYBE', 'NOT_FOR_ME']]) {
      assert.equal(await both(c, `item_${va}_${vb}`.toLowerCase(), va, vb), 'unchanged');
    }
    await answer(c.a, 'only_one_answered', 'YES');
    await syncMatch(db, c.coupleId, 'only_one_answered', NOW, T);

    await releaseDue(db, NOW + T.maxDelayMs);
    assert.equal((await pending(c.coupleId)).length, 0);
    assert.equal((await visible(c.coupleId)).length, 0);
  });

  it('a secret surfaces only against another secret', async () => {
    const c = await couple();
    await answer(c.a, 'roleplay_strangers', 'CURIOUS', true);
    await answer(c.b, 'roleplay_strangers', 'YES');
    assert.equal(await syncMatch(db, c.coupleId, 'roleplay_strangers', NOW, T), 'unchanged');
    assert.equal(await queued(c.coupleId, 'roleplay_strangers'), undefined);

    await answer(c.b, 'roleplay_strangers', 'CURIOUS', true);
    await syncMatch(db, c.coupleId, 'roleplay_strangers', NOW, T);
    await releaseDue(db, NOW + T.maxDelayMs);

    assert.equal((await revealed(c.coupleId, 'roleplay_strangers'))?.matchLevel, 'BOTH_SECRET');
  });

  it('a match withdrawn before its reveal vanishes without a trace', async () => {
    const c = await couple();
    await both(c, 'mood_intense', 'YES', 'YES');
    await answer(c.b, 'mood_intense', 'NOT_FOR_ME');
    assert.equal(await syncMatch(db, c.coupleId, 'mood_intense', NOW + MINUTE, T), 'dequeued');

    await releaseDue(db, NOW + T.maxDelayMs);
    assert.equal((await pending(c.coupleId)).length, 0);
    assert.equal((await visible(c.coupleId)).length, 0);
  });

  it('a match withdrawn after its reveal is taken back on its own random delay', async () => {
    const c = await couple();
    await both(c, 'mood_intense', 'YES', 'YES');
    await releaseDue(db, NOW + T.maxDelayMs);
    assert.ok(await revealed(c.coupleId, 'mood_intense'));

    const later = NOW + 5 * 60 * MINUTE;
    await answer(c.a, 'mood_intense', 'NEVER');
    assert.equal(await syncMatch(db, c.coupleId, 'mood_intense', later, T), 'queued');
    const retract = await queued(c.coupleId, 'mood_intense');
    assert.equal(retract?.op, 'RETRACT');
    // Still there until the retraction's own time: the change is not dated either.
    assert.ok(await revealed(c.coupleId, 'mood_intense'));

    await releaseDue(db, retract!.releaseAfter.toMillis());
    assert.equal(await revealed(c.coupleId, 'mood_intense'), undefined);
  });

  it('a queued match that changes keeps its time, so re-answering cannot nudge it', async () => {
    const c = await couple();
    await both(c, 'teasing_verbal', 'YES', 'YES');
    const first = (await queued(c.coupleId, 'teasing_verbal'))!;

    await answer(c.a, 'teasing_verbal', 'MAYBE');
    await syncMatch(db, c.coupleId, 'teasing_verbal', NOW + 10 * MINUTE, T);

    const after = (await queued(c.coupleId, 'teasing_verbal'))!;
    assert.equal(after.matchLevel, 'MIXED_POSITIVE');
    assert.equal(after.releaseAfter.toMillis(), first.releaseAfter.toMillis());
  });

  it('a release re-checks the answers and drops what they no longer support', async () => {
    const c = await couple();
    await both(c, 'sensory_music', 'YES', 'YES');
    // The answer changes and its trigger is lost: the release must still not reveal it.
    await answer(c.b, 'sensory_music', 'NEVER');

    const changes = await releaseForCouple(db, c.coupleId, NOW + T.maxDelayMs, () => true);
    assert.equal(changes, 0);
    assert.equal((await visible(c.coupleId)).length, 0);
    assert.equal((await pending(c.coupleId)).length, 0);
  });

  it('opening the app releases only what has waited the minimum delay', async () => {
    const c = await couple();
    await both(c, 'explore_trust', 'CURIOUS', 'YES');

    assert.equal(await releaseOnOpen(db, c.a, NOW + T.minDelayMs - 1, T), 0);
    assert.equal((await visible(c.coupleId)).length, 0);

    assert.equal(await releaseOnOpen(db, c.b, NOW + T.minDelayMs, T), 1);
    assert.equal((await revealed(c.coupleId, 'explore_trust'))?.matchLevel, 'MIXED_POSITIVE');
  });

  it("queues the matches a new couple's existing answers already make", async () => {
    counter += 1;
    const a = `early_a_${counter}`;
    const b = `early_b_${counter}`;
    await answer(a, 'mood_romantic', 'YES');
    await answer(b, 'mood_romantic', 'YES');
    await answer(a, 'mood_playful', 'YES');
    await answer(b, 'mood_playful', 'NEVER');
    const { code } = await createPairingCode(db, a, NOW);
    await requestPairing(db, b, code, NOW);
    const { coupleId } = await respondToPairing(db, a, true, NOW);

    assert.equal(await syncCoupleMatches(db, coupleId!, NOW, T), 1);
    assert.equal((await queued(coupleId!, 'mood_romantic'))?.matchLevel, 'BOTH_YES');
    assert.equal(await queued(coupleId!, 'mood_playful'), undefined);
  });

  it('an ended couple reveals nothing, and its queue is cleared', async () => {
    const c = await couple();
    await both(c, 'sensory_lighting', 'YES', 'YES');
    await unpairCouple(db, c.a, NOW);

    const { changes } = await releaseDue(db, NOW + T.maxDelayMs);
    assert.equal(changes, 0);
    assert.equal((await pending(c.coupleId)).length, 0);
    assert.equal((await visible(c.coupleId)).length, 0);
  });

  it('batches per couple, and never sends an empty batch', async () => {
    const due = await couple();
    const notYet = await couple();
    await both(due, 'mood_slow', 'YES', 'YES');
    await both(due, 'mood_flirty', 'CURIOUS', 'CURIOUS');
    await both(notYet, 'mood_slow', 'YES', 'YES');
    // Hold the second couple's change back past the release time.
    await queueRef(db, notYet.coupleId, 'mood_slow').update({ releaseAfter: Timestamp.fromMillis(NOW + 999 * MINUTE) });

    const result = await releaseDue(db, NOW + T.maxDelayMs);
    assert.deepEqual(result, { couples: 1, changes: 2 });
    assert.equal((await visible(notYet.coupleId)).length, 0);
  });

  it('spreads a batch of new matches across the window', async () => {
    const c = await couple();
    const items = Array.from({ length: 12 }, (_, i) => `spread_${i}`);
    for (const itemId of items) await both(c, itemId, 'YES', 'YES');

    const times = await Promise.all(items.map(async (itemId) => (await queued(c.coupleId, itemId))!.releaseAfter.toMillis()));
    assert.ok(new Set(times).size >= 11, 'each change gets its own random time');
    assert.ok(Math.max(...times) - Math.min(...times) > 30 * MINUTE, 'and they are not bunched together');
  });
});
