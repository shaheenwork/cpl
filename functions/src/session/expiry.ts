/**
 * Sessions nobody is in any more (BUILD_PROMPT.md section 12.3).
 *
 * Both phones send a heartbeat while a session is on screen — `sessions/{sid}/presence/{uid}`,
 * the one session document a client may write. A session neither phone has touched and neither
 * has been seen in for EXPIRE_AFTER_MS ends as EXPIRED: the couple is free to start another,
 * and nothing is left half-open. Whatever was under way still counts as played, exactly as a
 * STOP would (transitions.ts).
 *
 * Swept every five minutes by a scheduled function, and safe to run twice.
 */
import { Timestamp, type Firestore } from 'firebase-admin/firestore';
import { fcmSender, notifyUser, type Sender } from '../notify/notify';
import { expireSession } from './session';
import { isTerminal, type SessionState } from './transitions';

/** Ten minutes of silence from both phones (section 12.3). */
export const EXPIRE_AFTER_MS = 10 * 60 * 1000;

/**
 * An async session is not waiting for anyone to be there (section 12.4): one partner does their
 * part and the other comes to it hours or days later. It gives up its place after a week, so a
 * couple is never held by something neither of them is going to finish.
 */
export const EXPIRE_ASYNC_AFTER_MS = 7 * 24 * 60 * 60 * 1000;

/** How far back the sweep looks: sessions touched since then are certainly alive. */
const WINDOW_MS = 30 * 24 * 60 * 60 * 1000;

/**
 * How long before its time is up a session says so. A live session's ten minutes are shorter than
 * any warning would be useful for, so only an async one — a night waiting days for the other
 * partner — is ever worth a word (section 16.1).
 */
export const WARN_BEFORE_MS = 24 * 60 * 60 * 1000;

export async function expireStale(
  db: Firestore,
  nowMs: number,
  sender: Sender = fcmSender,
): Promise<{ expired: number; warned: number }> {
  const cutoff = Timestamp.fromMillis(nowMs - EXPIRE_AFTER_MS);
  const candidates = await db
    .collectionGroup('sessions')
    .where('updatedAt', '<=', cutoff)
    .where('updatedAt', '>=', Timestamp.fromMillis(nowMs - WINDOW_MS))
    .get();

  let expired = 0;
  let warned = 0;
  for (const session of candidates.docs) {
    if (isTerminal(session.get('state') as SessionState)) continue;
    const async = session.get('kind') === 'ASYNC';
    const patience = async ? EXPIRE_ASYNC_AFTER_MS : EXPIRE_AFTER_MS;
    const touched = (session.get('updatedAt') as Timestamp | undefined)?.toMillis() ?? 0;
    const seen = await lastSeen(session.ref);
    const quietSince = Math.max(touched, seen ?? 0);
    if (nowMs - quietSince < patience) {
      // Still alive, but not for much longer: said once, to whoever has not finished it.
      if (async && nowMs - quietSince >= patience - WARN_BEFORE_MS && session.get('expiryWarnedAt') == null) {
        await warn(db, session, nowMs, sender);
        warned += 1;
      }
      continue;
    }
    const coupleId = session.ref.parent.parent!.id;
    if (await expireSession(db, coupleId, session.id, nowMs)) expired += 1;
  }
  return { expired, warned };
}

/** Tells the couple a waiting night is nearly out of time, once. */
async function warn(
  db: Firestore,
  session: FirebaseFirestore.QueryDocumentSnapshot,
  nowMs: number,
  sender: Sender,
): Promise<void> {
  await session.ref.update({ expiryWarnedAt: Timestamp.fromMillis(nowMs) });
  const coupleId = session.ref.parent.parent!.id;
  const members: unknown = (await db.doc(`couples/${coupleId}`).get()).get('memberUids');
  if (!Array.isArray(members)) return;
  for (const member of members as string[]) {
    await notifyUser(db, member, 'SESSION_EXPIRING', session.id, nowMs, sender);
  }
}

/** When either phone was last in the session, or null if neither ever said. */
async function lastSeen(session: FirebaseFirestore.DocumentReference): Promise<number | null> {
  const presence = await session.collection('presence').get();
  const times = presence.docs
    .map((doc) => (doc.get('at') as Timestamp | undefined)?.toMillis())
    .filter((at): at is number => typeof at === 'number');
  return times.length === 0 ? null : Math.max(...times);
}
