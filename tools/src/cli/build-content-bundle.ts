/**
 * `npm --prefix tools run bundle` — validates the packs, then writes content/dist/bundle.json.
 *
 * Refuses to build from invalid content: a bundle is what reaches phones.
 */
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { BUNDLE_FILE, buildBundle, serializeBundle } from '../bundle';
import { DEFAULT_CONTENT_ROOT, loadContent } from '../content';
import { validateContent } from '../validate';

const root = process.argv[2] ?? DEFAULT_CONTENT_ROOT;
const repo = loadContent(root);
const { problems, report } = validateContent(repo);
console.log(report);

if (problems.length > 0) {
  console.error(`\nNot building: ${problems.length} problem(s).`);
  for (const line of problems) console.error(`  ✖ ${line}`);
  process.exit(1);
}

const bundle = buildBundle(repo);
const target = join(root, BUNDLE_FILE);
mkdirSync(dirname(target), { recursive: true });
writeFileSync(target, serializeBundle(bundle), 'utf8');
console.log(`\nwrote ${BUNDLE_FILE}: content v${bundle.contentVersion}, ${bundle.items.length} items`);
