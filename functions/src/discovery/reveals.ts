/**
 * The reveal queue (BUILD_PROMPT.md sections 5.2 and 5.3).
 *
 * A match never appears the moment it happens: if it did, a partner could watch for it and
 * learn exactly what was just answered. Every change a partner could see — a new match, a
 * match whose level changed, a match withdrawn — is queued in `couples/{cid}/revealQueue`,
 * which no client can read, and applied later in a batch at a random time.
 *
 * Only released matches exist in `couples/{cid}/mutualPreferences`, and a non-match leaves
 * nothing behind anywhere a client can see.
 *
 * Every function recomputes from the current answers inside a transaction, so triggers that
 * fire twice or out of order still converge, and a release never applies a change the
 * answers no longer support.
 */
import { Timestamp, type DocumentReference, type Firestore, type Transaction } from 'firebase-admin/firestore';
import { MATCH_VERSION, matchLevelOf, releaseAfterMs, type MatchLevel, type RevealTiming } from './match';

type QueueOp = 'REVEAL' | 'RETRACT';

export interface QueueDoc {
  op: QueueOp;
  matchLevel?: MatchLevel;
  queuedAt: Timestamp;
  releaseAfter: Timestamp;
}

interface CoupleDoc {
  status?: string;
  memberUids?: string[];
}

export type SyncResult = 'unchanged' | 'queued' | 'dequeued' | 'inactive';

const couples = (db: Firestore) => db.collection('couples');
export const queueRef = (db: Firestore, coupleId: string, itemId: string) =>
  couples(db).doc(coupleId).collection('revealQueue').doc(itemId);
export const mutualRef = (db: Firestore, coupleId: string, itemId: string) =>
  couples(db).doc(coupleId).collection('mutualPreferences').doc(itemId);

function activeMembers(couple: CoupleDoc | undefined): [string, string] | null {
  if (couple?.status !== 'ACTIVE' || couple.memberUids?.length !== 2) return null;
  return [couple.memberUids[0], couple.memberUids[1]];
}

/** The match the two partners' current answers make for [itemId], read inside [tx]. */
async function currentMatch(
  tx: Transaction,
  db: Firestore,
  [a, b]: [string, string],
  itemId: string,
): Promise<MatchLevel | null> {
  const answer = (uid: string) => tx.get(db.collection('users').doc(uid).collection('preferences').doc(itemId));
  const [answerA, answerB] = await Promise.all([answer(a), answer(b)]);
  return matchLevelOf(answerA.data(), answerB.data());
}

/**
 * Brings the queue for one item in line with the partners' current answers
 * (onPreferenceWrite). Nothing a partner can see changes here; that waits for a release.
 */
export async function syncMatch(
  db: Firestore,
  coupleId: string,
  itemId: string,
  nowMs: number,
  timing: RevealTiming,
): Promise<SyncResult> {
  return db.runTransaction(async (tx) => {
    const members = activeMembers((await tx.get(couples(db).doc(coupleId))).data() as CoupleDoc | undefined);
    if (!members) return 'inactive';

    const desired = await currentMatch(tx, db, members, itemId);
    const queued = await tx.get(queueRef(db, coupleId, itemId));
    const revealed = await tx.get(mutualRef(db, coupleId, itemId));
    const shown = revealed.exists ? (revealed.get('matchLevel') as MatchLevel) : null;
    const pending = queued.data() as QueueDoc | undefined;

    // What the couple sees is already right: anything still queued is stale. For a match
    // withdrawn before it was ever revealed, this is where it vanishes without a trace.
    if (desired === shown) {
      if (!pending) return 'unchanged';
      tx.delete(queued.ref);
      return 'dequeued';
    }

    if (desired !== null) {
      // A reveal already waiting keeps its time, so re-answering cannot be used to nudge it.
      if (pending?.op === 'REVEAL') {
        if (pending.matchLevel !== desired) tx.update(queued.ref, { matchLevel: desired });
        return pending.matchLevel === desired ? 'unchanged' : 'queued';
      }
      enqueue(tx, queued.ref, { op: 'REVEAL', matchLevel: desired }, nowMs, timing);
      return 'queued';
    }

    // No longer a match, but the couple still sees one: withdraw it, on the same delay.
    if (pending?.op === 'RETRACT') return 'unchanged';
    enqueue(tx, queued.ref, { op: 'RETRACT' }, nowMs, timing);
    return 'queued';
  });
}

function enqueue(
  tx: Transaction,
  ref: DocumentReference,
  change: { op: QueueOp; matchLevel?: MatchLevel },
  nowMs: number,
  timing: RevealTiming,
): void {
  tx.set(ref, {
    ...change,
    queuedAt: Timestamp.fromMillis(nowMs),
    releaseAfter: Timestamp.fromMillis(releaseAfterMs(nowMs, timing)),
  });
}

/**
 * Queues every match a newly formed couple already has — answers given before pairing, or
 * with a previous partner, count as soon as both have them (onCoupleMemberChange).
 */
export async function syncCoupleMatches(
  db: Firestore,
  coupleId: string,
  nowMs: number,
  timing: RevealTiming,
): Promise<number> {
  const members = activeMembers((await couples(db).doc(coupleId).get()).data() as CoupleDoc | undefined);
  if (!members) return 0;

  const answered = async (uid: string) =>
    new Set((await db.collection('users').doc(uid).collection('preferences').get()).docs.map((doc) => doc.id));
  const [a, b] = await Promise.all(members.map(answered));

  let queued = 0;
  for (const itemId of [...a].filter((id) => b.has(id)).sort()) {
    if ((await syncMatch(db, coupleId, itemId, nowMs, timing)) === 'queued') queued += 1;
  }
  return queued;
}

// ---------------------------------------------------------------------------
// Releasing
// ---------------------------------------------------------------------------

/**
 * Applies every queued change for one couple that [isDue] accepts, as one batch.
 *
 * Each change is re-checked against the current answers first: a reveal whose match has
 * since gone is dropped, and a withdrawal of a match that has since come back is dropped.
 * An inactive couple has its queue cleared and nothing revealed. Returns how many changes a
 * partner will see — never a batch of zero (section 5.3).
 */
export async function releaseForCouple(
  db: Firestore,
  coupleId: string,
  nowMs: number,
  isDue: (entry: QueueDoc) => boolean,
): Promise<number> {
  return db.runTransaction(async (tx) => {
    const queue = await tx.get(couples(db).doc(coupleId).collection('revealQueue'));
    const members = activeMembers((await tx.get(couples(db).doc(coupleId))).data() as CoupleDoc | undefined);
    if (!members) {
      queue.docs.forEach((doc) => tx.delete(doc.ref));
      return 0;
    }

    const due = queue.docs.filter((doc) => isDue(doc.data() as QueueDoc));
    const plans: Array<() => void> = [];
    let visible = 0;

    for (const entry of due) {
      const itemId = entry.id;
      const desired = await currentMatch(tx, db, members, itemId);
      const revealed = await tx.get(mutualRef(db, coupleId, itemId));
      const now = Timestamp.fromMillis(nowMs);

      plans.push(() => tx.delete(entry.ref));
      if (desired !== null && revealed.get('matchLevel') !== desired) {
        visible += 1;
        plans.push(() =>
          tx.set(mutualRef(db, coupleId, itemId), {
            prefId: itemId,
            matchLevel: desired,
            revealedAt: now,
            // News again for both of them, even if an earlier level had been seen.
            seenBy: {},
            sourceVersion: MATCH_VERSION,
          }),
        );
      } else if (desired === null && revealed.exists) {
        visible += 1;
        plans.push(() => tx.delete(revealed.ref));
      }
    }

    // All reads first, then all writes: a transaction cannot read after it has written.
    plans.forEach((apply) => apply());
    return visible;
  });
}

/**
 * The scheduled release (section 8, `releaseMutualRevealBatches`): every couple with at
 * least one change whose random release time has passed.
 */
export async function releaseDue(db: Firestore, nowMs: number): Promise<{ couples: number; changes: number }> {
  const due = await db
    .collectionGroup('revealQueue')
    .where('releaseAfter', '<=', Timestamp.fromMillis(nowMs))
    .get();

  const coupleIds = new Set(due.docs.map((doc) => doc.ref.parent.parent!.id));
  let changes = 0;
  for (const coupleId of coupleIds) {
    changes += await releaseForCouple(db, coupleId, nowMs, (entry) => entry.releaseAfter.toMillis() <= nowMs);
  }
  return { couples: coupleIds.size, changes };
}

/**
 * The "release now" on a couple's next app open (section 5.3), read conservatively: it
 * releases only changes that have already waited the minimum delay, so opening the app
 * can make a reveal arrive sooner than its random time, but never soon enough to date the
 * answer behind it (DECISIONS.md D-035).
 */
export async function releaseOnOpen(
  db: Firestore,
  uid: string,
  nowMs: number,
  timing: RevealTiming,
): Promise<number> {
  const coupleId = (await db.collection('users').doc(uid).get()).get('coupleId');
  if (typeof coupleId !== 'string' || coupleId.length === 0) return 0;
  const cutoff = nowMs - timing.minDelayMs;
  return releaseForCouple(db, coupleId, nowMs, (entry) => entry.queuedAt.toMillis() <= cutoff);
}

/** A couple that has ended keeps no pending reveals. */
export async function clearQueue(db: Firestore, coupleId: string): Promise<void> {
  const queue = await couples(db).doc(coupleId).collection('revealQueue').get();
  const batch = db.batch();
  queue.docs.forEach((doc) => batch.delete(doc.ref));
  if (!queue.empty) await batch.commit();
}

