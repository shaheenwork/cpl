/**
 * The session transition table (BUILD_PROMPT.md section 12; functions/src/session/transitions.ts):
 * every legal move, every retry a no-op, and everything else refused.
 */
import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { SESSION_STATES, step, type SessionEvent, type SessionSnapshot, type SessionState, type Step } from '../session/transitions';

const MEMBERS = ['uid_a', 'uid_b'];

function session(overrides: Partial<SessionSnapshot> = {}): SessionSnapshot {
  return {
    state: 'READY',
    pausedFrom: null,
    currentChapter: -1,
    chapterCount: 4,
    consentRequired: false,
    acked: [],
    members: MEMBERS,
    ...overrides,
  };
}

function changed(result: Step) {
  assert.equal(result.status, 'changed', JSON.stringify(result));
  return (result as Extract<Step, { status: 'changed' }>).to;
}

describe('consent before a BOLD or WILD night (section 3.1)', () => {
  const waiting = session({ state: 'WAITING_FOR_PARTNER', consentRequired: true });

  it('needs every partner, each for themselves', () => {
    const first = changed(step(waiting, { type: 'ACK', uid: 'uid_a' }));
    assert.equal(first.state, 'WAITING_FOR_PARTNER');
    assert.equal(first.ack, 'uid_a');
    const second = changed(step({ ...waiting, acked: ['uid_a'] }, { type: 'ACK', uid: 'uid_b' }));
    assert.equal(second.state, 'READY');
    assert.equal(second.ack, 'uid_b');
  });

  it('cannot be given by someone outside the couple, or twice', () => {
    assert.equal(step(waiting, { type: 'ACK', uid: 'stranger' }).status, 'illegal');
    assert.equal(step({ ...waiting, acked: ['uid_a'] }, { type: 'ACK', uid: 'uid_a' }).status, 'unchanged');
  });

  it('must come before the first chapter', () => {
    assert.equal(step(waiting, { type: 'START' }).status, 'illegal');
    assert.equal(step({ ...waiting, acked: ['uid_a'] }, { type: 'START' }).status, 'illegal');
  });

  it('can be declined by either partner, which ends the night', () => {
    const declined = changed(step({ ...waiting, acked: ['uid_a'] }, { type: 'DECLINE' }));
    assert.equal(declined.state, 'ABANDONED');
    assert.equal(declined.endReason, 'DECLINED');
    assert.deepEqual(declined.played, []);
    // Once the night is under way there is nothing to decline: STOP ends it instead.
    assert.equal(step(session({ state: 'CHAPTER_ACTIVE', currentChapter: 1 }), { type: 'DECLINE' }).status, 'illegal');
  });

  it('a night that needs none has nothing to acknowledge', () => {
    assert.equal(step(session(), { type: 'ACK', uid: 'uid_a' }).status, 'unchanged');
  });
});

describe('moving through the night', () => {
  it('starts at the first chapter, once', () => {
    const started = changed(step(session(), { type: 'START' }));
    assert.equal(started.state, 'CHAPTER_ACTIVE');
    assert.equal(started.currentChapter, 0);
    assert.equal(step(session({ state: 'CHAPTER_ACTIVE', currentChapter: 0 }), { type: 'START' }).status, 'unchanged');
  });

  it('advances one chapter at a time and records what was played', () => {
    const next = changed(step(session({ state: 'CHAPTER_ACTIVE', currentChapter: 1 }), { type: 'ADVANCE', fromChapter: 1 }));
    assert.equal(next.state, 'CHAPTER_ACTIVE');
    assert.equal(next.currentChapter, 2);
    assert.deepEqual(next.played, [1]);
  });

  it('completes after the last chapter', () => {
    const done = changed(step(session({ state: 'CHAPTER_ACTIVE', currentChapter: 3 }), { type: 'ADVANCE', fromChapter: 3 }));
    assert.equal(done.state, 'COMPLETED');
    assert.equal(done.endReason, 'COMPLETED');
    assert.deepEqual(done.played, [3]);
  });

  it('treats a repeated advance as the retry it is, never a double advance', () => {
    assert.equal(step(session({ state: 'CHAPTER_ACTIVE', currentChapter: 2 }), { type: 'ADVANCE', fromChapter: 1 }).status, 'unchanged');
    assert.equal(step(session({ state: 'COMPLETED', currentChapter: 3 }), { type: 'ADVANCE', fromChapter: 3 }).status, 'unchanged');
  });

  it('refuses to skip ahead, to move a night that has not started, or to go past the end', () => {
    assert.equal(step(session({ state: 'CHAPTER_ACTIVE', currentChapter: 1 }), { type: 'ADVANCE', fromChapter: 2 }).status, 'illegal');
    assert.equal(step(session(), { type: 'ADVANCE', fromChapter: 0 }).status, 'illegal');
    assert.equal(step(session({ state: 'CHAPTER_ACTIVE', currentChapter: 3 }), { type: 'ADVANCE', fromChapter: 4 }).status, 'illegal');
    assert.equal(step(session({ state: 'CHAPTER_ACTIVE', currentChapter: 1 }), { type: 'ADVANCE', fromChapter: -1 }).status, 'illegal');
  });

  it('does not move while paused', () => {
    const paused = session({ state: 'PAUSED', pausedFrom: 'CHAPTER_ACTIVE', currentChapter: 1 });
    assert.equal(step(paused, { type: 'ADVANCE', fromChapter: 1 }).status, 'illegal');
  });
});

describe('PAUSE and STOP are always there (section 3.1)', () => {
  const live: SessionState[] = ['WAITING_FOR_PARTNER', 'READY', 'CHAPTER_ACTIVE'];

  it('pauses any live state, and resumes exactly where it was', () => {
    for (const state of live) {
      const paused = changed(step(session({ state, currentChapter: state === 'CHAPTER_ACTIVE' ? 2 : -1 }), { type: 'PAUSE' }));
      assert.equal(paused.state, 'PAUSED');
      assert.equal(paused.pausedFrom, state);
      const resumed = changed(step(session({ state: 'PAUSED', pausedFrom: state, currentChapter: paused.currentChapter }), { type: 'RESUME' }));
      assert.equal(resumed.state, state);
      assert.equal(resumed.pausedFrom, null);
      assert.equal(resumed.currentChapter, paused.currentChapter);
    }
  });

  it('treats a second pause or a stray resume as a retry', () => {
    assert.equal(step(session({ state: 'PAUSED', pausedFrom: 'READY' }), { type: 'PAUSE' }).status, 'unchanged');
    assert.equal(step(session({ state: 'CHAPTER_ACTIVE', currentChapter: 0 }), { type: 'RESUME' }).status, 'unchanged');
  });

  it('stops from every live state and from a pause, counting the chapter under way as played', () => {
    for (const state of [...live, 'PAUSED'] as SessionState[]) {
      const midChapter = state === 'CHAPTER_ACTIVE' || state === 'PAUSED';
      const stopped = changed(
        step(session({ state, pausedFrom: state === 'PAUSED' ? 'CHAPTER_ACTIVE' : null, currentChapter: midChapter ? 2 : -1 }), { type: 'STOP' }),
      );
      assert.equal(stopped.state, 'ABANDONED');
      assert.equal(stopped.endReason, 'STOPPED');
      assert.deepEqual(stopped.played, midChapter ? [2] : []);
    }
  });

  it('a second stop, or a stop after the end, changes nothing', () => {
    assert.equal(step(session({ state: 'ABANDONED', currentChapter: 2 }), { type: 'STOP' }).status, 'unchanged');
    assert.equal(step(session({ state: 'COMPLETED', currentChapter: 3 }), { type: 'STOP' }).status, 'unchanged');
  });
});

describe('two phones: each partner answers their own (section 12.1)', () => {
  const active = session({ state: 'CHAPTER_ACTIVE', currentChapter: 1 });

  it('waits for the second answer, then has both', () => {
    const first = changed(step(active, { type: 'SUBMIT', uid: 'uid_a', chapter: 1 }));
    assert.equal(first.state, 'AWAITING_SUBMISSIONS');
    assert.equal(first.submit, 'uid_a');

    const waiting = { ...active, state: 'AWAITING_SUBMISSIONS' as SessionState, submitted: ['uid_a'] };
    const both = changed(step(waiting, { type: 'SUBMIT', uid: 'uid_b', chapter: 1 }));
    assert.equal(both.state, 'BOTH_SUBMITTED');
    assert.equal(both.submit, 'uid_b');
  });

  it('takes an answer once, for the chapter on screen, from a member, while it is live', () => {
    const waiting = { ...active, state: 'AWAITING_SUBMISSIONS' as SessionState, submitted: ['uid_a'] };
    assert.equal(step(waiting, { type: 'SUBMIT', uid: 'uid_a', chapter: 1 }).status, 'unchanged');
    assert.equal(step(active, { type: 'SUBMIT', uid: 'stranger', chapter: 1 }).status, 'illegal');
    assert.equal(step(active, { type: 'SUBMIT', uid: 'uid_a', chapter: 2 }).status, 'illegal');
    assert.equal(step(active, { type: 'SUBMIT', uid: 'uid_a', chapter: 0 }).status, 'illegal');
    for (const state of ['PAUSED', 'REVEAL', 'CHAPTER_COMPLETE', 'COMPLETED', 'ABANDONED'] as SessionState[]) {
      const at = { ...active, state, pausedFrom: state === 'PAUSED' ? ('CHAPTER_ACTIVE' as SessionState) : null };
      assert.equal(step(at, { type: 'SUBMIT', uid: 'uid_a', chapter: 1 }).status, 'illegal', state);
    }
  });

  it('reveals only once both have answered, and only once', () => {
    const both = { ...active, state: 'BOTH_SUBMITTED' as SessionState, submitted: MEMBERS };
    assert.equal(changed(step(both, { type: 'REVEAL_READY', chapter: 1 })).state, 'REVEAL');
    assert.equal(step({ ...both, state: 'REVEAL' }, { type: 'REVEAL_READY', chapter: 1 }).status, 'unchanged');
    assert.equal(step(active, { type: 'REVEAL_READY', chapter: 1 }).status, 'illegal');
    assert.equal(step(both, { type: 'REVEAL_READY', chapter: 0 }).status, 'illegal');
  });

  it('is over when both have seen it, and then either partner moves it on', () => {
    const reveal = { ...active, state: 'REVEAL' as SessionState, submitted: MEMBERS };
    const first = changed(step(reveal, { type: 'SEEN', uid: 'uid_a', chapter: 1 }));
    assert.equal(first.state, 'REVEAL');
    assert.equal(first.seen, 'uid_a');
    const complete = changed(step({ ...reveal, seen: ['uid_a'] }, { type: 'SEEN', uid: 'uid_b', chapter: 1 }));
    assert.equal(complete.state, 'CHAPTER_COMPLETE');

    const next = changed(step({ ...active, state: 'CHAPTER_COMPLETE', seen: MEMBERS }, { type: 'ADVANCE', fromChapter: 1 }));
    assert.equal(next.state, 'CHAPTER_ACTIVE');
    assert.equal(next.currentChapter, 2);
    assert.deepEqual(next.played, [1]);
    assert.equal(next.clears, true, 'the next chapter starts with nobody having answered or seen');
  });

  it('a chapter that ends mid-answer still counts as played', () => {
    for (const state of ['AWAITING_SUBMISSIONS', 'BOTH_SUBMITTED', 'REVEAL', 'CHAPTER_COMPLETE'] as SessionState[]) {
      const stopped = changed(step({ ...active, state }, { type: 'STOP' }));
      assert.equal(stopped.state, 'ABANDONED');
      assert.deepEqual(stopped.played, [1], state);
    }
  });

  it('pauses and resumes wherever the chapter had got to', () => {
    for (const state of ['AWAITING_SUBMISSIONS', 'REVEAL', 'CHAPTER_COMPLETE'] as SessionState[]) {
      const paused = changed(step({ ...active, state }, { type: 'PAUSE' }));
      assert.equal(paused.state, 'PAUSED');
      assert.equal(paused.pausedFrom, state);
      assert.equal(changed(step({ ...active, state: 'PAUSED', pausedFrom: state }, { type: 'RESUME' })).state, state);
    }
  });
});

describe('an ended night stays ended', () => {
  it('refuses to pause, resume or restart once completed or abandoned', () => {
    for (const state of ['COMPLETED', 'ABANDONED'] as SessionState[]) {
      for (const event of [{ type: 'PAUSE' }, { type: 'RESUME' }] as SessionEvent[]) {
        assert.equal(step(session({ state, currentChapter: 1 }), event).status, 'illegal', `${event.type} after ${state}`);
      }
      assert.equal(step(session({ state, currentChapter: -1 }), { type: 'START' }).status, 'illegal');
    }
  });

  it('never leaves the table: every event from every state is changed, unchanged or illegal', () => {
    const events: SessionEvent[] = [
      { type: 'ACK', uid: 'uid_a' },
      { type: 'START' },
      { type: 'SUBMIT', uid: 'uid_a', chapter: 0 },
      { type: 'REVEAL_READY', chapter: 0 },
      { type: 'SEEN', uid: 'uid_a', chapter: 0 },
      { type: 'ADVANCE', fromChapter: 0 },
      { type: 'PAUSE' },
      { type: 'RESUME' },
      { type: 'STOP' },
      { type: 'DECLINE' },
      { type: 'EXPIRE' },
    ];
    for (const state of SESSION_STATES) {
      for (const event of events) {
        const result = step(session({ state, currentChapter: 0, pausedFrom: state === 'PAUSED' ? 'READY' : null }), event);
        assert.ok(['changed', 'unchanged', 'illegal'].includes(result.status));
        if (result.status === 'changed') assert.ok(SESSION_STATES.includes(result.to.state));
      }
    }
  });
});
