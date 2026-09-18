import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { CODE_LENGTH, generateCode, isWellFormed } from '../pairing/code';
import { VERIFICATION_LENGTH, VERIFICATION_SYMBOLS, generateVerification } from '../pairing/verification';

describe('generateCode', () => {
  it('is always six digits, leading zeros included', () => {
    for (let i = 0; i < 2000; i++) {
      const code = generateCode();
      assert.equal(code.length, CODE_LENGTH);
      assert.ok(/^[0-9]{6}$/.test(code), code);
    }
  });

  it('spreads across the space rather than clustering', () => {
    const seen = new Set(Array.from({ length: 2000 }, generateCode));
    // 2000 draws from a million values: a handful of repeats at most.
    assert.ok(seen.size > 1990, `only ${seen.size} distinct codes`);
  });
});

describe('isWellFormed', () => {
  it('accepts exactly six digits', () => {
    assert.ok(isWellFormed('000123'));
    assert.ok(isWellFormed('987654'));
  });

  it('rejects anything else', () => {
    for (const bad of ['12345', '1234567', '12a456', ' 123456', '123456 ', '', null, undefined, 123456, {}]) {
      assert.equal(isWellFormed(bad), false, String(bad));
    }
  });
});

describe('generateVerification', () => {
  it('draws the right number of symbols from the safe set', () => {
    for (let i = 0; i < 500; i++) {
      const verification = generateVerification();
      assert.equal(verification.length, VERIFICATION_LENGTH);
      for (const symbol of verification) assert.ok(VERIFICATION_SYMBOLS.includes(symbol), symbol);
    }
  });

  it('has no duplicate symbols in the set, so every draw is distinguishable', () => {
    assert.equal(new Set(VERIFICATION_SYMBOLS).size, VERIFICATION_SYMBOLS.length);
  });
});
