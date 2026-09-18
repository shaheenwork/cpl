/**
 * `npm --prefix tools run import -- <file.tsv> [--write]`
 *
 * Bulk import from a spreadsheet (BUILD_PROMPT.md section 9.6). Rows are merged into their
 * packs by id, a new id is appended and an existing one is replaced where it stands, then
 * the whole catalogue is validated. Without --write it only reports. With --write it saves
 * the pack files, and only if the result is clean.
 *
 * Imported rows default to draft; publishing is a separate, deliberate edit.
 */
import { readFileSync } from 'node:fs';
import { type ContentItem, DEFAULT_CONTENT_ROOT, loadContent } from '../content';
import { withItem, writePackFile } from '../packfile';
import { parseTsv } from '../tsv';
import { validateContent } from '../validate';

const args = process.argv.slice(2);
const write = args.includes('--write');
const file = args.find((arg) => !arg.startsWith('--'));
if (!file) {
  console.error('usage: import <file.tsv> [--write]');
  process.exit(1);
}

let repo = loadContent(DEFAULT_CONTENT_ROOT);
const rows = parseTsv(readFileSync(file, 'utf8'), new Date().toISOString());
for (const row of rows) repo = withItem(repo, row);

const { problems, report } = validateContent(repo);
console.log(report);
console.log(`\n${rows.length} row(s) read from ${file}`);
if (problems.length > 0) {
  console.error(`\n${problems.length} problem(s), nothing written:`);
  for (const line of problems) console.error(`  ✖ ${line}`);
  process.exit(1);
}

if (!write) {
  console.log('clean. Run again with --write to save the pack files.');
} else {
  const touched = new Set(rows.map((row) => row.pack));
  for (const pack of repo.packs.filter((candidate) => touched.has(candidate.id))) {
    writePackFile(repo, pack, (repo.itemsByPack.get(pack.id) ?? []) as ContentItem[]);
    console.log(`wrote packs/${pack.file}`);
  }
  console.log('Now rebuild the bundle: npm --prefix tools run bundle');
}
