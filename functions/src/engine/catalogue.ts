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
import type { EngineItem } from './types';

export const SUPPORTED_BUNDLE_FORMAT = 1;
export const POINTER_DOC = 'contentMeta/pointer';
export const DELTA_COLLECTION = 'content';
const DELTA_CACHE_MS = 60_000;
const KNOWN_STATUSES = new Set(['draft', 'published', 'disabled', 'archived']);

export interface Catalogue {
  contentVersion: number;
  items: EngineItem[];
}

interface Bundle {
  format: number;
  contentVersion: number;
  items: EngineItem[];
}

export interface Delta {
  id: string;
  status: string;
  version: number;
  item: EngineItem | null;
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
    if (bundle.format !== SUPPORTED_BUNDLE_FORMAT || bundle.contentVersion !== version || !Array.isArray(bundle.items)) {
      return undefined;
    }
    return bundle;
  } catch {
    return undefined;
  }
}

/**
 * The bundle with the deltas laid over it — the same rule as the app's ContentRepository: a
 * delta applies to its item's bundled version and earlier; an edited item replaces the
 * bundled copy; an unknown status hides the item (it may be a disable); an item only a delta
 * carries is added when published.
 */
export function applyDeltas(items: readonly EngineItem[], deltas: readonly Delta[]): EngineItem[] {
  const byId = new Map(deltas.map((delta) => [delta.id, delta]));
  const bundled = new Set(items.map((item) => item.id));
  const statusOf = (delta: Delta) => (KNOWN_STATUSES.has(delta.status) ? delta.status : 'disabled');

  const overlaid = items.map((item) => {
    const delta = byId.get(item.id);
    if (!delta || delta.version < item.version) return item;
    return { ...(delta.item ?? item), status: statusOf(delta) };
  });
  const added = deltas
    .filter((delta) => !bundled.has(delta.id) && delta.item !== null && statusOf(delta) === 'published')
    .map((delta) => ({ ...(delta.item as EngineItem), status: 'published' }));
  return [...overlaid, ...added];
}

async function deltas(db: Firestore, nowMs: number): Promise<Delta[]> {
  if (deltaCache && nowMs - deltaCache.at < DELTA_CACHE_MS) return deltaCache.deltas;
  const snapshot = await db.collection(DELTA_COLLECTION).get();
  const fresh = snapshot.docs.map((doc) => ({
    id: doc.id,
    status: String(doc.get('status')),
    version: Number(doc.get('version')),
    item: (doc.get('item') as EngineItem | undefined) ?? null,
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
  return { contentVersion: bundle.contentVersion, items: applyDeltas(bundle.items, await deltas(db, nowMs)) };
}
