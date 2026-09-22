/**
 * THE chokepoint (BUILD_PROMPT.md section 3.2): the one function in the server that decides
 * whether a content item may be shown to a couple. Everything that puts content in front of
 * a couple — a generated night, its alternates, and every later feature — goes through
 * `hardFilter` first, and the pipeline runs it again over what it assembled before anything
 * leaves the server. An architecture test fails the build if the catalogue is read anywhere
 * else.
 *
 * A NEVER cannot be overridden by any parameter, mood, seed, score or partner choice: none of
 * them is an input here. The order of the checks is engine/SPEC.md section 3's, so the reason
 * reported for an item is the same in every implementation.
 */
import type { CoupleContext, EngineItem, EngineRequest, FilterReason } from './types';

const DAY_MS = 86_400_000;

/** The couple's ceiling tonight: never above either the request or the more careful partner. */
export function effectiveIntensity(request: EngineRequest, context: CoupleContext): number {
  return Math.min(request.intensity, context.filters.maxIntensity);
}

/** The first reason `item` may not be shown, or undefined if it may. */
export function permits(item: EngineItem, request: EngineRequest, context: CoupleContext): FilterReason | undefined {
  const { filters } = context;
  if (item.status !== 'published') return 'NOT_PUBLISHED';
  if (!item.modes.includes(request.mode)) return 'MODE';
  if (item.intensity > effectiveIntensity(request, context)) return 'ABOVE_CEILING';
  if (item.excludedByBoundaries.some((theme) => filters.excludedThemes.includes(theme))) return 'EXCLUDED_THEME';
  if ([...item.boostedByPreferences, ...item.requiredMutualPreferences].some((pref) => filters.excludedItems.includes(pref))) {
    return 'EXCLUDED_ITEM';
  }
  if (item.requiredMutualPreferences.some((pref) => context.mutual[pref] === undefined)) return 'NOT_MUTUAL';
  const last = context.lastPlayed[item.id];
  if (last !== undefined && context.nowMs - last < item.repeatCooldownDays * DAY_MS) return 'COOLDOWN';
  if (item.requiresMedia !== null && request.media[item.requiresMedia as 'VOICE' | 'PHOTO'] !== true) return 'MEDIA';
  return undefined;
}

/** Only what may be shown, in catalogue order. Nothing it removes ever reaches scoring. */
export function hardFilter<T extends EngineItem>(items: readonly T[], request: EngineRequest, context: CoupleContext): T[] {
  return items.filter((item) => permits(item, request, context) === undefined);
}

/**
 * Who must be asked before `item` appears (section 3.2): the uids that set ASK_FIRST on any of
 * its themes. Stored only where each of them alone can read it — never on the shared night,
 * where the other partner could see that a limit exists.
 */
export function askFirstUids(item: EngineItem, context: CoupleContext): string[] {
  const uids = new Set<string>();
  for (const theme of item.excludedByBoundaries) {
    for (const uid of context.filters.askFirstThemes[theme] ?? []) uids.add(uid);
  }
  return [...uids].sort();
}
