/**
 * The legal transitions of a night's session (BUILD_PROMPT.md section 12), as a pure table.
 *
 * Phase 10 plays a night on one phone (DECISIONS.md D-059), so it uses a subset of section
 * 12.1's states: no rounds, submissions or reveals yet — those arrive with realtime sessions
 * in Phase 12, which extends this table rather than replacing it.
 *
 *   WAITING_FOR_PARTNER ──every "I'm in"──▶ READY ──start──▶ CHAPTER_ACTIVE(0)
 *   CHAPTER_ACTIVE(i) ──advance──▶ CHAPTER_ACTIVE(i + 1) … ──advance from the last──▶ COMPLETED
 *   any live state ──pause──▶ PAUSED ──resume──▶ the state it paused
 *   any live state ──stop──▶ ABANDONED          WAITING_FOR_PARTNER ──decline──▶ ABANDONED
 *
 * Every event is idempotent: a retry of one that already happened changes nothing and is not
 * an error, so a client that lost a reply can simply send it again. Anything else outside the
 * table is illegal, and the callable rejects it with a typed error (section 12.2).
 */

export const SESSION_STATES = ['WAITING_FOR_PARTNER', 'READY', 'CHAPTER_ACTIVE', 'PAUSED', 'COMPLETED', 'ABANDONED'] as const;
export type SessionState = (typeof SESSION_STATES)[number];

export const END_REASONS = ['COMPLETED', 'STOPPED', 'DECLINED', 'EXPIRED'] as const;
export type EndReason = (typeof END_REASONS)[number];

const TERMINAL: ReadonlySet<SessionState> = new Set(['COMPLETED', 'ABANDONED']);

export function isTerminal(state: SessionState): boolean {
  return TERMINAL.has(state);
}

export type SessionEvent =
  | { type: 'ACK'; uid: string }
  | { type: 'START' }
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

/** The state a session is really in, looking through a pause. */
function underlying(s: SessionSnapshot): SessionState {
  return s.state === 'PAUSED' && s.pausedFrom !== null ? s.pausedFrom : s.state;
}

/** Ending mid-chapter still counts that chapter as played, for cooldowns. */
function inProgress(s: SessionSnapshot): number[] {
  return underlying(s) === 'CHAPTER_ACTIVE' && s.currentChapter >= 0 ? [s.currentChapter] : [];
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
      if (s.state === 'READY') return changed(s, { state: 'CHAPTER_ACTIVE', currentChapter: 0 });
      // Already started: a retry.
      return s.currentChapter >= 0 && (s.state !== 'PAUSED' || s.pausedFrom === 'CHAPTER_ACTIVE') ? UNCHANGED : ILLEGAL;
    }
    case 'ADVANCE': {
      const from = event.fromChapter;
      if (!Number.isInteger(from) || from < 0 || from >= s.chapterCount) return ILLEGAL;
      if (s.state === 'CHAPTER_ACTIVE' && s.currentChapter === from) {
        const last = from === s.chapterCount - 1;
        return last
          ? changed(s, { state: 'COMPLETED', played: [from], endReason: 'COMPLETED' })
          : changed(s, { currentChapter: from + 1, played: [from] });
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
