/**
 * generateExperience (BUILD_PROMPT.md sections 8, 10): the server-authoritative night.
 *
 * Everything private stays here. The couple's combined boundaries, both partners' affinity,
 * and the ask-first flags are read, used and never returned: the caller gets a night and a
 * bounded pool of alternates (section 5.5), and only their own ask-first chapters. The seed
 * is the server's own, so a client cannot probe the pool by choosing seeds.
 *
 * Core logic, tested directly against the emulator; the callable in index.ts only
 * authenticates and delegates.
 */
import { randomInt } from 'node:crypto';
import { Timestamp, type DocumentData, type Firestore } from 'firebase-admin/firestore';
import { filtersRef, recomputeCouple } from '../couple/recompute';
import { loadCatalogue, type Catalogue } from '../engine/catalogue';
import { generate } from '../engine/pipeline';
import {
  LEADERSHIPS,
  MATCH_LEVELS,
  MODES,
  MOODS,
  NOVELTIES,
  type CoupleContext,
  type EngineRequest,
  type FiltersInput,
  type MatchLevel,
  type Weights,
} from '../engine/types';
import { engineWeights } from '../engine/weights';
import { ServiceError } from '../shared/errors';
import { consumeRateLimit } from '../shared/rateLimit';

export const GENERATE_LIMIT = 30;
export const GENERATE_WINDOW_MS = 60 * 60 * 1000;
const MAX_AFFINITY_DOCS = 200;

export interface GenerateDeps {
  catalogue(db: Firestore, nowMs: number): Promise<Catalogue>;
  weights(nowMs: number): Promise<Weights>;
  /** An unsigned 32-bit seed. */
  seed(): number;
}

export const defaultDeps: GenerateDeps = {
  catalogue: loadCatalogue,
  weights: engineWeights,
  seed: () => randomInt(0, 2 ** 32),
};

export interface GeneratedExperience {
  experienceId: string;
  chapters: { index: number; kind: string; contentId: string; intensity: number }[];
  alternates: string[];
  effectiveIntensity: number;
  seed: number;
  engineVersion: number;
  contentVersion: number;
  /** Chapter indexes the caller is asked about before they appear. Only the caller's own. */
  askFirst: number[];
}

const REQUEST_KEYS = new Set(['mode', 'durationMin', 'moods', 'intensity', 'leadership', 'novelty', 'media']);

function invalid(reason: string): never {
  throw new ServiceError('invalid-argument', reason);
}

/**
 * The request, checked field by field. Unknown fields are refused rather than ignored: a
 * payload carrying a seed, a uid or a coupleId is a client trying to decide something only
 * the server decides.
 */
export function parseRequest(input: unknown, seed: number): EngineRequest {
  if (typeof input !== 'object' || input === null || Array.isArray(input)) invalid('request');
  const raw = input as Record<string, unknown>;
  for (const key of Object.keys(raw)) if (!REQUEST_KEYS.has(key)) invalid(`unknown field ${key}`);

  const { mode, durationMin, moods, intensity, leadership, novelty, media } = raw;
  if (!MODES.includes(mode as never)) invalid('mode');
  if (!Number.isInteger(durationMin) || (durationMin as number) < 10 || (durationMin as number) > 240) invalid('durationMin');
  if (!Array.isArray(moods) || moods.some((mood) => !MOODS.includes(mood)) || new Set(moods).size !== moods.length) invalid('moods');
  if (!Number.isInteger(intensity) || (intensity as number) < 1 || (intensity as number) > 5) invalid('intensity');
  if (!LEADERSHIPS.includes(leadership as never)) invalid('leadership');
  if (!NOVELTIES.includes(novelty as never)) invalid('novelty');

  let capture = { VOICE: true, PHOTO: true };
  if (media !== undefined) {
    if (typeof media !== 'object' || media === null) invalid('media');
    const { VOICE, PHOTO, ...rest } = media as Record<string, unknown>;
    if (Object.keys(rest).length > 0 || (VOICE !== undefined && typeof VOICE !== 'boolean') || (PHOTO !== undefined && typeof PHOTO !== 'boolean')) {
      invalid('media');
    }
    capture = { VOICE: VOICE !== false, PHOTO: PHOTO !== false };
  }
  return {
    mode: mode as EngineRequest['mode'],
    durationMin: durationMin as number,
    moods: moods as EngineRequest['moods'],
    intensity: intensity as number,
    leadership: leadership as EngineRequest['leadership'],
    novelty: novelty as EngineRequest['novelty'],
    seed,
    media: capture,
  };
}

/** The couple's filters, read fail-closed: anything malformed is the strictest it could be. */
function filtersFrom(data: DocumentData): FiltersInput {
  const strings = (value: unknown) => (Array.isArray(value) ? value.filter((entry): entry is string => typeof entry === 'string') : []);
  const askFirst: Record<string, string[]> = {};
  const rawAskFirst = data.askFirstThemes;
  if (typeof rawAskFirst === 'object' && rawAskFirst !== null) {
    for (const [theme, uids] of Object.entries(rawAskFirst)) askFirst[theme] = strings(uids);
  }
  const maxIntensity = Number.isInteger(data.maxIntensity) && data.maxIntensity >= 1 && data.maxIntensity <= 5 ? data.maxIntensity : 1;
  return {
    maxIntensity,
    excludedThemes: strings(data.excludedThemes),
    askFirstThemes: askFirst,
    curiousThemes: strings(data.curiousThemes),
    excludedItems: strings(data.excludedItems),
  };
}

export async function generateExperience(
  db: Firestore,
  uid: string,
  input: unknown,
  nowMs: number,
  deps: GenerateDeps = defaultDeps,
): Promise<GeneratedExperience> {
  const seed = deps.seed() >>> 0;
  const request = parseRequest(input, seed);

  // The couple comes from the server's records, never from the payload (section 8).
  const coupleId = (await db.doc(`users/${uid}`).get()).get('coupleId');
  if (typeof coupleId !== 'string' || coupleId.length === 0) throw new ServiceError('failed-precondition', 'not-paired');
  const couple = await db.doc(`couples/${coupleId}`).get();
  const members: unknown = couple.get('memberUids');
  if (couple.get('status') !== 'ACTIVE' || !Array.isArray(members) || !members.includes(uid)) {
    throw new ServiceError('failed-precondition', 'not-paired');
  }
  await consumeRateLimit(db, uid, 'generateExperience', GENERATE_LIMIT, GENERATE_WINDOW_MS, nowMs);

  let filters = await filtersRef(db, coupleId).get();
  if (!filters.exists) {
    // A couple whose filters were never computed gets them now, rather than a night built
    // with none.
    await recomputeCouple(db, coupleId, nowMs);
    filters = await filtersRef(db, coupleId).get();
    if (!filters.exists) throw new ServiceError('failed-precondition', 'not-paired');
  }

  const couplePath = `couples/${coupleId}`;
  const [mutualDocs, history, weights, catalogue] = await Promise.all([
    db.collection(`${couplePath}/mutualPreferences`).get(),
    db.doc(`${couplePath}/engineState/history`).get(),
    deps.weights(nowMs),
    deps.catalogue(db, nowMs),
  ]);

  const mutual: Record<string, MatchLevel> = {};
  for (const doc of mutualDocs.docs) {
    const level = doc.get('matchLevel');
    if (MATCH_LEVELS.includes(level)) mutual[doc.id] = level;
  }

  const lastPlayed: Record<string, number> = {};
  const rawHistory = history.get('lastPlayed');
  if (typeof rawHistory === 'object' && rawHistory !== null) {
    for (const [itemId, at] of Object.entries(rawHistory)) if (typeof at === 'number') lastPlayed[itemId] = at;
  }

  // Both partners' affinity, averaged per dimension. Read here, used here, never returned.
  const sums = new Map<string, { total: number; count: number }>();
  for (const member of members as string[]) {
    const docs = await db.collection(`users/${member}/affinity`).limit(MAX_AFFINITY_DOCS).get();
    for (const doc of docs.docs) {
      const score = doc.get('score');
      if (typeof score !== 'number' || !Number.isFinite(score)) continue;
      const entry = sums.get(doc.id) ?? { total: 0, count: 0 };
      sums.set(doc.id, { total: entry.total + score, count: entry.count + 1 });
    }
  }
  const affinity = Object.fromEntries([...sums].map(([dimension, { total, count }]) => [dimension, total / count]));

  const context: CoupleContext = { filters: filtersFrom(filters.data()!), mutual, lastPlayed, nowMs, affinity, weights };
  const result = generate(catalogue.items, request, context);
  if (typeof result === 'string') throw new ServiceError('failed-precondition', 'not-enough-content');

  const chapters = result.chapters.map((chapter) => ({
    index: chapter.index,
    kind: chapter.kind,
    contentId: chapter.itemId,
    intensity: chapter.intensity,
  }));
  const createdAt = Timestamp.fromMillis(nowMs);
  const ref = db.collection(`${couplePath}/experiences`).doc();
  const batch = db.batch();
  batch.set(ref, {
    mode: request.mode,
    durationMin: request.durationMin,
    mood: request.moods,
    intensity: request.intensity,
    effectiveIntensity: result.effectiveIntensity,
    leadership: request.leadership,
    novelty: request.novelty,
    chapters: chapters.map(({ index, kind, contentId, intensity }) => ({ index, kind, contentIds: [contentId], intensity, state: 'LOCKED' })),
    alternates: result.alternates,
    createdBy: uid,
    plannedBy: null,
    scheduledFor: null,
    status: 'READY',
    seed: result.seed,
    engineVersion: result.engineVersion,
    contentVersion: catalogue.contentVersion,
    createdAt,
  });
  // Each partner's ask-first chapters go where only they can read them (section 3.2).
  for (const [askUid, indexes] of Object.entries(result.askFirst)) {
    batch.set(ref.collection('askFirst').doc(askUid), { chapters: indexes, createdAt });
  }
  await batch.commit();

  return {
    experienceId: ref.id,
    chapters,
    alternates: result.alternates,
    effectiveIntensity: result.effectiveIntensity,
    seed: result.seed,
    engineVersion: result.engineVersion,
    contentVersion: catalogue.contentVersion,
    askFirst: result.askFirst[uid] ?? [],
  };
}
