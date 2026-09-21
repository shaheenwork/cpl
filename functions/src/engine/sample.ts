/**
 * The bounded alternates pool (engine/SPEC.md section 6; BUILD_PROMPT.md section 5.5): the best
 * candidate for every chapter plus a weighted random tail, about three times the night — and
 * never the whole permitted set, which would let a client work out what was excluded by
 * seeing what is missing.
 */
import { durationFit } from './assemble';
import type { Rng } from './rng';
import { W_DURATION, type EngineItem, type Plan } from './types';

export function byScore(scores: ReadonlyMap<string, number>): (a: EngineItem, b: EngineItem) => number {
  return (a, b) => {
    const diff = scores.get(b.id)! - scores.get(a.id)!;
    if (diff !== 0) return diff;
    return a.id < b.id ? -1 : a.id > b.id ? 1 : 0;
  };
}

const TAIL_PER_SLOT = 2;
const TAIL_FLOOR = 0.1;

export function samplePool(
  permitted: readonly EngineItem[],
  scores: ReadonlyMap<string, number>,
  plan: Plan,
  durationMin: number,
  rng: Rng,
): EngineItem[] {
  const pool: EngineItem[] = [];
  const inPool = new Set<string>();
  const firstPicks = new Set<string>();
  const order = byScore(scores);

  // A candidate's worth for the pool: its score, plus how well it fits an even share of the
  // night's time — a pool of perfect but hour-long items cannot make a 15-minute night.
  const budget = durationMin / plan.kinds.length;
  const value = new Map<string, number>();
  for (const item of permitted) value.set(item.id, scores.get(item.id)! + W_DURATION * durationFit(item.durationMin, budget));
  const byValue = byScore(value);

  plan.kinds.forEach((kind, slot) => {
    const eligible = permitted
      .filter((item) => !inPool.has(item.id) && item.chapterKinds.includes(kind) && item.intensity <= plan.targets[slot])
      .sort(byValue);
    if (eligible.length === 0) return;

    const [first, ...rest] = eligible;
    pool.push(first);
    inPool.add(first.id);
    firstPicks.add(first.id);

    const remaining = [...rest];
    for (let draw = 0; draw < TAIL_PER_SLOT && remaining.length > 0; draw++) {
      let min = Infinity;
      for (const item of remaining) min = Math.min(min, value.get(item.id)!);
      let total = 0;
      for (const item of remaining) total += value.get(item.id)! - min + TAIL_FLOOR;
      const r = rng.next() * total;
      let running = 0;
      let index = remaining.length - 1;
      for (let i = 0; i < remaining.length; i++) {
        running += value.get(remaining[i].id)! - min + TAIL_FLOOR;
        if (running > r) {
          index = i;
          break;
        }
      }
      const [picked] = remaining.splice(index, 1);
      pool.push(picked);
      inPool.add(picked.id);
    }
  });

  // Never the complete permitted set (section 5.5), unless the night needs all of it.
  if (pool.length === permitted.length && permitted.length > plan.kinds.length) {
    const droppable = pool.filter((item) => !firstPicks.has(item.id)).sort(order);
    const lowest = droppable[droppable.length - 1];
    if (lowest) pool.splice(pool.indexOf(lowest), 1);
  }
  return pool;
}
