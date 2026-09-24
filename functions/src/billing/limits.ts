/**
 * What the free tier includes, and where premium begins (BUILD_PROMPT.md section 19).
 *
 * Every gate here is **at selection time**, on the server, before anything is built. Nothing in
 * this file can refuse a night that is already under way: a session that has started finishes,
 * whatever anyone's subscription did in the meantime (section 19, and anti-pattern 6). The
 * callables that move a night on — consent, advance, submit, reveal, remix, end — never call any
 * of it.
 *
 * Free is meant to be worth using: ordinary nights, every game, voice notes and photos, the
 * library, surprises, planning a night for a partner. Premium is more of it, and the two shapes
 * that are the most work to run: Mystery Nights and multi-day arcs.
 */
import { Timestamp, type Firestore } from 'firebase-admin/firestore';
import { ServiceError } from '../shared/errors';
import { coupleEntitlement } from './entitlement';

/** Nights a free couple may build in a week. Playing them is never limited. */
export const FREE_NIGHTS_PER_WEEK = 3;

/** Things a free couple may have waiting to be found at once (section 13). */
export const FREE_SURPRISES_WAITING = 3;

export const WEEK_MS = 7 * 24 * 60 * 60 * 1000;

/** What a paywall would be about, if one is shown. The phone shows the same screen for each. */
export const GATES = ['NIGHTS_THIS_WEEK', 'MYSTERY_NIGHT', 'MULTI_DAY_ARC', 'SURPRISES_WAITING'] as const;
export type Gate = (typeof GATES)[number];

/** One refusal, one reason: the phone shows the paywall and says which limit it was. */
export function premiumRequired(gate: Gate): never {
  throw new ServiceError('failed-precondition', `premium-required:${gate}`);
}

export async function isPremium(db: Firestore, members: readonly string[], nowMs: number): Promise<boolean> {
  return (await coupleEntitlement(db, members, nowMs)).tier === 'PREMIUM';
}

/** Nights built for this couple in the last seven days, whoever asked for them. */
export async function nightsThisWeek(db: Firestore, coupleId: string, nowMs: number): Promise<number> {
  const since = Timestamp.fromMillis(nowMs - WEEK_MS);
  const built = await db
    .collection(`couples/${coupleId}/experiences`)
    .where('createdAt', '>=', since)
    .count()
    .get();
  return built.data().count;
}

/** Surprises left for a partner and not yet delivered (section 13). */
export async function surprisesWaiting(db: Firestore, coupleId: string): Promise<number> {
  const waiting = await db
    .collection(`couples/${coupleId}/surprises`)
    .where('state', '==', 'SCHEDULED')
    .count()
    .get();
  return waiting.data().count;
}

/**
 * The gate every night passes through, inside `buildExperience` — so a night built by the builder,
 * a surprise, a plan for a partner or a day of an arc is counted the same way and nothing routes
 * around it.
 */
export async function guardNewNight(
  db: Firestore,
  coupleId: string,
  members: readonly string[],
  mystery: boolean,
  nowMs: number,
): Promise<void> {
  if (await isPremium(db, members, nowMs)) return;
  if (mystery) premiumRequired('MYSTERY_NIGHT');
  if ((await nightsThisWeek(db, coupleId, nowMs)) >= FREE_NIGHTS_PER_WEEK) premiumRequired('NIGHTS_THIS_WEEK');
}

/** Multi-day arcs are premium outright (section 19). */
export async function guardArc(db: Firestore, members: readonly string[], nowMs: number): Promise<void> {
  if (!(await isPremium(db, members, nowMs))) premiumRequired('MULTI_DAY_ARC');
}

/** A free couple may keep a few things waiting at once; opening them is never limited. */
export async function guardSurprise(
  db: Firestore,
  coupleId: string,
  members: readonly string[],
  nowMs: number,
): Promise<void> {
  if (await isPremium(db, members, nowMs)) return;
  if ((await surprisesWaiting(db, coupleId)) >= FREE_SURPRISES_WAITING) premiumRequired('SURPRISES_WAITING');
}

/** What the phone shows on the paywall, so the words and the numbers cannot drift apart. */
export function freeLimits(): Record<string, number> {
  return {
    nightsPerWeek: FREE_NIGHTS_PER_WEEK,
    surprisesWaiting: FREE_SURPRISES_WAITING,
  };
}
