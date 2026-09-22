/**
 * A night being played (BUILD_PROMPT.md sections 3.1, 8, 12): createSession, markReady,
 * advanceSession and endSession.
 *
 * Server-authoritative. The session document is written only here, inside transactions, and
 * every change is checked against the legal transitions in transitions.ts — so two taps,
 * two phones or a retried request can never double-advance a night, skip a consent, or
 * restart one that was stopped (section 12.2).
 *
 * Phase 10 plays a night on the phone of the partner who built it (DECISIONS.md D-059). The
 * other partner's phone takes part twice: to say "I'm in" to a BOLD or WILD night before its
 * first chapter (section 3.1, D-061), and to pause or stop it at any moment.
 *
 * Core logic takes (db, uid, input, nowMs) and is tested directly against the emulator; the
 * callables in index.ts only authenticate and delegate.
 */
import { FieldValue, Timestamp, type DocumentData, type DocumentReference, type Firestore, type Transaction } from 'firebase-admin/firestore';
import { activeCoupleOf, blockedNow, defaultDeps, type GenerateDeps } from '../experience/generate';
import { ServiceError } from '../shared/errors';
import { isTerminal, step, type SessionEvent, type SessionSnapshot, type SessionState, type Transition } from './transitions';

/** A session untouched for this long no longer holds the couple's place (D-059). */
export const STALE_SESSION_MS = 6 * 60 * 60 * 1000;
/** BOLD and WILD need both partners' "I'm in" (section 3.1, Appendix A). */
export const CONSENT_FROM_INTENSITY = 4;
const DOC_ID = /^[A-Za-z0-9]{1,64}$/;

export interface SessionView {
  sessionId: string;
  state: SessionState;
  currentChapter: number;
  chapterCount: number;
  consentRequired: boolean;
}

export interface AdvanceResult extends SessionView {
  /**
   * Still-to-come chapters and alternates the couple's limits no longer permit (D-062): the
   * phone swaps them out before they are shown.
   */
  blocked: string[];
}

function invalid(reason: string): never {
  throw new ServiceError('invalid-argument', reason);
}

/** A payload object holding exactly [allowed] keys, or fewer. Unknown keys are refused. */
function payload(input: unknown, allowed: readonly string[]): Record<string, unknown> {
  if (typeof input !== 'object' || input === null || Array.isArray(input)) invalid('request');
  const raw = input as Record<string, unknown>;
  for (const key of Object.keys(raw)) if (!allowed.includes(key)) invalid(`unknown field ${key}`);
  return raw;
}

function docId(value: unknown, name: string): string {
  if (typeof value !== 'string' || !DOC_ID.test(value)) invalid(name);
  return value;
}

function snapshotOf(session: DocumentData, members: readonly string[]): SessionSnapshot {
  return {
    state: session.state,
    pausedFrom: session.pausedFrom ?? null,
    currentChapter: session.currentChapter,
    chapterCount: session.chapterCount,
    consentRequired: session.consentRequired === true,
    acked: Object.keys(session.consentAcks ?? {}),
    members,
  };
}

function viewOf(sessionId: string, session: DocumentData): SessionView {
  return {
    sessionId,
    state: session.state,
    currentChapter: session.currentChapter,
    chapterCount: session.chapterCount,
    consentRequired: session.consentRequired === true,
  };
}

interface Loaded {
  coupleRef: DocumentReference;
  sessionRef: DocumentReference;
  session: DocumentData;
  members: string[];
}

/**
 * The caller's session, read inside [tx]. The couple comes from the caller's own records and
 * is re-read here, so a couple unpaired mid-request cannot keep playing.
 */
async function loadSession(db: Firestore, tx: Transaction, coupleId: string, uid: string, sessionId: string): Promise<Loaded> {
  const coupleRef = db.doc(`couples/${coupleId}`);
  const sessionRef = coupleRef.collection('sessions').doc(sessionId);
  const [couple, session] = await Promise.all([tx.get(coupleRef), tx.get(sessionRef)]);
  const members: unknown = couple.get('memberUids');
  if (couple.get('status') !== 'ACTIVE' || !Array.isArray(members) || !members.includes(uid)) {
    throw new ServiceError('failed-precondition', 'not-paired');
  }
  if (!session.exists) throw new ServiceError('not-found', 'session');
  return { coupleRef, sessionRef, session: session.data()!, members: members as string[] };
}

/**
 * Writes one transition: the session, its night's chapter states and play count, and the
 * couple's history for cooldowns. Returns what the couple document needs — its place for a
 * next night and its stats — for the caller to write once, since one transaction may end
 * one session and start another. All after the caller's reads.
 */
function applyTransition(
  tx: Transaction,
  loaded: { coupleRef: DocumentReference; sessionRef: DocumentReference; session: DocumentData },
  experienceRef: DocumentReference,
  experience: DocumentData,
  to: Transition,
  nowMs: number,
  chapters: DocumentData[] = experience.chapters ?? [],
): Record<string, unknown> {
  const now = Timestamp.fromMillis(nowMs);
  const { coupleRef, sessionRef, session } = loaded;

  const sessionUpdate: Record<string, unknown> = {
    state: to.state,
    pausedFrom: to.pausedFrom,
    currentChapter: to.currentChapter,
    updatedAt: now,
  };
  if (to.ack) {
    sessionUpdate[`consentAcks.${to.ack}`] = now;
    sessionUpdate[`participants.${to.ack}.ready`] = true;
  }
  if (session.startedAt == null && to.state === 'CHAPTER_ACTIVE') sessionUpdate.startedAt = now;
  if (isTerminal(to.state)) {
    sessionUpdate.endedAt = now;
    sessionUpdate.endReason = to.endReason;
  }
  tx.update(sessionRef, sessionUpdate);

  const playedThrough = Math.max(experience.playedThrough ?? 0, ...to.played.map((index) => index + 1));
  const live = to.state !== 'COMPLETED' && to.currentChapter >= 0;
  const nextChapters: DocumentData[] = chapters.map((chapter: DocumentData, index: number) => ({
    ...chapter,
    state: index < playedThrough ? 'DONE' : live && index === to.currentChapter ? 'ACTIVE' : 'LOCKED',
  }));
  const status = to.state === 'COMPLETED' ? 'COMPLETED' : to.state === 'ABANDONED' ? 'ABANDONED' : experience.status;
  const unchanged =
    status === experience.status &&
    playedThrough === (experience.playedThrough ?? 0) &&
    nextChapters.every((chapter, index) => {
      const before = (experience.chapters ?? [])[index];
      return before !== undefined && chapter.state === before.state && chapter.contentIds[0] === before.contentIds[0];
    });
  // A night whose document has gone has nothing to update; its session still ends.
  const exists = typeof experience.status === 'string';
  if (exists && !unchanged) tx.update(experienceRef, { chapters: nextChapters, playedThrough, status });

  const played = to.played.filter((index) => index < nextChapters.length).map((index) => nextChapters[index].contentIds[0]);
  if (played.length > 0) {
    const lastPlayed = Object.fromEntries(played.map((itemId) => [itemId, nowMs]));
    tx.set(coupleRef.collection('engineState').doc('history'), { lastPlayed }, { merge: true });
  }
  if (!isTerminal(to.state)) return {};
  const coupleUpdate: Record<string, unknown> = { activeSessionId: FieldValue.delete() };
  if (to.state === 'COMPLETED') {
    coupleUpdate['stats.nightsPlayed'] = FieldValue.increment(1);
    coupleUpdate['stats.lastPlayedAt'] = now;
  }
  return coupleUpdate;
}

/** Writes [update] to the couple document, if there is anything to write. */
function updateCouple(tx: Transaction, coupleRef: DocumentReference, update: Record<string, unknown>): void {
  if (Object.keys(update).length > 0) tx.update(coupleRef, update);
}

function rejectIllegal(): never {
  throw new ServiceError('failed-precondition', 'illegal-transition');
}

// ---------------------------------------------------------------------------------------

/**
 * Starts playing a night the caller's couple was given. One live session per couple: a
 * night already under way refuses a second (`session-in-progress`) unless it has been
 * untouched for STALE_SESSION_MS, in which case it expires. Asking again for the same night
 * returns its session.
 */
export async function createSession(db: Firestore, uid: string, input: unknown, nowMs: number): Promise<SessionView> {
  const experienceId = docId(payload(input, ['experienceId']).experienceId, 'experienceId');
  const { coupleId } = await activeCoupleOf(db, uid);

  return db.runTransaction(async (tx) => {
    const coupleRef = db.doc(`couples/${coupleId}`);
    const experienceRef = coupleRef.collection('experiences').doc(experienceId);
    const [couple, experienceSnap] = await Promise.all([tx.get(coupleRef), tx.get(experienceRef)]);
    const members: unknown = couple.get('memberUids');
    if (couple.get('status') !== 'ACTIVE' || !Array.isArray(members) || !members.includes(uid)) {
      throw new ServiceError('failed-precondition', 'not-paired');
    }
    if (!experienceSnap.exists) throw new ServiceError('not-found', 'experience');
    const experience = experienceSnap.data()!;

    // The couple's place: a live session holds it, a stale one gives it up.
    const activeId = couple.get('activeSessionId');
    let stale: { ref: DocumentReference; data: DocumentData; experienceRef: DocumentReference; experience: DocumentData } | undefined;
    if (typeof activeId === 'string') {
      const activeRef = coupleRef.collection('sessions').doc(activeId);
      const active = await tx.get(activeRef);
      if (active.exists && !isTerminal(active.get('state'))) {
        if (active.get('experienceId') === experienceId) return viewOf(activeId, active.data()!);
        const touched = (active.get('updatedAt') as Timestamp | undefined)?.toMillis() ?? 0;
        if (nowMs - touched < STALE_SESSION_MS) throw new ServiceError('failed-precondition', 'session-in-progress');
        const staleExperienceRef = coupleRef.collection('experiences').doc(active.get('experienceId'));
        const staleExperience = await tx.get(staleExperienceRef);
        stale = { ref: activeRef, data: active.data()!, experienceRef: staleExperienceRef, experience: staleExperience.data() ?? { chapters: [] } };
      }
    }
    if (experience.status !== 'READY') throw new ServiceError('failed-precondition', 'experience-not-ready');

    let coupleUpdate: Record<string, unknown> = {};
    if (stale) {
      const expired = step(snapshotOf(stale.data, members as string[]), { type: 'EXPIRE' });
      if (expired.status === 'changed') {
        coupleUpdate = applyTransition(tx, { coupleRef, sessionRef: stale.ref, session: stale.data }, stale.experienceRef, stale.experience, expired.to, nowMs);
      }
    }

    const chapters = experience.chapters as DocumentData[];
    const consentRequired = chapters.some((chapter) => chapter.intensity >= CONSENT_FROM_INTENSITY);
    const state: SessionState = consentRequired ? 'WAITING_FOR_PARTNER' : 'READY';
    const now = Timestamp.fromMillis(nowMs);
    const sessionRef = coupleRef.collection('sessions').doc();
    const session = {
      experienceId,
      kind: 'ONE_DEVICE',
      hostUid: uid,
      state,
      pausedFrom: null,
      currentChapter: -1,
      chapterCount: chapters.length,
      consentRequired,
      consentAcks: {},
      participants: Object.fromEntries((members as string[]).map((member) => [member, { ready: false }])),
      createdAt: now,
      startedAt: null,
      endedAt: null,
      endReason: null,
      updatedAt: now,
    };
    tx.set(sessionRef, session);
    tx.update(experienceRef, { status: 'IN_PROGRESS', sessionId: sessionRef.id });
    // Replaces an expired session's place and stats in the same, single couple write.
    updateCouple(tx, coupleRef, { ...coupleUpdate, activeSessionId: sessionRef.id });
    return viewOf(sessionRef.id, session);
  });
}

/** "I'm in" (section 3.1): the caller's own acknowledgement, and nobody else's. */
export async function markReady(db: Firestore, uid: string, input: unknown, nowMs: number): Promise<SessionView> {
  const sessionId = docId(payload(input, ['sessionId']).sessionId, 'sessionId');
  const { coupleId } = await activeCoupleOf(db, uid);
  return db.runTransaction(async (tx) => {
    const loaded = await loadSession(db, tx, coupleId, uid, sessionId);
    const experienceRef = loaded.coupleRef.collection('experiences').doc(loaded.session.experienceId);
    const experience = (await tx.get(experienceRef)).data() ?? { chapters: [] };
    const result = step(snapshotOf(loaded.session, loaded.members), { type: 'ACK', uid });
    if (result.status === 'illegal') rejectIllegal();
    if (result.status === 'unchanged') return viewOf(sessionId, loaded.session);
    updateCouple(tx, loaded.coupleRef, applyTransition(tx, loaded, experienceRef, experience, result.to, nowMs));
    return viewOf(sessionId, { ...loaded.session, ...result.to });
  });
}

/**
 * The host's phone moves the night on: `fromChapter` -1 starts it, otherwise it is the
 * chapter just finished. `playedItemId` says what that chapter actually was, when the phone
 * swapped it ("Not this one", or a chapter the limits no longer allowed): it must be one of
 * the items the server released for this night (section 5.5). The reply names what the
 * couple's limits no longer permit from here on.
 */
export async function advanceSession(
  db: Firestore,
  uid: string,
  input: unknown,
  nowMs: number,
  deps: Pick<GenerateDeps, 'catalogue'> = defaultDeps,
): Promise<AdvanceResult> {
  const raw = payload(input, ['sessionId', 'fromChapter', 'playedItemId']);
  const sessionId = docId(raw.sessionId, 'sessionId');
  const fromChapter = raw.fromChapter;
  if (!Number.isInteger(fromChapter) || (fromChapter as number) < -1) invalid('fromChapter');
  const playedItemId = raw.playedItemId;
  if (playedItemId !== undefined && (typeof playedItemId !== 'string' || !/^[a-z0-9_]{1,80}$/.test(playedItemId))) invalid('playedItemId');
  if (playedItemId !== undefined && fromChapter === -1) invalid('playedItemId');
  const { coupleId } = await activeCoupleOf(db, uid);

  const { view, experience, hostUid } = await db.runTransaction(async (tx) => {
    const loaded = await loadSession(db, tx, coupleId, uid, sessionId);
    // One phone plays the night; the other can pause or stop it, never move it (D-059).
    if (loaded.session.hostUid !== uid) throw new ServiceError('permission-denied', 'not-host');
    const experienceRef = loaded.coupleRef.collection('experiences').doc(loaded.session.experienceId);
    const experience = (await tx.get(experienceRef)).data()!;
    const event: SessionEvent = fromChapter === -1 ? { type: 'START' } : { type: 'ADVANCE', fromChapter: fromChapter as number };
    const result = step(snapshotOf(loaded.session, loaded.members), event);
    if (result.status === 'illegal') rejectIllegal();
    if (result.status === 'unchanged') return { view: viewOf(sessionId, loaded.session), experience, hostUid: loaded.session.hostUid as string };

    let chapters = experience.chapters as DocumentData[];
    if (typeof playedItemId === 'string' && result.to.played.length > 0) {
      const index = result.to.played[0];
      if (chapters[index].contentIds[0] !== playedItemId) {
        const released = new Set<string>([...chapters.map((chapter) => chapter.contentIds[0]), ...(experience.alternates ?? [])]);
        const elsewhere = chapters.some((chapter, at) => at !== index && chapter.contentIds[0] === playedItemId);
        if (!released.has(playedItemId) || elsewhere) invalid('playedItemId');
        chapters = chapters.map((chapter, at) => (at === index ? { ...chapter, contentIds: [playedItemId] } : chapter));
      }
    }
    updateCouple(tx, loaded.coupleRef, applyTransition(tx, loaded, experienceRef, experience, result.to, nowMs, chapters));
    return {
      view: viewOf(sessionId, { ...loaded.session, ...result.to }),
      experience: { ...experience, chapters },
      hostUid: loaded.session.hostUid as string,
    };
  });

  // What is still to come: the chapter now active (not yet shown) and everything after it,
  // and the alternates the phone may swap in.
  const ahead =
    view.state === 'CHAPTER_ACTIVE'
      ? [
          ...(experience.chapters as DocumentData[]).slice(view.currentChapter).map((chapter) => chapter.contentIds[0] as string),
          ...((experience.alternates ?? []) as string[]),
        ]
      : [];
  const blocked = await blockedNow(db, coupleId, hostUid, experience, ahead, nowMs, deps);
  return { ...view, blocked };
}

const END_ACTIONS = ['PAUSE', 'RESUME', 'STOP', 'DECLINE'] as const;
type EndAction = (typeof END_ACTIONS)[number];

/**
 * PAUSE, RESUME, STOP and DECLINE (section 3.1): either partner, from either phone, at any
 * moment. STOP needs no confirmation and records no one: the night simply ends for both.
 */
export async function endSession(db: Firestore, uid: string, input: unknown, nowMs: number): Promise<SessionView> {
  const raw = payload(input, ['sessionId', 'action']);
  const sessionId = docId(raw.sessionId, 'sessionId');
  const action = raw.action;
  if (!END_ACTIONS.includes(action as EndAction)) invalid('action');
  const { coupleId } = await activeCoupleOf(db, uid);
  return db.runTransaction(async (tx) => {
    const loaded = await loadSession(db, tx, coupleId, uid, sessionId);
    const experienceRef = loaded.coupleRef.collection('experiences').doc(loaded.session.experienceId);
    const experience = (await tx.get(experienceRef)).data() ?? { chapters: [] };
    const result = step(snapshotOf(loaded.session, loaded.members), { type: action as EndAction });
    if (result.status === 'illegal') rejectIllegal();
    if (result.status === 'unchanged') return viewOf(sessionId, loaded.session);
    updateCouple(tx, loaded.coupleRef, applyTransition(tx, loaded, experienceRef, experience, result.to, nowMs));
    return viewOf(sessionId, { ...loaded.session, ...result.to });
  });
}
