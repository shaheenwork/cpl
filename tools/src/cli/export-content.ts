/**
 * `npm --prefix tools run export -- [--pack FLIRT] [--game TRUTH_OR_DARE] [--level 5] [--out file.tsv]`
 *
 * The catalogue as a spreadsheet (BUILD_PROMPT.md section 9.6), which is the other half of
 * `import`: what comes out here goes back in there unchanged, so editing a hundred cards is
 * editing a hundred rows rather than a hundred lines of JSON.
 *
 * Items by default; `--game` or `--prompts` exports the game prompts instead, which are a
 * different shape and live in their own files. Without `--out` it writes to stdout.
 */
import { writeFileSync } from 'node:fs';
import { type ContentItem, DEFAULT_CONTENT_ROOT, type GamePrompt, loadContent } from '../content';
import { itemsToTsv, promptsToTsv } from '../tsv';

const args = process.argv.slice(2);

function option(name: string): string | undefined {
  const flag = `--${name}`;
  const at = args.indexOf(flag);
  if (at >= 0 && args[at + 1] !== undefined && !args[at + 1].startsWith('--')) return args[at + 1];
  const inline = args.find((arg) => arg.startsWith(`${flag}=`));
  return inline?.slice(flag.length + 1);
}

const repo = loadContent(DEFAULT_CONTENT_ROOT);
const pack = option('pack');
const game = option('game');
const level = option('level');
const out = option('out');
const wantsPrompts = game !== undefined || args.includes('--prompts');
const at = level === undefined ? undefined : Number(level);

if (at !== undefined && !Number.isInteger(at)) {
  console.error(`--level must be a whole number, not "${level}"`);
  process.exit(1);
}

let text: string;
let count: number;
let what: string;

if (wantsPrompts) {
  const prompts = [...repo.promptsByGame.entries()]
    .filter(([id]) => game === undefined || id === game)
    .flatMap(([, rows]) => rows as GamePrompt[])
    .filter((prompt) => at === undefined || prompt.intensity === at);
  text = promptsToTsv(prompts);
  count = prompts.length;
  what = game === undefined ? 'prompts' : `${game} prompts`;
} else {
  const items = [...repo.itemsByPack.entries()]
    .filter(([id]) => pack === undefined || id === pack)
    .flatMap(([, rows]) => rows as ContentItem[])
    .filter((item) => at === undefined || item.intensity === at);
  text = itemsToTsv(items);
  count = items.length;
  what = pack === undefined ? 'items' : `${pack} items`;
}

if (count === 0) {
  console.error(`nothing matched: no ${what}${at === undefined ? '' : ` at intensity ${at}`}`);
  process.exit(1);
}

if (out === undefined) {
  process.stdout.write(text);
} else {
  writeFileSync(out, text, 'utf8');
  console.log(`wrote ${out}: ${count} ${what}${at === undefined ? '' : ` at intensity ${at}`}`);
  console.log('Edit it in a spreadsheet, then: npm --prefix tools run import -- ' + out);
}
