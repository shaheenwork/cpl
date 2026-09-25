/**
 * Two clients in one session, over HTTP and through the security rules, exactly as two phones
 * play (BUILD_PROMPT.md section 12; Phase 12 exit: "two-client sync test green").
 *
 * Each client writes only its own answer, sees the session move as the other answers, and gets
 * the reveal from the reveal document — never from the other's submission, which neither can
 * read at any point.
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

/** A read through the rules, with this client's own token. */
async function clientRead(user: User, path: string): Promise<{ status: number; fields?: Record<string, any> }> {
  const res = await fetch(`${FIRESTORE}/${path}`, { headers: { Authorization: `Bearer ${user.idToken}` } });
  const body = (await res.json()) as { fields?: Record<string, any> };
  return { status: res.status, fields: body.fields };
}

/** A client write through the rules, with the server's time, as the app makes it. */
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

describe('two phones, one session, end to end', () => {
  let alex: User;
  let sam: User;
  let coupleId: string;

  before(async () => {
    await fetch(`http://127.0.0.1:8080/emulator/v1/projects/${PROJECT}/databases/(default)/documents`, { method: 'DELETE' });
    alex = await signUp();
    sam = await signUp();
    const code = (await ok('createPairingCode', null, alex)).code;
    await ok('requestPairing', { code }, sam);
    coupleId = (await ok('respondToPairing', { approve: true }, alex)).coupleId as string;
    assert.ok(coupleId);
    await ok('verifyPurchase', { purchaseToken: 'dev-e2e-realtime' }, alex);
  });

  it('plays a round from both phones: each answers its own, the reveal comes from the server', async () => {
    const game = await ok('startGame', { game: 'WOULD_YOU_RATHER', intensity: 1 }, alex);
    const session = await ok('createSession', { experienceId: game.experienceId, kind: 'TWO_DEVICE' }, alex);
    const sessionId = session.sessionId as string;
    await ok('advanceSession', { sessionId, fromChapter: -1 }, alex);

    const sessionPath = `couples/${coupleId}/sessions/${sessionId}`;
    const state = async () => (await clientRead(sam, sessionPath)).fields?.state?.stringValue;
    assert.equal(await state(), 'CHAPTER_ACTIVE');

    // Alex answers on Alex's phone. Sam's phone sees the session waiting, and nothing else.
    const mine = { chapter: { integerValue: '0' }, byUid: { stringValue: alex.uid }, answer: { stringValue: '0' } };
    assert.equal(await clientCreate(alex, `${sessionPath}/submissions/0_${alex.uid}`, mine, 'at'), 200);
    assert.equal(await eventually(async () => ((await state()) === 'AWAITING_SUBMISSIONS' ? true : undefined)), true);
    assert.equal((await clientRead(sam, `${sessionPath}/submissions/0_${alex.uid}`)).status, 403);
    assert.equal((await clientRead(sam, `${sessionPath}/rounds/0`)).status, 404, 'nothing to reveal yet');

    // Sam answers on Sam's. The server writes the one reveal both may read.
    const theirs = { chapter: { integerValue: '0' }, byUid: { stringValue: sam.uid }, answer: { stringValue: '1' } };
    assert.equal(await clientCreate(sam, `${sessionPath}/submissions/0_${sam.uid}`, theirs, 'at'), 200);
    const reveal = await eventually(async () => {
      const read = await clientRead(alex, `${sessionPath}/rounds/0`);
      return read.status === 200 ? read.fields : undefined;
    });
    const answers = reveal!.answers.mapValue.fields;
    assert.equal(answers[alex.uid].stringValue, '0');
    assert.equal(answers[sam.uid].stringValue, '1');
    assert.equal(await state(), 'REVEAL');

    // Neither phone can read the other's answer, now that both are in and revealed.
    assert.equal((await clientRead(alex, `${sessionPath}/submissions/0_${sam.uid}`)).status, 403);
    assert.equal((await clientRead(sam, `${sessionPath}/submissions/0_${alex.uid}`)).status, 403);
    // Nor forge the reveal of the next round, nor the session's state.
    const forged = { chapter: { integerValue: '1' }, answers: { mapValue: { fields: {} } } };
    assert.equal(await clientCreate(alex, `${sessionPath}/rounds/1`, forged, 'at'), 403);

    // The chapter is over when both have seen it, and then either phone moves it on.
    assert.equal((await ok('markSeen', { sessionId, chapter: 0 }, alex)).state, 'REVEAL');
    assert.equal((await ok('markSeen', { sessionId, chapter: 0 }, sam)).state, 'CHAPTER_COMPLETE');
    const next = await ok('advanceSession', { sessionId, fromChapter: 0 }, sam);
    assert.equal(next.state, 'CHAPTER_ACTIVE');
    assert.equal(next.currentChapter, 1);

    // And each phone says it is still there, for itself alone.
    assert.equal(await clientCreate(alex, `${sessionPath}/presence/${alex.uid}`, {}, 'at'), 200);
    assert.equal(await clientCreate(alex, `${sessionPath}/presence/${sam.uid}`, {}, 'at'), 403);
  });

  it('refuses an answer to a chapter nobody is on, and a signed-out caller', async () => {
    // One session at a time per couple: the round above is still under way.
    const playing = (await clientRead(alex, `couples/${coupleId}`)).fields?.activeSessionId?.stringValue;
    if (playing) await ok('endSession', { sessionId: playing, action: 'STOP' }, alex);
    const game = await ok('startGame', { game: 'ME_OR_YOU', intensity: 1 }, alex);
    const session = await ok('createSession', { experienceId: game.experienceId, kind: 'TWO_DEVICE' }, sam);
    const sessionId = session.sessionId as string;
    const path = `couples/${coupleId}/sessions/${sessionId}/submissions`;
    const early = { chapter: { integerValue: '0' }, byUid: { stringValue: alex.uid }, answer: { nullValue: null } };
    // The session has not started: no chapter is on screen yet.
    assert.equal(await clientCreate(alex, `${path}/0_${alex.uid}`, early, 'at'), 403);
    assert.equal((await call('markSeen', { sessionId, chapter: 0 })).error?.status, 'UNAUTHENTICATED');
  });
});
