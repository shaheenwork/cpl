/**
 * Couple pairing: the trusted, transactional half (BUILD_PROMPT.md sections 8 and 51).
 *
 * The flow is a handshake, not a single step:
 *
 *   1. Creator:  createPairingCode()      code OPEN
 *   2. Joiner:   requestPairing(code)     code PENDING, both phones show the same symbols
 *   3. Creator:  respondToPairing(true)   couple ACTIVE, code CONSUMED
 *
 * Entering a code never pairs anyone on its own. See verification.ts for why.
 *
 * Every function here takes the database and the current time as parameters, so the
 * transactional logic — including the races — is tested directly against the emulator
 * without the callable wrapper in the way.
 *
 * Invariants these transactions protect:
 *  - A user belongs to at most one ACTIVE couple.
 *  - A couple has exactly two members, created together, atomically.
 *  - A code is used at most once.
 *  - Nobody can pair with themselves.
 *  - Only the code's creator can approve a request against it.
 */
import { FieldValue, Timestamp, type Firestore, type Transaction } from 'firebase-admin/firestore';
import { contentLevelOf } from '../engine/filters';
import { PairingError } from '../shared/errors';
import { consumeRateLimit } from '../shared/rateLimit';
import { CODE_TTL_MS, generateCode, isWellFormed } from './code';
import { generateVerification } from './verification';

/** How long a creator has to approve a request once it arrives. */
export const REQUEST_TTL_MS = 10 * 60 * 1000;

const CODE_CANDIDATES = 5;

export const LIMITS = {
  createCode: { limit: 10, windowMs: 60 * 60 * 1000 },
  requestPairing: { limit: 10, windowMs: 10 * 60 * 1000 },
} as const;

type CodeStatus = 'OPEN' | 'PENDING' | 'CONSUMED' | 'CANCELLED';

interface CodeDoc {
  creatorUid: string;
  status: CodeStatus;
  createdAt: Timestamp;
  expiresAt: Timestamp;
  joinerUid?: string;
  verification?: string[];
  requestedAt?: Timestamp;
}

interface UserDoc {
  coupleId?: string | null;
  contentLevel?: number;
  pairing?: {
    role: 'CREATOR' | 'JOINER';
    status: 'OPEN' | 'REQUESTED' | 'WAITING' | 'DECLINED';
    code: string;
    verification?: string[];
  };
}

const users = (db: Firestore) => db.collection('users');
const codes = (db: Firestore) => db.collection('pairingCodes');
const couples = (db: Firestore) => db.collection('couples');

function isPaired(user: UserDoc | undefined): boolean {
  return typeof user?.coupleId === 'string' && user.coupleId.length > 0;
}

function isLive(code: CodeDoc | undefined, nowMs: number): boolean {
  return (
    code !== undefined &&
    (code.status === 'OPEN' || code.status === 'PENDING') &&
    code.expiresAt.toMillis() > nowMs
  );
}

// ---------------------------------------------------------------------------
// 1. Creator makes a code
// ---------------------------------------------------------------------------

export async function createPairingCode(
  db: Firestore,
  uid: string,
  nowMs: number,
): Promise<{ code: string; expiresAtMs: number }> {
  await consumeRateLimit(db, uid, 'createCode', LIMITS.createCode.limit, LIMITS.createCode.windowMs, nowMs);

  // Generated outside the transaction; every candidate is read inside it, and the first
  // one not currently live wins. Five candidates colliding is not a realistic event.
  const candidates = Array.from({ length: CODE_CANDIDATES }, generateCode);

  return db.runTransaction(async (tx) => {
    const userRef = users(db).doc(uid);
    const user = (await tx.get(userRef)).data() as UserDoc | undefined;
    if (isPaired(user)) throw new PairingError('failed-precondition', 'already-paired');

    // One live code per creator: a new one retires the old.
    const previousCode = user?.pairing?.role === 'CREATOR' ? user.pairing.code : undefined;
    const previousSnap = previousCode ? await tx.get(codes(db).doc(previousCode)) : undefined;

    const candidateSnaps = await Promise.all(candidates.map((c) => tx.get(codes(db).doc(c))));
    const index = candidateSnaps.findIndex((snap) => !isLive(snap.data() as CodeDoc | undefined, nowMs));
    if (index < 0) throw new PairingError('resource-exhausted', 'no-code-available');
    const code = candidates[index];

    if (previousSnap?.exists && isLive(previousSnap.data() as CodeDoc, nowMs)) {
      retireCode(tx, db, previousSnap.data() as CodeDoc, previousCode as string);
    }

    const expiresAtMs = nowMs + CODE_TTL_MS;
    tx.set(codes(db).doc(code), {
      creatorUid: uid,
      status: 'OPEN',
      createdAt: Timestamp.fromMillis(nowMs),
      expiresAt: Timestamp.fromMillis(expiresAtMs),
    } satisfies CodeDoc);
    tx.set(
      userRef,
      { pairing: { role: 'CREATOR', status: 'OPEN', code, expiresAtMs } },
      { merge: true },
    );
    return { code, expiresAtMs };
  });
}

/** Cancels a code, and releases a joiner who was waiting on it. */
function retireCode(tx: Transaction, db: Firestore, doc: CodeDoc, code: string): void {
  tx.update(codes(db).doc(code), { status: 'CANCELLED' });
  if (doc.status === 'PENDING' && doc.joinerUid) {
    tx.set(
      users(db).doc(doc.joinerUid),
      { pairing: { role: 'JOINER', status: 'DECLINED', code } },
      { merge: true },
    );
  }
}

// ---------------------------------------------------------------------------
// 2. Joiner enters the code
// ---------------------------------------------------------------------------

export async function requestPairing(
  db: Firestore,
  joinerUid: string,
  rawCode: unknown,
  nowMs: number,
): Promise<{ verification: string[] }> {
  if (!isWellFormed(rawCode)) throw new PairingError('invalid-argument', 'malformed-code');
  const code = rawCode;

  // Counted before the lookup, so failed guesses are exactly what gets limited.
  await consumeRateLimit(
    db, joinerUid, 'requestPairing',
    LIMITS.requestPairing.limit, LIMITS.requestPairing.windowMs, nowMs,
  );

  return db.runTransaction(async (tx) => {
    const codeRef = codes(db).doc(code);
    const codeDoc = (await tx.get(codeRef)).data() as CodeDoc | undefined;

    // "Not found", "expired" and "cancelled" are deliberately indistinguishable to the
    // caller: telling a guesser *why* a code failed tells them which codes exist.
    if (!codeDoc || codeDoc.status === 'CANCELLED' || codeDoc.expiresAt.toMillis() <= nowMs) {
      throw new PairingError('not-found', 'invalid-code');
    }
    if (codeDoc.status === 'CONSUMED') throw new PairingError('not-found', 'invalid-code');
    if (codeDoc.creatorUid === joinerUid) throw new PairingError('failed-precondition', 'own-code');
    if (codeDoc.status === 'PENDING') throw new PairingError('failed-precondition', 'busy');

    const joinerRef = users(db).doc(joinerUid);
    const creatorRef = users(db).doc(codeDoc.creatorUid);
    const [joinerSnap, creatorSnap] = await Promise.all([tx.get(joinerRef), tx.get(creatorRef)]);
    const joiner = joinerSnap.data() as UserDoc | undefined;
    const creator = creatorSnap.data() as UserDoc | undefined;

    if (isPaired(joiner)) throw new PairingError('failed-precondition', 'already-paired');
    if (isPaired(creator)) throw new PairingError('not-found', 'invalid-code');
    if (joiner?.pairing?.role === 'JOINER' && joiner.pairing.status === 'WAITING') {
      throw new PairingError('failed-precondition', 'already-requesting');
    }

    const verification = generateVerification();
    // A request gets its own window, so a code entered in its last minute can still be
    // approved.
    const expiresAtMs = Math.max(codeDoc.expiresAt.toMillis(), nowMs + REQUEST_TTL_MS);

    tx.update(codeRef, {
      status: 'PENDING',
      joinerUid,
      verification,
      requestedAt: Timestamp.fromMillis(nowMs),
      expiresAt: Timestamp.fromMillis(expiresAtMs),
    });
    tx.set(joinerRef, { pairing: { role: 'JOINER', status: 'WAITING', code, verification } }, { merge: true });
    tx.set(creatorRef, { pairing: { role: 'CREATOR', status: 'REQUESTED', code, verification, expiresAtMs } }, { merge: true });
    return { verification };
  });
}

// ---------------------------------------------------------------------------
// 3. Creator approves or declines
// ---------------------------------------------------------------------------

export async function respondToPairing(
  db: Firestore,
  creatorUid: string,
  approve: boolean,
  nowMs: number,
): Promise<{ coupleId: string | null }> {
  return db.runTransaction(async (tx) => {
    const creatorRef = users(db).doc(creatorUid);
    const creator = (await tx.get(creatorRef)).data() as UserDoc | undefined;
    const pairing = creator?.pairing;
    if (pairing?.role !== 'CREATOR' || pairing.status !== 'REQUESTED') {
      throw new PairingError('failed-precondition', 'nothing-to-respond-to');
    }

    const codeRef = codes(db).doc(pairing.code);
    const codeDoc = (await tx.get(codeRef)).data() as CodeDoc | undefined;
    if (!codeDoc || codeDoc.status !== 'PENDING' || codeDoc.creatorUid !== creatorUid || !codeDoc.joinerUid) {
      throw new PairingError('failed-precondition', 'nothing-to-respond-to');
    }

    const joinerRef = users(db).doc(codeDoc.joinerUid);
    const joiner = (await tx.get(joinerRef)).data() as UserDoc | undefined;

    if (!approve) {
      tx.update(codeRef, { status: 'CANCELLED' });
      tx.set(creatorRef, { pairing: FieldValue.delete() }, { merge: true });
      tx.set(joinerRef, { pairing: { role: 'JOINER', status: 'DECLINED', code: pairing.code } }, { merge: true });
      return { coupleId: null };
    }

    if (codeDoc.expiresAt.toMillis() <= nowMs) throw new PairingError('failed-precondition', 'expired');
    // Either side may have paired elsewhere since the request was made.
    if (isPaired(creator) || isPaired(joiner)) {
      throw new PairingError('failed-precondition', 'already-paired');
    }

    const coupleRef = couples(db).doc();
    const now = Timestamp.fromMillis(nowMs);
    const memberUids = [creatorUid, codeDoc.joinerUid];

    tx.set(coupleRef, {
      status: 'ACTIVE',
      memberUids,
      createdAt: now,
      // Never escalated past the lower of the two content levels (BUILD_PROMPT.md
      // Appendix A). Recomputed whenever either partner changes theirs.
      contentLevelEffective: Math.min(contentLevelOf(creator?.contentLevel), contentLevelOf(joiner?.contentLevel)),
      currentMode: 'TOGETHER',
    });
    tx.set(coupleRef.collection('members').doc(creatorUid), { role: 'CREATOR', joinedAt: now, status: 'ACTIVE' });
    tx.set(coupleRef.collection('members').doc(codeDoc.joinerUid), { role: 'PARTNER', joinedAt: now, status: 'ACTIVE' });

    tx.set(creatorRef, { coupleId: coupleRef.id, pairing: FieldValue.delete() }, { merge: true });
    tx.set(joinerRef, { coupleId: coupleRef.id, pairing: FieldValue.delete() }, { merge: true });
    tx.update(codeRef, { status: 'CONSUMED', consumedAt: now, coupleId: coupleRef.id });

    return { coupleId: coupleRef.id };
  });
}

// ---------------------------------------------------------------------------
// Either side walks away mid-handshake
// ---------------------------------------------------------------------------

export async function cancelPairing(db: Firestore, uid: string): Promise<void> {
  await db.runTransaction(async (tx) => {
    const userRef = users(db).doc(uid);
    const pairing = ((await tx.get(userRef)).data() as UserDoc | undefined)?.pairing;
    if (!pairing) return;

    const codeRef = codes(db).doc(pairing.code);
    const codeDoc = (await tx.get(codeRef)).data() as CodeDoc | undefined;

    if (pairing.role === 'CREATOR') {
      if (codeDoc && (codeDoc.status === 'OPEN' || codeDoc.status === 'PENDING')) {
        retireCode(tx, db, codeDoc, pairing.code);
      }
    } else if (pairing.status === 'WAITING' && codeDoc?.status === 'PENDING' && codeDoc.joinerUid === uid) {
      // The code goes back to OPEN so the creator's real partner can still use it.
      tx.update(codeRef, {
        status: 'OPEN',
        joinerUid: FieldValue.delete(),
        verification: FieldValue.delete(),
        requestedAt: FieldValue.delete(),
      });
      tx.set(
        users(db).doc(codeDoc.creatorUid),
        {
          pairing: {
            role: 'CREATOR',
            status: 'OPEN',
            code: pairing.code,
            expiresAtMs: codeDoc.expiresAt.toMillis(),
          },
        },
        { merge: true },
      );
    }
    tx.set(userRef, { pairing: FieldValue.delete() }, { merge: true });
  });
}

// ---------------------------------------------------------------------------
// Unpairing
// ---------------------------------------------------------------------------

/**
 * Ends the couple for both partners at once.
 *
 * Neither partner can read the couple's data afterwards, because every couple-scoped rule
 * requires status ACTIVE. The data itself is retained for now; what happens to shared
 * memories on unpair is decided with account deletion in Phase 20.
 *
 * Who unpaired is deliberately not recorded. Nothing needs it, and it is exactly the kind of
 * detail that should not exist to be surfaced later (BUILD_PROMPT.md section 3.1).
 */
export async function unpairCouple(db: Firestore, uid: string, nowMs: number): Promise<void> {
  await db.runTransaction(async (tx) => {
    const userRef = users(db).doc(uid);
    const coupleId = ((await tx.get(userRef)).data() as UserDoc | undefined)?.coupleId;
    if (!coupleId) throw new PairingError('failed-precondition', 'not-paired');

    const coupleRef = couples(db).doc(coupleId);
    const couple = (await tx.get(coupleRef)).data() as { status: string; memberUids: string[] } | undefined;
    if (!couple || couple.status !== 'ACTIVE' || !couple.memberUids.includes(uid)) {
      throw new PairingError('failed-precondition', 'not-paired');
    }

    tx.update(coupleRef, { status: 'UNPAIRED', unpairedAt: Timestamp.fromMillis(nowMs) });
    for (const memberUid of couple.memberUids) {
      tx.update(coupleRef.collection('members').doc(memberUid), { status: 'LEFT' });
      tx.set(users(db).doc(memberUid), { coupleId: FieldValue.delete() }, { merge: true });
    }
  });
}
