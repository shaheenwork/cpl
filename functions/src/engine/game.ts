/**
 * Choosing a game's rounds (BUILD_PROMPT.md section 11; engine/SPEC.md section 10), in the
 * order section 10.3 makes mandatory for anything that puts content in front of a couple:
 *
 *   1. hard filter (the chokepoint)   2. score the survivors   3. pick each round
 *   4. run the chokepoint again over everything that would leave the server
 *
 * A game warms up: its rounds climb from a little below the couple's ceiling to the ceiling,
 * never above it. Each round holds one prompt, or one of each kind in a game whose prompts
 * have kinds (a truth and a dare — the player chooses which is played). Beside the rounds go
 * a few alternates of every kind, for "Not this one", and never the whole permitted set
 * (section 5.5).
 *
 * Pure and deterministic: the same prompts, request and context always choose the same game.
 * The callable (experience/game.ts) does the reading and writing around it.
 */
import { effectiveIntensity, hardFilter, permits } from './chokepoint';
import { Rng, attemptSeed } from './rng';
import { byScore } from './sample';
import { scoreAll } from './score';
import { GAME_ENGINE_VERSION, type CoupleContext, type EnginePrompt, type EngineRequest, type GameDefinition } from './types';

/** Fewer rounds than this is not a game. */
export const GAME_MIN_ROUNDS = 3;
/** How far below the ceiling a game starts: it warms up to the couple's level. */
export const GAME_RAMP_SPAN = 2;
export const GAME_ALTERNATES_PER_KIND = 3;
const TAIL_FLOOR = 0.1;

export interface GameRound {
  index: number;
  /** One prompt, or one of each of the game's kinds in the game's order. */
  promptIds: string[];
  /** The round's highest prompt intensity: what consent is asked against. */
  intensity: number;
}

export interface GeneratedGame {
  game: string;
  rounds: GameRound[];
  /** Released for "Not this one": best first, never the whole permitted set. */
  alternates: string[];
  effectiveIntensity: number;
  seed: number;
  engineVersion: number;
  /** Whether cooldowns were lifted to fill the game. Boundaries never are. */
  cooldownsLifted: boolean;
}

export type GameFailure = 'NOT_ENOUGH_CONTENT';

/** Each round's target intensity: a climb from [GAME_RAMP_SPAN] below [effective] to it. */
export function rampTargets(rounds: number, effective: number): number[] {
  const start = Math.max(1, effective - GAME_RAMP_SPAN);
  return Array.from({ length: rounds }, (_, round) =>
    rounds === 1 ? effective : start + Math.floor(((effective - start) * round) / (rounds - 1)),
  );
}

/**
 * The game [game] for this couple, or a failure when not even GAME_MIN_ROUNDS rounds can be
 * filled. Cooldowns keep games fresh; they are not a limit. A game that cannot be filled
 * without repeats is chosen again with them lifted — never a boundary — and ranking still
 * puts what was played longest ago first.
 */
export function generateGame(
  prompts: readonly EnginePrompt[],
  game: GameDefinition,
  request: EngineRequest,
  context: CoupleContext,
): GeneratedGame | GameFailure {
  const fresh = chooseRounds(prompts, game, request, context, false);
  if (fresh !== 'NOT_ENOUGH_CONTENT' && fresh.rounds.length === game.rounds) return fresh;
  const lifted = chooseRounds(prompts, game, request, context, true);
  if (lifted === 'NOT_ENOUGH_CONTENT') return fresh;
  if (fresh === 'NOT_ENOUGH_CONTENT' || lifted.rounds.length > fresh.rounds.length) return lifted;
  return fresh;
}

function chooseRounds(
  prompts: readonly EnginePrompt[],
  game: GameDefinition,
  request: EngineRequest,
  context: CoupleContext,
  liftCooldowns: boolean,
): GeneratedGame | GameFailure {
  // Cooldowns are the only thing ever lifted, and ranking still sees what was played when.
  const filterContext: CoupleContext = liftCooldowns ? { ...context, lastPlayed: {} } : context;
  const permitted = hardFilter(prompts, request, filterContext).filter((prompt) => prompt.game === game.id);
  const scores = scoreAll(permitted, request, context);
  const effective = effectiveIntensity(request, context);
  const kinds: (string | null)[] = game.promptKinds.length > 0 ? game.promptKinds : [null];
  const rng = new Rng(attemptSeed(request.seed, liftCooldowns ? 1 : 0));

  const used = new Set<string>();
  const rounds: GameRound[] = [];
  for (const target of rampTargets(game.rounds, effective)) {
    const picked = kinds.map((kind) => pickPrompt(permitted, scores, kind, target, used, rng));
    if (picked.some((prompt) => prompt === undefined)) continue;
    const round = picked as EnginePrompt[];
    for (const prompt of round) used.add(prompt.id);
    rounds.push({
      index: rounds.length,
      promptIds: round.map((prompt) => prompt.id),
      intensity: Math.max(...round.map((prompt) => prompt.intensity)),
    });
  }
  if (rounds.length < GAME_MIN_ROUNDS) return 'NOT_ENOUGH_CONTENT';

  const order = byScore(scores);
  const alternates = kinds.flatMap((kind) =>
    permitted
      .filter((prompt) => !used.has(prompt.id) && (kind === null || prompt.kind === kind))
      .sort(order)
      .slice(0, GAME_ALTERNATES_PER_KIND),
  );
  // Never the complete permitted set (section 5.5): what is missing would say what was excluded.
  if (used.size + alternates.length === permitted.length && alternates.length > 0) {
    const lowest = [...alternates].sort(order)[alternates.length - 1];
    alternates.splice(alternates.indexOf(lowest), 1);
  }

  // Belt and braces (section 10.3, step 5): the chokepoint again, over everything released.
  const byId = new Map(permitted.map((prompt) => [prompt.id, prompt]));
  const released = [...used, ...alternates.map((prompt) => prompt.id)];
  if (!released.every((id) => byId.has(id) && permits(byId.get(id)!, request, filterContext) === undefined)) {
    return 'NOT_ENOUGH_CONTENT';
  }
  return {
    game: game.id,
    rounds,
    alternates: alternates.map((prompt) => prompt.id),
    effectiveIntensity: effective,
    seed: request.seed,
    engineVersion: GAME_ENGINE_VERSION,
    cooldownsLifted: liftCooldowns,
  };
}

/**
 * One prompt of [kind] for a round aiming at [target]: among the unused prompts at the highest
 * intensity that does not pass the target, a draw weighted by score — the best is likeliest,
 * and a couple replaying a game still meets new rounds.
 */
function pickPrompt(
  permitted: readonly EnginePrompt[],
  scores: ReadonlyMap<string, number>,
  kind: string | null,
  target: number,
  used: ReadonlySet<string>,
  rng: Rng,
): EnginePrompt | undefined {
  const eligible = permitted.filter(
    (prompt) => !used.has(prompt.id) && prompt.intensity <= target && (kind === null || prompt.kind === kind),
  );
  if (eligible.length === 0) return undefined;
  let level = 0;
  for (const prompt of eligible) level = Math.max(level, prompt.intensity);
  const candidates = eligible.filter((prompt) => prompt.intensity === level).sort(byScore(scores));

  let min = Infinity;
  for (const prompt of candidates) min = Math.min(min, scores.get(prompt.id)!);
  let total = 0;
  for (const prompt of candidates) total += scores.get(prompt.id)! - min + TAIL_FLOOR;
  const r = rng.next() * total;
  let running = 0;
  for (const prompt of candidates) {
    running += scores.get(prompt.id)! - min + TAIL_FLOOR;
    if (running > r) return prompt;
  }
  return candidates[candidates.length - 1];
}
