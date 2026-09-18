/**
 * The boundary intersection (BUILD_PROMPT.md sections 3.2, 5.4, 10.3) — the Phase 6 exit
 * criterion "intersection unit-tested". Pure logic, no emulator.
 */
import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  DEFAULT_CONTENT_LEVEL,
  FILTERS_VERSION,
  computeEngineFilters,
  contentLevelOf,
  type MemberInput,
} from '../engine/filters';

function member(uid: string, overrides: Partial<MemberInput> = {}): MemberInput {
  return { uid, contentLevel: 3, boundaries: {}, neverItems: [], ...overrides };
}

const alex = (overrides: Partial<MemberInput> = {}) => member('alex', overrides);
const sam = (overrides: Partial<MemberInput> = {}) => member('sam', overrides);

describe('computeEngineFilters', () => {
  it('lets everything through when nobody has set a boundary', () => {
    const filters = computeEngineFilters([alex(), sam()]);

    assert.deepEqual(filters.excludedThemes, []);
    assert.deepEqual(filters.askFirstThemes, {});
    assert.deepEqual(filters.curiousThemes, []);
    assert.deepEqual(filters.excludedItems, []);
    assert.equal(filters.version, FILTERS_VERSION);
  });

  it("excludes a theme on either partner's NEVER, whatever the other said", () => {
    for (const other of ['ALWAYS_OK', 'CURIOUS', 'ASK_FIRST', 'NOT_TONIGHT', 'NEVER']) {
      const filters = computeEngineFilters([
        alex({ boundaries: { sensory_blindfold: 'NEVER' } }),
        sam({ boundaries: { sensory_blindfold: other } }),
      ]);
      assert.deepEqual(filters.excludedThemes, ['sensory_blindfold'], `partner said ${other}`);
    }
  });

  it('is symmetric: the same boundaries give the same filters whichever partner holds them', () => {
    const a = computeEngineFilters([alex({ boundaries: { power_negotiated: 'NEVER' } }), sam()]);
    const b = computeEngineFilters([alex(), sam({ boundaries: { power_negotiated: 'NEVER' } })]);

    assert.deepEqual(a.excludedThemes, b.excludedThemes);
  });

  it('excludes NOT_TONIGHT as firmly as NEVER', () => {
    const filters = computeEngineFilters([alex({ boundaries: { roleplay_strangers: 'NOT_TONIGHT' } }), sam()]);

    assert.deepEqual(filters.excludedThemes, ['roleplay_strangers']);
  });

  it('fails closed: a level it does not recognise excludes the theme', () => {
    const filters = computeEngineFilters([
      alex({ boundaries: { a: 'MAYBE_LATER', b: 5, c: null, d: '' } }),
      sam(),
    ]);

    assert.deepEqual(filters.excludedThemes, ['a', 'b', 'c', 'd']);
  });

  it('flags ASK_FIRST with who must be asked, and never for an excluded theme', () => {
    const filters = computeEngineFilters([
      alex({ boundaries: { teasing_instructions: 'ASK_FIRST', power_yielding: 'ASK_FIRST' } }),
      sam({ boundaries: { teasing_instructions: 'ASK_FIRST', power_yielding: 'NEVER' } }),
    ]);

    assert.deepEqual(filters.askFirstThemes, { teasing_instructions: ['alex', 'sam'] });
    assert.deepEqual(filters.excludedThemes, ['power_yielding']);
  });

  it('marks a theme curious when someone leans in and nobody excluded it', () => {
    const filters = computeEngineFilters([
      alex({ boundaries: { roleplay_characters: 'CURIOUS', sensory_touch: 'CURIOUS' } }),
      sam({ boundaries: { sensory_touch: 'NEVER' } }),
    ]);

    assert.deepEqual(filters.curiousThemes, ['roleplay_characters']);
  });

  it("caps intensity at the more careful partner's content level", () => {
    assert.equal(computeEngineFilters([alex({ contentLevel: 5 }), sam({ contentLevel: 2 })]).maxIntensity, 2);
    assert.equal(computeEngineFilters([alex({ contentLevel: 4 }), sam({ contentLevel: 4 })]).maxIntensity, 4);
  });

  it('removes items either partner answered NEVER, once each', () => {
    const filters = computeEngineFilters([
      alex({ neverItems: ['mood_intense', 'sensory_temperature'] }),
      sam({ neverItems: ['mood_intense'] }),
    ]);

    assert.deepEqual(filters.excludedItems, ['mood_intense', 'sensory_temperature']);
  });

  it('is deterministic: the same inputs in any order give identical output', () => {
    const inputs = [
      alex({ boundaries: { z: 'NEVER', a: 'ASK_FIRST', m: 'CURIOUS' }, neverItems: ['q', 'b'] }),
      sam({ boundaries: { a: 'ASK_FIRST', k: 'NOT_TONIGHT' }, neverItems: ['c'] }),
    ];

    assert.deepEqual(computeEngineFilters(inputs), computeEngineFilters([...inputs].reverse()));
  });

  it('refuses to compute for nobody', () => {
    assert.throws(() => computeEngineFilters([]));
  });
});

describe('contentLevelOf', () => {
  it('reads a valid level as itself', () => {
    for (const level of [1, 2, 3, 4, 5]) assert.equal(contentLevelOf(level), level);
  });

  it('defaults a missing level', () => {
    assert.equal(contentLevelOf(undefined), DEFAULT_CONTENT_LEVEL);
    assert.equal(contentLevelOf(null), DEFAULT_CONTENT_LEVEL);
  });

  it('fails closed on anything malformed: the lowest level, never a higher one', () => {
    for (const bad of [0, 6, 99, -1, 2.5, '5', true, {}]) assert.equal(contentLevelOf(bad), 1, String(bad));
  });
});
