/**
 * `npm --prefix tools run import -- <file.tsv> [--write]`
 *
 * Bulk import from a spreadsheet (BUILD_PROMPT.md section 9.6), and the other half of `export`.
 * Rows are merged by id into their pack or their game — a new id is appended, an existing one is
 * replaced where it stands — and then the whole catalogue is validated. Without --write it only
 * reports. With --write it saves the files, and only if the result is clean.
 *
 * Items or game prompts: the header says which, since only a prompt names its game. A row that
 * arrives without a `status` column is a draft; publishing is a separate, deliberate edit.
 */
import { readFileSync } from 'node:fs';
import { type ContentItem, DEFAULT_CONTENT_ROOT, type GamePrompt, loadContent } from '../content';
import { withItem, withPrompt, writePackFile, writePromptFile } from '../packfile';
import { isPromptTsv, parsePromptTsv, parseTsv } from '../tsv';
import { validateContent } from '../validate';

const args = process.argv.slice(2);
const write = args.includes('--write');
const file = args.find((arg) => !arg.startsWith('--'));
if (!file) {
  console.error('usage: import <file.tsv> [--write]');
  process.exit(1);
}

let repo = loadContent(DEFAULT_CONTENT_ROOT);
const text = readFileSync(file, 'utf8');
const now = new Date().toISOString();
const prompts = isPromptTsv(text);
const rows: Array<ContentItem | GamePrompt> = prompts ? parsePromptTsv(text, now) : parseTsv(text, now);
for (const row of rows) {
  repo = prompts ? withPrompt(repo, row as GamePrompt) : withItem(repo, row as ContentItem);
}

const { problems, report } = validateContent(repo);
console.log(report);
console.log(`\n${rows.length} ${prompts ? 'prompt' : 'item'} row(s) read from ${file}`);
if (problems.length > 0) {
  console.error(`\n${problems.length} problem(s), nothing written:`);
  for (const line of problems) console.error(`  ✖ ${line}`);
  process.exit(1);
}

if (!write) {
  console.log('clean. Run again with --write to save the pack files.');
} else {
  if (prompts) {
    const touched = new Set((rows as GamePrompt[]).map((row) => row.game));
    for (const game of repo.games.filter((candidate) => touched.has(candidate.id))) {
      writePromptFile(repo, game, (repo.promptsByGame.get(game.id) ?? []) as GamePrompt[]);
      console.log(`wrote prompts/${game.file}`);
    }
  } else {
    const touched = new Set((rows as ContentItem[]).map((row) => row.pack));
    for (const pack of repo.packs.filter((candidate) => touched.has(candidate.id))) {
      writePackFile(repo, pack, (repo.itemsByPack.get(pack.id) ?? []) as ContentItem[]);
      console.log(`wrote packs/${pack.file}`);
    }
  }
  console.log('Now rebuild the bundle: npm --prefix tools run bundle');
}
