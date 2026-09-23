/**
 * "Make it more…" (BUILD_PROMPT.md section 14.6): the night rearranged on the phone, and the
 * couple's record of it brought into line.
 *
 * The phone plans the remix itself, instantly and offline, from the pool the server released for
 * this night (DECISIONS.md D-048) — but the night is also a document both partners read and the
 * session counts its chapters against, so the server has to be told. It is told ids, never
 * content: every one must already have been released for this experience, and must still be
 * permitted now. Nothing new can enter a night this way, and nothing can enter it at all once it
 * has started.
 *
 * Core logic, tested directly against the emulator; the callable in index.ts only authenticates
 * and delegates.
 */
import { Timestamp, type Firestore } from 'firebase-admin/firestore';
import { chapterRange } from '../engine/plan';
import { CHAPTER_KINDS } from '../engine/types';
import { ServiceError } from '../shared/errors';
import { activeCoupleOf, blockedNow, defaultDeps, type GenerateDeps } from './generate';

const DOC_ID = /^[A-Za-z0-9_-]{1,64}$/;
const REMIX_KEYS = new Set(['experienceId', 'chapters']);
const CHAPTER_KEYS = new Set(['contentId', 'kind']);

export interface RemixChapter {
  contentId: string;
  kind: string;
}

export interface RemixRequest {
  experienceId: string;
  chapters: RemixChapter[];
}

export interface RemixResult {
  experienceId: string;
  chapterCount: number;
}

function invalid(reason: string): never {
  throw new ServiceError('invalid-argument', reason);
}

export function parseRemix(input: unknown): RemixRequest {
  if (typeof input !== 'object' || input === null || Array.isArray(input)) invalid('request');
  const raw = input as Record<string, unknown>;
  for (const key of Object.keys(raw)) if (!REMIX_KEYS.has(key)) invalid(`unknown field ${key}`);
  const { experienceId, chapters } = raw;
  if (typeof experienceId !== 'string' || !DOC_ID.test(experienceId)) invalid('experienceId');
  if (!Array.isArray(chapters) || chapters.length === 0) invalid('chapters');
  const parsed = chapters.map((chapter) => {
    if (typeof chapter !== 'object' || chapter === null || Array.isArray(chapter)) invalid('chapters');
    const fields = chapter as Record<string, unknown>;
    for (const key of Object.keys(fields)) if (!CHAPTER_KEYS.has(key)) invalid(`unknown field ${key}`);
    const { contentId, kind } = fields;
    if (typeof contentId !== 'string' || contentId.length === 0) invalid('contentId');
    if (!CHAPTER_KINDS.includes(kind as never)) invalid('kind');
    return { contentId, kind: kind as string };
  });
  if (new Set(parsed.map((chapter) => chapter.contentId)).size !== parsed.length) invalid('chapters');
  return { experienceId, chapters: parsed };
}

/**
 * Replaces the chapters of [experienceId] with [chapters], which must be a rearrangement of what
 * was released for it. Refused once the night has started: a session counts chapters, and they
 * cannot change under it.
 */
export async function remixExperience(
  db: Firestore,
  uid: string,
  input: unknown,
  nowMs: number,
  deps: GenerateDeps = defaultDeps,
): Promise<RemixResult> {
  const request = parseRemix(input);
  const { coupleId } = await activeCoupleOf(db, uid);
  const ref = db.doc(`couples/${coupleId}/experiences/${request.experienceId}`);
  const snapshot = await ref.get();
  const experience = snapshot.data();
  if (!snapshot.exists || experience === undefined) throw new ServiceError('not-found', 'experience');
  if (experience.type === 'GAME') invalid('experienceId');
  if (experience.createdBy !== uid) throw new ServiceError('permission-denied', 'not-yours');
  if (experience.status !== 'READY' || (experience.playedThrough ?? 0) > 0) {
    throw new ServiceError('failed-precondition', 'already-started');
  }

  // Only what this night released, and only as many chapters as a night that length may have.
  const released = new Set<string>([
    ...(experience.chapters ?? []).flatMap((chapter: { contentIds?: string[] }) => chapter.contentIds ?? []),
    ...(experience.alternates ?? []),
  ]);
  const unknown = request.chapters.find((chapter) => !released.has(chapter.contentId));
  if (unknown !== undefined) throw new ServiceError('permission-denied', 'not-released');
  const [lo, hi] = chapterRange(experience.durationMin ?? 0);
  if (request.chapters.length < lo || request.chapters.length > hi) invalid('chapters');

  // The chokepoint again (section 3.2): a limit set since the night was built rules its items out.
  const ids = request.chapters.map((chapter) => chapter.contentId);
  const blocked = await blockedNow(db, coupleId, uid, experience, ids, nowMs, deps);
  if (blocked.length > 0) throw new ServiceError('failed-precondition', 'not-permitted');

  const catalogue = await deps.catalogue(db, nowMs);
  const byId = new Map(catalogue.items.map((item) => [item.id, item]));
  const chapters = request.chapters.map((chapter, index) => ({
    index,
    kind: chapter.kind,
    contentIds: [chapter.contentId],
    // The item's own level, from the catalogue: never a number the phone chose.
    intensity: byId.get(chapter.contentId)?.intensity ?? experience.intensity,
    state: 'LOCKED',
  }));
  await ref.update({ chapters, remixedAt: Timestamp.fromMillis(nowMs) });
  return { experienceId: request.experienceId, chapterCount: chapters.length };
}
