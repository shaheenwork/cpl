/**
 * Reporting (BUILD_PROMPT.md section 3.5).
 *
 * Anything the app showed can be reported, and anything a partner sent can be too. A report is
 * written where **neither partner can read it** — `reports/{id}` is denied to every client — so
 * reporting the other person's voice note cannot become a way for them to find out, and reporting
 * a content item cannot become a way to learn what the other one saw.
 *
 * What is stored is the smallest thing that lets a human act: what was reported, why, who by, and
 * which couple it happened in. Never the words of a report, because a free-text box on a screen
 * like this is a place people put things they would not want kept.
 */
import { Timestamp, type Firestore } from 'firebase-admin/firestore';
import { activeCoupleOf } from '../experience/generate';
import { ServiceError } from '../shared/errors';
import { consumeRateLimit } from '../shared/rateLimit';

/** Why something was reported (section 3.5). */
export const REPORT_REASONS = ['BROKEN', 'INAPPROPRIATE', 'UNSAFE', 'TECHNICAL'] as const;
export type ReportReason = (typeof REPORT_REASONS)[number];

/** What can be reported: something the app wrote, or something the partner sent. */
export const REPORT_TARGETS = ['CONTENT', 'MEDIA'] as const;
export type ReportTarget = (typeof REPORT_TARGETS)[number];

export const REPORT_LIMIT = 30;
export const REPORT_WINDOW_MS = 60 * 60 * 1000;

const CONTENT_ID = /^[a-z0-9_]{1,80}$/;
const DOC_ID = /^[A-Za-z0-9_-]{1,64}$/;
const KEYS = new Set(['target', 'id', 'reason']);

function invalid(reason: string): never {
  throw new ServiceError('invalid-argument', reason);
}

export interface ReportRequest {
  target: ReportTarget;
  id: string;
  reason: ReportReason;
}

export function parseReport(input: unknown): ReportRequest {
  if (typeof input !== 'object' || input === null || Array.isArray(input)) invalid('request');
  const raw = input as Record<string, unknown>;
  for (const key of Object.keys(raw)) if (!KEYS.has(key)) invalid(`unknown field ${key}`);
  const target = raw.target;
  if (!REPORT_TARGETS.includes(target as ReportTarget)) invalid('target');
  const reason = raw.reason;
  if (!REPORT_REASONS.includes(reason as ReportReason)) invalid('reason');
  const id = raw.id;
  const shape = target === 'CONTENT' ? CONTENT_ID : DOC_ID;
  if (typeof id !== 'string' || !shape.test(id)) invalid('id');
  return { target: target as ReportTarget, id, reason: reason as ReportReason };
}

/**
 * Files a report. Returns nothing but the id it was given, because there is nothing about a
 * report the app should be able to show anyone — including the person who filed it.
 */
export async function reportContent(
  db: Firestore,
  uid: string,
  input: unknown,
  nowMs: number,
): Promise<{ reportId: string }> {
  const request = parseReport(input);
  const { coupleId } = await activeCoupleOf(db, uid);
  await consumeRateLimit(db, uid, 'reportContent', REPORT_LIMIT, REPORT_WINDOW_MS, nowMs);

  // A media report has to be about something of this couple's: a report is not a way to ask
  // whether an id exists somewhere else.
  if (request.target === 'MEDIA') {
    const media = await db.doc(`couples/${coupleId}/media/${request.id}`).get();
    if (!media.exists) throw new ServiceError('not-found', 'media');
  }

  const ref = db.collection('reports').doc();
  await ref.set({
    target: request.target,
    id: request.id,
    reason: request.reason,
    // For moderation, in a collection no client can read. Never exposed to either partner (3.5).
    reportedBy: uid,
    coupleId,
    state: 'OPEN',
    createdAt: Timestamp.fromMillis(nowMs),
  });
  return { reportId: ref.id };
}
