/**
 * The authored content, as read from `content/` (BUILD_PROMPT.md section 9).
 *
 * Everything here is loaded raw and typed loosely on purpose: the validator's job is to
 * find what is wrong with the files, so it must be able to read files that are wrong.
 */
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

export interface ContentItem {
  id: string;
  version: number;
  title: string;
  subtitle: string;
  body: string;
  pack: string;
  category: string;
  tags: string[];
  intensity: number;
  modes: string[];
  interactionType: string;
  durationMin: number;
  moods: string[];
  requiredMutualPreferences: string[];
  boostedByPreferences: string[];
  excludedByBoundaries: string[];
  chapterKinds: string[];
  noveltyWeight: number;
  repeatCooldownDays: number;
  requiresMedia: string | null;
  status: string;
  locale: string;
  createdAt: string;
  updatedAt: string;
}

/** One step of a game's round (BUILD_PROMPT.md section 11.1): a primitive, and who plays it. */
export interface GameStep {
  primitive: string;
  who: string;
  private?: boolean;
  /** "PROMPT" for the round's prompt, or the words to show. */
  text?: string;
  /** "PROMPT", "STATEMENTS", or a fixed list of option ids. */
  options?: string | string[];
  answer?: string;
  /** Earlier steps this one refers to: what is predicted, compared or chosen between. */
  of?: number[];
  outcome?: string;
  /** "PROMPT" or a number of seconds. */
  seconds?: string | number;
  /** The earlier CHOICE whose answer picks the prompt's kind (truth or dare). */
  kindFrom?: number;
  /** The dice a DICE step rolls: each with its faces, and at most one of them a clock. */
  dice?: GameDie[];
  /** What a MEDIA step records: a kind from the vocabulary. */
  media?: string;
}

/** A game (section 11.2): a declarative composition of primitives over its prompts. */
export interface GameInfo {
  id: string;
  prefix: string;
  file: string;
  title: string;
  tagline: string;
  description: string;
  modes: string[];
  rounds: number;
  turns: string;
  scoring: string;
  promptKinds: string[];
  /** How many options each prompt carries: [min, max]. */
  promptOptions: [number, number];
  minPrompts: number;
  steps: GameStep[];
}

/** One die of a DICE step. `seconds` runs parallel to `faces`: the clock each face gives. */
export interface GameDie {
  label: string;
  faces: string | string[];
  seconds?: number[];
}

/** A game prompt: what a round is about. Filtered by the same chokepoint as items (section 3.2). */
export interface GamePrompt {
  id: string;
  version: number;
  game: string;
  kind: string | null;
  text: string;
  options: string[];
  seconds: number | null;
  category: string;
  tags: string[];
  intensity: number;
  modes: string[];
  moods: string[];
  requiredMutualPreferences: string[];
  boostedByPreferences: string[];
  excludedByBoundaries: string[];
  noveltyWeight: number;
  repeatCooldownDays: number;
  status: string;
  locale: string;
  createdAt: string;
  updatedAt: string;
}

export interface PackInfo {
  id: string;
  prefix: string;
  file: string;
  title: string;
  description: string;
  minItems: number;
  intensity: [number, number];
  minPerIntensity: number;
}

export interface Vocabulary {
  moods: string[];
  interactionTypes: string[];
  chapterKinds: string[];
  media: string[];
  statuses: string[];
  tags: string[];
  boundaryKeywords: Array<{ pattern: string; themes: string[] }>;
  games: {
    primitives: string[];
    who: string[];
    answers: string[];
    outcomes: string[];
    turns: string[];
    scoring: string[];
    voteOptions: string[];
    promptKinds: string[];
  };
}

export interface Policy {
  groups: Array<{ reason: string; patterns: string[] }>;
}

export interface TaxonomyItem {
  id: string;
  intensityFloor: number;
  modes: string[];
}

export interface Taxonomy {
  version: number;
  categories: Array<{ id: string; themes: Array<{ id: string; items: TaxonomyItem[] }> }>;
}

/** Everything the validator and bundler read. */
export interface ContentRepo {
  root: string;
  contentVersion: number;
  packs: PackInfo[];
  /** pack id → its file's raw items (whatever shape they are in). */
  itemsByPack: Map<string, unknown[]>;
  games: GameInfo[];
  /** game id → its prompt file's raw prompts (whatever shape they are in). */
  promptsByGame: Map<string, unknown[]>;
  vocabulary: Vocabulary;
  policy: Policy;
  taxonomy: Taxonomy;
}

function readJson<T>(path: string): T {
  return JSON.parse(readFileSync(path, 'utf8')) as T;
}

export function loadContent(root: string): ContentRepo {
  const packs = readJson<{ packs: PackInfo[] }>(join(root, 'packs.json')).packs;
  const itemsByPack = new Map<string, unknown[]>();
  for (const pack of packs) {
    const file = readJson<{ items?: unknown[] }>(join(root, 'packs', pack.file));
    itemsByPack.set(pack.id, Array.isArray(file.items) ? file.items : []);
  }
  const games = readJson<{ games: GameInfo[] }>(join(root, 'games.json')).games;
  const promptsByGame = new Map<string, unknown[]>();
  for (const game of games) {
    const file = readJson<{ items?: unknown[] }>(join(root, 'prompts', game.file));
    promptsByGame.set(game.id, Array.isArray(file.items) ? file.items : []);
  }
  return {
    root,
    contentVersion: readJson<{ contentVersion: number }>(join(root, 'content.json')).contentVersion,
    packs,
    itemsByPack,
    games,
    promptsByGame,
    vocabulary: readJson<Vocabulary>(join(root, 'vocabulary.json')),
    policy: readJson<Policy>(join(root, 'policy', 'prohibited-terms.json')),
    taxonomy: readJson<Taxonomy>(join(root, 'taxonomy.json')),
  };
}

/** The repository's `content/` directory, from the tools package. */
export const DEFAULT_CONTENT_ROOT = join(__dirname, '..', '..', 'content');
