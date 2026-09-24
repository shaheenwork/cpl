/**
 * Subscriptions (BUILD_PROMPT.md section 19).
 *
 * The client never decides entitlement. A phone hands over a purchase token; this server asks
 * Google what that token is worth, writes the answer on the buyer's user document, and publishes
 * the couple's derived tier. Play then tells the server about every later change to the
 * subscription — renewal, grace period, on hold, cancellation, a refund — through a real-time
 * developer notification, and the same code applies it.
 *
 * Nothing here trusts a payload: not the tier, not the expiry, not even that the token belongs to
 * the caller. The only thing a phone contributes is the token itself.
 */
import { Timestamp, type Firestore } from 'firebase-admin/firestore';
import { ServiceError } from '../shared/errors';
import { consumeRateLimit } from '../shared/rateLimit';
import { entitlementOf, publishCoupleEntitlement, type Entitlement, type SubscriptionState } from './entitlement';

/** The one product. A couple buys premium, not a basket (section 19). */
export const PREMIUM_PRODUCT = 'afterhours.premium.monthly';

export const VERIFY_LIMIT = 20;
export const VERIFY_WINDOW_MS = 60 * 60 * 1000;

const TOKEN = /^[A-Za-z0-9_.:-]{8,512}$/;

function invalid(reason: string): never {
  throw new ServiceError('invalid-argument', reason);
}

/** What Google says a purchase token is worth. */
export interface VerifiedPurchase {
  productId: string;
  state: SubscriptionState;
  expiresAtMillis: number;
  /** Play's own account identifier for the subscription, for a later notification to match. */
  linkedToken?: string | null;
}

/**
 * Asking Google. Injected so the emulator can answer for itself: `androidpublisher` needs a real
 * Play account, a real package and a real purchase, none of which exist locally (D-091).
 */
export interface PurchaseVerifier {
  verify(productId: string, purchaseToken: string): Promise<VerifiedPurchase>;
}

/**
 * The dev verifier. Accepts a token that says what it is, and **only** inside the Emulator Suite:
 * outside it, this throws rather than entitling anyone, so the fake can never become a way in.
 */
export const devVerifier: PurchaseVerifier = {
  async verify(productId, purchaseToken) {
    if (process.env.FUNCTIONS_EMULATOR !== 'true') {
      throw new ServiceError('failed-precondition', 'billing-not-configured');
    }
    if (!purchaseToken.startsWith('dev-')) throw new ServiceError('invalid-argument', 'purchaseToken');
    return {
      productId,
      state: 'ACTIVE',
      expiresAtMillis: Date.now() + DEV_TERM_MS,
      linkedToken: null,
    };
  },
};

/** A month, for the dev build's fake purchase. */
const DEV_TERM_MS = 30 * 24 * 60 * 60 * 1000;

/**
 * A phone reporting a purchase. Verified, then written — and written to the caller's own document
 * only, whatever the payload says about anyone else.
 */
export async function verifyPurchase(
  db: Firestore,
  uid: string,
  input: unknown,
  nowMs: number,
  verifier: PurchaseVerifier = devVerifier,
): Promise<{ tier: string; expiresAtMillis: number | null }> {
  if (typeof input !== 'object' || input === null || Array.isArray(input)) invalid('request');
  const raw = input as Record<string, unknown>;
  for (const key of Object.keys(raw)) if (key !== 'purchaseToken' && key !== 'productId') invalid(`unknown field ${key}`);
  const purchaseToken = raw.purchaseToken;
  if (typeof purchaseToken !== 'string' || !TOKEN.test(purchaseToken)) invalid('purchaseToken');
  const productId = raw.productId === undefined ? PREMIUM_PRODUCT : raw.productId;
  if (productId !== PREMIUM_PRODUCT) invalid('productId');

  await consumeRateLimit(db, uid, 'verifyPurchase', VERIFY_LIMIT, VERIFY_WINDOW_MS, nowMs);
  const verified = await verifier.verify(productId, purchaseToken);

  const entitlement = await writeEntitlement(db, uid, {
    state: verified.state,
    expiresAtMillis: verified.expiresAtMillis,
    purchaseToken,
    source: process.env.FUNCTIONS_EMULATOR === 'true' ? 'DEV' : 'PLAY',
    nowMs,
  });
  return { tier: entitlement.tier, expiresAtMillis: entitlement.expiresAtMillis };
}

interface WriteInput {
  state: SubscriptionState;
  expiresAtMillis: number;
  purchaseToken: string;
  source: 'PLAY' | 'DEV';
  nowMs: number;
}

/** The one place an entitlement is written, so there is one place to read to know it is safe. */
async function writeEntitlement(db: Firestore, uid: string, input: WriteInput): Promise<Entitlement> {
  await db.doc(`users/${uid}`).set(
    {
      entitlement: {
        tier: 'PREMIUM',
        source: input.source,
        state: input.state,
        purchaseToken: input.purchaseToken,
        expiresAt: Timestamp.fromMillis(input.expiresAtMillis),
        updatedAt: Timestamp.fromMillis(input.nowMs),
      },
    },
    { merge: true },
  );
  const coupleId = (await db.doc(`users/${uid}`).get()).get('coupleId');
  if (typeof coupleId === 'string' && coupleId.length > 0) {
    await publishCoupleEntitlement(db, coupleId, input.nowMs);
  }
  return entitlementOf((await db.doc(`users/${uid}`).get()).data(), input.nowMs);
}

/**
 * What a phone may ask about its own subscription — after a reinstall, or a restore. It reports
 * what the server holds; it never takes the phone's word for anything.
 */
export async function currentEntitlement(
  db: Firestore,
  uid: string,
  nowMs: number,
): Promise<{ tier: string; state: SubscriptionState | null; expiresAtMillis: number | null }> {
  const entitlement = entitlementOf((await db.doc(`users/${uid}`).get()).data(), nowMs);
  return { tier: entitlement.tier, state: entitlement.state, expiresAtMillis: entitlement.expiresAtMillis };
}

/** What Play's notifications mean, in the words this server uses (section 19). */
export const NOTIFICATION_STATES: Record<number, SubscriptionState> = {
  1: 'ACTIVE', // RECOVERED
  2: 'ACTIVE', // RENEWED
  3: 'CANCELED', // CANCELED — runs to the end of its term
  4: 'ACTIVE', // PURCHASED
  5: 'ON_HOLD',
  6: 'GRACE',
  7: 'ACTIVE', // RESTARTED
  10: 'PAUSED',
  12: 'REVOKED',
  13: 'EXPIRED',
};

export interface PlayNotification {
  purchaseToken: string;
  notificationType: number;
  expiresAtMillis: number | null;
}

/**
 * A change Play told us about. Matched to the account that reported the token — a notification
 * for a token nobody here has is nothing to act on, rather than a reason to write anything.
 *
 * A revocation (a refund) ends the entitlement immediately, whatever its term said.
 */
export async function applyPlayNotification(
  db: Firestore,
  notification: PlayNotification,
  nowMs: number,
): Promise<'applied' | 'unknown-token'> {
  const state = NOTIFICATION_STATES[notification.notificationType];
  if (state === undefined) return 'unknown-token';
  const holders = await db
    .collection('users')
    .where('entitlement.purchaseToken', '==', notification.purchaseToken)
    .limit(2)
    .get();
  if (holders.empty) return 'unknown-token';

  for (const holder of holders.docs) {
    const expiresAt = state === 'REVOKED'
      ? Timestamp.fromMillis(nowMs)
      : Timestamp.fromMillis(notification.expiresAtMillis ?? currentExpiry(holder.data(), nowMs));
    await holder.ref.set(
      {
        entitlement: {
          tier: 'PREMIUM',
          state,
          expiresAt,
          updatedAt: Timestamp.fromMillis(nowMs),
        },
      },
      { merge: true },
    );
    const coupleId = holder.get('coupleId');
    if (typeof coupleId === 'string' && coupleId.length > 0) {
      await publishCoupleEntitlement(db, coupleId, nowMs);
    }
  }
  return 'applied';
}

function currentExpiry(data: FirebaseFirestore.DocumentData, nowMs: number): number {
  const expiresAt = data?.entitlement?.expiresAt;
  return expiresAt instanceof Timestamp ? expiresAt.toMillis() : nowMs;
}

/**
 * The sweep that keeps a missed notification from leaving someone premium for ever: a term that
 * has run out is written back as expired, and the couple's copy follows.
 */
export async function expireEntitlements(db: Firestore, nowMs: number): Promise<{ expired: number }> {
  const lapsed = await db
    .collection('users')
    .where('entitlement.expiresAt', '<=', Timestamp.fromMillis(nowMs))
    .get();

  let expired = 0;
  for (const user of lapsed.docs) {
    if (user.get('entitlement.state') === 'EXPIRED') continue;
    await user.ref.set(
      { entitlement: { state: 'EXPIRED', updatedAt: Timestamp.fromMillis(nowMs) } },
      { merge: true },
    );
    const coupleId = user.get('coupleId');
    if (typeof coupleId === 'string' && coupleId.length > 0) {
      await publishCoupleEntitlement(db, coupleId, nowMs);
    }
    expired += 1;
  }
  return { expired };
}
