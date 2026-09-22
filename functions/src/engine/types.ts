/**
 * The experience engine's vocabulary (engine/SPEC.md). Plain data, no Firestore types, so
 * every stage is a pure function tested directly and mirrored exactly by :core:engine.
 */

/** Raised whenever output changes for identical inputs (BUILD_PROMPT.md section 10.6). */
export const ENGINE_VERSION = 1;

export const MODES = ['TOGETHER', 'APART'] as const;
export type Mode = (typeof MODES)[number];

export const MOODS = ['romantic', 'naughty', 'bold', 'unpredictable', 'mysterious', 'roleplay', 'talk', 'experimental'] as const;
export type Mood = (typeof MOODS)[number];

export const LEADERSHIPS = ['ME', 'YOU', 'SWITCH', 'RANDOM'] as const;
export type Leadership = (typeof LEADERSHIPS)[number];

export const NOVELTIES = ['FAMILIAR', 'MIXED', 'SURPRISE_ME'] as const;
export type Novelty = (typeof NOVELTIES)[number];

export const CHAPTER_KINDS = [
  'WARM_UP', 'CURIOSITY', 'TEASE', 'CHALLENGE', 'CONTROL', 'CONFESSION', 'ROLEPLAY', 'WILDCARD', 'YOUR_CHOICE', 'FINALE',
] as const;
export type ChapterKind = (typeof CHAPTER_KINDS)[number];

/** The middle-slot kinds, in the order the plan walks them (SPEC section 5). */
export const MIDDLE_KINDS: readonly ChapterKind[] = [
  'CURIOSITY', 'TEASE', 'CHALLENGE', 'CONTROL', 'CONFESSION', 'ROLEPLAY', 'WILDCARD', 'YOUR_CHOICE',
];

export const MATCH_LEVELS = ['BOTH_YES', 'BOTH_CURIOUS', 'MIXED_POSITIVE', 'BOTH_SECRET'] as const;
export type MatchLevel = (typeof MATCH_LEVELS)[number];

/** A content item as the engine sees it: the bundle's item (BUILD_PROMPT.md section 9.1). */
export interface EngineItem {
  id: string;
  version: number;
  pack: string;
  category: string;
  intensity: number;
  modes: string[];
  interactionType: string;
  durationMin: number;
  moods: string[];
  chapterKinds: string[];
  tags: string[];
  noveltyWeight: number;
  repeatCooldownDays: number;
  requiresMedia: string | null;
  status: string;
  excludedByBoundaries: string[];
  requiredMutualPreferences: string[];
  boostedByPreferences: string[];
}

export interface EngineRequest {
  mode: Mode;
  durationMin: number;
  moods: Mood[];
  intensity: number;
  leadership: Leadership;
  novelty: Novelty;
  /** Unsigned 32-bit. */
  seed: number;
  /** What this device can capture. Both default to true. */
  media: { VOICE: boolean; PHOTO: boolean };
  /**
   * A revealed mutual match to build the night around ("Build a night around this",
   * section 14.7): a taxonomy item id. The caller checks it is one of the couple's matches.
   */
  focus?: string;
}

/** The couple's server-only filters (functions/src/engine/filters.ts, section 5.4). */
export interface FiltersInput {
  maxIntensity: number;
  excludedThemes: string[];
  askFirstThemes: Record<string, string[]>;
  curiousThemes: string[];
  excludedItems: string[];
}

export interface Weights {
  mutualPreference: number;
  affinity: number;
  moodMatch: number;
  novelty: number;
  intensityFit: number;
  recencyPenalty: number;
  repetitionPenalty: number;
}

export const DEFAULT_WEIGHTS: Weights = {
  mutualPreference: 3.0,
  affinity: 2.0,
  moodMatch: 2.0,
  novelty: 1.5,
  intensityFit: 1.5,
  recencyPenalty: 2.0,
  repetitionPenalty: 3.0,
};

/** Everything the server knows about the couple. Never sent to a client (section 5.4). */
export interface CoupleContext {
  filters: FiltersInput;
  /** Revealed mutual matches only: taxonomy item id → match level. */
  mutual: Record<string, MatchLevel>;
  /** Item id → epoch milliseconds it was last played. */
  lastPlayed: Record<string, number>;
  nowMs: number;
  /** Dimension → score in [-1, 1]. */
  affinity: Record<string, number>;
  weights: Weights;
}

export type FilterReason =
  | 'NOT_PUBLISHED'
  | 'MODE'
  | 'ABOVE_CEILING'
  | 'EXCLUDED_THEME'
  | 'EXCLUDED_ITEM'
  | 'NOT_MUTUAL'
  | 'COOLDOWN'
  | 'MEDIA';

export interface Plan {
  kinds: ChapterKind[];
  targets: number[];
}

export interface Chapter {
  index: number;
  kind: ChapterKind;
  itemId: string;
  intensity: number;
}

export type Violation =
  | 'CHAPTER_COUNT'
  | 'DUPLICATE_ITEM'
  | 'KIND_MISMATCH'
  | 'TYPE_RUN'
  | 'TOO_FEW_TYPES'
  | 'MULTIPLE_YOUR_CHOICE'
  | 'DURATION'
  | 'ESCALATION'
  | 'ABOVE_CEILING'
  | 'FINALE_NOT_PEAK'
  | 'MODE'
  | 'UNKNOWN_ITEM';

/** Assembly constants (SPEC section 7). Not tunable: they shape a night, not its taste. */
export const W_TARGET = 1.0;
export const W_DURATION = 2.0;

/**
 * The pull of a night's focus (SPEC section 4). Larger than any single ranking weight, so an
 * item about the match the couple asked for outranks one that merely suits the mood.
 */
export const W_FOCUS = 4.0;

/** Whether [item] is about the match [focus] names: it is boosted by it, or requires it. */
export function isFocusItem(item: EngineItem, focus: string | undefined): boolean {
  return focus !== undefined && (item.boostedByPreferences.includes(focus) || item.requiredMutualPreferences.includes(focus));
}
