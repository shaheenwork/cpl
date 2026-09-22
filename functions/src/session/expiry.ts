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
import { expireSession } from './session';
import { isTerminal, type SessionState } from './transitions';

/** Ten minutes of silence from both phones (section 12.3). */
export const EXPIRE_AFTER_MS = 10 * 60 * 1000;

/** How far back the sweep looks: sessions touched since then are certainly alive. */
const WINDOW_MS = 24 * 60 * 60 * 1000;

export async function expireStale(db: Firestore, nowMs: number): Promise<{ expired: number }> {
  const cutoff = Timestamp.fromMillis(nowMs - EXPIRE_AFTER_MS);
  const candidates = await db
    .collectionGroup('sessions')
    .where('updatedAt', '<=', cutoff)
    .where('updatedAt', '>=', Timestamp.fromMillis(nowMs - WINDOW_MS))
    .get();

  let expired = 0;
  for (const session of candidates.docs) {
    if (isTerminal(session.get('state') as SessionState)) continue;
    const seen = await lastSeen(session.ref);
    if (seen !== null && seen > nowMs - EXPIRE_AFTER_MS) continue;
    const coupleId = session.ref.parent.parent!.id;
    if (await expireSession(db, coupleId, session.id, nowMs)) expired += 1;
  }
  return { expired };
}

/** When either phone was last in the session, or null if neither ever said. */
async function lastSeen(session: FirebaseFirestore.DocumentReference): Promise<number | null> {
  const presence = await session.collection('presence').get();
  const times = presence.docs
    .map((doc) => (doc.get('at') as Timestamp | undefined)?.toMillis())
    .filter((at): at is number => typeof at === 'number');
  return times.length === 0 ? null : Math.max(...times);
}
