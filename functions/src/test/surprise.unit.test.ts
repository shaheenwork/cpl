/**
 * The night the couple did not configure (BUILD_PROMPT.md sections 2.4, 14.2): what the server
 * chooses for them, and what a phone is allowed to ask for — which is almost nothing.
 */
import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { MOODS } from '../engine/types';
import { ServiceError } from '../shared/errors';
import { DEFAULT_DURATION_MIN, parseSurprise, surpriseRequest, type NightHabit } from '../experience/surprise';

const noHabit: NightHabit = { durations: [], lastMoods: [] };

describe('what a phone may ask for', () => {
  it('asks for nothing at all, or only whether it is a mystery', () => {
    assert.deepEqual(parseSurprise(undefined), { mystery: false, mode: 'TOGETHER' });
    assert.deepEqual(parseSurprise({}), { mystery: false, mode: 'TOGETHER' });
    assert.deepEqual(parseSurprise({ mystery: true }), { mystery: true, mode: 'TOGETHER' });
    assert.deepEqual(parseSurprise({ mode: 'APART' }), { mystery: false, mode: 'APART' });
  });

  it('refuses anything that would let a phone steer it', () => {
    for (const bad of [{ moods: ['bold'] }, { intensity: 5 }, { seed: 1 }, { durationMin: 15 }, { mystery: 'yes' }, [], 'surprise']) {
      assert.throws(
        () => parseSurprise(bad),
        (error: unknown) => error instanceof ServiceError && error.code === 'invalid-argument',
        `accepted ${JSON.stringify(bad)}`,
      );
    }
  });
});

describe('the shape the server picks', () => {
  it('is one mood, at the ceiling the couple set, with nobody leading', () => {
    const request = surpriseRequest(1234, 3, noHabit);
    assert.equal(request.moods.length, 1);
    assert.ok(MOODS.includes(request.moods[0]));
    assert.equal(request.intensity, 3);
    assert.equal(request.leadership, 'RANDOM');
    assert.equal(request.novelty, 'SURPRISE_ME');
    assert.equal(request.durationMin, DEFAULT_DURATION_MIN);
  });

  it('never repeats the mood of the night before', () => {
    for (let seed = 0; seed < 200; seed += 1) {
      const request = surpriseRequest(seed, 5, { durations: [], lastMoods: ['bold'] });
      assert.notEqual(request.moods[0], 'bold', `seed ${seed} repeated the last mood`);
    }
  });

  it('runs as long as their nights usually do', () => {
    assert.equal(surpriseRequest(7, 5, { durations: [30, 60, 30], lastMoods: [] }).durationMin, 30);
    assert.equal(surpriseRequest(7, 5, { durations: [90], lastMoods: [] }).durationMin, 90);
  });

  it('is the same night for the same seed, and a different one for another', () => {
    assert.deepEqual(surpriseRequest(99, 4, noHabit), surpriseRequest(99, 4, noHabit));
    const moods = new Set(Array.from({ length: 50 }, (_, seed) => surpriseRequest(seed, 4, noHabit).moods[0]));
    assert.ok(moods.size > 1, 'every seed gave the same mood');
  });
});
