/**
 * Firestore security-rules tests.
 *
 * These exist because of BUILD_PROMPT.md section 7.1: client-side hiding is not security.
 * If a rule does not enforce it, it is not enforced — so every privacy claim in section 5
 * gets a test here, and the pass/fail matrix in section 7.3 is filled in by the phase that
 * introduces each collection.
 *
 * Requires a running Firestore emulator:
 *   firebase emulators:start --only auth,firestore,storage --project afterhours-dev-emulator
 */

import { after, before, describe, it } from 'node:test';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import { doc, getDoc, setDoc, updateDoc } from 'firebase/firestore';

const here = dirname(fileURLToPath(import.meta.url));

const ALICE = 'uid_alice';
const BOB = 'uid_bob';

let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: 'afterhours-dev-emulator',
    firestore: {
      host: '127.0.0.1',
      port: 8080,
      rules: readFileSync(resolve(here, '..', 'firestore.rules'), 'utf8'),
    },
  });
});

after(async () => {
  await testEnv?.cleanup();
});

const alice = () => testEnv.authenticatedContext(ALICE).firestore();
const bob = () => testEnv.authenticatedContext(BOB).firestore();
const anon = () => testEnv.unauthenticatedContext().firestore();

describe('users/{uid}', () => {
  it('lets a user create and read their own document', async () => {
    await assertSucceeds(
      setDoc(doc(alice(), 'users', ALICE), { displayName: 'Alice', timezone: 'UTC' }),
    );
    await assertSucceeds(getDoc(doc(alice(), 'users', ALICE)));
  });

  it('denies reading another user document', async () => {
    await assertFails(getDoc(doc(bob(), 'users', ALICE)));
  });

  it('denies writing another user document', async () => {
    await assertFails(setDoc(doc(bob(), 'users', ALICE), { displayName: 'hijacked' }));
  });

  it('denies an unauthenticated read', async () => {
    await assertFails(getDoc(doc(anon(), 'users', ALICE)));
  });

  // Section 7.2: coupleId, entitlement and contentLevelEffective are server-written only.
  it('denies a client setting its own coupleId', async () => {
    await assertFails(updateDoc(doc(alice(), 'users', ALICE), { coupleId: 'couple_forged' }));
  });

  it('denies a client granting itself an entitlement', async () => {
    await assertFails(
      updateDoc(doc(alice(), 'users', ALICE), { entitlement: { tier: 'premium' } }),
    );
  });

  it('still allows ordinary profile edits', async () => {
    await assertSucceeds(updateDoc(doc(alice(), 'users', ALICE), { displayName: 'Alice II' }));
  });
});

// Section 5.1. This is the promise the whole product rests on: individual answers are
// owner-only for life. There is deliberately no rule anywhere — not on match, not on
// reveal — that grants a partner read access to these paths.
describe('private, owner-only for life', () => {
  it('lets the owner write and read their own preferences', async () => {
    await assertSucceeds(
      setDoc(doc(alice(), 'users', ALICE, 'preferences', 'teasing_verbal'), {
        value: 'CURIOUS',
        secret: true,
      }),
    );
    await assertSucceeds(getDoc(doc(alice(), 'users', ALICE, 'preferences', 'teasing_verbal')));
  });

  it('denies a partner reading private preferences (7.3 test A)', async () => {
    await assertFails(getDoc(doc(bob(), 'users', ALICE, 'preferences', 'teasing_verbal')));
  });

  it('denies a partner reading private boundaries (7.3 test B)', async () => {
    await setDoc(doc(alice(), 'users', ALICE, 'boundaries', 'power_play'), { level: 'NEVER' });
    await assertFails(getDoc(doc(bob(), 'users', ALICE, 'boundaries', 'power_play')));
  });

  it('denies a partner overwriting private boundaries', async () => {
    await assertFails(
      setDoc(doc(bob(), 'users', ALICE, 'boundaries', 'power_play'), { level: 'ALWAYS_OK' }),
    );
  });

  it('denies a client writing its own affinity scores', async () => {
    await assertFails(setDoc(doc(alice(), 'users', ALICE, 'affinity', 'teasing'), { score: 99 }));
  });
});

describe('server-only collections', () => {
  // Section 5.4: the combined boundary set must never reach a device, because a user
  // could diff it against their own answers to derive their partner's hard limits.
  it('denies any client read of engineFilters (7.3 test L)', async () => {
    await assertFails(getDoc(doc(alice(), 'couples', 'couple_1', 'engineFilters', 'current')));
  });

  it('denies reading pairing codes', async () => {
    await assertFails(getDoc(doc(alice(), 'pairingCodes', '123456')));
  });

  it('denies writing pairing codes', async () => {
    await assertFails(setDoc(doc(alice(), 'pairingCodes', '123456'), { coupleId: 'x' }));
  });

  // Section 3.5: reporter identity is never exposed to the reported partner.
  it('denies writing reports directly (7.3 test Q)', async () => {
    await assertFails(setDoc(doc(alice(), 'reports', 'r1'), { reason: 'unsafe' }));
  });

  it('denies reading reports', async () => {
    await assertFails(getDoc(doc(alice(), 'reports', 'r1')));
  });
});

describe('default deny', () => {
  it('denies an unknown collection', async () => {
    await assertFails(getDoc(doc(alice(), 'somethingUnplanned', 'x')));
  });

  it('denies a non-member reading a couple (7.3 test K)', async () => {
    await assertFails(getDoc(doc(alice(), 'couples', 'couple_someone_else')));
  });
});
