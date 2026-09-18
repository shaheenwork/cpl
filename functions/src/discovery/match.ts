/**
 * Mutual matching and reveal timing (BUILD_PROMPT.md sections 5.2, 5.3, 14.7).
 *
 * Pure, with no Firestore types, so every rule is unit-tested directly.
 */
import { randomInt } from 'node:crypto';

export const MATCH_LEVELS = ['BOTH_YES', 'BOTH_CURIOUS', 'MIXED_POSITIVE', 'BOTH_SECRET'] as const;
export type MatchLevel = (typeof MATCH_LEVELS)[number];

/** Bumped whenever the shape of a mutual-preference document changes (section 5.2 `sourceVersion`). */
export const MATCH_VERSION = 1;

/** One partner's stored answer, untrusted in shape. */
export interface StoredAnswer {
  value?: unknown;
  secret?: unknown;
}

const POSITIVE: ReadonlySet<unknown> = new Set(['YES', 'CURIOUS', 'MAYBE']);

/**
 * Whether two answers to the same item make a match, and what kind.
 *
 * - Only two positives match (section 5.2). Anything else, including a value this code does
 *   not recognise, is no match — and no match materialises nothing at all.
 * - A secret curiosity matches **only another secret curiosity** (DECISIONS.md D-021): the
 *   app promises "Secretly curious stays hidden unless they secretly pick it too", so a
 *   secret answer never surfaces through an open one.
 */
export function matchLevelOf(a: StoredAnswer | undefined, b: StoredAnswer | undefined): MatchLevel | null {
  if (!a || !b) return null;
  if (!POSITIVE.has(a.value) || !POSITIVE.has(b.value)) return null;

  const aSecret = a.secret === true;
  const bSecret = b.secret === true;
  if (aSecret && bSecret) return 'BOTH_SECRET';
  if (aSecret || bSecret) return null;

  if (a.value === 'YES' && b.value === 'YES') return 'BOTH_YES';
  if (a.value === 'CURIOUS' && b.value === 'CURIOUS') return 'BOTH_CURIOUS';
  return 'MIXED_POSITIVE';
}

// ---------------------------------------------------------------------------
// Timing (section 5.3)
// ---------------------------------------------------------------------------

const MINUTE_MS = 60 * 1000;

export interface RevealTiming {
  /** Nothing is ever revealed sooner than this after the answer that caused it. */
  minDelayMs: number;
  maxDelayMs: number;
}

/** The spec's default window: 30 to 180 minutes. */
export const DEFAULT_TIMING: RevealTiming = { minDelayMs: 30 * MINUTE_MS, maxDelayMs: 180 * MINUTE_MS };

/**
 * A hard floor under any configuration. A reveal minutes after an answer would tell the
 * partner what was just answered; no Remote Config value may take the delay below this.
 */
export const MIN_DELAY_FLOOR_MS = 15 * MINUTE_MS;

/** Makes any configured window safe: floored, ordered, and never empty. */
export function safeTiming(minDelayMs: unknown, maxDelayMs: unknown): RevealTiming {
  const min = typeof minDelayMs === 'number' && Number.isFinite(minDelayMs) ? minDelayMs : DEFAULT_TIMING.minDelayMs;
  const max = typeof maxDelayMs === 'number' && Number.isFinite(maxDelayMs) ? maxDelayMs : DEFAULT_TIMING.maxDelayMs;
  const floored = Math.max(min, MIN_DELAY_FLOOR_MS);
  return { minDelayMs: floored, maxDelayMs: Math.max(max, floored) };
}

/**
 * When a change queued at [queuedAtMs] may be released: somewhere uniformly in the window,
 * never at a predictable offset. [randomBetween] is injectable so tests can pin it.
 */
export function releaseAfterMs(
  queuedAtMs: number,
  timing: RevealTiming,
  randomBetween: (min: number, maxInclusive: number) => number = (min, max) => randomInt(min, max + 1),
): number {
  return queuedAtMs + randomBetween(timing.minDelayMs, timing.maxDelayMs);
}
