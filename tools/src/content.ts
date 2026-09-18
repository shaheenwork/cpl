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
  return {
    root,
    contentVersion: readJson<{ contentVersion: number }>(join(root, 'content.json')).contentVersion,
    packs,
    itemsByPack,
    vocabulary: readJson<Vocabulary>(join(root, 'vocabulary.json')),
    policy: readJson<Policy>(join(root, 'policy', 'prohibited-terms.json')),
    taxonomy: readJson<Taxonomy>(join(root, 'taxonomy.json')),
  };
}

/** The repository's `content/` directory, from the tools package. */
export const DEFAULT_CONTENT_ROOT = join(__dirname, '..', '..', 'content');
