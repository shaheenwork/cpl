/**
 * The generation pipeline, in the order BUILD_PROMPT.md section 10.3 makes mandatory:
 *
 *   1. hard filter (the chokepoint)   2. score the survivors   3. sample the pool
 *   4. assemble                       5. validate, and run the chokepoint again
 *
 * Pure: the catalogue and the couple's context come in, a night or a failure goes out. The
 * callable (experience/generate.ts) does the reading and writing around it. The architecture
 * test holds this file to that order and keeps every other module away from the catalogue.
 */
import { assemble } from './assemble';
import { askFirstUids, effectiveIntensity, hardFilter, permits } from './chokepoint';
import { plan } from './plan';
import { Rng, attemptSeed } from './rng';
import { byScore, samplePool } from './sample';
import { scoreAll } from './score';
import { ENGINE_VERSION, isFocusItem, type Chapter, type CoupleContext, type EngineItem, type EngineRequest } from './types';
import { validate } from './validate';

export const MAX_ATTEMPTS = 5;

export interface GeneratedNight {
  chapters: Chapter[];
  /** The pool minus the night, best first: what a client may remix with (section 5.5). */
  alternates: string[];
  effectiveIntensity: number;
  seed: number;
  /** Which retry produced it; the seed alone reproduces it. */
  attempt: number;
  engineVersion: number;
  /** uid → chapter indexes that need their "is this OK?" first. Private to each uid. */
  askFirst: Record<string, number[]>;
  /** Whether a night asked to be built around a match holds an item about it. */
  focusIncluded: boolean;
}

export type GenerationFailure = 'NOT_ENOUGH_CONTENT';

export function generate(
  catalogue: readonly EngineItem[],
  request: EngineRequest,
  context: CoupleContext,
): GeneratedNight | GenerationFailure {
  const permitted = hardFilter(catalogue, request, context);
  const scores = scoreAll(permitted, request, context);
  const effective = effectiveIntensity(request, context);
  const available = new Set(permitted.flatMap((item) => item.chapterKinds));
  const catalogueById = new Map(catalogue.map((item) => [item.id, item]));
  // A focus only binds when something permitted is about it; otherwise any night will do.
  const focusIds = new Set(permitted.filter((item) => isFocusItem(item, request.focus)).map((item) => item.id));

  // A night that holds the focus (when there is one to hold) and whose finale reaches the
  // requested intensity is returned at once. Otherwise the best night so far is kept while
  // the remaining retries look: one with the focus beats one without, then the earliest.
  let fallback: GeneratedNight | undefined;
  for (let attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
    const rng = new Rng(attemptSeed(request.seed, attempt));
    const shape = plan(request, effective, available, rng);
    if (typeof shape === 'string') continue;
    const pool = samplePool(permitted, scores, shape, request.durationMin, rng);
    const chapters = assemble({
      pool,
      scores,
      plan: shape,
      durationMin: request.durationMin,
      effective,
      repetitionWeight: context.weights.repetitionPenalty,
    });
    if (!chapters) continue;

    const violations = validate(
      { chapters, durationMin: request.durationMin, effective, mode: request.mode },
      catalogueById,
    );
    const chosen = new Set(chapters.map((chapter) => chapter.itemId));
    const alternates = pool
      .filter((item) => !chosen.has(item.id))
      .sort(byScore(scores))
      .map((item) => item.id);
    // Belt and braces (section 10.3, step 5): the chokepoint again, over everything that
    // would leave the server.
    const stillPermitted = [...chosen, ...alternates].every((id) => {
      const item = catalogueById.get(id);
      return item !== undefined && permits(item, request, context) === undefined;
    });
    if (violations.length > 0 || !stillPermitted) continue;

    const askFirst: Record<string, number[]> = {};
    for (const chapter of chapters) {
      for (const uid of askFirstUids(catalogueById.get(chapter.itemId)!, context)) {
        (askFirst[uid] ??= []).push(chapter.index);
      }
    }
    const night: GeneratedNight = {
      chapters,
      alternates,
      effectiveIntensity: effective,
      seed: request.seed,
      attempt,
      engineVersion: ENGINE_VERSION,
      askFirst,
      focusIncluded: chapters.some((chapter) => focusIds.has(chapter.itemId)),
    };
    const holdsFocus = focusIds.size === 0 || night.focusIncluded;
    if (holdsFocus && chapters[chapters.length - 1].intensity === effective) return night;
    if (fallback === undefined || (holdsFocus && !fallback.focusIncluded && focusIds.size > 0)) fallback = night;
  }
  return fallback ?? 'NOT_ENOUGH_CONTENT';
}
