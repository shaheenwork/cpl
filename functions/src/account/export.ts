/**
 * Data export (BUILD_PROMPT.md sections 8, 14.9): everything this app holds about the person
 * asking, as one real file they can read.
 *
 * The hard rule is §5.1: a partner's private answers and limits are never exposed by any surface,
 * and an export is a surface. So this gathers **the requester's own** documents — their profile,
 * their preferences, their boundaries, their affinity, what they reported — plus the things that
 * are genuinely shared and that both partners can already see: the nights they played, the
 * memories they kept, what has been sent between them. Not one field of the other person's
 * private record, and not a word either of them wrote inside a surprise they never opened.
 *
 * The file lands in Storage under the requester's own path, which only they can read.
 */
import { type Firestore } from 'firebase-admin/firestore';
import { type Storage } from 'firebase-admin/storage';
import { bucketOf } from '../media/media';
import { consumeRateLimit } from '../shared/rateLimit';

export const EXPORT_LIMIT = 5;
export const EXPORT_WINDOW_MS = 24 * 60 * 60 * 1000;

/** How long an export stays in Storage before the sweep takes it (section 5.6). */
export const EXPORT_TTL_MS = 7 * 24 * 60 * 60 * 1000;

export interface ExportResult {
  exportPath: string;
  bytes: number;
  expiresAtMillis: number;
}

/** One document, as plain data. Timestamps become milliseconds so the file reads on its own. */
function plain(value: unknown): unknown {
  if (value === null || value === undefined) return null;
  if (Array.isArray(value)) return value.map(plain);
  if (typeof value === 'object') {
    const record = value as Record<string, unknown>;
    if (typeof (record as { toMillis?: () => number }).toMillis === 'function') {
      return (record as { toMillis: () => number }).toMillis();
    }
    return Object.fromEntries(Object.entries(record).map(([key, inner]) => [key, plain(inner)]));
  }
  return value;
}

async function collection(db: Firestore, path: string): Promise<Record<string, unknown>[]> {
  const snapshot = await db.collection(path).get();
  return snapshot.docs.map((doc) => ({ id: doc.id, ...(plain(doc.data()) as Record<string, unknown>) }));
}

/**
 * Builds the export. Separated from writing it so the whole shape can be asserted in a test
 * without a bucket — and so there is one place to read to be sure whose data is in it.
 */
export async function buildExport(db: Firestore, uid: string, nowMs: number): Promise<Record<string, unknown>> {
  const user = (await db.doc(`users/${uid}`).get()).data() ?? {};
  const coupleId = typeof user.coupleId === 'string' ? user.coupleId : null;

  const mine: Record<string, unknown> = {
    exportedAt: nowMs,
    account: {
      uid,
      ...(plain(user) as Record<string, unknown>),
      // A purchase token is a credential of sorts, and no use to anyone reading their own export.
      entitlement: user.entitlement === undefined
        ? null
        : { ...(plain(user.entitlement) as Record<string, unknown>), purchaseToken: undefined },
    },
    preferences: await collection(db, `users/${uid}/preferences`),
    boundaries: await collection(db, `users/${uid}/boundaries`),
    affinity: await collection(db, `users/${uid}/affinity`),
    devices: (await collection(db, `users/${uid}/fcmTokens`)).map((token) => ({
      id: token.id,
      platform: token.platform,
      updatedAt: token.updatedAt,
      // The token itself is how a phone is reached; it is not information about a person.
      token: undefined,
    })),
  };

  if (coupleId === null) return { ...mine, couple: null };

  // Shared, and only what both of them can already see on their own screens.
  const couple = (await db.doc(`couples/${coupleId}`).get()).data() ?? {};
  mine.couple = {
    id: coupleId,
    status: couple.status ?? null,
    createdAt: plain(couple.createdAt),
    memories: await collection(db, `couples/${coupleId}/memories`),
    nights: (await collection(db, `couples/${coupleId}/experiences`))
      // A night planned for the other partner is not this person's to read (D-081).
      .filter((night) => night.plannedFor === null || night.plannedFor === undefined || night.plannedFor === uid)
      .map((night) => ({
        id: night.id,
        mode: night.mode,
        mood: night.mood,
        durationMin: night.durationMin,
        status: night.status,
        playedThrough: night.playedThrough,
        createdAt: night.createdAt,
      })),
    media: (await collection(db, `couples/${coupleId}/media`)).map((item) => ({
      id: item.id,
      kind: item.kind,
      mine: item.ownerUid === uid,
      sentAt: item.sentAt,
      viewedAt: item.viewedAt,
      // The bytes stay where the media rules guard them; the export says one exists (section 5.8).
      note: item.ownerUid === uid ? item.note : undefined,
    })),
    countdowns: await collection(db, `couples/${coupleId}/countdowns`),
    arcs: await collection(db, `couples/${coupleId}/arcs`),
    // What this person left for the other, and what has been delivered to them. Never the inside
    // of something still waiting — that is the whole point of a surprise (section 5.7).
    surprises: (await collection(db, `couples/${coupleId}/surprises`)).map((surprise) => ({
      id: surprise.id,
      type: surprise.type,
      title: surprise.createdBy === uid || surprise.deliveredAt !== null ? surprise.title : undefined,
      state: surprise.state,
      mine: surprise.createdBy === uid,
      scheduledFor: surprise.scheduledFor,
      deliveredAt: surprise.deliveredAt,
    })),
    feedback: (await collection(db, `couples/${coupleId}/feedback`)).filter((one) => one.byUid === uid),
  };
  return mine;
}

/**
 * Writes the export and returns where it went. The path is under the requester's own prefix, so
 * the Storage rules serve it to them and to nobody else.
 */
export async function requestDataExport(
  db: Firestore,
  storage: Storage,
  uid: string,
  nowMs: number,
): Promise<ExportResult> {
  await consumeRateLimit(db, uid, 'requestDataExport', EXPORT_LIMIT, EXPORT_WINDOW_MS, nowMs);
  const data = await buildExport(db, uid, nowMs);
  const body = Buffer.from(JSON.stringify(data, null, 2), 'utf8');

  const exportPath = `users/${uid}/exports/afterhours-${nowMs}.json`;
  await bucketOf(storage).file(exportPath).save(body, {
    contentType: 'application/json',
    metadata: {
      // Swept with everything else that has a time on it (expireMedia), and a client cannot
      // change it: metadata on a user's own object is written only here.
      metadata: { expiresAt: String(nowMs + EXPORT_TTL_MS), ownerUid: uid },
    },
  });
  return { exportPath, bytes: body.length, expiresAtMillis: nowMs + EXPORT_TTL_MS };
}

/** Exports past their week, swept on the same schedule as everything else that expires. */
export async function expireExports(storage: Storage, nowMs: number): Promise<{ removed: number }> {
  const [files] = await bucketOf(storage).getFiles({ prefix: 'users/' });
  let removed = 0;
  for (const file of files) {
    if (!file.name.includes('/exports/')) continue;
    const expiresAt = Number(file.metadata?.metadata?.expiresAt ?? 0);
    if (expiresAt > 0 && expiresAt <= nowMs) {
      await file.delete({ ignoreNotFound: true });
      removed += 1;
    }
  }
  return { removed };
}
