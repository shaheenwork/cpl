/**
 * Who a round expects an answer from (BUILD_PROMPT.md section 12).
 *
 * The session protocol reveals a chapter once everyone it asked has answered. For most games that
 * is both of them — each partner answers privately and the two answers are shown together. For a
 * game of turns it is **one** of them: Truth or Dare asks whoever's turn it is and nothing of the
 * other, so a chapter that waited for two answers would wait for ever. That is exactly what two
 * paired phones did: the game ran on one and the other said it was elsewhere.
 *
 * The rule is read off the game's own steps rather than configured, so content decides it:
 *
 * - a step that **asks** for something — a choice, a prediction, a vote, words — is an answer one
 *   phone has and the other cannot know;
 * - if a step like that belongs to both of them (EACH, or a TOGETHER step they each take), the
 *   chapter expects BOTH;
 * - if one belongs to the active player and another to the other player, it expects BOTH as well:
 *   Hot or Not asks one to answer and the other to guess, which is one answer each;
 * - otherwise only the active player is asked, and the chapter is ACTIVE.
 *
 * Nothing about privacy changes. A chapter that expects one answer can only be a chapter where
 * nothing is being kept from anybody — the other partner has nothing to compare it against —
 * while a private answer always belongs to a game where both answer, so it is still held until
 * the second one lands.
 */

/** A step as the bundle carries it: enough of one to say whether it asks for an answer. */
export interface GameStepShape {
  primitive: string;
  who: string;
  answer?: string | null;
}

export const ANSWERED_BY = ['BOTH', 'ACTIVE'] as const;
export type AnsweredBy = (typeof ANSWERED_BY)[number];

/** Primitives whose answer is the answering phone's to give, and the other phone's to be told. */
const ASKS = new Set(['CHOICE', 'PREDICTION', 'VOTE']);

function asksForAnAnswer(step: GameStepShape): boolean {
  if (ASKS.has(step.primitive)) return true;
  return step.primitive === 'QUESTION' && step.answer !== 'SPOKEN';
}

/** Who each round of [game] asks. A game with no steps at all is treated as asking both. */
export function answeredBy(game: { steps?: readonly GameStepShape[] }): AnsweredBy {
  const asked = (game.steps ?? []).filter(asksForAnAnswer);
  if (asked.length === 0) return 'ACTIVE';
  const both = asked.some((step) => step.who === 'EACH' || step.who === 'TOGETHER');
  const each = asked.some((step) => step.who === 'ACTIVE') && asked.some((step) => step.who === 'OTHER');
  return both || each ? 'BOTH' : 'ACTIVE';
}

/**
 * The members a chapter is waiting on.
 *
 * Seat 0 is the host — the phone that opened the session — and turns alternate from the first
 * chapter, which is the same arithmetic the interpreter does on each phone. Both have to agree
 * about whose turn it is, so neither of them decides: it falls out of the chapter number.
 */
export function expectedAnswerers(
  members: readonly string[],
  hostUid: string | null,
  chapter: number,
  mode: AnsweredBy | undefined,
): readonly string[] {
  if (mode !== 'ACTIVE' || members.length !== 2 || chapter < 0) return members;
  const host = hostUid !== null && members.includes(hostUid) ? hostUid : members[0];
  const seats = [host, members.find((member) => member !== host) ?? host];
  return [seats[chapter % 2]];
}
