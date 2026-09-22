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
      { type: 'ADVANCE', fromChapter: 0 },
      { type: 'PAUSE' },
      { type: 'RESUME' },
      { type: 'STOP' },
      { type: 'DECLINE' },
      { type: 'EXPIRE' },
    ];
    for (const state of SESSION_STATES) {
      for (const event of events) {
        const result = step(session({ state, pausedFrom: state === 'PAUSED' ? 'READY' : null }), event);
        assert.ok(['changed', 'unchanged', 'illegal'].includes(result.status));
        if (result.status === 'changed') assert.ok(SESSION_STATES.includes(result.to.state));
      }
    }
  });
});
