/**
 * Every callable, over the wire, with nobody signed in (BUILD_PROMPT.md sections 7.2, 21.1).
 *
 * The rules tests prove what a *client* may read and write. This proves the other half: that the
 * server's own doors are shut. It reads the deployed surface out of `index.ts` rather than from a
 * list somebody has to remember to update — so a callable added in a later phase is tested the
 * moment it exists, and a callable that forgets its auth check fails this suite rather than
 * shipping.
 *
 * Requires the full emulator suite, including functions:
 *   firebase emulators:start --only auth,firestore,storage,functions --project afterhours-dev-emulator
 */
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, it } from 'node:test';

const PROJECT = 'afterhours-dev-emulator';
const FUNCTIONS = `http://127.0.0.1:5001/${PROJECT}/us-central1`;
const INDEX = readFileSync(join(__dirname, '..', '..', 'src', 'index.ts'), 'utf8');

/** The callables as they are actually exported, read from the source. */
function callables(): string[] {
  return [...INDEX.matchAll(/export const (\w+) = onCall\(/g)].map((match) => match[1]).sort();
}

/** Everything that is not a callable: triggers and schedules, which no client can reach at all. */
function background(): string[] {
  return [...INDEX.matchAll(/export const (\w+) = on(?!Call)(\w+)\(/g)].map((match) => match[1]).sort();
}

async function call(name: string, data: unknown, idToken?: string) {
  const res = await fetch(`${FUNCTIONS}/${name}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(idToken === undefined ? {} : { Authorization: `Bearer ${idToken}` }),
    },
    body: JSON.stringify({ data }),
  });
  const body = (await res.json()) as { error?: { status?: string; message?: string } };
  return { status: res.status, error: body.error };
}

describe('every door on the server', () => {
  it('found the surface it is testing', () => {
    const names = callables();
    assert.ok(names.length >= 25, `expected the whole callable surface, found ${names.length}`);
    // The ones this phase is about, so a rename cannot quietly empty the list.
    for (const name of ['deleteAccount', 'requestDataExport', 'reportContent', 'verifyPurchase']) {
      assert.ok(names.includes(name), `${name} is not exported as a callable`);
    }
    assert.ok(background().length > 0, 'triggers and schedules should exist too');
  });

  it('refuses every callable to a caller with no token', async () => {
    for (const name of callables()) {
      const { error } = await call(name, {});
      assert.equal(error?.status, 'UNAUTHENTICATED', `${name} answered ${error?.status} to a stranger`);
    }
  });

  it('refuses every callable to a caller with a token it cannot verify', async () => {
    for (const name of callables()) {
      const { error } = await call(name, {}, 'not.a.real.token');
      assert.equal(error?.status, 'UNAUTHENTICATED', `${name} accepted a forged token`);
    }
  });

  it('says nothing about itself while refusing', async () => {
    for (const name of callables()) {
      const { error } = await call(name, {});
      const message = error?.message ?? '';
      // No stack, no internals, no hint about what the payload should have been (§7.2).
      assert.ok(!/at |\.ts:|firebase-admin|Firestore|node_modules/.test(message), `${name} leaked "${message}"`);
    }
  });

  it('keeps every callable behind the same options, App Check included', () => {
    // `options` carries enforceAppCheck (outside the emulator) and a maxInstances cap. A callable
    // declared with its own inline options would sidestep both.
    const declarations = [...INDEX.matchAll(/export const (\w+) = onCall\(([^,]+),/g)];
    assert.equal(declarations.length, callables().length, 'a callable declared without options');
    for (const [, name, passed] of declarations) {
      assert.equal(passed.trim(), 'options', `${name} does not use the shared options`);
    }
    assert.match(INDEX, /const enforceAppCheck = process\.env\.FUNCTIONS_EMULATOR !== 'true'/);
    assert.match(INDEX, /const options = \{ enforceAppCheck, maxInstances: \d+ \}/);
  });

  it('never trusts a uid or a coupleId from a payload', () => {
    // Identity comes from the verified token, through callerUid, and from nowhere else (§7.2).
    assert.match(INDEX, /function callerUid\(request: CallableRequest\): string \{/);
    const suspicious = [...INDEX.matchAll(/request\.data[^\n]*\b(uid|coupleId)\b/g)].map((match) => match[0]);
    assert.deepEqual(suspicious, [], `a callable reads identity from the payload: ${suspicious.join(', ')}`);
  });
});
