import assert from 'node:assert/strict';
import { test } from 'node:test';
import { parseTsv } from '../tsv';

const NOW = '2026-09-18T00:00:00.000Z';
const HEADER = [
  'id', 'title', 'subtitle', 'body', 'pack', 'category', 'tags', 'intensity', 'modes',
  'interactionType', 'durationMin', 'moods', 'excludedByBoundaries', 'chapterKinds',
].join('\t');

test('a minimal row gets the authoring defaults and lands as a draft', () => {
  const row = [
    'flirt_imported', 'Imported', 'From a sheet.', 'A body long enough to be a real item body.', 'FLIRT',
    'communication', 'flirting, compliments', '2', 'TOGETHER,APART', 'CONVERSATION', '5', 'romantic',
    'comm_flirting', 'WARM_UP',
  ].join('\t');

  const [item] = parseTsv(`${HEADER}\n${row}\n`, NOW);

  assert.equal(item.id, 'flirt_imported');
  assert.deepEqual(item.tags, ['flirting', 'compliments']);
  assert.deepEqual(item.modes, ['TOGETHER', 'APART']);
  assert.equal(item.intensity, 2);
  assert.equal(item.version, 1);
  assert.equal(item.status, 'draft');
  assert.equal(item.noveltyWeight, 0.5);
  assert.equal(item.requiresMedia, null);
  assert.deepEqual(item.boostedByPreferences, []);
  assert.equal(item.createdAt, NOW);
});

test('spreadsheet quoting is undone', () => {
  const row = [
    'flirt_quoted', 'Quoted', '"Say ""yes""."', 'A body long enough to be a real item body.', 'FLIRT',
    'communication', 'flirting', '1', 'TOGETHER', 'CONVERSATION', '5', 'romantic', 'comm_flirting', 'WARM_UP',
  ].join('\t');
  assert.equal(parseTsv(`﻿${HEADER}\r\n${row}`, NOW)[0].subtitle, 'Say "yes".');
});

test('a missing required column is refused', () => {
  assert.throws(() => parseTsv('id\ttitle\nx\ty', NOW), /missing column\(s\): subtitle/);
});
