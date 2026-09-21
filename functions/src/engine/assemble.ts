/**
 * Assembly (engine/SPEC.md section 7; BUILD_PROMPT.md sections 10.4 and 10.5): chapter plan →
 * fill → variety pass → duration fit. Deterministic: every choice is by score, then id.
 * Mirrored exactly by :core:engine, which runs it again on the device for local remixes.
 */
import { chapterRange } from './plan';
import { W_DURATION, W_TARGET, type Chapter, type EngineItem, type Plan } from './types';

export interface AssemblyInput {
  pool: readonly EngineItem[];
  scores: ReadonlyMap<string, number>;
  plan: Plan;
  durationMin: number;
  effective: number;
  repetitionWeight: number;
}

function compareIds(a: string, b: string): number {
  return a < b ? -1 : a > b ? 1 : 0;
}

/** Would `item` in `slot` make three consecutive chapters of one interaction type? */
function makesRun(night: readonly (EngineItem | undefined)[], count: number, slot: number, item: EngineItem): boolean {
  const type = (at: number) => (at === slot ? item.interactionType : night[at]?.interactionType);
  for (let start = slot - 2; start <= slot; start++) {
    if (start < 0 || start + 2 >= count) continue;
    const a = type(start);
    if (a !== undefined && a === type(start + 1) && a === type(start + 2)) return true;
  }
  return false;
}

/**
 * The highest intensity any finale in the pool can reach. No other chapter may go above it,
 * so the finale can always be the peak of the night (section 10.4).
 */
function peakCap(input: AssemblyInput): number {
  const last = input.plan.kinds.length - 1;
  let cap = 0;
  for (const item of input.pool) {
    if (item.chapterKinds.includes('FINALE') && item.intensity <= input.plan.targets[last]) cap = Math.max(cap, item.intensity);
  }
  return cap;
}

/** Every rule a chapter must keep, against the chapters around it (SPEC section 7). */
function fitsSlot(
  night: readonly (EngineItem | undefined)[],
  slot: number,
  item: EngineItem,
  input: AssemblyInput,
  cap: number,
): boolean {
  const { kinds, targets } = input.plan;
  if (!item.chapterKinds.includes(kinds[slot])) return false;
  if (item.intensity > targets[slot]) return false;
  if (kinds[slot] === 'FINALE') {
    if (night.some((other, at) => at !== slot && other !== undefined && other.intensity > item.intensity)) return false;
  } else {
    const finale = night[kinds.length - 1];
    if (item.intensity > (finale ? Math.min(cap, finale.intensity) : cap)) return false;
  }
  const previous = night[slot - 1];
  if (previous && item.intensity > previous.intensity + 1) return false;
  const next = night[slot + 1];
  if (next && next.intensity > item.intensity + 1) return false;
  return !makesRun(night, kinds.length, slot, item);
}

/** 1 when an item's length is exactly the budget, falling towards 0 as it strays. */
export function durationFit(durationMin: number, budget: number): number {
  return 1 - Math.abs(durationMin - budget) / Math.max(durationMin, budget);
}

/** The shortest and longest any item may be (the validator's limits for content). */
const MIN_ITEM_MINUTES = 1;
const MAX_ITEM_MINUTES = 30;

/** How many candidate placements the fill may try before it gives up (SPEC section 7). */
export const MAX_FILL_STEPS = 2000;

/**
 * Fill the slots in order, trying candidates best first and backing up when a later slot
 * cannot be filled — so one greedy choice (say, a third CHALLENGE that leaves no finale)
 * never sinks a night another order could build. Deterministic, and bounded.
 */
function fill(input: AssemblyInput): EngineItem[] | undefined {
  const { plan, scores } = input;
  const count = plan.kinds.length;
  const night: EngineItem[] = [];
  const used = new Set<string>();
  const cap = peakCap(input);
  let steps = 0;

  const ranked = (slot: number, chosenDuration: number): EngineItem[] => {
    const budget = Math.max(1, (input.durationMin - chosenDuration) / (count - slot));
    const candidates = input.pool.filter((item) => !used.has(item.id) && fitsSlot(night, slot, item, input, cap));
    const value = new Map<string, number>();
    for (const item of candidates) {
      const sameType = night.filter((chosen) => chosen.interactionType === item.interactionType).length;
      value.set(
        item.id,
        scores.get(item.id)! +
          W_TARGET * (item.intensity === plan.targets[slot] ? 1 : 0) +
          W_DURATION * durationFit(item.durationMin, budget) -
          (input.repetitionWeight * sameType) / count,
      );
    }
    // Preferred first: on the finale, intensity E; everywhere, lengths that keep the ±20%
    // window reachable. Within each group, best value first, ties by id.
    const remaining = count - slot - 1;
    const longest = 1.2 * input.durationMin - chosenDuration - remaining * MIN_ITEM_MINUTES;
    const shortest = 0.8 * input.durationMin - chosenDuration - remaining * MAX_ITEM_MINUTES;
    const rank = (item: EngineItem): number =>
      (plan.kinds[slot] === 'FINALE' && item.intensity !== input.effective ? 2 : 0) +
      (item.durationMin >= shortest && item.durationMin <= longest ? 0 : 1);
    return candidates.sort((a, b) => {
      const byRank = rank(a) - rank(b);
      if (byRank !== 0) return byRank;
      const diff = value.get(b.id)! - value.get(a.id)!;
      if (diff !== 0) return diff;
      return compareIds(a.id, b.id);
    });
  };

  // The first complete night found, in case none within the step limit fits the ±20% window:
  // the duration pass may still bring it in.
  let fallback: EngineItem[] | undefined;
  const place = (slot: number, chosenDuration: number): boolean => {
    if (slot === count) {
      fallback ??= [...night];
      return chosenDuration >= 0.8 * input.durationMin && chosenDuration <= 1.2 * input.durationMin;
    }
    for (const item of ranked(slot, chosenDuration)) {
      if (++steps > MAX_FILL_STEPS) return false;
      night.push(item);
      used.add(item.id);
      if (place(slot + 1, chosenDuration + item.durationMin)) return true;
      night.pop();
      used.delete(item.id);
    }
    return false;
  };

  return place(0, 0) ? night : fallback;
}

function typeCount(night: readonly EngineItem[]): number {
  return new Set(night.map((item) => item.interactionType)).size;
}

function varietyPass(night: EngineItem[], input: AssemblyInput): void {
  const { plan, scores } = input;
  const cap = peakCap(input);
  while (night.length >= 6 && typeCount(night) < 4) {
    const counts = new Map<string, { count: number; latest: number }>();
    night.forEach((item, slot) => {
      const entry = counts.get(item.interactionType) ?? { count: 0, latest: -1 };
      counts.set(item.interactionType, { count: entry.count + 1, latest: slot });
    });
    let frequent = '';
    let best = { count: -1, latest: -1 };
    for (const [type, entry] of counts) {
      if (entry.count > best.count || (entry.count === best.count && entry.latest > best.latest)) {
        frequent = type;
        best = entry;
      }
    }

    const present = new Set(night.map((item) => item.interactionType));
    const used = new Set(night.map((item) => item.id));
    let applied = false;
    for (let slot = night.length - 1; slot >= 0 && !applied; slot--) {
      if (plan.kinds[slot] === 'FINALE' || night[slot].interactionType !== frequent) continue;
      let replacement: EngineItem | undefined;
      let replacementValue = -Infinity;
      for (const item of input.pool) {
        if (used.has(item.id) || present.has(item.interactionType)) continue;
        if (!fitsSlot(night, slot, item, input, cap)) continue;
        const value = scores.get(item.id)! + W_TARGET * (item.intensity === plan.targets[slot] ? 1 : 0);
        if (value > replacementValue || (value === replacementValue && replacement && compareIds(item.id, replacement.id) < 0)) {
          replacement = item;
          replacementValue = value;
        }
      }
      if (replacement) {
        night[slot] = replacement;
        applied = true;
      }
    }
    if (!applied) return;
  }
}

function total(night: readonly EngineItem[]): number {
  return night.reduce((sum, item) => sum + item.durationMin, 0);
}

function durationPass(night: EngineItem[], input: AssemblyInput): void {
  const { plan, scores } = input;
  const target = input.durationMin;
  const inRange = (minutes: number) => minutes >= 0.8 * target && minutes <= 1.2 * target;
  const finaleAtE = night[night.length - 1].intensity === input.effective;
  const cap = peakCap(input);

  for (let round = 0; round < 2 * night.length && !inRange(total(night)); round++) {
    const current = total(night);
    const used = new Set(night.map((item) => item.id));
    const typesBefore = typeCount(night);
    let best: { slot: number; item: EngineItem; distance: number } | undefined;

    night.forEach((existing, slot) => {
      for (const item of input.pool) {
        if (used.has(item.id) || !fitsSlot(night, slot, item, input, cap)) continue;
        if (plan.kinds[slot] === 'FINALE' && finaleAtE && item.intensity !== input.effective) continue;
        if (night.length >= 6 && typesBefore >= 4) {
          const after = night.map((chosen, at) => (at === slot ? item : chosen));
          if (typeCount(after) < 4) continue;
        }
        const distance = Math.abs(current - existing.durationMin + item.durationMin - target);
        if (
          !best ||
          distance < best.distance ||
          (distance === best.distance && slot === best.slot && scores.get(item.id)! > scores.get(best.item.id)!) ||
          (distance === best.distance &&
            slot === best.slot &&
            scores.get(item.id)! === scores.get(best.item.id)! &&
            compareIds(item.id, best.item.id) < 0)
        ) {
          best = { slot, item, distance };
        }
      }
    });

    if (!best || best.distance >= Math.abs(current - target)) return;
    night[best.slot] = best.item;
  }
}

/** A night from the pool, or undefined when the pool cannot fill the plan. */
export function assemble(input: AssemblyInput): Chapter[] | undefined {
  const night = fill(input);
  if (!night) return undefined;
  varietyPass(night, input);
  durationPass(night, input);
  return night.map((item, index) => ({ index, kind: input.plan.kinds[index], itemId: item.id, intensity: item.intensity }));
}

export { chapterRange };
