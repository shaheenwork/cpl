/**
 * A game over HTTP through the Functions emulator, exactly as the Android client plays one:
 * startGame from the host's token, the rounds read back through the security rules by either
 * partner, and the game played through the same session callables as a night.
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

/** A read through the security rules, with [user]'s own token. */
async function clientRead(user: User, path: string): Promise<{ status: number; fields?: Record<string, any> }> {
  const res = await fetch(`${FIRESTORE}/${path}`, { headers: { Authorization: `Bearer ${user.idToken}` } });
  const body = (await res.json()) as { fields?: Record<string, any> };
  return { status: res.status, fields: body.fields };
}

describe('a game, end to end', () => {
  let host: User;
  let partner: User;
  let stranger: User;
  let coupleId: string;

  before(async () => {
    await fetch(`http://127.0.0.1:8080/emulator/v1/projects/${PROJECT}/databases/(default)/documents`, { method: 'DELETE' });
    host = await signUp();
    partner = await signUp();
    stranger = await signUp();
    const code = (await ok('createPairingCode', null, host)).code;
    await ok('requestPairing', { code }, partner);
    coupleId = (await ok('respondToPairing', { approve: true }, host)).coupleId as string;
    assert.ok(coupleId);
    await ok('verifyPurchase', { purchaseToken: 'dev-e2e-game' }, host);
  });

  it('refuses a signed-out caller, and anyone not in a couple', async () => {
    assert.equal((await call('startGame', { game: 'ME_OR_YOU', intensity: 1 })).error?.status, 'UNAUTHENTICATED');
    const refused = await call('startGame', { game: 'ME_OR_YOU', intensity: 1 }, stranger);
    assert.equal(refused.error?.status, 'FAILED_PRECONDITION');
    assert.equal(refused.error?.message, 'not-paired');
  });

  it('starts a game both partners can read, and plays it through a session', async () => {
    const game = await ok('startGame', { game: 'TRUTH_OR_DARE', intensity: 2 }, host);
    const rounds = game.rounds as { contentIds: string[]; intensity: number }[];
    assert.ok(rounds.length >= 3);
    for (const round of rounds) assert.equal(round.contentIds.length, 2, 'a truth and a dare');

    const path = `couples/${coupleId}/experiences/${game.experienceId as string}`;
    for (const user of [host, partner]) {
      const read = await clientRead(user, path);
      assert.equal(read.status, 200);
      assert.equal(read.fields?.type?.stringValue, 'GAME');
    }
    assert.equal((await clientRead(stranger, path)).status, 403);

    const session = await ok('createSession', { experienceId: game.experienceId }, host);
    assert.equal(session.state, 'READY');
    const sessionId = session.sessionId as string;
    const stored = await clientRead(partner, `couples/${coupleId}/sessions/${sessionId}`);
    assert.equal(stored.fields?.experienceType?.stringValue, 'GAME', 'the partner\'s phone knows what is under way');

    let view = await ok('advanceSession', { sessionId, fromChapter: -1 }, host);
    view = await ok('advanceSession', { sessionId, fromChapter: 0, playedItemId: rounds[0].contentIds[1] }, host);
    for (let round = 1; round < rounds.length; round++) view = await ok('advanceSession', { sessionId, fromChapter: round }, host);
    assert.equal(view.state, 'COMPLETED');
  });

  it('refuses a payload choosing the seed, and a game that does not exist', async () => {
    assert.equal((await call('startGame', { game: 'ME_OR_YOU', intensity: 1, seed: 7 }, host)).error?.status, 'INVALID_ARGUMENT');
    assert.equal((await call('startGame', { game: 'NOT_A_GAME', intensity: 1 }, host)).error?.status, 'INVALID_ARGUMENT');
  });
});
