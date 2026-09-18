/**
 * `npm --prefix tools run validate` — the content gate. Run by `./gradlew check`.
 *
 * Fails (exit 1) on any validation problem, and when the committed bundle
 * (content/dist/bundle.json) no longer matches the packs it was built from.
 */
import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { BUNDLE_FILE, buildBundle, serializeBundle } from '../bundle';
import { DEFAULT_CONTENT_ROOT, loadContent } from '../content';
import { validateContent } from '../validate';

const root = process.argv[2] ?? DEFAULT_CONTENT_ROOT;
const repo = loadContent(root);
const { problems, report } = validateContent(repo);

const bundlePath = join(root, BUNDLE_FILE);
const expected = serializeBundle(buildBundle(repo));
if (!existsSync(bundlePath)) {
  problems.push(`${BUNDLE_FILE}: missing — run: npm --prefix tools run bundle`);
} else if (readFileSync(bundlePath, 'utf8').replace(/\r\n/g, '\n') !== expected) {
  problems.push(`${BUNDLE_FILE}: stale, it no longer matches the packs — run: npm --prefix tools run bundle`);
}

console.log(report);
if (problems.length > 0) {
  console.error(`\n${problems.length} problem(s):`);
  for (const line of problems) console.error(`  ✖ ${line}`);
  process.exit(1);
}
console.log('\ncontent OK');
