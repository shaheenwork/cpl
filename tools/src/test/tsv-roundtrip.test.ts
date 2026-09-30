/**
 * The spreadsheet round-trip (BUILD_PROMPT.md section 9.6): what `export` writes, `import` reads
 * back unchanged.
 *
 * Asserted against the real catalogue rather than a fixture, because the point of the pair is
 * bulk editing: 793 items and 322 prompts go out to a spreadsheet and come back, and anything
 * the journey quietly alters — a dash, a comma inside a list, an empty cell — would be a silent
 * edit to content nobody meant to touch.
 */
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { type ContentItem, DEFAULT_CONTENT_ROOT, type GamePrompt, loadContent } from '../content';
import { isPromptTsv, itemsToTsv, parsePromptTsv, parseTsv, promptsToTsv } from '../tsv';

const repo = loadContent(DEFAULT_CONTENT_ROOT);
const items = [...repo.itemsByPack.values()].flat() as ContentItem[];
const prompts = [...repo.promptsByGame.values()].flat() as GamePrompt[];
const NOW = '2026-01-01T00:00:00.000Z';

test('every item survives the journey to a spreadsheet and back', () => {
  assert.ok(items.length > 500, `expected the real catalogue, found ${items.length} items`);

  const back = parseTsv(itemsToTsv(items), NOW);

  assert.equal(back.length, items.length);
  assert.deepEqual(back, items);
});

test('every game prompt survives it too', () => {
  assert.ok(prompts.length > 200, `expected the real prompts, found ${prompts.length}`);

  const back = parsePromptTsv(promptsToTsv(prompts), NOW);

  assert.equal(back.length, prompts.length);
  assert.deepEqual(back, prompts);
});

test('the header says which of the two a file holds', () => {
  assert.equal(isPromptTsv(promptsToTsv(prompts)), true);
  assert.equal(isPromptTsv(itemsToTsv(items)), false);
});

test('a spreadsheet reads it as UTF-8, and the mark does not become content', () => {
  const text = itemsToTsv(items);

  // Excel opens a tab-separated file in the system codepage unless it is marked, and saves the
  // damage back. The mark is stripped on the way in, so the first column is still `id`.
  assert.ok(text.startsWith('﻿'), 'the export is marked as UTF-8');
  assert.equal(parseTsv(text, NOW)[0]?.id, items[0]?.id);
});

test('an option with a comma inside it is one option, not two', () => {
  const prompt = prompts.find((candidate) => candidate.options.some((option) => option.includes(',')));
  assert.ok(prompt, 'the catalogue has an option with a comma in it');

  const [back] = parsePromptTsv(promptsToTsv([prompt]), NOW);

  assert.deepEqual(back?.options, prompt.options);
});

test('an em dash, a quote and an empty list come back as themselves', () => {
  const awkward: ContentItem = {
    ...(items[0] as ContentItem),
    id: 'test_awkward_row',
    title: 'A "quoted" title',
    body: 'One of you hands it over — all of it — and says "when" out loud.',
    tags: ['game', 'trust'],
    requiredMutualPreferences: [],
    requiresMedia: null,
  };

  const [back] = parseTsv(itemsToTsv([awkward]), NOW);

  assert.deepEqual(back, awkward);
});
