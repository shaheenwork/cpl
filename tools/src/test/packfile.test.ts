import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { test } from 'node:test';
import { type ContentItem, DEFAULT_CONTENT_ROOT, loadContent } from '../content';
import { serializePackFile, withItem } from '../packfile';

const repo = loadContent(DEFAULT_CONTENT_ROOT);

test('every committed pack file is already in the canonical format', () => {
  // So that an import rewriting a pack file changes only the lines it meant to.
  for (const pack of repo.packs) {
    const committed = readFileSync(join(DEFAULT_CONTENT_ROOT, 'packs', pack.file), 'utf8').replace(/\r\n/g, '\n');
    const items = (repo.itemsByPack.get(pack.id) ?? []) as ContentItem[];
    assert.equal(serializePackFile(pack.id, items), committed, pack.file);
  }
});

test('withItem replaces an item where it stands and appends a new one', () => {
  const flirt = (repo.itemsByPack.get('FLIRT') ?? []) as ContentItem[];
  const edited = { ...flirt[3], version: flirt[3].version + 1, subtitle: 'Edited.' };
  const afterEdit = withItem(repo, edited).itemsByPack.get('FLIRT') as ContentItem[];
  assert.equal(afterEdit.length, flirt.length);
  assert.equal(afterEdit[3].subtitle, 'Edited.');

  const added = { ...flirt[0], id: 'flirt_brand_new' };
  const afterAdd = withItem(repo, added).itemsByPack.get('FLIRT') as ContentItem[];
  assert.equal(afterAdd.length, flirt.length + 1);
  assert.equal(afterAdd[afterAdd.length - 1].id, 'flirt_brand_new');

  // The input repo is left alone.
  assert.equal((repo.itemsByPack.get('FLIRT') as ContentItem[])[3].subtitle, flirt[3].subtitle);
  assert.equal((repo.itemsByPack.get('FLIRT') as ContentItem[]).length, flirt.length);
});

test('moving an item to another pack removes it from the first', () => {
  const flirt = (repo.itemsByPack.get('FLIRT') ?? []) as ContentItem[];
  const moved = withItem(repo, { ...flirt[0], pack: 'TEASE' });
  assert.ok(!(moved.itemsByPack.get('FLIRT') as ContentItem[]).some((item) => item.id === flirt[0].id));
  assert.ok((moved.itemsByPack.get('TEASE') as ContentItem[]).some((item) => item.id === flirt[0].id));
});
