/**
 * Deleting an account (BUILD_PROMPT.md sections 8, 14.9, 7.2).
 *
 * Everything goes: the Auth user, every document under `users/{uid}`, every object in Storage
 * under their prefix, and the couple itself — with all of it (D-093). That last part is the
 * decision worth stating: almost nothing under a couple belongs to one person. A memory is what
 * the two of them wrote about a night they played; a reveal holds both their answers; a voice note
 * is one partner's words in the other's hands. One person cannot consent to keeping the other's
 * half, and a "deleted" account whose words survive in a shared document is not deleted.
 *
 * So deleting an account ends the couple and takes the couple's data with it, and the partner is
 * left with their own account, their own private answers and limits, and nothing of what the two
 * of them made together. The screen says exactly that before anything happens.
 *
 * Idempotent and safe to run twice: everything is a delete, and a delete of nothing is nothing.
 */
import { type Auth } from 'firebase-admin/auth';
import { type CollectionReference, type DocumentReference, type Firestore } from 'firebase-admin/firestore';
import { type Storage } from 'firebase-admin/storage';
import { bucketOf } from '../media/media';
import { ServiceError } from '../shared/errors';

/** Subcollections under a user document. Listing them by name keeps the sweep predictable. */
const USER_COLLECTIONS = ['preferences', 'boundaries', 'affinity', 'fcmTokens', 'rateLimits'];

/** Subcollections under a couple. A session's own children are walked separately. */
const COUPLE_COLLECTIONS = [
  'members',
  'experiences',
  'memories',
  'media',
  'surprises',
  'countdowns',
  'arcs',
  'plans',
  'feedback',
  'mutualPreferences',
  'engineState',
  'engineFilters',
  'revealQueue',
];

export interface DeleteResult {
  /** What was removed, for the log and for the tests. Never returned to another client. */
  documents: number;
  objects: number;
  coupleEnded: boolean;
  /**
   * Deletes that would not go, after retries. The account still goes: a cascade that stops
   * half-way is worse than one that finishes and says what it could not reach (D-094).
   */
  failures: number;
}

function invalid(reason: string): never {
  throw new ServiceError('invalid-argument', reason);
}

export function parseDelete(input: unknown): void {
  if (typeof input !== 'object' || input === null || Array.isArray(input)) invalid('request');
  const raw = input as Record<string, unknown>;
  for (const key of Object.keys(raw)) if (key !== 'confirm') invalid(`unknown field ${key}`);
  // The confirmation is in the payload because deleting an account must never be one stray tap
  // away on a phone someone else is holding.
  if (raw.confirm !== true) throw new ServiceError('failed-precondition', 'not-confirmed');
}

/**
 * Deleting documents a phone is watching is noisy: a listener dropping as its document goes can
 * come back as CANCELLED on the write that caused it. Those are retried, and a delete that still
 * will not go is counted rather than allowed to abandon the rest of the cascade (D-094).
 */
const TRANSIENT = /CANCELLED|ABORTED|UNAVAILABLE|DEADLINE_EXCEEDED|already cancelled/i;

const RETRIES = 3;

interface Tally {
  documents: number;
  objects: number;
  failures: number;
}

async function attempt(tally: Tally, work: () => Promise<void>): Promise<boolean> {
  for (let tries = 0; tries < RETRIES; tries += 1) {
    try {
      await work();
      return true;
    } catch (error) {
      if (!TRANSIENT.test(String(error))) {
        console.error('delete step failed', error);
        tally.failures += 1;
        return false;
      }
    }
  }
  tally.failures += 1;
  return false;
}

/** Deletes a document and everything under it, whatever depth it goes to. */
async function deleteDeep(tally: Tally, ref: DocumentReference): Promise<void> {
  const children = await ref.listCollections().catch(() => []);
  for (const child of children) await deleteCollection(tally, child);
  if (await attempt(tally, async () => { await ref.delete(); })) tally.documents += 1;
}

async function deleteCollection(tally: Tally, ref: CollectionReference): Promise<void> {
  const docs = await ref.listDocuments().catch(() => []);
  for (const doc of docs) await deleteDeep(tally, doc);
}

/** Everything in Storage under a prefix: avatars, outbox, exports, and a couple's media. */
async function deleteObjects(tally: Tally, storage: Storage, prefix: string): Promise<void> {
  const found = await bucketOf(storage).getFiles({ prefix }).catch(() => [[]]);
  for (const file of found[0]) {
    if (await attempt(tally, async () => { await file.delete({ ignoreNotFound: true }); })) {
      tally.objects += 1;
    }
  }
}

/**
 * The cascade. Firestore first, then Storage, then Auth — in that order, because a signed-in
 * phone losing its account mid-sweep must not leave documents nobody can reach any more.
 */
export async function deleteAccount(
  db: Firestore,
  auth: Auth,
  storage: Storage,
  uid: string,
  input: unknown,
  _nowMs: number,
): Promise<DeleteResult> {
  parseDelete(input);

  const userRef = db.doc(`users/${uid}`);
  const coupleId = (await userRef.get()).get('coupleId');
  const tally: Tally = { documents: 0, objects: 0, failures: 0 };
  let coupleEnded = false;

  if (typeof coupleId === 'string' && coupleId.length > 0) {
    const coupleRef = db.doc(`couples/${coupleId}`);
    const couple = await coupleRef.get();
    const members: string[] = Array.isArray(couple.get('memberUids')) ? couple.get('memberUids') : [];

    // The other partner keeps their own account and their own private documents, and is cut
    // loose from a couple that no longer exists.
    for (const member of members) {
      if (member !== uid) {
        await attempt(tally, async () => {
          await db.doc(`users/${member}`).set({ coupleId: null }, { merge: true });
        });
      }
    }
    for (const name of COUPLE_COLLECTIONS) {
      await deleteCollection(tally, coupleRef.collection(name));
    }
    // Sessions carry submissions, rounds and presence beneath them.
    await deleteCollection(tally, coupleRef.collection('sessions'));
    await deleteObjects(tally, storage, `couples/${coupleId}/`);
    if (couple.exists && await attempt(tally, async () => { await coupleRef.delete(); })) {
      tally.documents += 1;
      coupleEnded = true;
    }
  }

  for (const name of USER_COLLECTIONS) {
    await deleteCollection(tally, userRef.collection(name));
  }
  // Anything a later phase added under the user and this list has not heard of.
  for (const child of await userRef.listCollections().catch(() => [])) {
    await deleteCollection(tally, child);
  }
  if ((await userRef.get()).exists && await attempt(tally, async () => { await userRef.delete(); })) {
    tally.documents += 1;
  }
  await deleteObjects(tally, storage, `users/${uid}/`);

  // Last: with the account gone, nothing above can be reached by that phone again.
  try {
    await auth.deleteUser(uid);
  } catch (error) {
    // Already gone is the outcome we wanted. Anything else is worth knowing about.
    if ((error as { code?: string }).code !== 'auth/user-not-found') throw error;
  }

  if (tally.failures > 0) {
    // The account itself is gone either way. What is left is unreachable by anyone, and the next
    // call finishes it — which is why this is idempotent (D-094).
    console.error('deleteAccount left', tally.failures, 'unreachable deletes for', uid);
  }
  return { ...tally, coupleEnded };
}
