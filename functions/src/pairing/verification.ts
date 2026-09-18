/**
 * The pairing handshake's verification symbols.
 *
 * Why a handshake at all: a six-digit code has a million values, and at any moment only a
 * few are live. Rate limiting slows guessing but does not stop someone with many free
 * accounts from eventually landing on a stranger's live code. If entering a code paired
 * the accounts immediately, that stranger would be joined to an unknown person — in an app
 * built around private intimacy.
 *
 * So entering a code only creates a *request*. Both phones then show the same three
 * symbols, and the person who shared the code confirms with their partner before
 * approving. A guessed code produces a request whose symbols nobody on the other end can
 * vouch for, and the creator declines it (BUILD_PROMPT.md section 8: "prevent
 * unauthorized joining").
 *
 * The symbol set is restricted to emoji from Unicode Emoji 5.0 or earlier, because
 * minSdk 26 (Android 8.0) cannot render anything newer — a verification symbol that shows
 * up as an empty box on one phone defeats the comparison.
 */
import { randomInt } from 'node:crypto';

export const VERIFICATION_SYMBOLS: readonly string[] = [
  '🌙', '🍷', '🔥', '🌹', '⭐', '🎲', '🎭', '🔑',
  '💎', '🌊', '🍒', '🎵', '🌿', '🦋', '🍓', '☕',
  '🎈', '🌸', '🍋', '🐚', '🌈', '🎹', '🍂', '🌵',
  '🐾', '🎯', '🍉', '🌻', '🍀', '🎁', '🌴', '🐙',
];

export const VERIFICATION_LENGTH = 3;

export function generateVerification(): string[] {
  return Array.from(
    { length: VERIFICATION_LENGTH },
    () => VERIFICATION_SYMBOLS[randomInt(0, VERIFICATION_SYMBOLS.length)],
  );
}
