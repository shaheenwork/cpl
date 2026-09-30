/**
 * The catalogue as a spreadsheet, both ways (BUILD_PROMPT.md section 9.6): one item or prompt
 * per row, tab-separated, with a header row naming the fields.
 *
 * List fields are comma-separated in their cell. Columns an author rarely touches may be left
 * out of an import and get the same defaults the authoring scripts use. A row imported without
 * a `status` column is a `draft`: nothing reaches a phone until someone has read it and set it
 * to `published`. An export writes every column, so what comes back out goes back in unchanged.
 */
import type { ContentItem, GamePrompt } from './content';

const REQUIRED = [
  'id', 'title', 'subtitle', 'body', 'pack', 'category', 'tags', 'intensity', 'modes',
  'interactionType', 'durationMin', 'moods', 'excludedByBoundaries', 'chapterKinds',
] as const;

const LISTS = new Set([
  'tags', 'modes', 'moods', 'requiredMutualPreferences', 'boostedByPreferences',
  'excludedByBoundaries', 'chapterKinds', 'options',
]);

/**
 * What separates the entries of a list cell.
 *
 * Commas read best and every closed-vocabulary list uses them — but a prompt's options are
 * free text, and forty-nine of the shipped ones have a comma inside a single option ("Both, an
 * hour each"). A cell holding one of those is written with pipes instead, and a cell is read
 * the way it was written: pipes if it has any, commas otherwise.
 */
const PIPE = ' | ';

const NUMBERS = new Set(['version', 'intensity', 'durationMin', 'noveltyWeight', 'repeatCooldownDays', 'seconds']);

/** What a prompt row must carry. A prompt has no pack, no duration and no chapter kinds. */
const PROMPT_REQUIRED = ['id', 'game', 'text', 'category', 'tags', 'intensity', 'modes', 'excludedByBoundaries'] as const;

/** Every column an export writes, in the order an author reads them. */
export const ITEM_COLUMNS: Array<keyof ContentItem> = [
  'id', 'version', 'title', 'subtitle', 'body', 'pack', 'category', 'tags', 'intensity', 'modes',
  'interactionType', 'durationMin', 'moods', 'requiredMutualPreferences', 'boostedByPreferences',
  'excludedByBoundaries', 'chapterKinds', 'noveltyWeight', 'repeatCooldownDays', 'requiresMedia',
  'status', 'locale', 'createdAt', 'updatedAt',
];

export const PROMPT_COLUMNS: Array<keyof GamePrompt> = [
  'id', 'version', 'game', 'kind', 'text', 'options', 'seconds', 'category', 'tags', 'intensity',
  'modes', 'moods', 'requiredMutualPreferences', 'boostedByPreferences', 'excludedByBoundaries',
  'noveltyWeight', 'repeatCooldownDays', 'status', 'locale', 'createdAt', 'updatedAt',
];

/** Spreadsheets quote a cell that contains a quote, doubling the quotes inside it. */
function unquote(cell: string): string {
  const trimmed = cell.trim();
  if (trimmed.length >= 2 && trimmed.startsWith('"') && trimmed.endsWith('"')) {
    return trimmed.slice(1, -1).replace(/""/g, '"');
  }
  return trimmed;
}

/** The header and the rows under it, or null for an empty file. */
function read(text: string): { header: string[]; body: string[][] } | null {
  const rows = text
    .replace(/^﻿/, '')
    .split(/\r?\n/)
    .filter((line) => line.trim().length > 0)
    .map((line) => line.split('\t').map(unquote));
  if (rows.length === 0) return null;
  const [header, ...body] = rows;
  return { header: header as string[], body: body as string[][] };
}

/** One row as named fields: lists split on commas, numbers parsed, an empty cell left unset. */
function fields(header: string[], cells: string[], index: number): Record<string, unknown> {
  if (cells.length > header.length) throw new Error(`row ${index + 2}: more cells than columns`);
  const raw: Record<string, unknown> = {};
  header.forEach((column, position) => {
    const cell = cells[position] ?? '';
    if (LISTS.has(column)) {
      const parts = cell === '' ? [] : cell.split(cell.includes('|') ? '|' : ',');
      raw[column] = parts.map((entry) => entry.trim()).filter((entry) => entry !== '');
    } else if (NUMBERS.has(column)) {
      raw[column] = cell === '' ? undefined : Number(cell);
    } else if (column === 'requiresMedia') {
      raw[column] = cell === '' ? null : cell;
    } else {
      raw[column] = cell === '' ? undefined : cell;
    }
  });
  return raw;
}

export function parseTsv(text: string, now: string): ContentItem[] {
  const table = read(text);
  if (table === null) return [];
  const { header, body } = table;
  const missing = REQUIRED.filter((column) => !header.includes(column));
  if (missing.length > 0) throw new Error(`missing column(s): ${missing.join(', ')}`);

  return body.map((cells, index) => {
    const raw = fields(header, cells, index);
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

/**
 * One cell, as a spreadsheet reads it.
 *
 * A tab or a newline inside a cell would end the column or the row, so neither may survive: the
 * authored text has neither, and anything that somehow does is written as a space rather than
 * quietly splitting the file. A quote is doubled inside quotes, which is what [unquote] undoes.
 */
function cell(value: unknown): string {
  const text = Array.isArray(value)
    ? value.join(value.some((entry) => String(entry).includes(',')) ? PIPE : ', ')
    : value === null || value === undefined
      ? ''
      : String(value);
  const flat = text.replace(/[\t\r\n]+/g, ' ');
  return flat.includes('"') ? `"${flat.replace(/"/g, '""')}"` : flat;
}

function rows(columns: readonly string[], records: ReadonlyArray<object>): string {
  // A byte-order mark, so a spreadsheet opens the file as UTF-8 and writes it back as UTF-8.
  // Without it Excel reads an em dash or a curly quote in the system codepage and saves the
  // damage, which then imports as real content. `read` strips it again on the way in.
  const lines = [`\ufeff${columns.join('\t')}`];
  for (const record of records) {
    const named = record as Record<string, unknown>;
    lines.push(columns.map((column) => cell(named[column])).join('\t'));
  }
  return `${lines.join('\n')}\n`;
}

/** The items as a spreadsheet: every column, so importing what comes out changes nothing. */
export function itemsToTsv(items: ReadonlyArray<ContentItem>): string {
  return rows(ITEM_COLUMNS as readonly string[], items);
}

/** The game prompts as a spreadsheet, on the same terms. */
export function promptsToTsv(prompts: ReadonlyArray<GamePrompt>): string {
  return rows(PROMPT_COLUMNS as readonly string[], prompts);
}

/** Whether a spreadsheet holds prompts rather than items: a prompt names its game. */
export function isPromptTsv(text: string): boolean {
  const header = text.replace(/^\ufeff/, '').split(/\r?\n/, 1)[0] ?? '';
  const columns = header.split('\t').map((column) => column.trim());
  return columns.includes('game') && !columns.includes('pack');
}

/** Game prompts read back from a spreadsheet, on the same terms as [parseTsv]. */
export function parsePromptTsv(text: string, now: string): GamePrompt[] {
  const table = read(text);
  if (table === null) return [];
  const { header, body } = table;
  const missing = PROMPT_REQUIRED.filter((column) => !header.includes(column));
  if (missing.length > 0) throw new Error(`missing column(s): ${missing.join(', ')}`);

  return body.map((cells, index) => {
    const raw = fields(header, cells, index);
    const intensity = raw.intensity as number;
    return {
      id: raw.id as string,
      version: (raw.version as number | undefined) ?? 1,
      game: raw.game as string,
      kind: (raw.kind as string | undefined) ?? null,
      text: raw.text as string,
      options: (raw.options as string[] | undefined) ?? [],
      seconds: (raw.seconds as number | undefined) ?? null,
      category: raw.category as string,
      tags: raw.tags as string[],
      intensity,
      modes: raw.modes as string[],
      moods: (raw.moods as string[] | undefined) ?? [],
      requiredMutualPreferences: (raw.requiredMutualPreferences as string[] | undefined) ?? [],
      boostedByPreferences: (raw.boostedByPreferences as string[] | undefined) ?? [],
      excludedByBoundaries: raw.excludedByBoundaries as string[],
      noveltyWeight: (raw.noveltyWeight as number | undefined) ?? Math.round((0.3 + 0.1 * intensity) * 100) / 100,
      repeatCooldownDays: (raw.repeatCooldownDays as number | undefined) ?? 21,
      status: (raw.status as string | undefined) ?? 'draft',
      locale: (raw.locale as string | undefined) ?? 'en',
      createdAt: (raw.createdAt as string | undefined) ?? now,
      updatedAt: (raw.updatedAt as string | undefined) ?? now,
    };
  });
}
