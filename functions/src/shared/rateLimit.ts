import type { Firestore } from 'firebase-admin/firestore';
import { PairingError } from './errors';

/**
 * Fixed-window rate limiting, one counter document per user and action, stored in the
 * server-only `rateLimits` collection.
 *
 * Runs in its own transaction on purpose: an attempt counts even if the operation it
 * guards then fails. For code guessing that is exactly right — a failed guess is the thing
 * being limited.
 */
export async function consumeRateLimit(
  db: Firestore,
  uid: string,
  action: string,
  limit: number,
  windowMs: number,
  nowMs: number,
): Promise<void> {
  const ref = db.collection('rateLimits').doc(`${uid}_${action}`);
  await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const data = snap.data() as { windowStart: number; count: number } | undefined;

    if (!data || nowMs - data.windowStart >= windowMs) {
      tx.set(ref, { windowStart: nowMs, count: 1 });
      return;
    }
    if (data.count >= limit) {
      throw new PairingError('resource-exhausted', 'rate-limited');
    }
    tx.update(ref, { count: data.count + 1 });
  });
}
