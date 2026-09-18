/**
 * Pairing codes: pure logic, no Firebase. Kept separate so the rules that decide whether a
 * code is usable can be unit-tested without an emulator.
 *
 * BUILD_PROMPT.md section 8: a six-digit temporary code, single use, short-lived.
 */
import { randomInt } from 'node:crypto';

export const CODE_LENGTH = 6;
export const CODE_TTL_MS = 15 * 60 * 1000;

/**
 * A fresh six-digit code, leading zeros allowed.
 *
 * `crypto.randomInt` rather than `Math.random`: a predictable code generator would let
 * someone guess the next code a stranger is about to share.
 */
export function generateCode(): string {
  return randomInt(0, 10 ** CODE_LENGTH).toString().padStart(CODE_LENGTH, '0');
}

export function isWellFormed(code: unknown): code is string {
  return typeof code === 'string' && /^[0-9]{6}$/.test(code);
}
