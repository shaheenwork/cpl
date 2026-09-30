/**
 * Reading and writing the authored pack files, content/packs/*.json.
 *
 * One item per line, in a fixed key order, so that a pull request touching one item shows
 * a one-line diff. Every tool that writes a pack file goes through serializePackFile, and a
 * test holds the committed files to exactly this format, so an import never reformats the
 * lines it did not change.
 */
import { writeFileSync } from 'node:fs';
import { join } from 'node:path';
import type { ContentItem, ContentRepo, GameInfo, GamePrompt, PackInfo } from './content';

/** The authored field order: identity, copy, classification, engine inputs, lifecycle. */
export const PACK_ITEM_KEYS: Array<keyof ContentItem> = [
  'id', 'version', 'title', 'subtitle', 'body', 'pack', 'category', 'tags', 'intensity', 'modes',
  'interactionType', 'durationMin', 'moods', 'requiredMutualPreferences', 'boostedByPreferences',
  'excludedByBoundaries', 'chapterKinds', 'noveltyWeight', 'repeatCooldownDays', 'requiresMedia',
  'status', 'locale', 'createdAt', 'updatedAt',
];

/** A prompt's field order, the same idea: identity, copy, classification, engine, lifecycle. */
export const PROMPT_KEYS: Array<keyof GamePrompt> = [
  'id', 'version', 'game', 'kind', 'text', 'options', 'seconds', 'category', 'tags', 'intensity',
  'modes', 'moods', 'requiredMutualPreferences', 'boostedByPreferences', 'excludedByBoundaries',
  'noveltyWeight', 'repeatCooldownDays', 'status', 'locale', 'createdAt', 'updatedAt',
];

/** JSON with a space after every colon and comma: the pack files' house style. */
function spaced(value: unknown): string {
  if (Array.isArray(value)) return `[${value.map(spaced).join(', ')}]`;
  if (value !== null && typeof value === 'object') {
    return `{${Object.entries(value).map(([key, entry]) => `${JSON.stringify(key)}: ${spaced(entry)}`).join(', ')}}`;
  }
  return JSON.stringify(value);
}

export function serializeItem(item: ContentItem): string {
  const ordered: Record<string, unknown> = {};
  for (const key of PACK_ITEM_KEYS) ordered[key] = item[key];
  return spaced(ordered);
}

export function serializePackFile(packId: string, items: ContentItem[]): string {
  const lines = items.map((item) => `    ${serializeItem(item)}`).join(',\n');
  return `{\n  "pack": ${JSON.stringify(packId)},\n  "items": [\n${lines}\n  ]\n}\n`;
}

export function writePackFile(repo: ContentRepo, pack: PackInfo, items: ContentItem[]): void {
  writeFileSync(join(repo.root, 'packs', pack.file), serializePackFile(pack.id, items), 'utf8');
}

export function serializePrompt(prompt: GamePrompt): string {
  const ordered: Record<string, unknown> = {};
  for (const key of PROMPT_KEYS) ordered[key] = prompt[key];
  return spaced(ordered);
}

export function serializePromptFile(gameId: string, prompts: GamePrompt[]): string {
  const lines = prompts.map((prompt) => `    ${serializePrompt(prompt)}`).join(',\n');
  return `{\n  "game": ${JSON.stringify(gameId)},\n  "items": [\n${lines}\n  ]\n}\n`;
}

export function writePromptFile(repo: ContentRepo, game: GameInfo, prompts: GamePrompt[]): void {
  writeFileSync(join(repo.root, 'prompts', game.file), serializePromptFile(game.id, prompts), 'utf8');
}

/** [repo] with [prompt] in its game's file, replacing any prompt with the same id. */
export function withPrompt(repo: ContentRepo, prompt: GamePrompt): ContentRepo {
  const promptsByGame = new Map<string, unknown[]>();
  for (const [gameId, prompts] of repo.promptsByGame) {
    promptsByGame.set(gameId, prompts.filter((existing) => (existing as GamePrompt).id !== prompt.id));
  }
  const target = promptsByGame.get(prompt.game);
  if (!target) throw new Error(`${prompt.id}: unknown game "${prompt.game}"`);
  const original = (repo.promptsByGame.get(prompt.game) ?? []) as GamePrompt[];
  const index = original.findIndex((existing) => existing.id === prompt.id);
  target.splice(index === -1 ? target.length : index, 0, prompt);
  return { ...repo, promptsByGame };
}

/**
 * [repo] with [item] inserted into its pack, replacing any item with the same id. The input
 * is not modified: callers validate the result before deciding to write it.
 */
export function withItem(repo: ContentRepo, item: ContentItem): ContentRepo {
  const itemsByPack = new Map<string, unknown[]>();
  for (const [packId, items] of repo.itemsByPack) {
    itemsByPack.set(packId, items.filter((existing) => (existing as ContentItem).id !== item.id));
  }
  const target = itemsByPack.get(item.pack);
  if (!target) throw new Error(`${item.id}: unknown pack "${item.pack}"`);
  const original = (repo.itemsByPack.get(item.pack) ?? []) as ContentItem[];
  const index = original.findIndex((existing) => existing.id === item.id);
  // An edit keeps its place in the file; a new item goes at the end.
  target.splice(index === -1 ? target.length : index, 0, item);
  return { ...repo, itemsByPack };
}
