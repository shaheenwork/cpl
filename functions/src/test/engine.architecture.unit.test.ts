/**
 * The boundary chokepoint, held in place by the build (BUILD_PROMPT.md sections 3.2, 21.1: "an
 * architectural test proving no code path selects content without passing through the
 * chokepoint").
 *
 * Runtime tests show the chokepoint works; these show nothing goes around it. They read the
 * sources: only experience/generate.ts may run the pipeline and only experience/game.ts may
 * choose a game, and the catalogue is loaded only through generate.ts and where feedback
 * learns what a played item was about; the pipeline and the game chooser both filter before
 * they score or pick, and re-check what they chose; and the combined boundaries are read in
 * no other place.
 */
import assert from 'node:assert/strict';
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { describe, it } from 'node:test';

const SRC = join(__dirname, '..', '..', 'src');

function sources(dir: string = SRC): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) return name === 'test' ? [] : sources(path);
    return name.endsWith('.ts') ? [path] : [];
  });
}

const files = sources().map((path) => ({
  path: relative(SRC, path).split(sep).join('/'),
  text: readFileSync(path, 'utf8'),
}));

function importersOf(module: string): string[] {
  const pattern = new RegExp(`from '(?:\\.\\./|\\./)+(?:engine/)?${module}'`);
  return files.filter((file) => pattern.test(file.text)).map((file) => file.path).sort();
}

describe('the chokepoint cannot be bypassed', () => {
  it('found the sources it guards', () => {
    assert.ok(files.some((file) => file.path === 'engine/pipeline.ts'));
    assert.ok(files.some((file) => file.path === 'experience/generate.ts'));
  });

  it('only generateExperience runs the pipeline, and nothing else selects from the catalogue', () => {
    assert.deepEqual(importersOf('pipeline'), ['experience/generate.ts']);
    // A game's rounds are chosen by the engine's game chooser, called from startGame alone.
    assert.deepEqual(importersOf('game'), ['experience/game.ts']);
    // Feedback reads the catalogue for one item's category, type and moods — a chapter the
    // couple already played — and selects nothing (DECISIONS.md D-063).
    assert.deepEqual(importersOf('catalogue'), ['experience/generate.ts', 'feedback/affinity.ts']);
  });

  it('nothing outside the engine reaches its ranking, sampling or assembly', () => {
    for (const internal of ['score', 'sample', 'assemble']) {
      const outside = importersOf(internal).filter((path) => !path.startsWith('engine/'));
      assert.deepEqual(outside, [], `${internal} imported from outside the engine`);
    }
    assert.deepEqual(importersOf('score'), ['engine/game.ts', 'engine/pipeline.ts']);
    assert.deepEqual(importersOf('sample'), ['engine/game.ts', 'engine/pipeline.ts']);

    // How many chapters a night of a given length may have is a fact about its shape, not a way
    // to plan one. The remix bound is the only place outside the engine that reads it, and it
    // reads nothing else from the planner.
    const planners = importersOf('plan').filter((path) => !path.startsWith('engine/'));
    assert.deepEqual(planners, ['experience/remix.ts']);
    const remix = files.find((file) => file.path === 'experience/remix.ts')!.text;
    assert.match(remix, /import \{ chapterRange \} from '\.\.\/engine\/plan'/);
  });

  it('prompts reach a couple only through the game chooser or the mid-game re-check', () => {
    const readers = files.filter((file) => /catalogue\.prompts/.test(file.text)).map((file) => file.path).sort();
    assert.deepEqual(readers, ['experience/game.ts', 'experience/generate.ts']);
    const startGame = files.find((file) => file.path === 'experience/game.ts')!.text;
    assert.match(startGame, /generateGame\(catalogue\.prompts,/);
  });

  it('a game is chosen the same way: filtered first, then scored and picked, then checked again', () => {
    const game = files.find((file) => file.path === 'engine/game.ts')!.text;
    const body = game.slice(game.indexOf('function chooseRounds('), game.indexOf('function pickPrompt('));
    const at = (call: string) => {
      const index = body.indexOf(call);
      assert.ok(index >= 0, `${call} missing from chooseRounds()`);
      return index;
    };
    assert.ok(at('hardFilter(') < at('scoreAll('), 'scored before filtering');
    assert.ok(at('scoreAll(') < at('pickPrompt('));
    assert.ok(at('pickPrompt(') < at('permits('), 'nothing re-checked after picking');
    assert.match(body, /scoreAll\(permitted,/);
    assert.match(body, /pickPrompt\(permitted,/);
  });

  it('the pipeline filters first, then scores, samples and assembles, then checks again', () => {
    const pipeline = files.find((file) => file.path === 'engine/pipeline.ts')!.text;
    const body = pipeline.slice(pipeline.indexOf('export function generate('));
    const at = (call: string) => {
      const index = body.indexOf(call);
      assert.ok(index >= 0, `${call} missing from generate()`);
      return index;
    };
    assert.ok(at('hardFilter(') < at('scoreAll('), 'scored before filtering');
    assert.ok(at('scoreAll(') < at('samplePool('));
    assert.ok(at('samplePool(') < at('assemble('));
    assert.ok(at('assemble(') < at('permits('), 'nothing re-checked after assembly');
    // Ranking sees only what the filter let through.
    assert.match(body, /scoreAll\(permitted,/);
    assert.match(body, /samplePool\(permitted,/);
  });

  it('every night is built by the one builder, whoever had the idea', () => {
    const callers = files.filter((file) => /buildExperience\(/.test(file.text)).map((file) => file.path).sort();
    assert.deepEqual(callers, ['experience/generate.ts', 'experience/plan.ts', 'experience/surprise.ts']);
    // And a night's record is created in one place: a surprise reads the couple's habit there,
    // but writes nothing of its own.
    const creators = files
      .filter((file) => /experiences`\)\.doc\(\)/.test(file.text))
      .map((file) => file.path)
      .sort();
    assert.deepEqual(creators, ['experience/game.ts', 'experience/generate.ts']);
  });

  it('the combined boundaries are read only where they are computed, filtered or loaded', () => {
    const readers = files.filter((file) => /excludedThemes|excludedItems/.test(file.text)).map((file) => file.path).sort();
    assert.deepEqual(readers, ['engine/chokepoint.ts', 'engine/filters.ts', 'engine/types.ts', 'experience/generate.ts']);
  });
});
