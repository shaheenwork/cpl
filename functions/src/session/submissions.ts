/**
 * Answers from two phones, and the reveal they complete (BUILD_PROMPT.md section 12).
 *
 * Each phone writes its own submission — `sessions/{sid}/submissions/{chapter}_{uid}` — and the
 * security rules let it write no other, for no other chapter, and never twice. Nothing ever
 * reads a submission back to a client: this trigger turns the pair into one reveal document
 * both partners may read, and moves the session on (rules tests D–G).
 *
 * In a transaction, so two answers landing together cannot reveal twice, skip the reveal, or
 * leave a chapter waiting for an answer that is already in.
 */
import { Timestamp, type DocumentData, type Firestore } from 'firebase-admin/firestore';
import { fcmSender, notifyPartner, type NotificationKind, type Sender } from '../notify/notify';
import { snapshotOf } from './session';
import { isTerminal, step } from './transitions';

/**
 * A submission id is `{chapter}_{uid}`: the chapter it answers, and whose answer it is. The uid
 * runs to the end, whatever is in it; the rules check it is the caller's own.
 */
const SUBMISSION_ID = /^(\d+)_(.+)$/;


/**
 * Applies the submission [submissionId] of [sessionId]: records who has answered, and — when
 * that was the last answer — writes the chapter's reveal and puts the session in REVEAL.
 * Safe to run again: a submission already recorded changes nothing.
 */
export async function applySubmission(
  db: Firestore,
  coupleId: string,
  sessionId: string,
  submissionId: string,
  nowMs: number,
  sender: Sender = fcmSender,
): Promise<void> {
  const match = SUBMISSION_ID.exec(submissionId);
  if (!match) return;
  const chapter = Number(match[1]);
  const uid = match[2];

  const coupleRef = db.doc(`couples/${coupleId}`);
  const sessionRef = coupleRef.collection('sessions').doc(sessionId);
  const submissionsRef = sessionRef.collection('submissions');
  const revealRef = sessionRef.collection('rounds').doc(String(chapter));

  const told = await db.runTransaction(async (tx) => {
    const [couple, session, reveal] = await Promise.all([tx.get(coupleRef), tx.get(sessionRef), tx.get(revealRef)]);
    const members: unknown = couple.get('memberUids');
    if (!session.exists || !Array.isArray(members) || !members.includes(uid)) return null;
    const data = session.data()!;
    if (isTerminal(data.state)) return null;

    const snapshot = snapshotOf(data, members as string[]);
    const submitted = step(snapshot, { type: 'SUBMIT', uid, chapter });
    if (submitted.status === 'illegal') return null;

    const update: Record<string, unknown> = {};
    const now = Timestamp.fromMillis(nowMs);
    let state = data.state as string;
    if (submitted.status === 'changed') {
      state = submitted.to.state;
      update.state = state;
      update[`submittedBy.${uid}`] = now;
      update.updatedAt = now;
    }

    // The last answer in: the reveal both partners read, written once, from what they wrote.
    if (state === 'BOTH_SUBMITTED') {
      const answers = await answersFor(tx, submissionsRef, chapter, members as string[]);
      const ready = step({ ...snapshot, state: 'BOTH_SUBMITTED' }, { type: 'REVEAL_READY', chapter });
      if (ready.status === 'changed') {
        if (!reveal.exists) tx.set(revealRef, { chapter, answers, at: now });
        update.state = ready.to.state;
      }
    }
    if (Object.keys(update).length > 0) tx.update(sessionRef, update);
    if (submitted.status !== 'changed') return null;
    // One of them has answered. The other is told that, and never what was answered (section 12.2):
    // that they finished their part, or — when this answer completed the round — that both are in.
    const reached = (update.state as string | undefined) ?? state;
    const kind: NotificationKind = reached === 'REVEAL' ? 'BOTH_READY' : 'PARTNER_FINISHED';
    return { members: members as string[], kind };
  });

  // After the commit: a transaction may run more than once, and a phone must never be told twice.
  if (told !== null) await notifyPartner(db, told.members, uid, told.kind, sessionId, nowMs, sender);
}

/** What each partner answered for [chapter]: read inside the transaction, never sent to a client. */
async function answersFor(
  tx: FirebaseFirestore.Transaction,
  submissions: FirebaseFirestore.CollectionReference,
  chapter: number,
  members: readonly string[],
): Promise<Record<string, string | null>> {
  const docs = await Promise.all(members.map((member) => tx.get(submissions.doc(`${chapter}_${member}`))));
  const answers: Record<string, string | null> = {};
  docs.forEach((doc: DocumentData, index: number) => {
    if (doc.exists) answers[members[index]] = (doc.get('answer') as string | null) ?? null;
  });
  return answers;
}
