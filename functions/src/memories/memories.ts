/**
 * Memories — the couple's own library of their nights (BUILD_PROMPT.md sections 6.1, 14.8).
 *
 * What a memory holds is theirs: a title, a note, the moment they want to keep, a rating, and a
 * voice note or photo if they attached one. What it does *not* hold is a content id. A night one
 * partner planned for the other is readable only by the partner it was for (D-081), and a memory
 * both may read must not be the way around that — so the summary is moods, length and the kind of
 * night, and never what was in it.
 *
 * The phone sends its own words and nothing else it could be wrong about: the date, the length,
 * the moods and the type are read here from the night itself, which is also how a night that was
 * never played, or belongs to another couple, is refused.
 */
import { Timestamp, type DocumentData, type Firestore } from 'firebase-admin/firestore';
import { activeCoupleOf } from '../experience/generate';
import { ServiceError } from '../shared/errors';
import { consumeRateLimit } from '../shared/rateLimit';

export const MAX_TITLE = 60;
export const MAX_NOTE = 2000;
export const MAX_MOMENT = 280;
export const MAX_MEDIA = 4;
export const MIN_RATING = 1;
export const MAX_RATING = 5;

export const SAVE_LIMIT = 60;
export const SAVE_WINDOW_MS = 60 * 60 * 1000;

const DOC_ID = /^[A-Za-z0-9_-]{1,64}$/;
const SAVE_KEYS = new Set(['experienceId', 'title', 'note', 'favoriteMoment', 'rating', 'mediaIds']);

/** A night worth keeping has been played to the end, or stopped part-way — but played. */
const KEEPABLE = new Set(['COMPLETED', 'ABANDONED']);

function invalid(reason: string): never {
  throw new ServiceError('invalid-argument', reason);
}

export interface SaveRequest {
  experienceId: string;
  title: string;
  note: string | null;
  favoriteMoment: string | null;
  rating: number | null;
  mediaIds: string[];
}

export function parseSave(input: unknown): SaveRequest {
  if (typeof input !== 'object' || input === null || Array.isArray(input)) invalid('request');
  const raw = input as Record<string, unknown>;
  for (const key of Object.keys(raw)) if (!SAVE_KEYS.has(key)) invalid(`unknown field ${key}`);

  const experienceId = raw.experienceId;
  if (typeof experienceId !== 'string' || !DOC_ID.test(experienceId)) invalid('experienceId');

  const title = raw.title;
  if (typeof title !== 'string' || title.trim().length === 0 || title.length > MAX_TITLE) invalid('title');

  const note = text(raw.note, MAX_NOTE, 'note');
  const favoriteMoment = text(raw.favoriteMoment, MAX_MOMENT, 'favoriteMoment');

  let rating: number | null = null;
  if (raw.rating !== undefined && raw.rating !== null) {
    if (!Number.isInteger(raw.rating)) invalid('rating');
    rating = raw.rating as number;
    if (rating < MIN_RATING || rating > MAX_RATING) invalid('rating');
  }

  const mediaIds: string[] = [];
  if (raw.mediaIds !== undefined && raw.mediaIds !== null) {
    if (!Array.isArray(raw.mediaIds) || raw.mediaIds.length > MAX_MEDIA) invalid('mediaIds');
    for (const id of raw.mediaIds) {
      if (typeof id !== 'string' || !DOC_ID.test(id)) invalid('mediaIds');
      mediaIds.push(id);
    }
  }

  return { experienceId, title: title.trim(), note, favoriteMoment, rating, mediaIds };
}

function text(value: unknown, max: number, field: string): string | null {
  if (value === undefined || value === null) return null;
  if (typeof value !== 'string' || value.length > max) invalid(field);
  const trimmed = value.trim();
  return trimmed.length === 0 ? null : trimmed;
}

/**
 * Saves a night to the library. One memory per night: saving it again edits the one they have,
 * rather than filling the library with the same evening four times.
 */
export async function saveMemory(
  db: Firestore,
  uid: string,
  input: unknown,
  nowMs: number,
): Promise<{ memoryId: string }> {
  const request = parseSave(input);
  const { coupleId } = await activeCoupleOf(db, uid);
  await consumeRateLimit(db, uid, 'saveMemory', SAVE_LIMIT, SAVE_WINDOW_MS, nowMs);

  const night = await db.doc(`couples/${coupleId}/experiences/${request.experienceId}`).get();
  const experience = night.data();
  if (!night.exists || experience === undefined) throw new ServiceError('not-found', 'experience');
  if (!KEEPABLE.has(experience.status as string)) throw new ServiceError('failed-precondition', 'not-played');

  // Media is referenced by id, and only the couple's own: the bytes stay where the media rules
  // guard them (section 5.8), and a memory cannot be used to point at anything else.
  for (const mediaId of request.mediaIds) {
    const media = await db.doc(`couples/${coupleId}/media/${mediaId}`).get();
    if (!media.exists || media.get('state') !== 'SENT') throw new ServiceError('not-found', 'media');
  }

  // The night's own summary, read here rather than taken from the phone.
  const ref = db.doc(`couples/${coupleId}/memories/${request.experienceId}`);
  const existing = await ref.get();
  await ref.set(
    {
      experienceId: request.experienceId,
      date: experience.createdAt ?? Timestamp.fromMillis(nowMs),
      durationMin: typeof experience.durationMin === 'number' ? experience.durationMin : 0,
      mood: Array.isArray(experience.mood) ? experience.mood : [],
      experienceType: experience.mystery === true ? 'MYSTERY' : 'NIGHT',
      chapterCount: Array.isArray(experience.chapters) ? experience.chapters.length : 0,
      playedThrough: typeof experience.playedThrough === 'number' ? experience.playedThrough : 0,
      title: request.title,
      note: request.note,
      favoriteMoment: request.favoriteMoment,
      rating: request.rating,
      mediaIds: request.mediaIds,
      favorite: existing.get('favorite') === true,
      createdBy: existing.exists ? existing.get('createdBy') : uid,
      createdAt: existing.exists ? existing.get('createdAt') : Timestamp.fromMillis(nowMs),
      updatedAt: Timestamp.fromMillis(nowMs),
    } satisfies DocumentData,
    { merge: true },
  );
  return { memoryId: ref.id };
}

/** Forgetting one. Either of them may: the library is theirs together (D-090). */
export async function forgetMemory(
  db: Firestore,
  uid: string,
  input: unknown,
  _nowMs: number,
): Promise<{ memoryId: string }> {
  if (typeof input !== 'object' || input === null || Array.isArray(input)) invalid('request');
  const raw = input as Record<string, unknown>;
  for (const key of Object.keys(raw)) if (key !== 'memoryId') invalid(`unknown field ${key}`);
  const memoryId = raw.memoryId;
  if (typeof memoryId !== 'string' || !DOC_ID.test(memoryId)) invalid('memoryId');

  const { coupleId } = await activeCoupleOf(db, uid);
  const ref = db.doc(`couples/${coupleId}/memories/${memoryId}`);
  if (!(await ref.get()).exists) throw new ServiceError('not-found', 'memory');
  // The media it pointed at is not deleted with it: it has a life of its own (section 5.8).
  await ref.delete();
  return { memoryId };
}
