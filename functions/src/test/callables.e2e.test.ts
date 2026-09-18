/**
 * The deployed shape of the pairing callables, exercised over HTTP through the Functions
 * emulator exactly as the Android client calls them.
 *
 * The transactional logic is covered in pairing.int.test.ts. What these add is the thin
 * wrapper that sits on the network: that identity comes from the verified auth token and
 * never from the payload, that unauthenticated calls are refused, and that server errors
 * reach the client as callable error codes rather than as internal failures.
 *
 * Requires the full emulator suite, including functions:
 *   firebase emulators:start --only auth,firestore,storage,functions --project afterhours-dev-emulator
 */
import assert from 'node:assert/strict';
import { before, describe, it } from 'node:test';

const PROJECT = 'afterhours-dev-emulator';
const FUNCTIONS = `http://127.0.0.1:5001/${PROJECT}/us-central1`;
const AUTH = 'http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1';
const FIRESTORE = `http://127.0.0.1:8080/v1/projects/${PROJECT}/databases/(default)/documents`;

interface CallResult {
  status: number;
  result?: Record<string, unknown>;
  error?: { status: string; message: string };
}

async function signUp(): Promise<{ idToken: string; uid: string }> {
  const res = await fetch(`${AUTH}/accounts:signUp?key=emulator`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ returnSecureToken: true }),
  });
  const body = (await res.json()) as { idToken: string; localId: string };
  return { idToken: body.idToken, uid: body.localId };
}

async function call(name: string, data: unknown, idToken?: string): Promise<CallResult> {
  const res = await fetch(`${FUNCTIONS}/${name}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(idToken ? { Authorization: `Bearer ${idToken}` } : {}),
    },
    body: JSON.stringify({ data }),
  });
  const body = (await res.json()) as Omit<CallResult, 'status'>;
  return { status: res.status, ...body };
}

/** Reads a document as the emulator's owner, bypassing rules. */
async function readDoc(path: string): Promise<Record<string, unknown> | undefined> {
  const res = await fetch(`${FIRESTORE}/${path}`, { headers: { Authorization: 'Bearer owner' } });
  if (res.status === 404) return undefined;
  return ((await res.json()) as { fields?: Record<string, unknown> }).fields;
}

describe('pairing callables, end to end', () => {
  before(async () => {
    await fetch(`http://127.0.0.1:8080/emulator/v1/projects/${PROJECT}/databases/(default)/documents`, {
      method: 'DELETE',
    });
  });

  it('refuses an unauthenticated caller', async () => {
    const response = await call('createPairingCode', null);
    assert.equal(response.error?.status, 'UNAUTHENTICATED');
  });

  it('takes identity from the token, never from the payload', async () => {
    const alice = await signUp();
    const mallory = await signUp();

    const { result } = await call('createPairingCode', null, alice.idToken);
    const code = result?.code as string;

    // Mallory claims to be Alice in the payload. The server must ignore it: the request is
    // recorded against Mallory's own uid.
    await call('requestPairing', { code, uid: alice.uid, creatorUid: alice.uid }, mallory.idToken);

    const codeDoc = await readDoc(`pairingCodes/${code}`);
    assert.deepEqual(codeDoc?.joinerUid, { stringValue: mallory.uid });
  });

  it('runs the whole handshake over the wire', async () => {
    const creator = await signUp();
    const joiner = await signUp();

    const created = await call('createPairingCode', null, creator.idToken);
    assert.equal(created.status, 200);
    const code = created.result?.code as string;
    assert.match(code, /^[0-9]{6}$/);

    const requested = await call('requestPairing', { code }, joiner.idToken);
    assert.equal((requested.result?.verification as string[]).length, 3);

    const approved = await call('respondToPairing', { approve: true }, creator.idToken);
    const coupleId = approved.result?.coupleId as string;
    assert.ok(coupleId);

    const joinerDoc = await readDoc(`users/${joiner.uid}`);
    assert.deepEqual(joinerDoc?.coupleId, { stringValue: coupleId });
  });

  it('reports a bad code as not-found, with no internals', async () => {
    const someone = await signUp();
    const response = await call('requestPairing', { code: '000000' }, someone.idToken);
    assert.equal(response.error?.status, 'NOT_FOUND');
    assert.equal(response.error?.message, 'invalid-code');
  });

  it('validates the payload shape', async () => {
    const someone = await signUp();
    const respond = await call('respondToPairing', { approve: 'yes' }, someone.idToken);
    assert.equal(respond.error?.status, 'INVALID_ARGUMENT');

    const unpair = await call('unpairCouple', {}, someone.idToken);
    assert.equal(unpair.error?.status, 'INVALID_ARGUMENT');
  });
});
