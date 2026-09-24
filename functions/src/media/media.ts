/**
 * Voice notes and photos one partner sends the other (BUILD_PROMPT.md sections 5.8, 13).
 *
 * The object and the document that describes it are never apart. The server reserves the
 * document first and names the path; the phone uploads into exactly that path, which the
 * Storage rules allow only for the author of a PENDING document; the server then confirms the
 * object is there and marks it SENT. So there is no object without a document, and deleting the
 * document — on a burn, on an expiry — takes the object's readability with it, because the rule
 * asks for the document before it allows the read.
 *
 * Deletion is the server's, always: a burn-after-view really deletes, rather than hiding
 * something a client could still fetch (section 5.8).
 *
 * Core logic, tested directly against the emulator; the callables in index.ts only
 * authenticate and delegate.
 */
import { Timestamp, type DocumentData, type Firestore } from 'firebase-admin/firestore';
import type { Bucket } from '@google-cloud/storage';
import type { Storage } from 'firebase-admin/storage';
import { activeCoupleOf } from '../experience/generate';
import { fcmSender, notifyPartner, type Sender } from '../notify/notify';
import { ServiceError } from '../shared/errors';
import { consumeRateLimit } from '../shared/rateLimit';

export const MEDIA_KINDS = ['VOICE', 'PHOTO'] as const;
export type MediaKind = (typeof MEDIA_KINDS)[number];

/** Where each kind lives in Storage (section 6.2). */
const FOLDER: Record<MediaKind, string> = { VOICE: 'voice', PHOTO: 'media' };

/** What a client may ask for, and the most it may send. */
export const MAX_NOTE = 140;
export const SEND_LIMIT = 40;
export const SEND_WINDOW_MS = 60 * 60 * 1000;

/** How long a sent item lasts unless it burns first, and how long an unfinished upload lives. */
export const DEFAULT_TTL_MS = 7 * 24 * 60 * 60 * 1000;
export const MAX_TTL_MS = 30 * 24 * 60 * 60 * 1000;
export const PENDING_TTL_MS = 60 * 60 * 1000;

const RESERVE_KEYS = new Set(['kind', 'burnAfterView', 'ttlMs', 'note']);
const ITEM_ID = /^[A-Za-z0-9_-]{1,64}$/;

function invalid(reason: string): never {
  throw new ServiceError('invalid-argument', reason);
}

/**
 * The bucket the phones upload to. Deployed, that is simply the project's default. In the
 * Emulator Suite the functions runtime still defaults to the older `<project>.appspot.com` name
 * while the app's google-services.json uses `<project>.firebasestorage.app` — so without this,
 * the server would look for every upload in a bucket nothing was ever uploaded to (D-084).
 */
function bucketOf(storage: Storage): Bucket {
  if (process.env.FUNCTIONS_EMULATOR !== 'true') return storage.bucket();
  return storage.bucket(`${process.env.GCLOUD_PROJECT ?? ''}.firebasestorage.app`);
}

export interface ReservedMedia {
  itemId: string;
  /** The one path the phone may upload to. The rules allow no other. */
  path: string;
  expiresAt: number;
}

export interface MediaView {
  itemId: string;
  /** Gone for good: the object and its document are deleted, and nothing can read it again. */
  burned: boolean;
}

interface ReserveRequest {
  kind: MediaKind;
  burnAfterView: boolean;
  ttlMs: number;
  note: string | null;
}

export function parseReserve(input: unknown): ReserveRequest {
  if (typeof input !== 'object' || input === null || Array.isArray(input)) invalid('request');
  const raw = input as Record<string, unknown>;
  for (const key of Object.keys(raw)) if (!RESERVE_KEYS.has(key)) invalid(`unknown field ${key}`);
  const { kind, burnAfterView, ttlMs, note } = raw;
  if (!MEDIA_KINDS.includes(kind as MediaKind)) invalid('kind');
  if (burnAfterView !== undefined && typeof burnAfterView !== 'boolean') invalid('burnAfterView');
  if (ttlMs !== undefined && (!Number.isInteger(ttlMs) || (ttlMs as number) <= 0 || (ttlMs as number) > MAX_TTL_MS)) {
    invalid('ttlMs');
  }
  if (note !== undefined && note !== null && (typeof note !== 'string' || note.length > MAX_NOTE)) invalid('note');
  return {
    kind: kind as MediaKind,
    burnAfterView: burnAfterView === true,
    ttlMs: (ttlMs as number) ?? DEFAULT_TTL_MS,
    note: typeof note === 'string' && note.length > 0 ? note : null,
  };
}

/** A place to upload to, and the document that will describe what lands there. */
export async function reserveMedia(db: Firestore, uid: string, input: unknown, nowMs: number): Promise<ReservedMedia> {
  const request = parseReserve(input);
  const { coupleId } = await activeCoupleOf(db, uid);
  await consumeRateLimit(db, uid, 'sendMedia', SEND_LIMIT, SEND_WINDOW_MS, nowMs);

  const ref = db.collection(`couples/${coupleId}/media`).doc();
  const path = `couples/${coupleId}/${FOLDER[request.kind]}/${ref.id}/${request.kind === 'VOICE' ? 'note' : 'photo'}`;
  const expiresAt = nowMs + request.ttlMs;
  await ref.set({
    ownerUid: uid,
    kind: request.kind,
    state: 'PENDING',
    path,
    note: request.note,
    burnAfterView: request.burnAfterView,
    createdAt: Timestamp.fromMillis(nowMs),
    sentAt: null,
    viewedAt: null,
    expiresAt: Timestamp.fromMillis(expiresAt),
  });
  return { itemId: ref.id, path, expiresAt };
}

/** The object has landed: it becomes the couple's, and the partner can see there is one. */
export async function confirmMedia(
  db: Firestore,
  storage: Storage,
  uid: string,
  input: unknown,
  nowMs: number,
  sender: Sender = fcmSender,
): Promise<{ itemId: string }> {
  const itemId = idOf(input);
  const { coupleId, members } = await activeCoupleOf(db, uid);
  const ref = db.doc(`couples/${coupleId}/media/${itemId}`);
  const snapshot = await ref.get();
  const media = snapshot.data();
  if (!snapshot.exists || media === undefined) throw new ServiceError('not-found', 'media');
  if (media.ownerUid !== uid) throw new ServiceError('permission-denied', 'not-yours');
  if (media.state === 'SENT') return { itemId };

  const [exists] = await bucketOf(storage).file(media.path).exists();
  if (!exists) throw new ServiceError('failed-precondition', 'nothing-uploaded');
  await ref.update({ state: 'SENT', sentAt: Timestamp.fromMillis(nowMs) });
  // Not what it is, not even whether it is a voice note: that they sent something (section 16.2).
  await notifyPartner(db, members, uid, 'THEY_SENT_SOMETHING', itemId, nowMs, sender);
  return { itemId };
}

/**
 * The partner opening it. A burn-after-view item is deleted here — the object first, so a
 * reader can never be left with a path that still works — and only then the document.
 */
export async function viewMedia(
  db: Firestore,
  storage: Storage,
  uid: string,
  input: unknown,
  nowMs: number,
): Promise<MediaView> {
  const itemId = idOf(input);
  const { coupleId } = await activeCoupleOf(db, uid);
  const ref = db.doc(`couples/${coupleId}/media/${itemId}`);
  const snapshot = await ref.get();
  const media = snapshot.data();
  if (!snapshot.exists || media === undefined) throw new ServiceError('not-found', 'media');
  if (media.state !== 'SENT') throw new ServiceError('failed-precondition', 'not-sent');
  // Only the partner it was sent to burns it: the author re-reading their own does not.
  if (media.ownerUid === uid) return { itemId, burned: false };

  if (media.burnAfterView !== true) {
    await ref.update({ viewedAt: Timestamp.fromMillis(nowMs) });
    return { itemId, burned: false };
  }
  await deleteMedia(storage, ref, media);
  return { itemId, burned: true };
}

/**
 * Everything past its time, and every upload that never finished (section 5.8). Swept hourly,
 * and safe to run twice: a document already gone is simply not there to delete.
 */
export async function expireMedia(db: Firestore, storage: Storage, nowMs: number): Promise<{ deleted: number }> {
  const now = Timestamp.fromMillis(nowMs);
  const expired = await db.collectionGroup('media').where('expiresAt', '<=', now).get();
  const stale = await db
    .collectionGroup('media')
    .where('state', '==', 'PENDING')
    .where('createdAt', '<=', Timestamp.fromMillis(nowMs - PENDING_TTL_MS))
    .get();

  let deleted = 0;
  const seen = new Set<string>();
  for (const doc of [...expired.docs, ...stale.docs]) {
    if (seen.has(doc.ref.path)) continue;
    seen.add(doc.ref.path);
    await deleteMedia(storage, doc.ref, doc.data());
    deleted += 1;
  }
  return { deleted };
}

/** The object, then the document: never the other way round (section 5.8). */
async function deleteMedia(
  storage: Storage,
  ref: FirebaseFirestore.DocumentReference,
  media: DocumentData,
): Promise<void> {
  if (typeof media.path === 'string') {
    await bucketOf(storage).file(media.path).delete({ ignoreNotFound: true });
  }
  await ref.delete();
}

function idOf(input: unknown): string {
  if (typeof input !== 'object' || input === null || Array.isArray(input)) invalid('request');
  const raw = input as Record<string, unknown>;
  for (const key of Object.keys(raw)) if (key !== 'itemId') invalid(`unknown field ${key}`);
  const itemId = raw.itemId;
  if (typeof itemId !== 'string' || !ITEM_ID.test(itemId)) invalid('itemId');
  return itemId;
}
