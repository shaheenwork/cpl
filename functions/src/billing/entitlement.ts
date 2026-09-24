/**
 * What a couple is entitled to (BUILD_PROMPT.md section 19).
 *
 * Two rules shape everything here. **The client never decides**: an entitlement is written only
 * by this server, from a purchase Google confirmed, and the security rules reject a client that
 * writes the field at all (section 7.3 row I). And it is **couple-scoped**: one subscription
 * covers both partners, so what the couple may do is the better of the two members' own records.
 *
 * A purchase lives on the buyer's user document, because that is whose account bought it and
 * whose account can restore it. What a night is built against is the couple's — derived, never
 * bought directly.
 */
import { Timestamp, type DocumentData, type Firestore } from 'firebase-admin/firestore';

export const TIERS = ['FREE', 'PREMIUM'] as const;
export type Tier = (typeof TIERS)[number];

/** How a premium record came to be: a real purchase, or the dev build's fake one. */
export const SOURCES = ['PLAY', 'DEV'] as const;
export type Source = (typeof SOURCES)[number];

/**
 * Where a subscription stands, in Play's own terms (section 19). GRACE still entitles — the
 * payment failed but the subscription is alive — while ON_HOLD does not, and PAUSED does not.
 */
export const STATES = ['ACTIVE', 'GRACE', 'ON_HOLD', 'PAUSED', 'CANCELED', 'EXPIRED', 'REVOKED'] as const;
export type SubscriptionState = (typeof STATES)[number];

/** The states that still let a couple in. A cancelled subscription runs to the end of its term. */
const ENTITLING = new Set<SubscriptionState>(['ACTIVE', 'GRACE', 'CANCELED']);

export interface Entitlement {
  tier: Tier;
  source: Source | null;
  state: SubscriptionState | null;
  expiresAtMillis: number | null;
  purchaseToken?: string | null;
}

export const FREE: Entitlement = { tier: 'FREE', source: null, state: null, expiresAtMillis: null };

/** What a stored record means right now: a term that has run out is FREE, whatever it says. */
export function entitlementOf(data: DocumentData | undefined, nowMs: number): Entitlement {
  const raw = data?.entitlement;
  if (raw === undefined || raw === null) return FREE;
  const state = STATES.includes(raw.state) ? (raw.state as SubscriptionState) : null;
  const expiresAtMillis = raw.expiresAt instanceof Timestamp ? raw.expiresAt.toMillis() : null;
  const live = state !== null
    && ENTITLING.has(state)
    && (expiresAtMillis === null || expiresAtMillis > nowMs);
  return {
    tier: live ? 'PREMIUM' : 'FREE',
    source: SOURCES.includes(raw.source) ? (raw.source as Source) : null,
    state,
    expiresAtMillis,
    purchaseToken: typeof raw.purchaseToken === 'string' ? raw.purchaseToken : null,
  };
}

/** The better of two: one subscription covers both partners (section 19). */
export function betterOf(a: Entitlement, b: Entitlement): Entitlement {
  if (a.tier === b.tier) {
    return (a.expiresAtMillis ?? 0) >= (b.expiresAtMillis ?? 0) ? a : b;
  }
  return a.tier === 'PREMIUM' ? a : b;
}

/**
 * What this couple may do. Read from both members' own records, so a partner who subscribed on
 * their own account covers the other without anything being copied between accounts.
 */
export async function coupleEntitlement(
  db: Firestore,
  members: readonly string[],
  nowMs: number,
): Promise<Entitlement> {
  let best = FREE;
  for (const uid of members) {
    const user = (await db.doc(`users/${uid}`).get()).data();
    best = betterOf(best, entitlementOf(user, nowMs));
  }
  return best;
}

/** The same for one person, by their couple — used where only a uid is at hand. */
export async function entitlementFor(db: Firestore, uid: string, nowMs: number): Promise<Entitlement> {
  const coupleId = (await db.doc(`users/${uid}`).get()).get('coupleId');
  if (typeof coupleId !== 'string' || coupleId.length === 0) {
    return entitlementOf((await db.doc(`users/${uid}`).get()).data(), nowMs);
  }
  const members = (await db.doc(`couples/${coupleId}`).get()).get('memberUids');
  return coupleEntitlement(db, Array.isArray(members) ? (members as string[]) : [uid], nowMs);
}

/** What both partners may read about it: the tier and when it runs out. Never a purchase token. */
export function publicView(entitlement: Entitlement): DocumentData {
  return {
    tier: entitlement.tier,
    state: entitlement.state,
    expiresAt: entitlement.expiresAtMillis === null
      ? null
      : Timestamp.fromMillis(entitlement.expiresAtMillis),
  };
}

/**
 * Writes the couple's derived entitlement onto the couple document, where both partners read it.
 * Nothing is gated on this copy — every gate reads the members' own records — it is there so a
 * phone can say "premium" without reading its partner's account.
 */
export async function publishCoupleEntitlement(db: Firestore, coupleId: string, nowMs: number): Promise<Entitlement> {
  const couple = await db.doc(`couples/${coupleId}`).get();
  const members = couple.get('memberUids');
  if (!Array.isArray(members)) return FREE;
  const entitlement = await coupleEntitlement(db, members as string[], nowMs);
  await db.doc(`couples/${coupleId}`).update({ entitlement: publicView(entitlement) });
  return entitlement;
}
