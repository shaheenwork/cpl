/**
 * The validator catches what it claims to (BUILD_PROMPT.md section 9.4). Each case takes the
 * real, clean catalogue, breaks one thing, and asserts that exactly that is reported —
 * a gate is only proven by watching it fail.
 */
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { type ContentItem, type ContentRepo, DEFAULT_CONTENT_ROOT, loadContent } from '../content';
import { withItem } from '../packfile';
import { jaccard, trigrams, validateContent } from '../validate';

const repo = loadContent(DEFAULT_CONTENT_ROOT);
const baseline = new Set(validateContent(repo).problems);

function itemsOf(pack: string, from: ContentRepo = repo): ContentItem[] {
  return (from.itemsByPack.get(pack) ?? []) as ContentItem[];
}

/** A copy of a real item to break. */
function sample(pack = 'FLIRT', index = 0): ContentItem {
  return structuredClone(itemsOf(pack)[index]);
}

/** Problems the edit introduced, beyond the clean baseline. */
function introducedBy(changed: ContentRepo): string[] {
  return validateContent(changed).problems.filter((line) => !baseline.has(line));
}

function problemsFor(item: ContentItem): string[] {
  return introducedBy(withItem(repo, item));
}

test('the committed catalogue is clean', () => {
  assert.deepEqual([...baseline], []);
});

test('a prohibited term is refused, as a whole word only', () => {
  const item = sample();
  item.body = `${item.body} Then invite a third.`;
  assert.ok(problemsFor(item).some((line) => line.includes('prohibited term /third/')));

  const harmless = sample();
  harmless.body = `${harmless.body} Thirdly, smile.`;
  assert.deepEqual(problemsFor(harmless), []);
});

test('an item mentioning a sensitive theme must list a boundary that covers it', () => {
  const item = sample();
  item.body = `${item.body} Add a blindfold if you like.`;
  assert.ok(problemsFor(item).some((line) => line.includes('mentions /blindfold')));

  item.excludedByBoundaries = [...item.excludedByBoundaries, 'sensory_blindfold'];
  assert.deepEqual(problemsFor(item), []);
});

test('leaning on a preference requires being excludable by its boundary theme', () => {
  const item = sample();
  item.boostedByPreferences = [...item.boostedByPreferences, 'sensory_music'];
  assert.ok(problemsFor(item).some((line) => line.includes('leans on "sensory_music"')));
});

test('a preference that applies to none of the item modes is refused', () => {
  const item = sample('LONG_DISTANCE', 0);
  assert.deepEqual(item.modes, ['APART']);
  item.boostedByPreferences = ['sensory_massage'];
  item.excludedByBoundaries = ['sensory_touch'];
  assert.ok(problemsFor(item).some((line) => line.includes('applies to none of this item')));
});

test('an item excluded by its own required preference is refused', () => {
  const item = sample();
  assert.equal(item.intensity, 1);
  item.requiredMutualPreferences = ['mood_intense'];
  item.excludedByBoundaries = [...item.excludedByBoundaries, 'mood_charged'];
  assert.ok(problemsFor(item).some((line) => line.includes('only asked at level 4+')));
});

test('unknown tags, moods, packs and chapter kinds are refused', () => {
  const item = sample();
  item.tags = ['nonsense'];
  item.moods = ['sleepy'];
  item.chapterKinds = ['INTERMISSION'];
  const problems = problemsFor(item);
  assert.ok(problems.some((line) => line.includes('tags: unknown "nonsense"')));
  assert.ok(problems.some((line) => line.includes('moods: unknown "sleepy"')));
  assert.ok(problems.some((line) => line.includes('chapterKinds: unknown "INTERMISSION"')));

  const stray = sample();
  stray.pack = 'NOT_A_PACK';
  assert.throws(() => withItem(repo, stray), /unknown pack/);
});

test('a misspelt or missing field is refused', () => {
  const item = sample() as unknown as Record<string, unknown>;
  item.intensitty = item.intensity;
  delete item.intensity;
  const problems = problemsFor(item as unknown as ContentItem);
  assert.ok(problems.some((line) => line.includes('unknown field "intensitty"')));
  assert.ok(problems.some((line) => line.includes('missing field "intensity"')));
});

test('intensity outside the pack range is refused', () => {
  const item = sample('AFTER_DARK', 0);
  item.intensity = 2;
  assert.ok(problemsFor(item).some((line) => line.includes('intensity must be 3..5')));
});

test('a near-duplicate body is refused, even across packs', () => {
  const original = sample('FLIRT', 5);
  const copy = sample('TEASE', 3);
  copy.body = original.body.replace(/\.$/, '!');
  assert.ok(problemsFor(copy).some((line) => line.includes(`near-duplicate of`) && line.includes(original.id)));
});

test('duplicate ids are refused', () => {
  const duplicate = sample('FLIRT', 0);
  const itemsByPack = new Map(repo.itemsByPack);
  itemsByPack.set('FLIRT', [...itemsOf('FLIRT'), duplicate]);
  assert.ok(introducedBy({ ...repo, itemsByPack }).some((line) => line.includes('duplicate id')));
});

test('every pack keeps its minimum per intensity', () => {
  const itemsByPack = new Map(repo.itemsByPack);
  itemsByPack.set('FLIRT', itemsOf('FLIRT').filter((item) => item.intensity !== 5));
  const problems = introducedBy({ ...repo, itemsByPack });
  assert.ok(problems.some((line) => line.includes('FLIRT: only 0 items at intensity 5')));
});

test('items that are not published do not count towards coverage', () => {
  const itemsByPack = new Map(repo.itemsByPack);
  itemsByPack.set(
    'DEEP_TALK',
    itemsOf('DEEP_TALK').map((item) => ({ ...item, status: item.intensity === 4 ? 'draft' : item.status })),
  );
  assert.ok(introducedBy({ ...repo, itemsByPack }).some((line) => line.includes('DEEP_TALK: only 0 items at intensity 4')));
});

test('word-trigram similarity', () => {
  assert.equal(jaccard(trigrams('one two three four'), trigrams('One two three four.')), 1);
  assert.equal(jaccard(trigrams('one two three'), trigrams('four five six')), 0);
  assert.equal(jaccard(trigrams('too short'), trigrams('too short')), 0);
});
