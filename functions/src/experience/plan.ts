/**
 * "Your partner planned tonight 👀" (BUILD_PROMPT.md sections 2.5 #2, 13): one partner plans a
 * night the other only knows is coming.
 *
 * The night is built for the partner who will play it — their boundaries decide what it may
 * hold, their ask-first themes are the ones flagged, and the planner's are left out, because it
 * is the partner's phone it plays on (DECISIONS.md D-060). The planner is told nothing but the
 * shape they asked for: the security rules keep a planned night's contents to the partner it was
 * planned for, and this returns no ids at all.
 *
 * What both may see is the plan itself — that there is one, how many chapters, and whether it
 * has been opened — in `couples/{cid}/plans/{experienceId}`.
 *
 * Core logic, tested directly against the emulator; the callable in index.ts only authenticates
 * and delegates.
 */
import { Timestamp, type Firestore } from 'firebase-admin/firestore';
import { ServiceError } from '../shared/errors';
import { activeCoupleOf, buildExperience, defaultDeps, parseRequest, type GenerateDeps } from './generate';

/**
 * Where a plan stands: waiting to be found, or opened. What happened after that is the session's
 * to say, and both partners can read that.
 */
export const PLAN_STATES = ['WAITING', 'OPENED'] as const;
export type PlanState = (typeof PLAN_STATES)[number];

export interface PlannedNight {
  experienceId: string;
  chapterCount: number;
  plannedFor: string;
}

/** A night planned for the partner. The planner learns the shape and nothing else. */
export async function planNight(
  db: Firestore,
  uid: string,
  input: unknown,
  nowMs: number,
  deps: GenerateDeps = defaultDeps,
): Promise<PlannedNight> {
  const seed = deps.seed() >>> 0;
  const request = parseRequest(input, seed);
  const { coupleId, members } = await activeCoupleOf(db, uid);
  const partner = members.find((member) => member !== uid);
  if (partner === undefined) throw new ServiceError('failed-precondition', 'not-paired');

  const built = await buildExperience(db, uid, request, { mystery: false, plannedFor: partner }, nowMs, deps);
  await db.doc(`couples/${coupleId}/plans/${built.experienceId}`).set({
    experienceId: built.experienceId,
    plannedBy: uid,
    plannedFor: partner,
    chapterCount: built.chapters.length,
    state: 'WAITING' satisfies PlanState,
    createdAt: Timestamp.fromMillis(nowMs),
    openedAt: null,
  });
  // Ids, alternates and ask-first items all stay out of this: the planner is not told what they
  // planned, only that it is there and how long it runs.
  return { experienceId: built.experienceId, chapterCount: built.chapters.length, plannedFor: partner };
}

/**
 * The partner opening what was planned for them: the plan is marked opened once, so the planner
 * can see it landed without ever seeing what is in it. Playing it is an ordinary session.
 */
export async function openPlan(db: Firestore, uid: string, input: unknown, nowMs: number): Promise<{ experienceId: string }> {
  if (typeof input !== 'object' || input === null || Array.isArray(input)) {
    throw new ServiceError('invalid-argument', 'request');
  }
  const raw = input as Record<string, unknown>;
  for (const key of Object.keys(raw)) if (key !== 'experienceId') throw new ServiceError('invalid-argument', `unknown field ${key}`);
  const experienceId = raw.experienceId;
  if (typeof experienceId !== 'string' || !/^[A-Za-z0-9_-]{1,64}$/.test(experienceId)) {
    throw new ServiceError('invalid-argument', 'experienceId');
  }
  const { coupleId } = await activeCoupleOf(db, uid);
  const ref = db.doc(`couples/${coupleId}/plans/${experienceId}`);
  const plan = await ref.get();
  if (!plan.exists) throw new ServiceError('not-found', 'plan');
  if (plan.get('plannedFor') !== uid) throw new ServiceError('permission-denied', 'not-yours');
  if (plan.get('state') === 'WAITING') {
    await ref.update({ state: 'OPENED' satisfies PlanState, openedAt: Timestamp.fromMillis(nowMs) });
  }
  return { experienceId };
}
