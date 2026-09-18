import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { test } from 'node:test';
import { BUNDLE_FILE, buildBundle, serializeBundle } from '../bundle';
import { type ContentItem, DEFAULT_CONTENT_ROOT, loadContent } from '../content';

const repo = loadContent(DEFAULT_CONTENT_ROOT);

test('the committed bundle is exactly what the packs build', () => {
  const committed = readFileSync(join(DEFAULT_CONTENT_ROOT, BUNDLE_FILE), 'utf8').replace(/\r\n/g, '\n');
  assert.equal(committed, serializeBundle(buildBundle(repo)));
});

test('serialization is deterministic and valid JSON', () => {
  const text = serializeBundle(buildBundle(repo));
  assert.equal(text, serializeBundle(buildBundle(loadContent(DEFAULT_CONTENT_ROOT))));
  const parsed = JSON.parse(text);
  assert.equal(parsed.format, 1);
  assert.equal(parsed.contentVersion, repo.contentVersion);
  assert.equal(parsed.items.length, buildBundle(repo).items.length);
});

test('ships only published items, pack by pack, sorted by id within each', () => {
  const itemsByPack = new Map(repo.itemsByPack);
  const flirt = (repo.itemsByPack.get('FLIRT') ?? []) as ContentItem[];
  itemsByPack.set('FLIRT', flirt.map((item, index) => (index === 0 ? { ...item, status: 'draft' } : item)));

  const bundle = buildBundle({ ...repo, itemsByPack });
  assert.ok(!bundle.items.some((item) => item.id === flirt[0].id));
  assert.ok(bundle.items.every((item) => item.status === 'published'));

  const packOrder = repo.packs.map((pack) => pack.id);
  for (let i = 1; i < bundle.items.length; i++) {
    const previous = bundle.items[i - 1];
    const current = bundle.items[i];
    const byPack = packOrder.indexOf(previous.pack) - packOrder.indexOf(current.pack);
    assert.ok(byPack < 0 || (byPack === 0 && previous.id < current.id), `${previous.id} before ${current.id}`);
  }
});

test('carries the taxonomy verbatim, so it updates with the content', () => {
  const bundle = JSON.parse(serializeBundle(buildBundle(repo)));
  assert.deepEqual(bundle.taxonomy, JSON.parse(readFileSync(join(DEFAULT_CONTENT_ROOT, 'taxonomy.json'), 'utf8')));
  assert.equal(bundle.taxonomyVersion, bundle.taxonomy.version);
});
