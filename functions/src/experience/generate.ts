/**
 * generateExperience (BUILD_PROMPT.md sections 8, 10): the server-authoritative night.
 *
 * Everything private stays here. The couple's combined boundaries, both partners' affinity,
 * and the ask-first flags are read, used and never returned: the caller gets a night and a
 * bounded pool of alternates (section 5.5), and only their own ask-first items. The seed is
 * the server's own, so a client cannot probe the pool by choosing seeds.
 *
 * A night is played on the phone of the partner who built it, in front of both (DECISIONS.md
 * D-059). So a partner's ASK_FIRST themes are left out of it — they cannot be asked on that
 * phone without telling the other that a limit exists — and only the host's own are flagged,
 * to be asked on the host's phone (D-060).
 *
 * Core logic, tested directly against the emulator; the callable in index.ts only
 * authenticates and delegates.
 */
import { randomInt } from 'node:crypto';
import { Timestamp, type DocumentData, type DocumentSnapshot, type Firestore } from 'firebase-admin/firestore';
import { filtersRef, recomputeCouple } from '../couple/recompute';
import { loadCatalogue, type Catalogue } from '../engine/catalogue';
import { askFirstUids, permits } from '../engine/chokepoint';
import { MAX_CONTENT_LEVEL } from '../engine/filters';
import { generate } from '../engine/pipeline';
import {
  DEFAULT_WEIGHTS,
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
import { guardNewNight } from '../billing/limits';
import { ServiceError } from '../shared/errors';
import { consumeRateLimit } from '../shared/rateLimit';

export const GENERATE_LIMIT = 30;
export const GENERATE_WINDOW_MS = 60 * 60 * 1000;
const MAX_AFFINITY_DOCS = 200;
/** The taxonomy's id rule (content/taxonomy.json), for the focus a night is built around. */
const TAXONOMY_ID = /^[a-z0-9_]{1,64}$/;

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
  /** Set for a Mystery Night: the phone shows nothing of its shape (section 14.2, D-077). */
  mystery?: boolean;
  /**
   * What the night was built from. The caller usually sent these; a surprise did not, and
   * needs them back to remix what it was given (section 14.6).
   */
  settings: {
    mode: string;
    durationMin: number;
    moods: string[];
    intensity: number;
    leadership: string;
    novelty: string;
  };
  chapters: { index: number; kind: string; contentId: string; intensity: number }[];
  alternates: string[];
  effectiveIntensity: number;
  seed: number;
  engineVersion: number;
  contentVersion: number;
  /** Released items (chapters or alternates) the caller asked to be asked about first. */
  askFirstItems: string[];
  /** Whether a night asked to be built around a match holds an item about it. */
  focusIncluded: boolean;
}

const REQUEST_KEYS = new Set(['mode', 'durationMin', 'moods', 'intensity', 'leadership', 'novelty', 'media', 'focus']);

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

  const { mode, durationMin, moods, intensity, leadership, novelty, media, focus } = raw;
  if (!MODES.includes(mode as never)) invalid('mode');
  if (!Number.isInteger(durationMin) || (durationMin as number) < 10 || (durationMin as number) > 240) invalid('durationMin');
  if (!Array.isArray(moods) || moods.some((mood) => !MOODS.includes(mood)) || new Set(moods).size !== moods.length) invalid('moods');
  if (!Number.isInteger(intensity) || (intensity as number) < 1 || (intensity as number) > 5) invalid('intensity');
  if (!LEADERSHIPS.includes(leadership as never)) invalid('leadership');
  if (!NOVELTIES.includes(novelty as never)) invalid('novelty');
  if (focus !== undefined && focus !== null && (typeof focus !== 'string' || !TAXONOMY_ID.test(focus))) invalid('focus');

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
    ...(typeof focus === 'string' ? { focus } : {}),
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

/**
 * The filters for a night played on [hostUid]'s phone (DECISIONS.md D-060). A theme someone
 * other than the host asked to be asked about first is left out: asking on the host's phone
 * would tell the host that limit exists. The host's own stay, as flags. An entry naming no one
 * is malformed, and is left out too (fail closed).
 */
export function forHost(filters: FiltersInput, hostUid: string): FiltersInput {
  const excluded = new Set(filters.excludedThemes);
  const askFirstThemes: Record<string, string[]> = {};
  for (const [theme, uids] of Object.entries(filters.askFirstThemes)) {
    if (uids.length === 0 || uids.some((uid) => uid !== hostUid)) excluded.add(theme);
    else askFirstThemes[theme] = uids;
  }
  return { ...filters, excludedThemes: [...excluded].sort(), askFirstThemes };
}

/**
 * The filters for a game passed between the partners on one phone (DECISIONS.md D-069). Every
 * ask-first theme is left out, the host's own included: on a phone both partners hold, asking
 * anyone would show the other that a limit exists.
 *
 * **And the couple's level is not a ceiling here** (D-148). A game goes as far as the person who
 * started it chose — the request carries that, and `effectiveIntensity` takes the lower of it and
 * this. It used to be the lower of the two *accounts'* levels, which nothing kept in step with
 * what either phone was set to, so every game stopped at Flirty. Themes and items a partner
 * excluded still are: only the number is theirs to lift, and the exclusions are the limits.
 */
export function forSharedPhone(filters: FiltersInput): FiltersInput {
  const excluded = new Set([...filters.excludedThemes, ...Object.keys(filters.askFirstThemes)]);
  return {
    ...filters,
    maxIntensity: MAX_CONTENT_LEVEL,
    excludedThemes: [...excluded].sort(),
    askFirstThemes: {},
  };
}

interface ActiveCouple {
  coupleId: string;
  members: string[];
}

/** The caller's couple, from the server's records — never from a payload (section 8). */
export async function activeCoupleOf(db: Firestore, uid: string): Promise<ActiveCouple> {
  const coupleId = (await db.doc(`users/${uid}`).get()).get('coupleId');
  if (typeof coupleId !== 'string' || coupleId.length === 0) throw new ServiceError('failed-precondition', 'not-paired');
  const couple = await db.doc(`couples/${coupleId}`).get();
  const members: unknown = couple.get('memberUids');
  if (couple.get('status') !== 'ACTIVE' || !Array.isArray(members) || !members.includes(uid)) {
    throw new ServiceError('failed-precondition', 'not-paired');
  }
  return { coupleId, members: members as string[] };
}

/** The couple's current filters, computed first if they never were. */
export async function currentFilters(db: Firestore, coupleId: string, nowMs: number): Promise<FiltersInput> {
  let filters = await filtersRef(db, coupleId).get();
  if (!filters.exists) {
    // A couple whose filters were never computed gets them now, rather than a night built
    // with none.
    await recomputeCouple(db, coupleId, nowMs);
    filters = await filtersRef(db, coupleId).get();
    if (!filters.exists) throw new ServiceError('failed-precondition', 'not-paired');
  }
  return filtersFrom(filters.data()!);
}

export async function revealedMatches(db: Firestore, coupleId: string): Promise<Record<string, MatchLevel>> {
  const mutual: Record<string, MatchLevel> = {};
  for (const doc of (await db.collection(`couples/${coupleId}/mutualPreferences`).get()).docs) {
    const level = doc.get('matchLevel');
    if (MATCH_LEVELS.includes(level)) mutual[doc.id] = level;
  }
  return mutual;
}

/** When each item and prompt was last played, from the couple's engine history. */
export function lastPlayedOf(history: DocumentSnapshot): Record<string, number> {
  const lastPlayed: Record<string, number> = {};
  const raw = history.get('lastPlayed');
  if (typeof raw === 'object' && raw !== null) {
    for (const [itemId, at] of Object.entries(raw)) if (typeof at === 'number') lastPlayed[itemId] = at;
  }
  return lastPlayed;
}

/** Both partners' affinity, averaged per dimension. Read, used and never returned. */
export async function coupleAffinity(db: Firestore, members: readonly string[]): Promise<Record<string, number>> {
  const sums = new Map<string, { total: number; count: number }>();
  for (const member of members) {
    const docs = await db.collection(`users/${member}/affinity`).limit(MAX_AFFINITY_DOCS).get();
    for (const doc of docs.docs) {
      const score = doc.get('score');
      if (typeof score !== 'number' || !Number.isFinite(score)) continue;
      const entry = sums.get(doc.id) ?? { total: 0, count: 0 };
      sums.set(doc.id, { total: entry.total + score, count: entry.count + 1 });
    }
  }
  return Object.fromEntries([...sums].map(([dimension, { total, count }]) => [dimension, total / count]));
}

export async function generateExperience(
  db: Firestore,
  uid: string,
  input: unknown,
  nowMs: number,
  deps: GenerateDeps = defaultDeps,
): Promise<GeneratedExperience> {
  const seed = deps.seed() >>> 0;
  return buildExperience(db, uid, parseRequest(input, seed), { mystery: false }, nowMs, deps);
}

/** What a night is, beyond what the engine was asked for (section 14.2). */
export interface ExperienceShape {
  /** Its shape is never shown: not how many chapters, not how long (D-077). */
  mystery: boolean;
  /**
   * The partner this night is for, when one partner planned it for the other (section 2.5 #2).
   * The night is filtered and flagged for *them*, because it is their phone it will play on,
   * and the rules keep its contents from the planner.
   */
  plannedFor?: string;
}

/**
 * A night from [request]: the pipeline behind the chokepoint, and the couple's own record of
 * what it produced. Every night in the app is built here — asked for, or the app's own idea.
 */
export async function buildExperience(
  db: Firestore,
  uid: string,
  request: EngineRequest,
  shape: ExperienceShape,
  nowMs: number,
  deps: GenerateDeps = defaultDeps,
): Promise<GeneratedExperience> {
  const { coupleId, members } = await activeCoupleOf(db, uid);
  // Whose night it is to play: the caller's, or the partner's when it was planned for them.
  const playerUid = shape.plannedFor ?? uid;
  if (shape.plannedFor !== undefined && !members.includes(shape.plannedFor)) invalid('plannedFor');
  // Selection time, before anything is built, and never once a night is under way (section 19).
  await guardNewNight(db, coupleId, members, shape.mystery, nowMs);
  await consumeRateLimit(db, uid, 'generateExperience', GENERATE_LIMIT, GENERATE_WINDOW_MS, nowMs);

  const couplePath = `couples/${coupleId}`;
  const [filters, mutual, history, weights, catalogue] = await Promise.all([
    currentFilters(db, coupleId, nowMs),
    revealedMatches(db, coupleId),
    db.doc(`${couplePath}/engineState/history`).get(),
    deps.weights(nowMs),
    deps.catalogue(db, nowMs),
  ]);
  // Only a match both partners have been shown can steer a night (section 14.7).
  if (request.focus !== undefined && mutual[request.focus] === undefined) invalid('focus');

  const context: CoupleContext = {
    filters: forHost(filters, playerUid),
    mutual,
    lastPlayed: lastPlayedOf(history),
    nowMs,
    affinity: await coupleAffinity(db, members),
    weights,
  };
  const result = generate(catalogue.items, request, context);
  if (typeof result === 'string') throw new ServiceError('failed-precondition', 'not-enough-content');

  const catalogueById = new Map(catalogue.items.map((item) => [item.id, item]));
  const released = [...result.chapters.map((chapter) => chapter.itemId), ...result.alternates];
  const askFirstItems = released.filter((id) => askFirstUids(catalogueById.get(id)!, context).includes(playerUid));

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
    focus: request.focus ?? null,
    focusIncluded: result.focusIncluded,
    mystery: shape.mystery,
    chapters: chapters.map(({ index, kind, contentId, intensity }) => ({ index, kind, contentIds: [contentId], intensity, state: 'LOCKED' })),
    alternates: result.alternates,
    createdBy: uid,
    plannedBy: shape.plannedFor === undefined ? null : uid,
    plannedFor: shape.plannedFor ?? null,
    scheduledFor: null,
    status: 'READY',
    /** How many chapters have been played, in order. Server-written as the night goes on. */
    playedThrough: 0,
    seed: result.seed,
    engineVersion: result.engineVersion,
    contentVersion: catalogue.contentVersion,
    createdAt,
  });
  // The caller's own ask-first items, where only they can read them (section 3.2), so a
  // restarted player can still ask before each one.
  if (askFirstItems.length > 0) {
    batch.set(ref.collection('askFirst').doc(playerUid), { items: askFirstItems, createdAt });
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
    askFirstItems,
    focusIncluded: result.focusIncluded,
    mystery: shape.mystery,
    settings: {
      mode: request.mode,
      durationMin: request.durationMin,
      moods: [...request.moods],
      intensity: request.intensity,
      leadership: request.leadership,
      novelty: request.novelty,
    },
  };
}

/**
 * The chokepoint again, mid-night (BUILD_PROMPT.md section 3.2; DECISIONS.md D-062): of [ids]
 * — a night's or a game's still-to-come content and its alternates — those the couple's
 * boundaries, levels and content no longer permit. A limit set during a night applies to the
 * rest of it. Cooldowns are not re-applied: the night itself is what set them. An id the
 * catalogue no longer knows is blocked.
 */
export async function blockedNow(
  db: Firestore,
  coupleId: string,
  hostUid: string,
  experience: DocumentData,
  ids: readonly string[],
  nowMs: number,
  deps: Pick<GenerateDeps, 'catalogue'> = defaultDeps,
): Promise<string[]> {
  if (ids.length === 0) return [];
  const [filters, mutual, catalogue] = await Promise.all([
    currentFilters(db, coupleId, nowMs),
    revealedMatches(db, coupleId),
    deps.catalogue(db, nowMs),
  ]);
  const game = experience.type === 'GAME';
  const request: EngineRequest = {
    mode: experience.mode,
    durationMin: experience.durationMin ?? 0,
    moods: experience.mood ?? [],
    intensity: experience.intensity,
    leadership: experience.leadership ?? 'RANDOM',
    novelty: experience.novelty ?? 'MIXED',
    seed: experience.seed,
    media: { VOICE: true, PHOTO: true },
  };
  const context: CoupleContext = {
    // A game is played by both partners on one phone; a night on its host's (D-060, D-069).
    filters: game ? forSharedPhone(filters) : forHost(filters, hostUid),
    mutual,
    lastPlayed: {},
    nowMs,
    affinity: {},
    weights: DEFAULT_WEIGHTS,
  };
  const byId = new Map(game ? catalogue.prompts.map((prompt) => [prompt.id, prompt]) : catalogue.items.map((item) => [item.id, item]));
  return ids.filter((id) => {
    const item = byId.get(id);
    return item === undefined || permits(item, request, context) !== undefined;
  });
}
