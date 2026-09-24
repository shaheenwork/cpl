/**
 * Multi-day arcs (BUILD_PROMPT.md section 13): one · three · seven · fourteen days, each day its
 * own night, unlocking on the clock and only once the day before it has been played.
 *
 * An arc is a plan, not content. Each day names a mood and how far it may go; the night itself is
 * built the moment the day is opened, through the same `buildExperience` every other night goes
 * through — so a day of an arc is filtered by the couple's boundaries exactly like anything else,
 * and nothing is generated for a day they may never reach.
 *
 * Core logic, tested directly against the emulator; the callables in index.ts only authenticate
 * and delegate.
 */
import { Timestamp, type DocumentData, type Firestore } from 'firebase-admin/firestore';
import { MOODS, type Mood } from '../engine/types';
import { activeCoupleOf, buildExperience, currentFilters, defaultDeps, type GenerateDeps } from '../experience/generate';
import { fcmSender, notifyPartner, type Sender } from '../notify/notify';
import { ServiceError } from '../shared/errors';

export const ARC_LENGTHS = [1, 3, 7, 14] as const;
export type ArcLength = (typeof ARC_LENGTHS)[number];

export const ARC_DAY_STATES = ['LOCKED', 'READY', 'DONE'] as const;
export type ArcDayState = (typeof ARC_DAY_STATES)[number];

export const DAY_MS = 24 * 60 * 60 * 1000;

/** How long each day's night runs. Short enough to keep on a weekday. */
const DAY_DURATION_MIN = 30;

/**
 * The seven beats of AFTER DARK (section 13), as moods the engine already knows. A shorter arc
 * takes the ones that matter most; a longer one runs them twice, the second time from higher up.
 */
const BEATS: { title: string; mood: Mood }[] = [
  { title: 'Curiosity', mood: 'mysterious' },
  { title: 'Teasing', mood: 'naughty' },
  { title: 'A secret', mood: 'talk' },
  { title: 'A challenge', mood: 'unpredictable' },
  { title: 'A fantasy', mood: 'roleplay' },
  { title: 'A surprise', mood: 'experimental' },
  { title: 'The last night', mood: 'bold' },
];

export interface ArcDay {
  index: number;
  title: string;
  mood: Mood;
  state: ArcDayState;
  unlockAt: Timestamp;
  experienceId: string | null;
}

export interface ArcView {
  arcId: string;
  lengthDays: number;
  days: { index: number; title: string; state: ArcDayState; unlockAtMillis: number; experienceId: string | null }[];
}

function invalid(reason: string): never {
  throw new ServiceError('invalid-argument', reason);
}

/** The plan: which beat each day is, and when it opens. */
export function planDays(lengthDays: ArcLength, startedAtMs: number): ArcDay[] {
  const beats = lengthDays <= BEATS.length
    ? [...BEATS.slice(0, Math.max(0, lengthDays - 1)), BEATS[BEATS.length - 1]]
    : [...BEATS, ...BEATS.slice(0, lengthDays - BEATS.length - 1), BEATS[BEATS.length - 1]];
  return beats.slice(0, lengthDays).map((beat, index) => ({
    index,
    title: beat.title,
    mood: beat.mood,
    state: (index === 0 ? 'READY' : 'LOCKED') satisfies ArcDayState,
    unlockAt: Timestamp.fromMillis(startedAtMs + index * DAY_MS),
    experienceId: null,
  }));
}

export function parseStart(input: unknown): ArcLength {
  if (typeof input !== 'object' || input === null || Array.isArray(input)) invalid('request');
  const raw = input as Record<string, unknown>;
  for (const key of Object.keys(raw)) if (key !== 'lengthDays') invalid(`unknown field ${key}`);
  if (!ARC_LENGTHS.includes(raw.lengthDays as ArcLength)) invalid('lengthDays');
  return raw.lengthDays as ArcLength;
}

/** Starts an arc. One at a time: a couple already in one is told so rather than given a second. */
export async function startArc(db: Firestore, uid: string, input: unknown, nowMs: number): Promise<ArcView> {
  const lengthDays = parseStart(input);
  const { coupleId } = await activeCoupleOf(db, uid);

  const running = await db.collection(`couples/${coupleId}/arcs`).where('state', '==', 'ACTIVE').limit(1).get();
  if (!running.empty) throw new ServiceError('failed-precondition', 'arc-in-progress');

  const days = planDays(lengthDays, nowMs);
  const ref = db.collection(`couples/${coupleId}/arcs`).doc();
  await ref.set({
    title: lengthDays === 1 ? 'One night' : `${lengthDays} days`,
    lengthDays,
    state: 'ACTIVE',
    createdBy: uid,
    startedAt: Timestamp.fromMillis(nowMs),
    days,
  });
  return viewOf(ref.id, { lengthDays, days });
}

/**
 * Opens the day that is due: builds its night, and hands back the experience to play. A day the
 * clock has not reached, or one whose day before was never played, is refused (section 13).
 */
export async function openArcDay(
  db: Firestore,
  uid: string,
  input: unknown,
  nowMs: number,
  deps: GenerateDeps = defaultDeps,
  sender: Sender = fcmSender,
): Promise<{ arcId: string; dayIndex: number; experienceId: string }> {
  if (typeof input !== 'object' || input === null || Array.isArray(input)) invalid('request');
  const raw = input as Record<string, unknown>;
  for (const key of Object.keys(raw)) if (key !== 'arcId' && key !== 'dayIndex') invalid(`unknown field ${key}`);
  const arcId = raw.arcId;
  if (typeof arcId !== 'string' || !/^[A-Za-z0-9_-]{1,64}$/.test(arcId)) invalid('arcId');
  if (!Number.isInteger(raw.dayIndex) || (raw.dayIndex as number) < 0) invalid('dayIndex');
  const dayIndex = raw.dayIndex as number;

  const { coupleId, members } = await activeCoupleOf(db, uid);
  const ref = db.doc(`couples/${coupleId}/arcs/${arcId}`);
  const snapshot = await ref.get();
  const arc = snapshot.data();
  if (!snapshot.exists || arc === undefined) throw new ServiceError('not-found', 'arc');
  if (arc.state !== 'ACTIVE') throw new ServiceError('failed-precondition', 'arc-over');

  const days = arc.days as DocumentData[];
  const day = days[dayIndex];
  if (day === undefined) throw new ServiceError('not-found', 'day');
  if (typeof day.experienceId === 'string') {
    return { arcId, dayIndex, experienceId: day.experienceId };
  }
  if ((day.unlockAt as Timestamp).toMillis() > nowMs) throw new ServiceError('failed-precondition', 'not-yet');

  // The day before has to have been played. An arc is a sequence, not a menu (section 13).
  const before = days[dayIndex - 1];
  if (before !== undefined && !(await isPlayed(db, coupleId, before))) {
    throw new ServiceError('failed-precondition', 'finish-the-day-before');
  }

  const filters = await currentFilters(db, coupleId, nowMs);
  const built = await buildExperience(
    db,
    uid,
    {
      mode: 'TOGETHER',
      durationMin: DAY_DURATION_MIN,
      moods: [day.mood as Mood].filter((mood) => MOODS.includes(mood)),
      intensity: filters.maxIntensity,
      leadership: 'RANDOM',
      novelty: 'MIXED',
      seed: (nowMs + dayIndex) >>> 0,
      media: { VOICE: true, PHOTO: true },
    },
    { mystery: false },
    nowMs,
    deps,
  );

  days[dayIndex] = { ...day, state: 'READY' satisfies ArcDayState, experienceId: built.experienceId };
  if (before !== undefined) days[dayIndex - 1] = { ...before, state: 'DONE' satisfies ArcDayState };
  await ref.update({ days });
  // Today's day is open. Which beat it is stays on the phone that asks (section 16.2).
  await notifyPartner(db, members, uid, 'DAY_UNLOCKED', built.experienceId, nowMs, sender);
  return { arcId, dayIndex, experienceId: built.experienceId };
}

/** An arc ends when its last day has been played, or when the couple stops it. */
export async function closeArc(db: Firestore, uid: string, input: unknown, nowMs: number): Promise<{ arcId: string }> {
  if (typeof input !== 'object' || input === null || Array.isArray(input)) invalid('request');
  const raw = input as Record<string, unknown>;
  for (const key of Object.keys(raw)) if (key !== 'arcId') invalid(`unknown field ${key}`);
  const arcId = raw.arcId;
  if (typeof arcId !== 'string' || !/^[A-Za-z0-9_-]{1,64}$/.test(arcId)) invalid('arcId');
  const { coupleId } = await activeCoupleOf(db, uid);
  const ref = db.doc(`couples/${coupleId}/arcs/${arcId}`);
  if (!(await ref.get()).exists) throw new ServiceError('not-found', 'arc');
  await ref.update({ state: 'ENDED', endedAt: Timestamp.fromMillis(nowMs) });
  return { arcId };
}

/** Whether the night a day was given has been played to the end. */
async function isPlayed(db: Firestore, coupleId: string, day: DocumentData): Promise<boolean> {
  if (day.state === 'DONE') return true;
  if (typeof day.experienceId !== 'string') return false;
  const experience = await db.doc(`couples/${coupleId}/experiences/${day.experienceId}`).get();
  return experience.get('status') === 'COMPLETED';
}

function viewOf(arcId: string, arc: { lengthDays: number; days: ArcDay[] }): ArcView {
  return {
    arcId,
    lengthDays: arc.lengthDays,
    days: arc.days.map((day) => ({
      index: day.index,
      title: day.title,
      state: day.state,
      unlockAtMillis: day.unlockAt.toMillis(),
      experienceId: day.experienceId,
    })),
  };
}
