import assert from 'node:assert/strict';
import { test } from 'node:test';
import { type ContentItem, DEFAULT_CONTENT_ROOT, loadContent } from '../content';
import { parseTarget } from '../firebase';
import { bundleObjectPath, publishableBundle } from '../publish';

const repo = loadContent(DEFAULT_CONTENT_ROOT);

test('bundles are stored per version', () => {
  assert.equal(bundleObjectPath(7), 'content/bundles/v7/bundle.json');
});

test('only the valid, current committed bundle is publishable', () => {
  const { version, text } = publishableBundle(repo);
  assert.equal(version, repo.contentVersion);
  assert.equal(JSON.parse(text).contentVersion, version);

  const itemsByPack = new Map(repo.itemsByPack);
  const flirt = (repo.itemsByPack.get('FLIRT') ?? []) as ContentItem[];
  itemsByPack.set('FLIRT', flirt.map((item, index) => (index === 0 ? { ...item, subtitle: 'Changed.' } : item)));
  assert.throws(() => publishableBundle({ ...repo, itemsByPack }), /stale/);
});

test('a target always names its project', () => {
  assert.throws(() => parseTarget(['disable', 'x']), /--project/);

  const { target, rest } = parseTarget(['disable', 'flirt_x', '--project', 'afterhours-dev-emulator', '--emulator']);
  assert.deepEqual(rest, ['disable', 'flirt_x']);
  assert.equal(target.emulator, true);
  assert.equal(target.bucket, 'afterhours-dev-emulator.firebasestorage.app');

  assert.equal(parseTarget(['--project', 'p', '--bucket', 'b']).target.bucket, 'b');
});
