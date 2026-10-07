/**
 * startGame (BUILD_PROMPT.md sections 3.2, 11): a game's rounds, chosen by the server behind
 * both partners' boundaries — the same chokepoint as a night — and stored as an experience
 * the couple then plays through a session, exactly as a night is: "I'm in" from both phones
 * at BOLD and WILD, PAUSE and STOP from either, every round moved on by the server.
 *
 * A game is passed between the partners on one phone (DECISIONS.md D-069), so every ask-first
 * theme is left out of it: asking on a phone both hold would show a limit exists. What either
 * answers stays on that phone; the server keeps only which prompts were played, for
 * cooldowns.
 *
 * Core logic, tested directly against the emulator; the callable in index.ts only
 * authenticates and delegates.
 */
import { Timestamp, type Firestore } from 'firebase-admin/firestore';
import { generateGame } from '../engine/game';
import { type CoupleContext, type EngineRequest } from '../engine/types';
import { answeredBy } from '../session/answerers';
import { ServiceError } from '../shared/errors';
import { consumeRateLimit } from '../shared/rateLimit';
import {
  activeCoupleOf,
  coupleAffinity,
  currentFilters,
  defaultDeps,
  forSharedPhone,
  lastPlayedOf,
  revealedMatches,
  type GenerateDeps,
} from './generate';

export const GAME_LIMIT = 30;
export const GAME_WINDOW_MS = 60 * 60 * 1000;
const GAME_ID = /^[A-Z][A-Z_]{2,40}$/;
const REQUEST_KEYS = new Set(['game', 'intensity']);

export interface StartedGame {
  experienceId: string;
  game: string;
  rounds: { index: number; contentIds: string[]; intensity: number }[];
  alternates: string[];
  effectiveIntensity: number;
  seed: number;
  engineVersion: number;
  contentVersion: number;
}

function invalid(reason: string): never {
  throw new ServiceError('invalid-argument', reason);
}

/** The request, field by field. Unknown fields are refused: only the server chooses a seed. */
export function parseGameRequest(input: unknown): { game: string; intensity: number } {
  if (typeof input !== 'object' || input === null || Array.isArray(input)) invalid('request');
  const raw = input as Record<string, unknown>;
  for (const key of Object.keys(raw)) if (!REQUEST_KEYS.has(key)) invalid(`unknown field ${key}`);
  const { game, intensity } = raw;
  if (typeof game !== 'string' || !GAME_ID.test(game)) invalid('game');
  if (!Number.isInteger(intensity) || (intensity as number) < 1 || (intensity as number) > 5) invalid('intensity');
  return { game, intensity: intensity as number };
}

export async function startGame(
  db: Firestore,
  uid: string,
  input: unknown,
  nowMs: number,
  deps: GenerateDeps = defaultDeps,
): Promise<StartedGame> {
  const seed = deps.seed() >>> 0;
  const { game: gameId, intensity } = parseGameRequest(input);
  const { coupleId, members } = await activeCoupleOf(db, uid);
  await consumeRateLimit(db, uid, 'startGame', GAME_LIMIT, GAME_WINDOW_MS, nowMs);

  const couplePath = `couples/${coupleId}`;
  const [filters, mutual, history, weights, catalogue] = await Promise.all([
    currentFilters(db, coupleId, nowMs),
    revealedMatches(db, coupleId),
    db.doc(`${couplePath}/engineState/history`).get(),
    deps.weights(nowMs),
    deps.catalogue(db, nowMs),
  ]);
  const game = catalogue.games.find((candidate) => candidate.id === gameId);
  if (game === undefined || !game.modes.includes('TOGETHER')) invalid('game');

  const request: EngineRequest = {
    mode: 'TOGETHER',
    durationMin: 0,
    moods: [],
    intensity,
    leadership: 'RANDOM',
    novelty: 'MIXED',
    seed,
    media: { VOICE: true, PHOTO: true },
  };
  const context: CoupleContext = {
    filters: forSharedPhone(filters),
    mutual,
    lastPlayed: lastPlayedOf(history),
    nowMs,
    affinity: await coupleAffinity(db, members),
    weights,
  };
  const result = generateGame(catalogue.prompts, game, request, context);
  if (typeof result === 'string') throw new ServiceError('failed-precondition', 'not-enough-content');

  const rounds = result.rounds.map((round) => ({ index: round.index, contentIds: round.promptIds, intensity: round.intensity }));
  const ref = db.collection(`${couplePath}/experiences`).doc();
  await ref.set({
    type: 'GAME',
    game: game.id,
    // Who each round asks, read off the game's steps: a game of turns asks one of them, and its
    // chapters are revealed as soon as that one has answered (section 12).
    answeredBy: answeredBy(game),
    mode: request.mode,
    intensity,
    effectiveIntensity: result.effectiveIntensity,
    // A game's rounds are a session's chapters: one list, so every session rule applies as is.
    chapters: rounds.map((round) => ({ ...round, kind: 'ROUND', state: 'LOCKED' })),
    alternates: result.alternates,
    createdBy: uid,
    status: 'READY',
    playedThrough: 0,
    seed: result.seed,
    engineVersion: result.engineVersion,
    contentVersion: catalogue.contentVersion,
    createdAt: Timestamp.fromMillis(nowMs),
  });
  return {
    experienceId: ref.id,
    game: game.id,
    rounds,
    alternates: result.alternates,
    effectiveIntensity: result.effectiveIntensity,
    seed: result.seed,
    engineVersion: result.engineVersion,
    contentVersion: catalogue.contentVersion,
  };
}
