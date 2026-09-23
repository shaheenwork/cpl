/**
 * Surprise Us and Mystery Night (BUILD_PROMPT.md sections 2.4, 14.2): the nights the couple did
 * not configure.
 *
 * The one UX law is that nobody should be asked "which question should we pick?" — so nothing
 * about these is chosen on the phone. The server picks the shape from what only it can see: the
 * couple's ceiling, and what their last nights were like. A phone that asked for a surprise and
 * did not like it can ask again; it cannot steer what it gets, because it sends nothing.
 *
 * A Mystery Night is the same night with its shape kept back (D-077): the phone is told only
 * that there is one.
 *
 * Core logic, tested directly against the emulator; the callable in index.ts only
 * authenticates and delegates.
 */
import type { Firestore } from 'firebase-admin/firestore';
import { Rng } from '../engine/rng';
import { MOODS, type EngineRequest, type Mood } from '../engine/types';
import { ServiceError } from '../shared/errors';
import {
  activeCoupleOf,
  buildExperience,
  currentFilters,
  defaultDeps,
  type GenerateDeps,
  type GeneratedExperience,
} from './generate';

/** How long a surprise runs when the couple has no habit yet. */
export const DEFAULT_DURATION_MIN = 45;

/** How many of the couple's last nights are read to find their habit. */
export const RECENT_NIGHTS = 5;

const SURPRISE_KEYS = new Set(['mystery', 'mode']);
const MODES = ['TOGETHER', 'APART'] as const;

export interface SurpriseRequest {
  mystery: boolean;
  mode: (typeof MODES)[number];
}

/** What the couple's recent nights were like: enough to not repeat the last one. */
export interface NightHabit {
  /** The durations of their last nights, most recent first. */
  durations: number[];
  /** The moods of their most recent night. */
  lastMoods: Mood[];
}

export function parseSurprise(input: unknown): SurpriseRequest {
  if (input === undefined || input === null) return { mystery: false, mode: 'TOGETHER' };
  if (typeof input !== 'object' || Array.isArray(input)) throw new ServiceError('invalid-argument', 'request');
  const raw = input as Record<string, unknown>;
  for (const key of Object.keys(raw)) if (!SURPRISE_KEYS.has(key)) throw new ServiceError('invalid-argument', `unknown field ${key}`);
  const { mystery, mode } = raw;
  if (mystery !== undefined && typeof mystery !== 'boolean') throw new ServiceError('invalid-argument', 'mystery');
  if (mode !== undefined && !MODES.includes(mode as never)) throw new ServiceError('invalid-argument', 'mode');
  return { mystery: mystery === true, mode: (mode as SurpriseRequest['mode']) ?? 'TOGETHER' };
}

/** The couple's last few nights, for a surprise that is theirs rather than anyone's. */
export async function nightHabit(db: Firestore, coupleId: string): Promise<NightHabit> {
  const recent = await db
    .collection(`couples/${coupleId}/experiences`)
    .orderBy('createdAt', 'desc')
    .limit(RECENT_NIGHTS)
    .get();
  const nights = recent.docs.filter((doc) => doc.get('type') !== 'GAME');
  const durations = nights
    .map((doc) => doc.get('durationMin'))
    .filter((value): value is number => Number.isInteger(value));
  const moods = nights[0]?.get('mood');
  const lastMoods = Array.isArray(moods) ? moods.filter((mood): mood is Mood => MOODS.includes(mood)) : [];
  return { durations, lastMoods };
}

/**
 * The night the app decides on (section 2.4): one mood, never the last night's, at the couple's
 * own ceiling — the engine warms up to it (SPEC section 6) rather than opening there — for as
 * long as their nights usually run. Novelty is SURPRISE_ME and nobody leads: this is the app's
 * turn to be the third participant.
 */
export function surpriseRequest(seed: number, ceiling: number, habit: NightHabit): EngineRequest {
  const rng = new Rng(seed);
  const choices = MOODS.filter((mood) => !habit.lastMoods.includes(mood));
  const moods: Mood[] = [(choices.length > 0 ? choices : MOODS)[rng.nextInt(choices.length > 0 ? choices.length : MOODS.length)]];
  return {
    mode: 'TOGETHER',
    durationMin: usualDuration(habit.durations),
    moods,
    intensity: ceiling,
    leadership: 'RANDOM',
    novelty: 'SURPRISE_ME',
    seed,
    media: { VOICE: true, PHOTO: true },
  };
}

/** How long their nights usually run: the most common of the recent ones, ties to the newest. */
function usualDuration(durations: readonly number[]): number {
  if (durations.length === 0) return DEFAULT_DURATION_MIN;
  const counts = new Map<number, number>();
  for (const duration of durations) counts.set(duration, (counts.get(duration) ?? 0) + 1);
  let best = durations[0];
  for (const duration of durations) {
    if ((counts.get(duration) ?? 0) > (counts.get(best) ?? 0)) best = duration;
  }
  return best;
}

export async function surpriseExperience(
  db: Firestore,
  uid: string,
  input: unknown,
  nowMs: number,
  deps: GenerateDeps = defaultDeps,
): Promise<GeneratedExperience> {
  const request = parseSurprise(input);
  const { coupleId } = await activeCoupleOf(db, uid);
  const [filters, habit] = await Promise.all([currentFilters(db, coupleId, nowMs), nightHabit(db, coupleId)]);
  const seed = deps.seed() >>> 0;
  const engineRequest = { ...surpriseRequest(seed, filters.maxIntensity, habit), mode: request.mode };
  return buildExperience(db, uid, engineRequest, { mystery: request.mystery }, nowMs, deps);
}
