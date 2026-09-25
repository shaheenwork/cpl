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
import { getAuth } from 'firebase-admin/auth';
import { getStorage } from 'firebase-admin/storage';
import { onDocumentCreated, onDocumentWritten } from 'firebase-functions/v2/firestore';
import { HttpsError, onCall, type CallableRequest } from 'firebase-functions/v2/https';
import { onSchedule } from 'firebase-functions/v2/scheduler';
import { coupleOf, recomputeCouple } from './couple/recompute';
import * as reveals from './discovery/reveals';
import * as experience from './experience/generate';
import * as games from './experience/game';
import * as plan from './experience/plan';
import * as remix from './experience/remix';
import * as surprise from './experience/surprise';
import * as arcs from './surprises/arcs';
import * as surprises from './surprises/surprises';
import * as feedback from './feedback/affinity';
import * as accountDelete from './account/delete';
import * as accountExport from './account/export';
import * as report from './account/report';
import * as billing from './billing/billing';
import * as media from './media/media';
import * as memories from './memories/memories';
import { revealTiming } from './discovery/timing';
import * as pairing from './pairing/pairing';
import * as session from './session/session';
import { expireStale } from './session/expiry';
import { applySubmission } from './session/submissions';
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

// ---------------------------------------------------------------------------
// The experience engine (section 10)
// ---------------------------------------------------------------------------

/**
 * A night for the caller's couple, built server-side behind the chokepoint (sections 3.2,
 * 10.3). Returns the chapters, a bounded pool of alternates (section 5.5) and the caller's own
 * ask-first items; nothing about either partner's boundaries. Rate-limited in the core.
 */
export const generateExperience = onCall(options, (request) =>
  guarded(() => experience.generateExperience(getFirestore(), callerUid(request), request.data, Date.now())),
);

/**
 * The night the couple did not configure (sections 2.4, 14.2): the server chooses its shape
 * from what only it can see, and a Mystery Night keeps that shape to itself.
 */
export const surpriseNight = onCall(options, (request) =>
  guarded(() => surprise.surpriseExperience(getFirestore(), callerUid(request), request.data, Date.now())),
);

/**
 * "Make it more…" (section 14.6): the night rearranged on the phone from what was released for
 * it, written back so both partners' record and the session agree with what is played.
 */
export const remixNight = onCall(options, (request) =>
  guarded(() => remix.remixExperience(getFirestore(), callerUid(request), request.data, Date.now())),
);

/**
 * A night one partner plans for the other (sections 2.5 #2, 13). Built for the partner who will
 * play it, and told back to the planner as a shape and nothing else.
 */
export const planNight = onCall(options, (request) =>
  guarded(() => plan.planNight(getFirestore(), callerUid(request), request.data, Date.now())),
);

/** The partner opening what was planned for them: the planner learns it landed, never what it is. */
export const openPlan = onCall(options, (request) =>
  guarded(() => plan.openPlan(getFirestore(), callerUid(request), request.data, Date.now())),
);

/** A game's rounds, chosen behind both partners' boundaries like a night (section 11). */
export const startGame = onCall(options, (request) =>
  guarded(() => games.startGame(getFirestore(), callerUid(request), request.data, Date.now())),
);

// ---------------------------------------------------------------------------
// Playing a night or a game (sections 3.1, 12): every transition checked, inside a transaction
// ---------------------------------------------------------------------------

export const createSession = onCall(options, (request) =>
  guarded(() => session.createSession(getFirestore(), callerUid(request), request.data, Date.now())),
);

/** "I'm in": the caller's own consent to a BOLD or WILD night (section 3.1). */
export const markReady = onCall(options, (request) =>
  guarded(() => session.markReady(getFirestore(), callerUid(request), request.data, Date.now())),
);

export const advanceSession = onCall(options, (request) =>
  guarded(() => session.advanceSession(getFirestore(), callerUid(request), request.data, Date.now())),
);

/** "I've seen the reveal": each partner's own, from their own phone (section 12.1). */
export const markSeen = onCall(options, (request) =>
  guarded(() => session.markSeen(getFirestore(), callerUid(request), request.data, Date.now())),
);

/** PAUSE, RESUME, STOP and DECLINE, from either partner's phone (section 3.1). */
export const endSession = onCall(options, (request) =>
  guarded(() => session.endSession(getFirestore(), callerUid(request), request.data, Date.now())),
);

/**
 * An answer written by one phone completes the chapter's reveal for both (section 12.2): the
 * only thing that ever reads a submission, and it returns one reveal rather than either answer.
 */
export const onSubmissionCreated = onDocumentCreated(
  { ...triggerOptions, document: 'couples/{coupleId}/sessions/{sessionId}/submissions/{submissionId}' },
  async (event) => {
    await applySubmission(
      getFirestore(),
      event.params.coupleId,
      event.params.sessionId,
      event.params.submissionId,
      Date.now(),
    );
  },
);

// ---------------------------------------------------------------------------
// Things left for later (sections 5.7, 13): a scheduled surprise, an "Open when…", a capsule.
// The payload is guarded until the server delivers it; a client never delivers anything
// ---------------------------------------------------------------------------

/** Leaves something for the partner to find later. */
export const createSurprise = onCall(options, (request) =>
  guarded(() => surprises.createSurprise(getFirestore(), callerUid(request), request.data, Date.now())),
);

/** "Open when you can't sleep": the partner pulls a trigger, and one of them is delivered. */
export const openWhen = onCall(options, (request) =>
  guarded(() => surprises.openWhen(getFirestore(), callerUid(request), request.data, Date.now())),
);

/** The partner opening what was delivered. */
export const openSurprise = onCall(options, (request) =>
  guarded(() => surprises.openSurprise(getFirestore(), callerUid(request), request.data, Date.now())),
);

/** A few days of this (section 13): one, three, seven or fourteen, a night each. */
export const startArc = onCall(options, (request) =>
  guarded(() => arcs.startArc(getFirestore(), callerUid(request), request.data, Date.now())),
);

/** Today's day of an arc: built when it is opened, and only once the day before was played. */
export const openArcDay = onCall(options, (request) =>
  guarded(() => arcs.openArcDay(getFirestore(), callerUid(request), request.data, Date.now())),
);

/** Ending an arc, whether it ran its course or not. */
export const closeArc = onCall(options, (request) =>
  guarded(() => arcs.closeArc(getFirestore(), callerUid(request), request.data, Date.now())),
);

// ---------------------------------------------------------------------------
// The account itself (sections 3.5, 8, 14.9): reporting, an export, and deletion
// ---------------------------------------------------------------------------

/** Reporting something the app showed, or something a partner sent (section 3.5). */
export const reportContent = onCall(options, (request) =>
  guarded(() => report.reportContent(getFirestore(), callerUid(request), request.data, Date.now())),
);

/** Everything this app holds about the person asking, as one file only they can read. */
export const requestDataExport = onCall(options, (request) =>
  guarded(() => accountExport.requestDataExport(getFirestore(), getStorage(), callerUid(request), Date.now())),
);

/**
 * Deleting an account, and the couple with it (D-093). Auth, Firestore, Storage and everything
 * scheduled — the whole cascade, in one call the client cannot half-finish.
 */
export const deleteAccount = onCall(options, (request) =>
  guarded(() => accountDelete.deleteAccount(
    getFirestore(),
    getAuth(),
    getStorage(),
    callerUid(request),
    request.data,
    Date.now(),
  )),
);

// ---------------------------------------------------------------------------
// Subscription (section 19): the client hands over a token, the server decides what it is worth
// ---------------------------------------------------------------------------

/** A purchase, as the phone saw it. Verified with Google before anything is written. */
export const verifyPurchase = onCall(options, (request) =>
  guarded(() => billing.verifyPurchase(getFirestore(), callerUid(request), request.data, Date.now())),
);

/** What this account is entitled to, as the server holds it: for a restore, or a reinstall. */
export const currentEntitlement = onCall(options, (request) =>
  guarded(() => billing.currentEntitlement(getFirestore(), callerUid(request), Date.now())),
);

/** A term that has run out, written back — so a missed notification cannot leave anyone premium. */
export const expireEntitlements = onSchedule(
  { schedule: 'every 6 hours', retryCount: 3, maxInstances: 1 },
  async () => {
    await billing.expireEntitlements(getFirestore(), Date.now());
  },
);

// ---------------------------------------------------------------------------
// Memories (sections 6.1, 14.8): the couple's own library of the nights they played
// ---------------------------------------------------------------------------

/** Saves a night they played, in their own words. Saving it again edits the one they have. */
export const saveMemory = onCall(options, (request) =>
  guarded(() => memories.saveMemory(getFirestore(), callerUid(request), request.data, Date.now())),
);

/** Forgetting one. Either of them may; what it pointed at is not deleted with it. */
export const forgetMemory = onCall(options, (request) =>
  guarded(() => memories.forgetMemory(getFirestore(), callerUid(request), request.data, Date.now())),
);

/** Everything whose moment has come, delivered on the clock (section 13). */
export const deliverSurprises = onSchedule(
  { schedule: 'every 5 minutes', retryCount: 3, maxInstances: 1 },
  async () => {
    await surprises.deliverDue(getFirestore(), Date.now());
  },
);

// ---------------------------------------------------------------------------
// Voice notes and photos (sections 5.8, 13): the server names every path, confirms every
// object, and is the only thing that ever deletes one
// ---------------------------------------------------------------------------

/** A place to upload a voice note or a photo to, and the record that will describe it. */
export const reserveMedia = onCall(options, (request) =>
  guarded(() => media.reserveMedia(getFirestore(), callerUid(request), request.data, Date.now())),
);

/** The upload landed: it becomes the couple's, and their partner can see there is something. */
export const confirmMedia = onCall(options, (request) =>
  guarded(() => media.confirmMedia(getFirestore(), getStorage(), callerUid(request), request.data, Date.now())),
);

/** Opening one. A burn-after-view item is deleted here — the object first, then its record. */
export const viewMedia = onCall(options, (request) =>
  guarded(() => media.viewMedia(getFirestore(), getStorage(), callerUid(request), request.data, Date.now())),
);

/** Media past its time, and uploads that never finished, are deleted server-side (section 5.8). */
export const expireMedia = onSchedule(
  { schedule: 'every 60 minutes', retryCount: 3, maxInstances: 1 },
  async () => {
    await media.expireMedia(getFirestore(), getStorage(), Date.now());
  },
);

/** Sessions neither phone has been in for ten minutes end themselves (section 12.3). */
export const expireStaleSessions = onSchedule(
  { schedule: 'every 5 minutes', retryCount: 3, maxInstances: 1 },
  async () => {
    await expireStale(getFirestore(), Date.now());
  },
);

/** A partner's reaction to a chapter moves their own affinity (onFeedbackWrite, section 8). */
export const onFeedbackCreated = onDocumentCreated(
  { ...triggerOptions, document: 'couples/{coupleId}/feedback/{feedbackId}' },
  async (event) => {
    await feedback.applyFeedback(getFirestore(), event.params.coupleId, event.params.feedbackId, Date.now());
  },
);
