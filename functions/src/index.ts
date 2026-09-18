/**
 * Callable entry points. Deliberately thin: authenticate, delegate to the core logic, map
 * errors. Everything that can race lives in the core modules, where it is tested directly.
 *
 * Every callable:
 *  - enforces App Check in production (BUILD_PROMPT.md section 8), relaxed only under the
 *    Functions emulator, where no real attestation exists;
 *  - takes the caller's identity from the verified auth token and never from the payload —
 *    a client-supplied uid or coupleId is never trusted.
 */
import { initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { onDocumentWritten } from 'firebase-functions/v2/firestore';
import { HttpsError, onCall, type CallableRequest } from 'firebase-functions/v2/https';
import { onSchedule } from 'firebase-functions/v2/scheduler';
import { coupleOf, recomputeCouple } from './couple/recompute';
import * as reveals from './discovery/reveals';
import { revealTiming } from './discovery/timing';
import * as pairing from './pairing/pairing';
import { consumeRateLimit } from './shared/rateLimit';
import { toHttpsError } from './shared/errors';

initializeApp();

const enforceAppCheck = process.env.FUNCTIONS_EMULATOR !== 'true';
const options = { enforceAppCheck, maxInstances: 10 };

function callerUid(request: CallableRequest): string {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError('unauthenticated', 'Sign in first.');
  return uid;
}

async function guarded<T>(work: () => Promise<T>): Promise<T> {
  try {
    return await work();
  } catch (error) {
    throw toHttpsError(error);
  }
}

export const createPairingCode = onCall(options, (request) =>
  guarded(() => pairing.createPairingCode(getFirestore(), callerUid(request), Date.now())),
);

export const requestPairing = onCall(options, (request) =>
  guarded(() => pairing.requestPairing(getFirestore(), callerUid(request), request.data?.code, Date.now())),
);

export const respondToPairing = onCall(options, (request) =>
  guarded(() => {
    const approve = request.data?.approve;
    if (typeof approve !== 'boolean') throw new HttpsError('invalid-argument', 'approve must be a boolean');
    return pairing.respondToPairing(getFirestore(), callerUid(request), approve, Date.now());
  }),
);

export const cancelPairing = onCall(options, (request) =>
  guarded(() => pairing.cancelPairing(getFirestore(), callerUid(request))),
);

export const unpairCouple = onCall(options, (request) =>
  guarded(() => {
    // An explicit confirmation flag, so an accidental or replayed empty call cannot end a
    // couple.
    if (request.data?.confirm !== true) throw new HttpsError('invalid-argument', 'confirm must be true');
    return pairing.unpairCouple(getFirestore(), callerUid(request), Date.now());
  }),
);

// ---------------------------------------------------------------------------
// Triggers: keep each couple's server-only filters in step (section 8)
// ---------------------------------------------------------------------------
//
// Every trigger recomputes from the current documents, so a duplicate or out-of-order
// delivery still converges on the truth. Retried on failure for the same reason, and
// because a boundary that silently failed to apply is the one failure this app cannot
// afford.
const triggerOptions = { retry: true, maxInstances: 10 };

/**
 * A member's own content level or couple membership changed (onCoupleMemberChange). Covers
 * pairing, where `coupleId` appears, and unpairing, where it goes and the couple's filters
 * are deleted with it.
 */
export const onUserWritten = onDocumentWritten({ ...triggerOptions, document: 'users/{uid}' }, async (event) => {
  const before = event.data?.before.data();
  const after = event.data?.after.data();
  if (before?.contentLevel === after?.contentLevel && before?.coupleId === after?.coupleId) return;

  const db = getFirestore();
  const coupleIds = new Set(
    [before?.coupleId, after?.coupleId].filter((id): id is string => typeof id === 'string' && id.length > 0),
  );
  for (const coupleId of coupleIds) await recomputeCouple(db, coupleId, Date.now());

  if (before?.coupleId === after?.coupleId) return;
  // A couple formed: answers either partner already gave can match now (section 5.2).
  if (typeof after?.coupleId === 'string') {
    await reveals.syncCoupleMatches(db, after.coupleId, Date.now(), await revealTiming());
  }
  // A couple ended: nothing still waiting is ever revealed.
  if (typeof before?.coupleId === 'string') await reveals.clearQueue(db, before.coupleId);
});

/** A partner changed one of their private boundaries (onBoundaryWrite). */
export const onBoundaryWritten = onDocumentWritten(
  { ...triggerOptions, document: 'users/{uid}/boundaries/{themeId}' },
  async (event) => {
    const coupleId = await coupleOf(getFirestore(), event.params.uid);
    if (coupleId) await recomputeCouple(getFirestore(), coupleId, Date.now());
  },
);

/**
 * A partner changed a private answer (onPreferenceWrite): queue whatever that does to the
 * couple's matches (section 5.2) — never reveal it now (section 5.3) — and, if a NEVER was
 * given or taken back, rebuild the filters.
 */
export const onPreferenceWritten = onDocumentWritten(
  { ...triggerOptions, document: 'users/{uid}/preferences/{itemId}' },
  async (event) => {
    const before = event.data?.before;
    const after = event.data?.after;
    const db = getFirestore();
    const coupleId = await coupleOf(db, event.params.uid);
    if (!coupleId) return;

    if (before?.get('value') !== after?.get('value') || before?.get('secret') !== after?.get('secret')) {
      await reveals.syncMatch(db, coupleId, event.params.itemId, Date.now(), await revealTiming());
    }
    if ((before?.get('value') === 'NEVER') !== (after?.get('value') === 'NEVER')) {
      await recomputeCouple(db, coupleId, Date.now());
    }
  },
);

// ---------------------------------------------------------------------------
// Reveals: batched and jittered (section 5.3)
// ---------------------------------------------------------------------------

/**
 * Releases every queued change whose random time has come (section 8,
 * releaseMutualRevealBatches). Couples with nothing due are untouched, so no empty batch is
 * ever sent.
 */
export const releaseMutualRevealBatches = onSchedule(
  { schedule: 'every 15 minutes', retryCount: 3, maxInstances: 1 },
  async () => {
    await reveals.releaseDue(getFirestore(), Date.now());
  },
);

/**
 * The "release now" on opening the app (section 5.3) — only for changes that have already
 * waited the minimum delay (DECISIONS.md D-035). Rate-limited: it is cheap, but it should
 * not be a way to poll.
 */
export const releaseRevealsNow = onCall(options, (request) =>
  guarded(async () => {
    const uid = callerUid(request);
    const db = getFirestore();
    const now = Date.now();
    await consumeRateLimit(db, uid, 'releaseOnOpen', RELEASE_ON_OPEN_LIMIT, RELEASE_ON_OPEN_WINDOW_MS, now);
    const released = await reveals.releaseOnOpen(db, uid, now, await revealTiming(now));
    // A count only: which items changed is for the listener to show, not this reply.
    return { released };
  }),
);

const RELEASE_ON_OPEN_LIMIT = 30;
const RELEASE_ON_OPEN_WINDOW_MS = 60 * 60 * 1000;
