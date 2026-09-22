/**
 * How a reaction moves a partner's affinity (functions/src/feedback/affinity.ts): bounded,
 * quick to learn and slow to forget, and fail-safe on anything malformed.
 */
import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { AFFINITY_WINDOW, dimensionsOf, nextAffinity } from '../feedback/affinity';
import { fixtureCatalogue } from './fixtures/engineFixtures';

describe('affinity from feedback', () => {
  it('scores against exactly the engine\'s affinity keys', () => {
    const item = fixtureCatalogue()[0];
    assert.deepEqual(dimensionsOf(item), [
      `category:${item.category}`,
      `interaction:${item.interactionType}`,
      ...item.moods.map((mood) => `mood:${mood}`),
    ]);
  });

  it('learns from the very first reaction', () => {
    assert.deepEqual(nextAffinity(undefined, 'LOVED'), { score: 1, samples: 1 });
    assert.deepEqual(nextAffinity(undefined, 'NOT_FOR_US'), { score: -1, samples: 1 });
    assert.deepEqual(nextAffinity(undefined, 'FINE'), { score: 0, samples: 1 });
  });

  it('is a running mean of the reactions so far', () => {
    let state = nextAffinity(undefined, 'LOVED');
    state = nextAffinity(state, 'NOT_FOR_US');
    assert.equal(state.score, 0);
    state = nextAffinity(state, 'LOVED');
    assert.ok(Math.abs(state.score - 1 / 3) < 1e-12);
    assert.equal(state.samples, 3);
  });

  it('never lets one night swing a long history', () => {
    let state = { score: 1, samples: 500 };
    state = nextAffinity(state, 'NOT_FOR_US');
    assert.ok(Math.abs(state.score - (1 - 2 / AFFINITY_WINDOW)) < 1e-12);
  });

  it('stays inside [-1, 1] and survives malformed stored values', () => {
    for (const reaction of ['LOVED', 'FINE', 'NOT_FOR_US'] as const) {
      for (const current of [undefined, { score: 7, samples: 3 }, { score: Number.NaN, samples: -2 }, { score: -9, samples: 1.5 }]) {
        const next = nextAffinity(current, reaction);
        assert.ok(next.score >= -1 && next.score <= 1, `${reaction} from ${JSON.stringify(current)}`);
        assert.ok(Number.isInteger(next.samples) && next.samples >= 1);
      }
    }
  });
});
