/**
 * Keeps a couple's server-only filters in step with both partners' private settings
 * (BUILD_PROMPT.md section 8: onBoundaryWrite, onCoupleMemberChange).
 *
 * Always recomputed from scratch, from the current documents, inside one transaction. So it
 * is idempotent and order-independent: triggers may fire more than once and in any order,
 * and whichever runs last still writes the truth. The transaction also means two partners
 * changing boundaries at the same moment cannot leave filters built from a stale read.
 */
import { Timestamp, type Firestore } from 'firebase-admin/firestore';
import { computeEngineFilters, type MemberInput } from '../engine/filters';

export type RecomputeResult = 'updated' | 'cleared';

interface CoupleDoc {
  status?: string;
  memberUids?: string[];
  contentLevelEffective?: number;
}

export function filtersRef(db: Firestore, coupleId: string) {
  return db.collection('couples').doc(coupleId).collection('engineFilters').doc('current');
}

export async function recomputeCouple(db: Firestore, coupleId: string, nowMs: number): Promise<RecomputeResult> {
  return db.runTransaction(async (tx) => {
    const coupleRef = db.collection('couples').doc(coupleId);
    const couple = (await tx.get(coupleRef)).data() as CoupleDoc | undefined;

    // The combined boundaries must not outlive the couple: once it is no longer active,
    // nothing may be built from them, so there is no reason to keep them at all.
    if (!couple || couple.status !== 'ACTIVE' || !couple.memberUids?.length) {
      tx.delete(filtersRef(db, coupleId));
      return 'cleared';
    }

    const members: MemberInput[] = [];
    for (const uid of couple.memberUids) {
      const userRef = db.collection('users').doc(uid);
      const user = (await tx.get(userRef)).data();
      const boundaries = await tx.get(userRef.collection('boundaries'));
      const nevers = await tx.get(userRef.collection('preferences').where('value', '==', 'NEVER'));
      members.push({
        uid,
        contentLevel: user?.contentLevel,
        boundaries: Object.fromEntries(boundaries.docs.map((doc) => [doc.id, doc.get('level')])),
        neverItems: nevers.docs.map((doc) => doc.id),
      });
    }

    const filters = computeEngineFilters(members);
    if (couple.contentLevelEffective !== filters.maxIntensity) {
      tx.update(coupleRef, { contentLevelEffective: filters.maxIntensity });
    }
    tx.set(filtersRef(db, coupleId), { ...filters, computedAt: Timestamp.fromMillis(nowMs) });
    return 'updated';
  });
}

/** The couple a user belongs to right now, if any. */
export async function coupleOf(db: Firestore, uid: string): Promise<string | undefined> {
  const coupleId = (await db.collection('users').doc(uid).get()).get('coupleId');
  return typeof coupleId === 'string' && coupleId.length > 0 ? coupleId : undefined;
}
