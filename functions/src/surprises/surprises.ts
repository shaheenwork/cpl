/**
 * Things one partner leaves for the other to find later (BUILD_PROMPT.md sections 5.7, 6.1, 13):
 * a scheduled surprise, an "Open when…", and a future capsule. One mechanism, three shapes.
 *
 * What makes them work is that the payload is not readable until it is meant to be. The surprise
 * document says there is something — both partners may see that, and that is the whole point of
 * the hook — while the payload lives beside it under a rule that reads it out only to its author,
 * or to anyone once the server has written `deliveredAt` (section 5.7, section 7.3 row S). The
 * client never flips that field; a scheduled function does, when the time comes, and a callable
 * does when the partner pulls the trigger on an "Open when…".
 *
 * Core logic, tested directly against the emulator; the callables in index.ts only authenticate
 * and delegate.
 */
import { FieldValue, Timestamp, type DocumentData, type Firestore } from 'firebase-admin/firestore';
import { guardSurprise } from '../billing/limits';
import { activeCoupleOf } from '../experience/generate';
import { fcmSender, notifyUser, type Sender } from '../notify/notify';
import { ServiceError } from '../shared/errors';
import { consumeRateLimit } from '../shared/rateLimit';

/** How a surprise is opened: by the clock, by a feeling, or by a date far off. */
export const SURPRISE_TYPES = ['SCHEDULED', 'OPEN_WHEN', 'CAPSULE'] as const;
export type SurpriseType = (typeof SURPRISE_TYPES)[number];

export const SURPRISE_STATES = ['SCHEDULED', 'DELIVERED', 'OPENED', 'EXPIRED'] as const;
export type SurpriseState = (typeof SURPRISE_STATES)[number];

/** The moments an "Open when…" can be kept for (section 13). */
export const OPEN_WHEN_TRIGGERS = [
  'MISS_ME',
  'NEED_A_LAUGH',
  'CANT_SLEEP',
  'WANT_A_SURPRISE',
  'THINKING_OF_ME',
  'NEED_ATTENTION',
  'REMEMBER_US',
] as const;
export type OpenWhenTrigger = (typeof OPEN_WHEN_TRIGGERS)[number];

export const MAX_TITLE = 60;
export const MAX_TEXT = 2000;
export const CREATE_LIMIT = 30;
export const CREATE_WINDOW_MS = 60 * 60 * 1000;

/** The furthest ahead anything may be set: a year, so nothing is scheduled into nowhere. */
export const MAX_AHEAD_MS = 365 * 24 * 60 * 60 * 1000;

const CREATE_KEYS = new Set(['type', 'title', 'text', 'mediaId', 'scheduledFor', 'trigger', 'timezone']);
const DOC_ID = /^[A-Za-z0-9_-]{1,64}$/;
const TIMEZONE = /^[A-Za-z_]+\/[A-Za-z_+-]+$|^UTC$/;

function invalid(reason: string): never {
  throw new ServiceError('invalid-argument', reason);
}

export interface CreatedSurprise {
  surpriseId: string;
  state: SurpriseState;
}

interface CreateRequest {
  type: SurpriseType;
  title: string;
  text: string | null;
  mediaId: string | null;
  scheduledFor: number | null;
  trigger: OpenWhenTrigger | null;
  timezone: string | null;
}

export function parseCreate(input: unknown, nowMs: number): CreateRequest {
  if (typeof input !== 'object' || input === null || Array.isArray(input)) invalid('request');
  const raw = input as Record<string, unknown>;
  for (const key of Object.keys(raw)) if (!CREATE_KEYS.has(key)) invalid(`unknown field ${key}`);
  const { type, title, text, mediaId, scheduledFor, trigger, timezone } = raw;
  if (!SURPRISE_TYPES.includes(type as SurpriseType)) invalid('type');
  if (typeof title !== 'string' || title.length === 0 || title.length > MAX_TITLE) invalid('title');
  if (text !== undefined && text !== null && (typeof text !== 'string' || text.length > MAX_TEXT)) invalid('text');
  if (mediaId !== undefined && mediaId !== null && (typeof mediaId !== 'string' || !DOC_ID.test(mediaId))) {
    invalid('mediaId');
  }
  if ((text === undefined || text === null) && (mediaId === undefined || mediaId === null)) invalid('text');
  if (timezone !== undefined && timezone !== null && (typeof timezone !== 'string' || !TIMEZONE.test(timezone))) {
    invalid('timezone');
  }

  const wantsTime = type === 'SCHEDULED' || type === 'CAPSULE';
  if (wantsTime) {
    if (!Number.isInteger(scheduledFor)) invalid('scheduledFor');
    const at = scheduledFor as number;
    if (at <= nowMs || at > nowMs + MAX_AHEAD_MS) invalid('scheduledFor');
  } else if (scheduledFor !== undefined && scheduledFor !== null) {
    invalid('scheduledFor');
  }
  if (type === 'OPEN_WHEN') {
    if (!OPEN_WHEN_TRIGGERS.includes(trigger as OpenWhenTrigger)) invalid('trigger');
  } else if (trigger !== undefined && trigger !== null) {
    invalid('trigger');
  }

  return {
    type: type as SurpriseType,
    title,
    text: typeof text === 'string' && text.length > 0 ? text : null,
    mediaId: typeof mediaId === 'string' ? mediaId : null,
    scheduledFor: wantsTime ? (scheduledFor as number) : null,
    trigger: type === 'OPEN_WHEN' ? (trigger as OpenWhenTrigger) : null,
    timezone: typeof timezone === 'string' ? timezone : null,
  };
}

/**
 * Leaves something for the partner. Both of them may see that it exists — that is the point —
 * and only its author may read what is in it, until the server delivers it.
 */
export async function createSurprise(
  db: Firestore,
  uid: string,
  input: unknown,
  nowMs: number,
  sender: Sender = fcmSender,
): Promise<CreatedSurprise> {
  const request = parseCreate(input, nowMs);
  const { coupleId, members } = await activeCoupleOf(db, uid);
  const partner = members.find((member) => member !== uid);
  if (partner === undefined) throw new ServiceError('failed-precondition', 'not-paired');
  // How many may be waiting at once is a free-tier limit; opening them never is (section 19).
  await guardSurprise(db, coupleId, members, nowMs);
  await consumeRateLimit(db, uid, 'createSurprise', CREATE_LIMIT, CREATE_WINDOW_MS, nowMs);

  // Media is only ever referenced by id: the bytes stay where the media rules guard them (5.8).
  if (request.mediaId !== null) {
    const media = await db.doc(`couples/${coupleId}/media/${request.mediaId}`).get();
    if (!media.exists || media.get('ownerUid') !== uid) throw new ServiceError('not-found', 'media');
  }

  const ref = db.collection(`couples/${coupleId}/surprises`).doc();
  const batch = db.batch();
  batch.set(ref, {
    type: request.type,
    title: request.title,
    createdBy: uid,
    forUid: partner,
    state: 'SCHEDULED' satisfies SurpriseState,
    scheduledFor: request.scheduledFor === null ? null : Timestamp.fromMillis(request.scheduledFor),
    trigger: request.trigger,
    timezone: request.timezone,
    hasMedia: request.mediaId !== null,
    createdAt: Timestamp.fromMillis(nowMs),
    deliveredAt: null,
    openedAt: null,
  });
  // The payload, under the rule that keeps it to its author until the server delivers it (5.7).
  batch.set(ref.collection('payload').doc(PAYLOAD_DOC), {
    ownerUid: uid,
    text: request.text,
    mediaId: request.mediaId,
    deliveredAt: null,
  });
  await batch.commit();
  // They are told that something exists, with its id and nothing else (section 16.2).
  await notifyUser(db, partner, 'SURPRISE_LEFT', ref.id, nowMs, sender);
  return { surpriseId: ref.id, state: 'SCHEDULED' };
}

/**
 * "Open when you can't sleep": the partner pulls a trigger, and that one is delivered. Only the
 * partner it was left for can, and only one at a time — the first one waiting for that moment.
 */
export async function openWhen(db: Firestore, uid: string, input: unknown, nowMs: number): Promise<CreatedSurprise> {
  if (typeof input !== 'object' || input === null || Array.isArray(input)) invalid('request');
  const raw = input as Record<string, unknown>;
  for (const key of Object.keys(raw)) if (key !== 'trigger') invalid(`unknown field ${key}`);
  if (!OPEN_WHEN_TRIGGERS.includes(raw.trigger as OpenWhenTrigger)) invalid('trigger');
  const { coupleId } = await activeCoupleOf(db, uid);

  const waiting = await db
    .collection(`couples/${coupleId}/surprises`)
    .where('forUid', '==', uid)
    .where('type', '==', 'OPEN_WHEN')
    .where('trigger', '==', raw.trigger)
    .where('state', '==', 'SCHEDULED')
    .orderBy('createdAt')
    .limit(1)
    .get();
  const surprise = waiting.docs[0];
  if (surprise === undefined) throw new ServiceError('not-found', 'nothing-waiting');

  await deliver(db, surprise.ref, nowMs);
  return { surpriseId: surprise.id, state: 'DELIVERED' };
}

/** The partner opening what was delivered: the author learns it landed, never what happened next. */
export async function openSurprise(
  db: Firestore,
  uid: string,
  input: unknown,
  nowMs: number,
): Promise<CreatedSurprise> {
  if (typeof input !== 'object' || input === null || Array.isArray(input)) invalid('request');
  const raw = input as Record<string, unknown>;
  for (const key of Object.keys(raw)) if (key !== 'surpriseId') invalid(`unknown field ${key}`);
  const surpriseId = raw.surpriseId;
  if (typeof surpriseId !== 'string' || !DOC_ID.test(surpriseId)) invalid('surpriseId');
  const { coupleId } = await activeCoupleOf(db, uid);

  const ref = db.doc(`couples/${coupleId}/surprises/${surpriseId}`);
  const snapshot = await ref.get();
  const surprise = snapshot.data();
  if (!snapshot.exists || surprise === undefined) throw new ServiceError('not-found', 'surprise');
  if (surprise.forUid !== uid) throw new ServiceError('permission-denied', 'not-yours');
  if (surprise.state === 'SCHEDULED') throw new ServiceError('failed-precondition', 'not-yet');
  if (surprise.state === 'OPENED') return { surpriseId, state: 'OPENED' };

  await ref.update({ state: 'OPENED' satisfies SurpriseState, openedAt: Timestamp.fromMillis(nowMs) });
  return { surpriseId, state: 'OPENED' };
}

/**
 * Everything whose time has come (section 13). Swept every five minutes, and safe to run twice:
 * a surprise already delivered is not delivered again.
 */
export async function deliverDue(
  db: Firestore,
  nowMs: number,
  sender: Sender = fcmSender,
): Promise<{ delivered: number }> {
  const due = await db
    .collectionGroup('surprises')
    .where('state', '==', 'SCHEDULED')
    .where('scheduledFor', '<=', Timestamp.fromMillis(nowMs))
    .get();

  let delivered = 0;
  for (const surprise of due.docs) {
    await deliver(db, surprise.ref, nowMs);
    const forUid = surprise.get('forUid');
    if (typeof forUid === 'string') {
      await notifyUser(db, forUid, 'SURPRISE_UNLOCKED', surprise.id, nowMs, sender);
    }
    delivered += 1;
  }
  return { delivered };
}

/** Delivery, in one write each: the surprise, then the payload it guards. */
async function deliver(db: Firestore, ref: FirebaseFirestore.DocumentReference, nowMs: number): Promise<void> {
  const at = Timestamp.fromMillis(nowMs);
  const batch = db.batch();
  batch.update(ref, { state: 'DELIVERED' satisfies SurpriseState, deliveredAt: at });
  batch.set(ref.collection('payload').doc(PAYLOAD_DOC), { deliveredAt: at }, { merge: true });
  await batch.commit();
}

/** What a phone may read of a surprise before it is delivered: everything but the payload. */
export function summaryOf(surprise: DocumentData): Record<string, unknown> {
  return {
    type: surprise.type,
    title: surprise.title,
    state: surprise.state,
    createdBy: surprise.createdBy,
    forUid: surprise.forUid,
    scheduledFor: surprise.scheduledFor ?? null,
    trigger: surprise.trigger ?? null,
    deliveredAt: surprise.deliveredAt ?? null,
    openedAt: surprise.openedAt ?? null,
    hasMedia: surprise.hasMedia === true,
  };
}

/** Removes a surprise nobody opened, long after its moment passed. */
export async function expireSurprises(db: Firestore, nowMs: number, olderThanMs: number): Promise<{ expired: number }> {
  const cutoff = Timestamp.fromMillis(nowMs - olderThanMs);
  const stale = await db
    .collectionGroup('surprises')
    .where('state', '==', 'DELIVERED')
    .where('deliveredAt', '<=', cutoff)
    .get();

  let expired = 0;
  for (const surprise of stale.docs) {
    await surprise.ref.update({ state: 'EXPIRED' satisfies SurpriseState, expiredAt: FieldValue.serverTimestamp() });
    expired += 1;
  }
  return { expired };
}

const PAYLOAD_DOC = 'content';
