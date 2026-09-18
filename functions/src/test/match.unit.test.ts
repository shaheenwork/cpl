/**
 * Mutual matching and reveal timing (BUILD_PROMPT.md sections 5.2, 5.3, 14.7). Pure logic.
 */
import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  DEFAULT_TIMING,
  MIN_DELAY_FLOOR_MS,
  matchLevelOf,
  releaseAfterMs,
  safeTiming,
  type StoredAnswer,
} from '../discovery/match';

const open = (value: string): StoredAnswer => ({ value, secret: false });
const secret: StoredAnswer = { value: 'CURIOUS', secret: true };
const MINUTE = 60 * 1000;

describe('matchLevelOf', () => {
  it('matches only two positives, and says how', () => {
    assert.equal(matchLevelOf(open('YES'), open('YES')), 'BOTH_YES');
    assert.equal(matchLevelOf(open('CURIOUS'), open('CURIOUS')), 'BOTH_CURIOUS');
    assert.equal(matchLevelOf(open('YES'), open('MAYBE')), 'MIXED_POSITIVE');
    assert.equal(matchLevelOf(open('MAYBE'), open('CURIOUS')), 'MIXED_POSITIVE');
    assert.equal(matchLevelOf(open('MAYBE'), open('MAYBE')), 'MIXED_POSITIVE');
  });

  it('makes nothing of a positive against anything that is not', () => {
    for (const other of ['NOT_FOR_ME', 'NEVER']) {
      for (const positive of ['YES', 'CURIOUS', 'MAYBE']) {
        assert.equal(matchLevelOf(open(positive), open(other)), null, `${positive} + ${other}`);
        assert.equal(matchLevelOf(open(other), open(positive)), null, `${other} + ${positive}`);
      }
    }
  });

  it('makes nothing when either partner has not answered', () => {
    assert.equal(matchLevelOf(open('YES'), undefined), null);
    assert.equal(matchLevelOf(undefined, open('YES')), null);
  });

  it('fails closed on a value it does not recognise', () => {
    assert.equal(matchLevelOf(open('ABSOLUTELY'), open('YES')), null);
    assert.equal(matchLevelOf({ value: 1 }, open('YES')), null);
    assert.equal(matchLevelOf({}, open('YES')), null);
  });

  it('matches two secret curiosities as the secret reveal', () => {
    assert.equal(matchLevelOf(secret, secret), 'BOTH_SECRET');
  });

  it('never lets a secret surface through an open answer (D-021)', () => {
    for (const value of ['YES', 'CURIOUS', 'MAYBE']) {
      assert.equal(matchLevelOf(secret, open(value)), null, `secret + open ${value}`);
      assert.equal(matchLevelOf(open(value), secret), null, `open ${value} + secret`);
    }
  });

  it('is symmetric', () => {
    const answers = [open('YES'), open('CURIOUS'), open('MAYBE'), open('NEVER'), secret];
    for (const a of answers) for (const b of answers) assert.equal(matchLevelOf(a, b), matchLevelOf(b, a));
  });
});

describe('reveal timing', () => {
  it('releases somewhere inside the window, never at a fixed offset', () => {
    const queuedAt = Date.UTC(2026, 8, 18, 20, 0, 0);
    const offsets = new Set<number>();
    for (let i = 0; i < 200; i++) {
      const offset = releaseAfterMs(queuedAt, DEFAULT_TIMING) - queuedAt;
      assert.ok(offset >= 30 * MINUTE && offset <= 180 * MINUTE, `offset ${offset / MINUTE} min`);
      offsets.add(offset);
    }
    // 200 draws from ~9 million possible offsets: a constant, or a handful, would be a bug.
    assert.ok(offsets.size > 190, `only ${offsets.size} distinct offsets`);
  });

  it('spreads across the whole window rather than bunching at one end', () => {
    const queuedAt = 0;
    const offsets = Array.from({ length: 400 }, () => releaseAfterMs(queuedAt, DEFAULT_TIMING));
    const early = offsets.filter((o) => o < 105 * MINUTE).length; // first half of 30..180
    assert.ok(early > 120 && early < 280, `${early} of 400 in the first half`);
  });

  it('uses exactly the injected draw, so tests can pin it', () => {
    assert.equal(releaseAfterMs(1_000, DEFAULT_TIMING, (min) => min), 1_000 + 30 * MINUTE);
    assert.equal(releaseAfterMs(1_000, DEFAULT_TIMING, (_min, max) => max), 1_000 + 180 * MINUTE);
  });

  it('keeps any configuration safe: floored, ordered, never empty', () => {
    assert.deepEqual(safeTiming(0, 0), { minDelayMs: MIN_DELAY_FLOOR_MS, maxDelayMs: MIN_DELAY_FLOOR_MS });
    assert.deepEqual(safeTiming(60 * MINUTE, 10 * MINUTE), { minDelayMs: 60 * MINUTE, maxDelayMs: 60 * MINUTE });
    assert.deepEqual(safeTiming('soon', Number.NaN), DEFAULT_TIMING);
    assert.deepEqual(safeTiming(45 * MINUTE, 90 * MINUTE), { minDelayMs: 45 * MINUTE, maxDelayMs: 90 * MINUTE });
  });
});
