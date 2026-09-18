/**
 * The content bundle (BUILD_PROMPT.md section 9.2): every published item, plus the taxonomy
 * (section 9.5), compiled into one versioned file. The app ships it for first launch and
 * offline use, and downloads newer ones from Storage when Remote Config's `content_version`
 * moves on — which is also how the taxonomy is updated without a release.
 *
 * Serialized deterministically — fixed key order, one item per line — so the committed copy
 * diffs readably and "is it stale?" is a plain string comparison.
 */
import { join } from 'node:path';
import type { ContentItem, ContentRepo, Taxonomy } from './content';

export const BUNDLE_FORMAT = 1;

/** Where the committed bundle lives, relative to the content root. */
export const BUNDLE_FILE = join('dist', 'bundle.json');

export interface Bundle {
  format: number;
  contentVersion: number;
  taxonomyVersion: number;
  packs: Array<{ id: string; title: string; description: string; intensity: [number, number] }>;
  /** content/taxonomy.json, verbatim. */
  taxonomy: Taxonomy;
  items: ContentItem[];
}

const ITEM_KEYS: Array<keyof ContentItem> = [
  'id', 'version', 'pack', 'category', 'title', 'subtitle', 'body', 'tags', 'intensity', 'modes',
  'interactionType', 'durationMin', 'moods', 'requiredMutualPreferences', 'boostedByPreferences',
  'excludedByBoundaries', 'chapterKinds', 'noveltyWeight', 'repeatCooldownDays', 'requiresMedia',
  'status', 'locale', 'createdAt', 'updatedAt',
];

export function buildBundle(repo: ContentRepo): Bundle {
  const items: ContentItem[] = [];
  for (const pack of repo.packs) {
    const published = ((repo.itemsByPack.get(pack.id) ?? []) as ContentItem[])
      .filter((item) => item.status === 'published')
      .sort((a, b) => a.id.localeCompare(b.id));
    items.push(...published);
  }
  return {
    format: BUNDLE_FORMAT,
    contentVersion: repo.contentVersion,
    taxonomyVersion: repo.taxonomy.version,
    packs: repo.packs.map((pack) => ({
      id: pack.id,
      title: pack.title,
      description: pack.description,
      intensity: pack.intensity,
    })),
    taxonomy: repo.taxonomy,
    items,
  };
}

export function serializeBundle(bundle: Bundle): string {
  const item = (value: ContentItem) =>
    JSON.stringify(Object.fromEntries(ITEM_KEYS.map((key) => [key, value[key]])));
  return [
    '{',
    `  "format": ${bundle.format},`,
    `  "contentVersion": ${bundle.contentVersion},`,
    `  "taxonomyVersion": ${bundle.taxonomyVersion},`,
    '  "packs": [',
    bundle.packs.map((pack) => `    ${JSON.stringify(pack)}`).join(',\n') + '\n  ],',
    // One line: the readable, reviewed copy is content/taxonomy.json itself.
    `  "taxonomy": ${JSON.stringify(bundle.taxonomy)},`,
    '  "items": [',
    bundle.items.map((value) => `    ${item(value)}`).join(',\n'),
    '  ]',
    '}',
    '',
  ].join('\n');
}
