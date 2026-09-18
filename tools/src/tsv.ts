/**
 * Bulk import from a spreadsheet (BUILD_PROMPT.md section 9.6): one item per row, exported
 * as tab-separated values with a header row naming the fields.
 *
 * List fields are comma-separated in their cell. Columns an author rarely touches may be
 * left out and get the same defaults the authoring scripts use. Imported rows default to
 * `draft`: nothing reaches a phone until someone has read it and set it to `published`.
 */
import type { ContentItem } from './content';

const REQUIRED = [
  'id', 'title', 'subtitle', 'body', 'pack', 'category', 'tags', 'intensity', 'modes',
  'interactionType', 'durationMin', 'moods', 'excludedByBoundaries', 'chapterKinds',
] as const;

const LISTS = new Set([
  'tags', 'modes', 'moods', 'requiredMutualPreferences', 'boostedByPreferences', 'excludedByBoundaries', 'chapterKinds',
]);

const NUMBERS = new Set(['version', 'intensity', 'durationMin', 'noveltyWeight', 'repeatCooldownDays']);

/** Spreadsheets quote a cell that contains a quote, doubling the quotes inside it. */
function unquote(cell: string): string {
  const trimmed = cell.trim();
  if (trimmed.length >= 2 && trimmed.startsWith('"') && trimmed.endsWith('"')) {
    return trimmed.slice(1, -1).replace(/""/g, '"');
  }
  return trimmed;
}

export function parseTsv(text: string, now: string): ContentItem[] {
  const rows = text
    .replace(/^﻿/, '')
    .split(/\r?\n/)
    .filter((line) => line.trim().length > 0)
    .map((line) => line.split('\t').map(unquote));
  if (rows.length === 0) return [];

  const [header, ...body] = rows;
  const missing = REQUIRED.filter((column) => !header.includes(column));
  if (missing.length > 0) throw new Error(`missing column(s): ${missing.join(', ')}`);

  return body.map((cells, index) => {
    if (cells.length > header.length) throw new Error(`row ${index + 2}: more cells than columns`);
    const raw: Record<string, unknown> = {};
    header.forEach((column, position) => {
      const cell = cells[position] ?? '';
      if (LISTS.has(column)) {
        raw[column] = cell === '' ? [] : cell.split(',').map((entry) => entry.trim()).filter((entry) => entry !== '');
      } else if (NUMBERS.has(column)) {
        raw[column] = cell === '' ? undefined : Number(cell);
      } else if (column === 'requiresMedia') {
        raw[column] = cell === '' ? null : cell;
      } else {
        raw[column] = cell === '' ? undefined : cell;
      }
    });

    const intensity = raw.intensity as number;
    return {
      id: raw.id as string,
      version: (raw.version as number | undefined) ?? 1,
      title: raw.title as string,
      subtitle: raw.subtitle as string,
      body: raw.body as string,
      pack: raw.pack as string,
      category: raw.category as string,
      tags: raw.tags as string[],
      intensity,
      modes: raw.modes as string[],
      interactionType: raw.interactionType as string,
      durationMin: raw.durationMin as number,
      moods: raw.moods as string[],
      requiredMutualPreferences: (raw.requiredMutualPreferences as string[] | undefined) ?? [],
      boostedByPreferences: (raw.boostedByPreferences as string[] | undefined) ?? [],
      excludedByBoundaries: raw.excludedByBoundaries as string[],
      chapterKinds: raw.chapterKinds as string[],
      noveltyWeight: (raw.noveltyWeight as number | undefined) ?? Math.round((0.3 + 0.1 * intensity) * 100) / 100,
      repeatCooldownDays: (raw.repeatCooldownDays as number | undefined) ?? 21,
      requiresMedia: (raw.requiresMedia as string | null | undefined) ?? null,
      status: (raw.status as string | undefined) ?? 'draft',
      locale: (raw.locale as string | undefined) ?? 'en',
      createdAt: (raw.createdAt as string | undefined) ?? now,
      updatedAt: (raw.updatedAt as string | undefined) ?? now,
    };
  });
}
