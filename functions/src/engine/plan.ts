/**
 * The shape of a night (engine/SPEC.md section 5; BUILD_PROMPT.md section 10.4): how many
 * chapters, which kind each is, and the intensity each aims for.
 */
import type { Rng } from './rng';
import { MIDDLE_KINDS, type ChapterKind, type EngineRequest, type Plan } from './types';

export type PlanFailure = 'NO_FINALE' | 'NO_KINDS';

/** The chapter-count range for a requested duration (section 10.4's table). */
export function chapterRange(durationMin: number): [number, number] {
  if (durationMin <= 20) return [3, 3];
  if (durationMin <= 37) return [4, 5];
  if (durationMin <= 52) return [5, 6];
  if (durationMin <= 75) return [6, 7];
  return [8, 10];
}

const MOOD_KINDS: Record<string, ChapterKind[]> = {
  romantic: ['CONFESSION', 'CURIOSITY'],
  naughty: ['TEASE', 'CHALLENGE'],
  bold: ['CONTROL', 'CHALLENGE'],
  unpredictable: ['WILDCARD', 'YOUR_CHOICE'],
  mysterious: ['TEASE', 'WILDCARD'],
  roleplay: ['ROLEPLAY'],
  talk: ['CONFESSION', 'CURIOSITY'],
  experimental: ['CHALLENGE', 'WILDCARD'],
};

function kindWeights(request: EngineRequest): Map<ChapterKind, number> {
  const weights = new Map<ChapterKind, number>(MIDDLE_KINDS.map((kind) => [kind, 1.0]));
  for (const mood of request.moods) {
    for (const kind of MOOD_KINDS[mood] ?? []) weights.set(kind, weights.get(kind)! + 1.0);
  }
  if (request.leadership === 'RANDOM') {
    weights.set('WILDCARD', weights.get('WILDCARD')! + 0.5);
    weights.set('YOUR_CHOICE', weights.get('YOUR_CHOICE')! + 0.5);
  } else {
    weights.set('CONTROL', weights.get('CONTROL')! + 1.0);
  }
  return weights;
}

/** Intensity targets: rise to E and never by more than one per chapter. */
export function targets(count: number, effective: number): number[] {
  const start = Math.max(1, effective - 2);
  if (count === 1) return [effective];
  return Array.from({ length: count }, (_, i) => start + Math.floor(((effective - start) * i) / (count - 1)));
}

export function plan(
  request: EngineRequest,
  effective: number,
  available: ReadonlySet<string>,
  rng: Rng,
): Plan | PlanFailure {
  const [lo, hi] = chapterRange(request.durationMin);
  const count = lo + rng.nextInt(hi - lo + 1);
  if (!available.has('FINALE')) return 'NO_FINALE';

  const weights = kindWeights(request);
  const kinds: ChapterKind[] = [];
  const used = new Set<ChapterKind>();

  for (let slot = 0; slot < count - 1; slot++) {
    if (slot === 0 && available.has('WARM_UP')) {
      kinds.push('WARM_UP');
      continue;
    }
    const previous = kinds[kinds.length - 1];
    let candidates = MIDDLE_KINDS.filter(
      (kind) => available.has(kind) && kind !== previous && !(kind === 'YOUR_CHOICE' && used.has('YOUR_CHOICE')),
    );
    const unused = candidates.filter((kind) => !used.has(kind));
    if (unused.length > 0) candidates = unused;
    if (candidates.length === 0) return 'NO_KINDS';

    const total = candidates.reduce((sum, kind) => sum + weights.get(kind)!, 0);
    const r = rng.next() * total;
    let running = 0;
    let chosen = candidates[candidates.length - 1];
    for (const kind of candidates) {
      running += weights.get(kind)!;
      if (running > r) {
        chosen = kind;
        break;
      }
    }
    kinds.push(chosen);
    used.add(chosen);
  }
  kinds.push('FINALE');

  return { kinds, targets: targets(count, effective) };
}
