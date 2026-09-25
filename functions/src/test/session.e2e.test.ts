/**
 * Playing a night over HTTP through the Functions emulator, exactly as the Android client
 * does: sessions through the callables, consent from each partner's own token, STOP from the
 * other phone, and a reaction written through the security rules reaching the reacting
 * partner's affinity by the real trigger.
 *
 * Requires the full emulator suite, including functions:
 *   firebase emulators:start --only auth,firestore,storage,functions --project afterhours-dev-emulator
 */
import assert from 'node:assert/strict';
import { before, describe, it } from 'node:test';

const PROJECT = 'afterhours-dev-emulator';
const FUNCTIONS = `http://127.0.0.1:5001/${PROJECT}/us-central1`;
const AUTH = 'http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1';
const DOCUMENTS = `projects/${PROJECT}/databases/(default)/documents`;
const FIRESTORE = `http://127.0.0.1:8080/v1/${DOCUMENTS}`;

interface User {
  idToken: string;
  uid: string;
}

interface CallResult {
  status: number;
  result?: Record<string, unknown>;
  error?: { status: string; message: string };
}

async function signUp(): Promise<User> {
  const res = await fetch(`${AUTH}/accounts:signUp?key=emulator`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ returnSecureToken: true }),
  });
  const body = (await res.json()) as { idToken: string; localId: string };
  return { idToken: body.idToken, uid: body.localId };
}

async function call(name: string, data: unknown, user?: User): Promise<CallResult> {
  const res = await fetch(`${FUNCTIONS}/${name}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...(user ? { Authorization: `Bearer ${user.idToken}` } : {}) },
    body: JSON.stringify({ data }),
  });
  return { status: res.status, ...((await res.json()) as Omit<CallResult, 'status'>) };
}

async function ok(name: string, data: unknown, user: User): Promise<Record<string, unknown>> {
  const response = await call(name, data, user);
  assert.equal(response.error, undefined, `${name}: ${JSON.stringify(response.error)}`);
  return response.result!;
}

/** Reads a document as the admin, bypassing rules: what the server stored. */
async function adminRead(path: string): Promise<Record<string, any> | undefined> {
  const res = await fetch(`${FIRESTORE}/${path}`, { headers: { Authorization: 'Bearer owner' } });
  if (res.status !== 200) return undefined;
  return ((await res.json()) as { fields: Record<string, any> }).fields;
}

/** A client write through the security rules, with the server's time, as the app makes it. */
async function clientCreate(user: User, path: string, fields: Record<string, unknown>, serverTimeField: string): Promise<number> {
  const res = await fetch(`http://127.0.0.1:8080/v1/${DOCUMENTS}:commit`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${user.idToken}` },
    body: JSON.stringify({
      writes: [
        {
          update: { name: `${DOCUMENTS}/${path}`, fields },
          updateTransforms: [{ fieldPath: serverTimeField, setToServerValue: 'REQUEST_TIME' }],
          currentDocument: { exists: false },
        },
      ],
    }),
  });
  return res.status;
}

async function eventually<T>(read: () => Promise<T | undefined>, timeoutMs = 20_000): Promise<T> {
  const deadline = Date.now() + timeoutMs;
  for (;;) {
    const value = await read();
    if (value !== undefined) return value;
    if (Date.now() > deadline) throw new Error('timed out waiting');
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
}

const gentle = { mode: 'TOGETHER', durationMin: 15, moods: ['romantic'], intensity: 2, leadership: 'ME', novelty: 'MIXED' };

describe('playing a night, end to end', () => {
  let host: User;
  let partner: User;
  let coupleId: string;

  before(async () => {
    await fetch(`http://127.0.0.1:8080/emulator/v1/projects/${PROJECT}/databases/(default)/documents`, { method: 'DELETE' });
    host = await signUp();
    partner = await signUp();
    const code = (await ok('createPairingCode', null, host)).code;
    await ok('requestPairing', { code }, partner);
    coupleId = (await ok('respondToPairing', { approve: true }, host)).coupleId as string;
    // More nights than the free tier allows, so this suite buys premium the way the app does: a
    // dev purchase token, which only the emulator's verifier accepts (section 19).
    await ok('verifyPurchase', { purchaseToken: 'dev-e2e-session' }, host);
    assert.ok(coupleId);
  });

  it('refuses a signed-out caller at every step', async () => {
    for (const name of ['createSession', 'markReady', 'advanceSession', 'endSession']) {
      assert.equal((await call(name, { sessionId: 'x' })).error?.status, 'UNAUTHENTICATED', name);
    }
  });

  it("plays a night through on the host's phone, and a reaction reaches the host's own affinity", async () => {
    const night = await ok('generateExperience', gentle, host);
    const chapters = night.chapters as { contentId: string }[];
    const session = await ok('createSession', { experienceId: night.experienceId }, host);
    assert.equal(session.state, 'READY');
    const sessionId = session.sessionId as string;

    assert.equal((await call('advanceSession', { sessionId, fromChapter: -1 }, partner)).error?.status, 'PERMISSION_DENIED');
    let view = await ok('advanceSession', { sessionId, fromChapter: -1 }, host);
    for (let chapter = 0; chapter < chapters.length; chapter++) view = await ok('advanceSession', { sessionId, fromChapter: chapter }, host);
    assert.equal(view.state, 'COMPLETED');

    const feedbackId = `${night.experienceId as string}_0_${host.uid}`;
    const status = await clientCreate(
      host,
      `couples/${coupleId}/feedback/${feedbackId}`,
      {
        experienceId: { stringValue: night.experienceId },
        chapterIndex: { integerValue: '0' },
        contentId: { stringValue: chapters[0].contentId },
        reaction: { stringValue: 'LOVED' },
        byUid: { stringValue: host.uid },
      },
      'at',
    );
    assert.equal(status, 200, 'the rules accept a reaction to a played chapter');

    const processed = await eventually(async () => {
      const fields = await adminRead(`couples/${coupleId}/feedback/${feedbackId}`);
      return fields?.processedAt ? fields : undefined;
    });
    assert.ok(processed.processedAt);
    const affinity = await fetch(`${FIRESTORE}/users/${host.uid}/affinity`, { headers: { Authorization: 'Bearer owner' } });
    const docs = ((await affinity.json()) as { documents?: unknown[] }).documents ?? [];
    assert.ok(docs.length >= 3, 'category, interaction type and at least one mood');
    const partners = await fetch(`${FIRESTORE}/users/${partner.uid}/affinity`, { headers: { Authorization: 'Bearer owner' } });
    assert.equal((((await partners.json()) as { documents?: unknown[] }).documents ?? []).length, 0);
  });

  it('a BOLD night starts only after both partners say "I\'m in", each on their own phone', async () => {
    // Both partners raise their own content level; the trigger rebuilds the couple's filters.
    for (const user of [host, partner]) {
      const res = await fetch(`${FIRESTORE}/users/${user.uid}?updateMask.fieldPaths=contentLevel`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${user.idToken}` },
        body: JSON.stringify({ fields: { contentLevel: { integerValue: '5' } } }),
      });
      assert.equal(res.status, 200);
    }
    await eventually(async () => {
      const couple = await adminRead(`couples/${coupleId}`);
      return couple?.contentLevelEffective?.integerValue === '5' ? true : undefined;
    });

    const night = await ok('generateExperience', { ...gentle, durationMin: 30, intensity: 5 }, host);
    const session = await ok('createSession', { experienceId: night.experienceId }, host);
    assert.equal(session.consentRequired, true);
    const sessionId = session.sessionId as string;

    await ok('markReady', { sessionId }, host);
    assert.equal((await call('advanceSession', { sessionId, fromChapter: -1 }, host)).error?.message, 'illegal-transition');
    assert.equal((await ok('markReady', { sessionId }, partner)).state, 'READY');
    assert.equal((await ok('advanceSession', { sessionId, fromChapter: -1 }, host)).state, 'CHAPTER_ACTIVE');

    // The other phone stops it: one tap, no confirmation, the night over for both.
    assert.equal((await ok('endSession', { sessionId, action: 'STOP' }, partner)).state, 'ABANDONED');
    const stored = await adminRead(`couples/${coupleId}/sessions/${sessionId}`);
    assert.equal(stored?.endReason?.stringValue, 'STOPPED');
    assert.equal(stored?.endedBy, undefined);
  });

  it('refuses a payload naming a couple or a uid, and says a second night must wait', async () => {
    const night = await ok('generateExperience', gentle, host);
    for (const extra of [{ coupleId: 'someone_elses' }, { uid: partner.uid }]) {
      const response = await call('createSession', { experienceId: night.experienceId, ...extra }, host);
      assert.equal(response.error?.status, 'INVALID_ARGUMENT', JSON.stringify(extra));
    }
    await ok('createSession', { experienceId: night.experienceId }, host);
    const another = await ok('generateExperience', gentle, partner);
    const refused = await call('createSession', { experienceId: another.experienceId }, partner);
    assert.equal(refused.error?.status, 'FAILED_PRECONDITION');
    assert.equal(refused.error?.message, 'session-in-progress');
  });
});
