/**
 * The content the server's engine chooses from (BUILD_PROMPT.md section 9.2): the same bundle
 * the app has, with the same admin deltas laid over it by the same rule — so the server never
 * picks an item a phone could not show, and a disable reaches the engine as fast as the app.
 *
 * - The bundle deployed with these functions (copied from content/dist at build time) is the
 *   floor, so the engine works with nothing published at all.
 * - A newer published bundle, when `contentMeta/pointer` names one, is read from Storage once
 *   per instance and kept.
 * - Deltas (`content/{id}`) are re-read at most once a minute.
 *
 * Only pipeline.ts's caller (experience/generate.ts) may load this; the architecture test
 * holds everything else to going through the chokepoint.
 */
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import type { Firestore } from 'firebase-admin/firestore';
import { getStorage } from 'firebase-admin/storage';
import type { EngineItem, EnginePrompt, GameDefinition } from './types';

export const SUPPORTED_BUNDLE_FORMAT = 1;
export const POINTER_DOC = 'contentMeta/pointer';
export const DELTA_COLLECTION = 'content';
const DELTA_CACHE_MS = 60_000;
const KNOWN_STATUSES = new Set(['draft', 'published', 'disabled', 'archived']);

export interface Catalogue {
  contentVersion: number;
  items: EngineItem[];
  /** The games (section 11), and their prompts as the chokepoint reads them. */
  games: GameDefinition[];
  prompts: EnginePrompt[];
}

/** A prompt as the bundle carries it: the fields an item has for the chokepoint, and its game. */
type BundledPrompt = Omit<EnginePrompt, 'pack' | 'interactionType' | 'durationMin' | 'chapterKinds' | 'requiresMedia'>;

interface Bundle {
  format: number;
  contentVersion: number;
  items: EngineItem[];
  /** Absent from bundles published before games existed. */
  games?: GameDefinition[];
  prompts?: BundledPrompt[];
}

export interface Delta {
  id: string;
  status: string;
  version: number;
  /** An edited or added item — or prompt, which says which game it belongs to. */
  item: EngineItem | BundledPrompt | null;
}

let deployedBundle: Bundle | undefined;
const published = new Map<number, Bundle>();
let deltaCache: { at: number; deltas: Delta[] } | undefined;

/** For tests: forget everything cached in this instance. */
export function resetCatalogueCache(): void {
  deployedBundle = undefined;
  published.clear();
  deltaCache = undefined;
}

function deployed(): Bundle {
  deployedBundle ??= JSON.parse(readFileSync(join(__dirname, '..', 'content', 'bundle.json'), 'utf8')) as Bundle;
  return deployedBundle;
}

async function fromStorage(version: number): Promise<Bundle | undefined> {
  try {
    const [bytes] = await getStorage().bucket().file(`content/bundles/v${version}/bundle.json`).download();
    const bundle = JSON.parse(bytes.toString('utf8')) as Bundle;
    // A bundle must be the version it was published as, in a format this code reads.
    const optional = (value: unknown) => value === undefined || Array.isArray(value);
    if (
      bundle.format !== SUPPORTED_BUNDLE_FORMAT ||
      bundle.contentVersion !== version ||
      !Array.isArray(bundle.items) ||
      !optional(bundle.games) ||
      !optional(bundle.prompts)
    ) {
      return undefined;
    }
    return bundle;
  } catch {
    return undefined;
  }
}

/**
 * A prompt as the chokepoint reads it. A prompt has no pack, chapter or media of its own: its
 * game stands in for the pack, and it fits no night chapter, so a night can never pick one.
 */
export function asEnginePrompt(prompt: BundledPrompt): EnginePrompt {
  return { ...prompt, pack: prompt.game, interactionType: 'GAME', durationMin: 0, chapterKinds: [], requiresMedia: null };
}

function isPrompt(entry: EngineItem | BundledPrompt): entry is BundledPrompt {
  return typeof (entry as { game?: unknown }).game === 'string';
}

/**
 * [entries] with the deltas laid over them — the same rule as the app's ContentRepository: a
 * delta applies to its entry's bundled version and earlier; an edited entry replaces the
 * bundled copy; an unknown status hides the entry (it may be a disable); an entry only a
 * delta carries is added when published. [carried] reads a delta's copy, or rejects one that
 * belongs to the other collection (a prompt is never an item, nor an item a prompt).
 */
function overlay<T extends { id: string; version: number; status: string }>(
  entries: readonly T[],
  deltas: readonly Delta[],
  carried: (entry: EngineItem | BundledPrompt) => T | undefined,
): T[] {
  const byId = new Map(deltas.map((delta) => [delta.id, delta]));
  const bundled = new Set(entries.map((entry) => entry.id));
  const statusOf = (delta: Delta) => (KNOWN_STATUSES.has(delta.status) ? delta.status : 'disabled');
  const copyOf = (delta: Delta) => (delta.item === null ? undefined : carried(delta.item));

  const overlaid = entries.map((entry) => {
    const delta = byId.get(entry.id);
    if (!delta || delta.version < entry.version) return entry;
    return { ...(copyOf(delta) ?? entry), status: statusOf(delta) };
  });
  const added = deltas
    .filter((delta) => !bundled.has(delta.id) && statusOf(delta) === 'published')
    .flatMap((delta) => {
      const copy = copyOf(delta);
      return copy === undefined ? [] : [{ ...copy, status: 'published' }];
    });
  return [...overlaid, ...added];
}

/** The bundle's items with the deltas laid over them (see overlay). */
export function applyDeltas(items: readonly EngineItem[], deltas: readonly Delta[]): EngineItem[] {
  return overlay(items, deltas, (entry) => (isPrompt(entry) ? undefined : entry));
}

/** The bundle's prompts with the deltas laid over them, by the same rule as items. */
export function applyPromptDeltas(prompts: readonly EnginePrompt[], deltas: readonly Delta[]): EnginePrompt[] {
  return overlay(prompts, deltas, (entry) => (isPrompt(entry) ? asEnginePrompt(entry) : undefined));
}

async function deltas(db: Firestore, nowMs: number): Promise<Delta[]> {
  if (deltaCache && nowMs - deltaCache.at < DELTA_CACHE_MS) return deltaCache.deltas;
  const snapshot = await db.collection(DELTA_COLLECTION).get();
  const fresh = snapshot.docs.map((doc) => ({
    id: doc.id,
    status: String(doc.get('status')),
    version: Number(doc.get('version')),
    item: (doc.get('item') as EngineItem | BundledPrompt | undefined) ?? null,
  }));
  deltaCache = { at: nowMs, deltas: fresh };
  return fresh;
}

export async function loadCatalogue(db: Firestore, nowMs: number): Promise<Catalogue> {
  let bundle = deployed();
  const pointer = (await db.doc(POINTER_DOC).get()).get('version');
  if (typeof pointer === 'number' && pointer > bundle.contentVersion) {
    const newer = published.get(pointer) ?? (await fromStorage(pointer));
    if (newer) {
      published.set(pointer, newer);
      bundle = newer;
    }
  }
  const current = await deltas(db, nowMs);
  return {
    contentVersion: bundle.contentVersion,
    items: applyDeltas(bundle.items, current),
    games: bundle.games ?? [],
    prompts: applyPromptDeltas((bundle.prompts ?? []).map(asEnginePrompt), current),
  };
}
