/**
 * The legal transitions of a session (BUILD_PROMPT.md section 12), as a pure table.
 *
 * One phone or two. On one phone (DECISIONS.md D-059) a chapter is played and the host moves
 * the session on: the Phase 10 path, unchanged. On two (D-074) each partner answers on their
 * own phone, and the chapter is only over when both have:
 *
 *   WAITING_FOR_PARTNER ──every "I'm in"──▶ READY ──start──▶ CHAPTER_ACTIVE(0)
 *   CHAPTER_ACTIVE(i) ──one submits──▶ AWAITING_SUBMISSIONS ──the last submits──▶ BOTH_SUBMITTED
 *     ──the reveal is written──▶ REVEAL ──both have seen it──▶ CHAPTER_COMPLETE
 *   CHAPTER_ACTIVE(i) or CHAPTER_COMPLETE(i) ──advance──▶ CHAPTER_ACTIVE(i + 1) …
 *     ──advance from the last──▶ COMPLETED
 *   any live state ──pause──▶ PAUSED ──resume──▶ the state it paused
 *   any live state ──stop──▶ ABANDONED          WAITING_FOR_PARTNER ──decline──▶ ABANDONED
 *
 * Every event is idempotent: a retry of one that already happened changes nothing and is not
 * an error, so a client that lost a reply can simply send it again. Anything else outside the
 * table is illegal, and the callable rejects it with a typed error (section 12.2).
 */
import { expectedAnswerers, type AnsweredBy } from './answerers';

export const SESSION_STATES = [
  'WAITING_FOR_PARTNER',
  'READY',
  'CHAPTER_ACTIVE',
  'AWAITING_SUBMISSIONS',
  'BOTH_SUBMITTED',
  'REVEAL',
  'CHAPTER_COMPLETE',
  'PAUSED',
  'COMPLETED',
  'ABANDONED',
] as const;
export type SessionState = (typeof SESSION_STATES)[number];

/** The states a chapter is under way in: what a pause returns to, and what an advance leaves. */
const IN_CHAPTER: ReadonlySet<SessionState> = new Set([
  'CHAPTER_ACTIVE',
  'AWAITING_SUBMISSIONS',
  'BOTH_SUBMITTED',
  'REVEAL',
  'CHAPTER_COMPLETE',
]);

export function isInChapter(state: SessionState): boolean {
  return IN_CHAPTER.has(state);
}

export const END_REASONS = ['COMPLETED', 'STOPPED', 'DECLINED', 'EXPIRED'] as const;
export type EndReason = (typeof END_REASONS)[number];

const TERMINAL: ReadonlySet<SessionState> = new Set(['COMPLETED', 'ABANDONED']);

export function isTerminal(state: SessionState): boolean {
  return TERMINAL.has(state);
}

export type SessionEvent =
  | { type: 'ACK'; uid: string }
  | { type: 'START' }
  /** A partner's answer for the chapter they are on, from their own phone (two phones). */
  | { type: 'SUBMIT'; uid: string; chapter: number }
  /** The reveal document for the chapter is written: both may see it. */
  | { type: 'REVEAL_READY'; chapter: number }
  /** A partner has seen the reveal. */
  | { type: 'SEEN'; uid: string; chapter: number }
  | { type: 'ADVANCE'; fromChapter: number }
  | { type: 'PAUSE' }
  | { type: 'RESUME' }
  | { type: 'STOP' }
  | { type: 'DECLINE' }
  | { type: 'EXPIRE' };

/** What the table needs to know about a session. */
export interface SessionSnapshot {
  state: SessionState;
  pausedFrom: SessionState | null;
  /** -1 before the first chapter. */
  currentChapter: number;
  chapterCount: number;
  consentRequired: boolean;
  /** Members who have said "I'm in". */
  acked: readonly string[];
  members: readonly string[];
  /** Members who have answered the chapter they are on (two phones). */
  submitted?: readonly string[];
  /**
   * Who each chapter asks: both of them, or whoever's turn it is.
   *
   * Decided from the game's own steps when the experience is built, and copied onto the session.
   * Absent on every session made before a game of turns could be played apart, which reads as
   * BOTH — the behaviour those sessions were created under.
   */
  answeredBy?: AnsweredBy;
  /** The phone that opened the session: seat 0, and so the active player in chapter 0. */
  hostUid?: string | null;
  /** Members who have seen its reveal. */
  seen?: readonly string[];
}

export interface Transition {
  state: SessionState;
  pausedFrom: SessionState | null;
  currentChapter: number;
  /** Chapters this transition finished playing: the night's history records them. */
  played: number[];
  endReason: EndReason | null;
  /** A member whose "I'm in" this transition records. */
  ack: string | null;
  /** A member whose answer for [currentChapter] this transition records. */
  submit?: string | null;
  /** A member whose "I've seen the reveal" this transition records. */
  seen?: string | null;
  /** Whether this transition starts a chapter, clearing who answered and saw the last one. */
  clears?: boolean;
}

export type Step = { status: 'changed'; to: Transition } | { status: 'unchanged' } | { status: 'illegal' };

const UNCHANGED: Step = { status: 'unchanged' };
const ILLEGAL: Step = { status: 'illegal' };

function changed(from: SessionSnapshot, to: Partial<Transition>): Step {
  return {
    status: 'changed',
    to: {
      state: from.state,
      pausedFrom: from.pausedFrom,
      currentChapter: from.currentChapter,
      played: [],
      endReason: null,
      ack: null,
      ...to,
    },
  };
}

/** Everyone but [uid] has already done something. */
function othersDone(s: SessionSnapshot, uid: string, done: readonly string[]): boolean {
  return s.members.every((member) => member === uid || done.includes(member));
}

/**
 * Everyone this chapter *asked*, but [uid], has already answered.
 *
 * The difference from [othersDone] is the whole of one-sided play: a chapter that asks one of them
 * is complete when that one has answered, rather than waiting for a second answer the game never
 * asks anybody for.
 */
function expectedDone(s: SessionSnapshot, uid: string, done: readonly string[]): boolean {
  const expected = expectedAnswerers(s.members, s.hostUid ?? null, s.currentChapter, s.answeredBy);
  return expected.every((member) => member === uid || done.includes(member));
}

/** The state a session is really in, looking through a pause. */
function underlying(s: SessionSnapshot): SessionState {
  return s.state === 'PAUSED' && s.pausedFrom !== null ? s.pausedFrom : s.state;
}

/** Ending mid-chapter still counts that chapter as played, for cooldowns. */
function inProgress(s: SessionSnapshot): number[] {
  return isInChapter(underlying(s)) && s.currentChapter >= 0 ? [s.currentChapter] : [];
}

export function step(s: SessionSnapshot, event: SessionEvent): Step {
  const terminal = isTerminal(s.state);
  switch (event.type) {
    case 'ACK': {
      if (!s.members.includes(event.uid)) return ILLEGAL;
      const already = s.acked.includes(event.uid);
      if (s.state !== 'WAITING_FOR_PARTNER') return already || (!terminal && !s.consentRequired) ? UNCHANGED : ILLEGAL;
      if (already) return UNCHANGED;
      const everyone = s.members.every((uid) => uid === event.uid || s.acked.includes(uid));
      return changed(s, { state: everyone ? 'READY' : 'WAITING_FOR_PARTNER', ack: event.uid });
    }
    case 'START': {
      if (s.state === 'READY') return changed(s, { state: 'CHAPTER_ACTIVE', currentChapter: 0, clears: true });
      // Already started: a retry.
      return s.currentChapter >= 0 && (s.state !== 'PAUSED' || isInChapter(s.pausedFrom ?? 'READY')) ? UNCHANGED : ILLEGAL;
    }
    case 'SUBMIT': {
      if (!s.members.includes(event.uid)) return ILLEGAL;
      if (terminal || s.state === 'PAUSED' || event.chapter !== s.currentChapter) return ILLEGAL;
      const submitted = s.submitted ?? [];
      // A retry of an answer already recorded, or one sent after the reveal it belongs to.
      if (submitted.includes(event.uid)) return UNCHANGED;
      if (s.state !== 'CHAPTER_ACTIVE' && s.state !== 'AWAITING_SUBMISSIONS') return ILLEGAL;
      // "BOTH_SUBMITTED" now means "everyone this chapter asked": for a game of turns that is one
      // of them, and the reveal goes out as soon as they have answered.
      const last = expectedDone(s, event.uid, submitted);
      return changed(s, { state: last ? 'BOTH_SUBMITTED' : 'AWAITING_SUBMISSIONS', submit: event.uid });
    }
    case 'REVEAL_READY': {
      if (event.chapter !== s.currentChapter) return ILLEGAL;
      if (s.state === 'BOTH_SUBMITTED') return changed(s, { state: 'REVEAL' });
      // The reveal is already out, or the chapter moved on without it.
      return s.state === 'REVEAL' || s.state === 'CHAPTER_COMPLETE' ? UNCHANGED : ILLEGAL;
    }
    case 'SEEN': {
      if (!s.members.includes(event.uid)) return ILLEGAL;
      if (event.chapter !== s.currentChapter) return ILLEGAL;
      const seen = s.seen ?? [];
      if (seen.includes(event.uid)) return UNCHANGED;
      if (s.state !== 'REVEAL') return terminal || s.state === 'CHAPTER_COMPLETE' ? UNCHANGED : ILLEGAL;
      const last = othersDone(s, event.uid, seen);
      return changed(s, { state: last ? 'CHAPTER_COMPLETE' : 'REVEAL', seen: event.uid });
    }
    case 'ADVANCE': {
      const from = event.fromChapter;
      if (!Number.isInteger(from) || from < 0 || from >= s.chapterCount) return ILLEGAL;
      if (isInChapter(s.state) && s.currentChapter === from) {
        const last = from === s.chapterCount - 1;
        return last
          ? changed(s, { state: 'COMPLETED', played: [from], endReason: 'COMPLETED' })
          : changed(s, { state: 'CHAPTER_ACTIVE', currentChapter: from + 1, played: [from], clears: true });
      }
      // A retry of an advance that already happened.
      if (s.currentChapter > from || (s.state === 'COMPLETED' && s.currentChapter === from)) return UNCHANGED;
      return ILLEGAL;
    }
    case 'PAUSE':
      if (s.state === 'PAUSED') return UNCHANGED;
      return terminal ? ILLEGAL : changed(s, { state: 'PAUSED', pausedFrom: s.state });
    case 'RESUME':
      if (s.state === 'PAUSED') return changed(s, { state: s.pausedFrom ?? 'READY', pausedFrom: null });
      return terminal ? ILLEGAL : UNCHANGED;
    case 'STOP':
      if (terminal) return UNCHANGED;
      return changed(s, { state: 'ABANDONED', pausedFrom: null, played: inProgress(s), endReason: 'STOPPED' });
    case 'DECLINE':
      if (terminal) return UNCHANGED;
      if (underlying(s) !== 'WAITING_FOR_PARTNER') return ILLEGAL;
      return changed(s, { state: 'ABANDONED', pausedFrom: null, endReason: 'DECLINED' });
    case 'EXPIRE':
      if (terminal) return UNCHANGED;
      return changed(s, { state: 'ABANDONED', pausedFrom: null, played: inProgress(s), endReason: 'EXPIRED' });
  }
}
