/**
 * Shared test support. Not a test itself: the runner takes `*.test.js` only.
 */
import { Timestamp, type Firestore } from 'firebase-admin/firestore';

/** A month, which is longer than any test's clock travels. */
const TERM_MS = 30 * 24 * 60 * 60 * 1000;

/**
 * Gives a couple premium, the way a verified purchase would (section 19).
 *
 * Most suites are about what the app does, not about what it costs: they build more nights than
 * the free tier allows, and they play the shapes premium pays for. The billing suite is where the
 * free tier's own limits are tested.
 */
export async function grantPremium(db: Firestore, uid: string, fromMs: number): Promise<void> {
  await db.doc(`users/${uid}`).set(
    {
      entitlement: {
        tier: 'PREMIUM',
        source: 'DEV',
        state: 'ACTIVE',
        purchaseToken: `dev-${uid}`,
        expiresAt: Timestamp.fromMillis(fromMs + TERM_MS),
        updatedAt: Timestamp.fromMillis(fromMs),
      },
    },
    { merge: true },
  );
}
