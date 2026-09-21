/**
 * The boundary chokepoint, held in place by the build (BUILD_PROMPT.md sections 3.2, 21.1: "an
 * architectural test proving no code path selects content without passing through the
 * chokepoint").
 *
 * Runtime tests show the chokepoint works; these show nothing goes around it. They read the
 * sources: only experience/generate.ts may load the catalogue or run the pipeline, the pipeline
 * filters before it scores, samples or assembles and re-checks what it built, and the combined
 * boundaries are read in no other place.
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

  it('only generateExperience loads the catalogue or runs the pipeline', () => {
    assert.deepEqual(importersOf('catalogue'), ['experience/generate.ts']);
    assert.deepEqual(importersOf('pipeline'), ['experience/generate.ts']);
  });

  it('nothing outside the engine reaches its ranking, sampling or assembly', () => {
    for (const internal of ['score', 'sample', 'assemble', 'plan']) {
      const outside = importersOf(internal).filter((path) => !path.startsWith('engine/'));
      assert.deepEqual(outside, [], `${internal} imported from outside the engine`);
    }
    assert.deepEqual(importersOf('score'), ['engine/pipeline.ts']);
    assert.deepEqual(importersOf('sample'), ['engine/pipeline.ts']);
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

  it('the combined boundaries are read only where they are computed, filtered or loaded', () => {
    const readers = files.filter((file) => /excludedThemes|excludedItems/.test(file.text)).map((file) => file.path).sort();
    assert.deepEqual(readers, ['engine/chokepoint.ts', 'engine/filters.ts', 'engine/types.ts', 'experience/generate.ts']);
  });
});
