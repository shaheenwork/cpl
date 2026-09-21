/**
 * generateExperience over HTTP through the Functions emulator, exactly as the Android client
 * will call it: identity from the token, the couple from the server's records, errors as
 * callable codes, and the night stored for both partners.
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
    headers: { 'Content-Type': 'application/json', ...(idToken ? { Authorization: `Bearer ${idToken}` } : {}) },
    body: JSON.stringify({ data }),
  });
  return { status: res.status, ...((await res.json()) as Omit<CallResult, 'status'>) };
}

async function exists(path: string): Promise<boolean> {
  const res = await fetch(`${FIRESTORE}/${path}`, { headers: { Authorization: 'Bearer owner' } });
  return res.status === 200;
}

const night = { mode: 'TOGETHER', durationMin: 30, moods: ['romantic'], intensity: 5, leadership: 'RANDOM', novelty: 'SURPRISE_ME' };

describe('generateExperience, end to end', () => {
  let alice: { idToken: string; uid: string };
  let bob: { idToken: string; uid: string };
  let coupleId: string;

  before(async () => {
    await fetch(`http://127.0.0.1:8080/emulator/v1/projects/${PROJECT}/databases/(default)/documents`, { method: 'DELETE' });
    alice = await signUp();
    bob = await signUp();
    const code = (await call('createPairingCode', null, alice.idToken)).result?.code;
    await call('requestPairing', { code }, bob.idToken);
    coupleId = (await call('respondToPairing', { approve: true }, alice.idToken)).result?.coupleId as string;
    assert.ok(coupleId, 'the couple formed');
  });

  it('refuses an unauthenticated caller', async () => {
    assert.equal((await call('generateExperience', night)).error?.status, 'UNAUTHENTICATED');
  });

  it("builds a night within the couple's ceiling and stores it for both", async () => {
    const { result, error } = await call('generateExperience', night, alice.idToken);
    assert.equal(error, undefined, JSON.stringify(error));
    const chapters = result!.chapters as { contentId: string; intensity: number; kind: string }[];
    assert.ok(chapters.length >= 4 && chapters.length <= 5);
    assert.equal(chapters.at(-1)!.kind, 'FINALE');
    // Neither partner set a content level, so the ceiling is the default, FLIRTY.
    assert.equal(result!.effectiveIntensity, 2);
    assert.ok(chapters.every((chapter) => chapter.intensity <= 2));
    assert.ok(await exists(`couples/${coupleId}/experiences/${result!.experienceId as string}`));

    const theirs = await call('generateExperience', night, bob.idToken);
    assert.equal(theirs.error, undefined);
    assert.notEqual(theirs.result!.experienceId, result!.experienceId);
  });

  it('refuses a payload naming a couple, a uid or a seed', async () => {
    for (const extra of [{ coupleId: 'someone_elses' }, { uid: bob.uid }, { seed: 1 }]) {
      const response = await call('generateExperience', { ...night, ...extra }, alice.idToken);
      assert.equal(response.error?.status, 'INVALID_ARGUMENT', JSON.stringify(extra));
    }
  });

  it('tells an unpaired caller so, as a precondition rather than a crash', async () => {
    const loner = await signUp();
    const response = await call('generateExperience', night, loner.idToken);
    assert.equal(response.error?.status, 'FAILED_PRECONDITION');
    assert.equal(response.error?.message, 'not-paired');
  });
});
