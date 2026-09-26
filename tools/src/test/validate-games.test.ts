/**
 * The games gate catches what it claims to (BUILD_PROMPT.md sections 9.4, 11). As with items,
 * each case breaks one thing in the real catalogue and asserts that exactly that is reported.
 */
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { type ContentRepo, DEFAULT_CONTENT_ROOT, type GameInfo, type GamePrompt, loadContent } from '../content';
import { validateContent } from '../validate';

const repo = loadContent(DEFAULT_CONTENT_ROOT);
const baseline = new Set(validateContent(repo).problems);

function introducedBy(changed: ContentRepo): string[] {
  return validateContent(changed).problems.filter((line) => !baseline.has(line));
}

function game(id: string): GameInfo {
  const found = repo.games.find((candidate) => candidate.id === id);
  assert.ok(found, `no game ${id}`);
  return structuredClone(found);
}

function prompts(gameId: string): GamePrompt[] {
  return structuredClone((repo.promptsByGame.get(gameId) ?? []) as GamePrompt[]);
}

/** The catalogue with one game's definition replaced. */
function withGame(changed: GameInfo): ContentRepo {
  return { ...repo, games: repo.games.map((original) => (original.id === changed.id ? changed : original)) };
}

/** The catalogue with one game's prompts replaced. */
function withPrompts(gameId: string, items: GamePrompt[]): ContentRepo {
  const promptsByGame = new Map(repo.promptsByGame);
  promptsByGame.set(gameId, items);
  return { ...repo, promptsByGame };
}

/** The catalogue with one prompt edited in place. */
function withPrompt(gameId: string, index: number, edit: (prompt: GamePrompt) => void): ContentRepo {
  const items = prompts(gameId);
  edit(items[index]);
  return withPrompts(gameId, items);
}

test('every game ships with a playable definition and enough prompts', () => {
  assert.deepEqual([...baseline], []);
  // A count rather than a list: the names are asserted server-side, and what matters here is
  // that no game ships without prompts enough to play it.
  assert.ok(repo.games.length >= 8, `only ${repo.games.length} games`);
  for (const each of repo.games) assert.ok((repo.promptsByGame.get(each.id) ?? []).length >= each.minPrompts, each.id);
});

test('a prompt is held to the same safety standard as an item', () => {
  const prohibited = introducedBy(withPrompt('ME_OR_YOU', 0, (prompt) => {
    prompt.text = `${prompt.text} Invite a third.`;
  }));
  assert.ok(prohibited.some((line) => line.includes('prohibited term /third/')));

  const uncovered = introducedBy(withPrompt('ME_OR_YOU', 0, (prompt) => {
    prompt.text = `${prompt.text} Blindfold on.`;
  }));
  assert.ok(uncovered.some((line) => line.includes('mentions /blindfold')));
});

test('the options are checked for safety too, not only the question', () => {
  const problems = introducedBy(withPrompt('WOULD_YOU_RATHER', 0, (prompt) => {
    prompt.options = [prompt.options[0], 'Wear a blindfold all night'];
  }));
  assert.ok(problems.some((line) => line.includes('mentions /blindfold')));
});

test('a prompt must fit its game: options, seconds and kind', () => {
  const tooFew = introducedBy(withPrompt('WOULD_YOU_RATHER', 0, (prompt) => {
    prompt.options = prompt.options.slice(0, 1);
  }));
  assert.ok(tooFew.some((line) => line.includes('needs 2..2 options')));

  const timed = introducedBy(withPrompt('DEEP_TALK', 0, (prompt) => {
    prompt.seconds = 60;
  }));
  assert.ok(timed.some((line) => line.includes('seconds must be null')));

  const untimed = introducedBy(withPrompt('COUPLE_BATTLE', 0, (prompt) => {
    prompt.seconds = null;
  }));
  assert.ok(untimed.some((line) => line.includes('seconds must be 10..180')));

  const kindless = introducedBy(withPrompt('TRUTH_OR_DARE', 0, (prompt) => {
    prompt.kind = null;
  }));
  assert.ok(kindless.some((line) => line.includes('kind must be one of TRUTH, DARE')));
});

test('a prompt id must carry its game prefix and be unique across items and prompts', () => {
  const misnamed = introducedBy(withPrompt('ME_OR_YOU', 0, (prompt) => {
    prompt.id = 'wyr_stray_prompt';
  }));
  assert.ok(misnamed.some((line) => line.includes('id must start with "meoryou_"')));

  const itemId = [...repo.itemsByPack.values()][0][0] as { id: string };
  const clash = introducedBy(withPrompt('ME_OR_YOU', 0, (prompt) => {
    prompt.id = itemId.id;
  }));
  assert.ok(clash.some((line) => line.includes('duplicate id')));
});

test('the same prompt twice is refused, but a shared stem with other options is not', () => {
  const items = prompts('WOULD_YOU_RATHER');
  const copy = structuredClone(items[0]);
  copy.id = 'wyr_copy_of_first';
  assert.ok(introducedBy(withPrompts('WOULD_YOU_RATHER', [...items, copy])).some((line) => line.includes('same prompt as')));

  const stem = structuredClone(items[0]);
  stem.id = 'wyr_same_stem';
  stem.options = ['Dance in the kitchen at midnight', 'Sing to each other on the stairs'];
  assert.deepEqual(introducedBy(withPrompts('WOULD_YOU_RATHER', [...items, stem])), []);
});

test('a game keeps its minimum per level, and draft prompts do not count', () => {
  const noWild = prompts('DEEP_TALK').filter((prompt) => prompt.intensity !== 5);
  assert.ok(introducedBy(withPrompts('DEEP_TALK', noWild)).some((line) => line.includes('DEEP_TALK: only 0 prompts at intensity 5')));

  const drafts = prompts('TRUTH_OR_DARE').map((prompt) =>
    prompt.kind === 'DARE' && prompt.intensity === 4 ? { ...prompt, status: 'draft' } : prompt,
  );
  assert.ok(introducedBy(withPrompts('TRUTH_OR_DARE', drafts)).some((line) => line.includes('only 0 DARE at intensity 4')));
});

test('a SOFT couple can play a whole game: enough prompts of every kind at level 1', () => {
  const soft = prompts('TRUTH_OR_DARE').filter((prompt) => prompt.intensity === 1 && prompt.kind === 'DARE');
  const others = prompts('TRUTH_OR_DARE').filter((prompt) => !(prompt.intensity === 1 && prompt.kind === 'DARE'));
  const keep = soft.slice(0, game('TRUTH_OR_DARE').rounds);
  const problems = introducedBy(withPrompts('TRUTH_OR_DARE', [...others, ...keep]));
  assert.ok(problems.some((line) => line.includes(`only ${keep.length} DARE at intensity 1, needs ${keep.length + 1} to fill a game`)));
});

test('a step may only refer to steps before it', () => {
  const broken = game('PREDICT_MY_ANSWER');
  broken.steps[1].of = [2];
  assert.ok(introducedBy(withGame(broken)).some((line) => line.includes('"of" may only name earlier steps')));
});

test('nobody predicts their own answer', () => {
  const broken = game('PREDICT_MY_ANSWER');
  broken.steps[1].who = broken.steps[0].who;
  assert.ok(introducedBy(withGame(broken)).some((line) => line.includes('nobody predicts their own answer')));
});

test('a reveal needs an outcome and answers to reveal', () => {
  const noOutcome = game('ME_OR_YOU');
  const reveal = noOutcome.steps.findIndex((step) => step.primitive === 'REVEAL');
  delete noOutcome.steps[reveal].outcome;
  assert.ok(introducedBy(withGame(noOutcome)).some((line) => line.includes('outcome must be one of')));

  const nothing = game('COUPLE_BATTLE');
  const battleReveal = nothing.steps.findIndex((step) => step.primitive === 'REVEAL');
  nothing.steps[battleReveal].of = [0];
  assert.ok(introducedBy(withGame(nothing)).some((line) => line.includes('reveals earlier answers')));
});

test('a game with prompt kinds must say which choice picks the kind', () => {
  const broken = game('TRUTH_OR_DARE');
  const challenge = broken.steps.findIndex((step) => step.primitive === 'CHALLENGE');
  delete broken.steps[challenge].kindFrom;
  assert.ok(introducedBy(withGame(broken)).some((line) => line.includes('must say which choice picks the kind')));
});

test('a game that never shows its prompt, or scores without a reveal, is refused', () => {
  const silent = game('DEEP_TALK');
  silent.steps = silent.steps.map((step) => ({ ...step, text: step.text === 'PROMPT' ? 'Talk.' : step.text }));
  assert.ok(introducedBy(withGame(silent)).some((line) => line.includes('no step shows the round\'s prompt')));

  const unscored = game('ME_OR_YOU');
  unscored.steps = unscored.steps.filter((step) => step.primitive !== 'REVEAL');
  assert.ok(introducedBy(withGame(unscored)).some((line) => line.includes('a scored game needs a REVEAL')));
});

test('unknown primitives, audiences and outcomes are refused', () => {
  const broken = game('ME_OR_YOU');
  broken.steps[0].primitive = 'ROULETTE';
  broken.steps[0].who = 'EVERYONE';
  const problems = introducedBy(withGame(broken));
  assert.ok(problems.some((line) => line.includes('unknown primitive')));
  assert.ok(problems.some((line) => line.includes('unknown who "EVERYONE"')));
});
