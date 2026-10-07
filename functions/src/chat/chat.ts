/**
 * Telling the other phone that something was said — and never what (CHAT_PROMPT.md §5).
 *
 * The message itself is written by the phone, under rules that hold its shape. The only thing the
 * server does with a chat is this: notice one arrived, and send a data-only push that names a kind
 * and nothing else. The words never leave Firestore, where both of them can already read them.
 *
 * It is rate-limited, because a conversation is not an event: twenty messages in a minute is one
 * notification, not twenty. The stamp lives beside the read mark, on the recipient's own document.
 */
import { Timestamp, type Firestore } from 'firebase-admin/firestore';
import { fcmSender, notifyPartner, type Sender } from '../notify/notify';

/** One push per partner per this long, however much is said inside it. */
export const QUIET_MS = 2 * 60 * 1000;

/**
 * A message landed: tell the other one, unless they have been told recently.
 *
 * Safe to run again — the stamp is written in the same transaction that reads it, so two messages
 * arriving together produce one notification rather than two.
 */
export async function onMessageWritten(
  db: Firestore,
  coupleId: string,
  fromUid: string,
  nowMs: number,
  sender: Sender = fcmSender,
): Promise<void> {
  const coupleRef = db.doc(`couples/${coupleId}`);
  const couple = await coupleRef.get();
  const members: unknown = couple.get('memberUids');
  if (!Array.isArray(members) || !members.includes(fromUid)) return;
  const partner = (members as string[]).find((member) => member !== fromUid);
  if (partner === undefined) return;

  const stateRef = coupleRef.collection('chatState').doc(partner);
  const tell = await db.runTransaction(async (tx) => {
    const state = await tx.get(stateRef);
    const told = (state.get('notifiedAt') as Timestamp | undefined)?.toMillis() ?? 0;
    if (nowMs - told < QUIET_MS) return false;
    tx.set(stateRef, { notifiedAt: Timestamp.fromMillis(nowMs) }, { merge: true });
    return true;
  });

  // Outside the transaction: it may run more than once, and a phone must never be told twice.
  // THEY_SENT_SOMETHING is the kind that already exists for exactly this — the words are the
  // phone's, from a fixed table, and carry no part of what was said (D-088).
  if (tell) await notifyPartner(db, members as string[], fromUid, 'THEY_SENT_SOMETHING', null, nowMs, sender);
}
