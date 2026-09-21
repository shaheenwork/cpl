/**
 * Ranking (engine/SPEC.md section 4). Only ever called with items the chokepoint permitted:
 * ranking never sees excluded content (BUILD_PROMPT.md section 10.3).
 */
import { effectiveIntensity } from './chokepoint';
import type { CoupleContext, EngineItem, EngineRequest, MatchLevel } from './types';

const DAY_MS = 86_400_000;
const RECENCY_DAYS = 90;
const CURIOUS_THEME_BONUS = 0.25;

const MATCH_WEIGHT: Record<MatchLevel, number> = {
  BOTH_YES: 1,
  BOTH_SECRET: 1,
  BOTH_CURIOUS: 0.8,
  MIXED_POSITIVE: 0.6,
};

function mutualFit(item: EngineItem, context: CoupleContext): number {
  let fit = 0;
  if (item.boostedByPreferences.length > 0) {
    let sum = 0;
    for (const pref of item.boostedByPreferences) {
      const level = context.mutual[pref];
      sum += level === undefined ? 0 : (MATCH_WEIGHT[level] ?? 0);
    }
    fit = sum / item.boostedByPreferences.length;
  }
  if (item.excludedByBoundaries.some((theme) => context.filters.curiousThemes.includes(theme))) {
    fit += CURIOUS_THEME_BONUS;
  }
  return Math.min(1, fit);
}

function affinityFit(item: EngineItem, context: CoupleContext): number {
  const keys = [`category:${item.category}`, `interaction:${item.interactionType}`, ...item.moods.map((mood) => `mood:${mood}`)];
  let sum = 0;
  let count = 0;
  for (const key of keys) {
    const raw = context.affinity[key];
    if (raw === undefined) continue;
    const clamped = Math.max(-1, Math.min(1, raw));
    sum += (clamped + 1) / 2;
    count += 1;
  }
  return count === 0 ? 0.5 : sum / count;
}

function moodFit(item: EngineItem, request: EngineRequest): number {
  if (request.moods.length === 0) return 0.5;
  let hits = 0;
  for (const mood of request.moods) if (item.moods.includes(mood)) hits += 1;
  return hits / request.moods.length;
}

function noveltyFit(item: EngineItem, request: EngineRequest, fresh: boolean): number {
  switch (request.novelty) {
    case 'FAMILIAR':
      return 0.5 * (1 - item.noveltyWeight) + 0.5 * (fresh ? 0 : 1);
    case 'SURPRISE_ME':
      return 0.5 * item.noveltyWeight + 0.5 * (fresh ? 1 : 0);
    default:
      return 0.5;
  }
}

export function score(item: EngineItem, request: EngineRequest, context: CoupleContext): number {
  const w = context.weights;
  const last = context.lastPlayed[item.id];
  const fresh = last === undefined;
  const intensityFit = 1 - (effectiveIntensity(request, context) - item.intensity) / 4;
  const recency = fresh ? 0 : Math.max(0, 1 - (context.nowMs - last) / DAY_MS / RECENCY_DAYS);
  return (
    w.mutualPreference * mutualFit(item, context) +
    w.affinity * affinityFit(item, context) +
    w.moodMatch * moodFit(item, request) +
    w.novelty * noveltyFit(item, request, fresh) +
    w.intensityFit * intensityFit -
    w.recencyPenalty * recency
  );
}

/** Scores for permitted items only, by id. */
export function scoreAll(items: readonly EngineItem[], request: EngineRequest, context: CoupleContext): Map<string, number> {
  return new Map(items.map((item) => [item.id, score(item, request, context)]));
}
