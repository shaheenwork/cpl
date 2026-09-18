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
import {
  Timestamp, collection, deleteDoc, doc, getDoc, getDocs, serverTimestamp, setDoc, updateDoc,
} from 'firebase/firestore';

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

/** A well-formed private boundary, stamped the way the app stamps it. */
const limit = (level) => ({ level, updatedAt: serverTimestamp() });

/** A well-formed private answer, stamped the way the app stamps it. */
const answer = (value, secret = false) => ({
  value,
  secret,
  updatedAt: serverTimestamp(),
  taxonomyVersion: 1,
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

// Section 3.1. The 18+ attestation is an audit record: well-formed, and stamped with the
// server's time rather than the device's.
describe('users/{uid}.ageAttestation', () => {
  const CAROL = 'uid_carol';
  const carol = () => testEnv.authenticatedContext(CAROL).firestore();

  it('accepts an attestation stamped with the server time', async () => {
    await assertSucceeds(
      setDoc(doc(carol(), 'users', CAROL), {
        ageAttestation: { confirmed: true, at: serverTimestamp() },
      }),
    );
  });

  it('rejects an attestation with a client-chosen time', async () => {
    // Back-dated, future-dated, or simply skewed — none can be trusted for an audit record.
    await assertFails(
      setDoc(doc(carol(), 'users', `${CAROL}_b`), {
        ageAttestation: { confirmed: true, at: Timestamp.fromMillis(Date.now()) },
      }),
    );
  });

  it('rejects a malformed attestation', async () => {
    const dave = testEnv.authenticatedContext('uid_dave').firestore();
    await assertFails(
      setDoc(doc(dave, 'users', 'uid_dave'), {
        ageAttestation: { confirmed: 'yes', at: serverTimestamp() },
      }),
    );
  });

  it('rejects extra keys smuggled into the attestation', async () => {
    const erin = testEnv.authenticatedContext('uid_erin').firestore();
    await assertFails(
      setDoc(doc(erin, 'users', 'uid_erin'), {
        ageAttestation: { confirmed: true, at: serverTimestamp(), verifiedBy: 'admin' },
      }),
    );
  });

  it('still allows ordinary profile edits long after attesting', async () => {
    // Regression: an earlier draft of the rules re-validated the stored attestation time on
    // every update, which would have blocked all profile edits once it was a day old.
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'users', 'uid_frank'), {
        ageAttestation: { confirmed: true, at: Timestamp.fromMillis(Date.UTC(2020, 0, 1)) },
      });
    });
    const frank = testEnv.authenticatedContext('uid_frank').firestore();
    await assertSucceeds(updateDoc(doc(frank, 'users', 'uid_frank'), { displayName: 'Frank' }));
  });

  it('rejects an overlong display name', async () => {
    const gina = testEnv.authenticatedContext('uid_gina').firestore();
    await assertFails(setDoc(doc(gina, 'users', 'uid_gina'), { displayName: 'x'.repeat(61) }));
  });

  it('rejects a content level outside 1..5', async () => {
    const hank = testEnv.authenticatedContext('uid_hank').firestore();
    await assertFails(setDoc(doc(hank, 'users', 'uid_hank'), { contentLevel: 6 }));
    await assertFails(setDoc(doc(hank, 'users', 'uid_hank'), { contentLevel: 0 }));
  });
});

// Section 8 and 7.3 rows K and M. Couples are created and joined only by Cloud Functions,
// inside transactions; clients can read their own couple and nothing else.
describe('couples/{coupleId}', () => {
  const seed = async (status = 'ACTIVE') => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      await setDoc(doc(db, 'couples', 'c1'), { status, memberUids: [ALICE, BOB], contentLevelEffective: 2 });
      await setDoc(doc(db, 'couples', 'c1', 'members', ALICE), { role: 'CREATOR', status: 'ACTIVE' });
      await setDoc(doc(db, 'couples', 'c1', 'members', BOB), { role: 'PARTNER', status: 'ACTIVE' });
    });
  };
  const mallory = () => testEnv.authenticatedContext('uid_mallory').firestore();

  it('lets both members read their couple', async () => {
    await seed();
    await assertSucceeds(getDoc(doc(alice(), 'couples', 'c1')));
    await assertSucceeds(getDoc(doc(bob(), 'couples', 'c1')));
  });

  it('denies a third party reading a couple (7.3 test K)', async () => {
    await seed();
    await assertFails(getDoc(doc(mallory(), 'couples', 'c1')));
    await assertFails(getDoc(doc(mallory(), 'couples', 'c1', 'members', ALICE)));
  });

  it('denies listing couples at all', async () => {
    await seed();
    await assertFails(getDocs(collection(alice(), 'couples')));
  });

  it('denies a client creating a couple', async () => {
    await assertFails(setDoc(doc(mallory(), 'couples', 'forged'), {
      status: 'ACTIVE', memberUids: ['uid_mallory', ALICE],
    }));
  });

  it('denies adding a third member (7.3 test M)', async () => {
    await seed();
    await assertFails(setDoc(doc(mallory(), 'couples', 'c1', 'members', 'uid_mallory'), { status: 'ACTIVE' }));
    // Not even a genuine member can add one.
    await assertFails(setDoc(doc(alice(), 'couples', 'c1', 'members', 'uid_mallory'), { status: 'ACTIVE' }));
  });

  it("denies even a member reading their own couple's engineFilters (7.3 test L)", async () => {
    await seed();
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'couples', 'c1', 'engineFilters', 'current'), {
        excludedThemes: ['sensory_blindfold'], maxIntensity: 2,
      });
    });
    // Membership is exactly what would let a partner diff the combined set against their
    // own boundaries, so it must not help.
    await assertFails(getDoc(doc(alice(), 'couples', 'c1', 'engineFilters', 'current')));
    await assertFails(getDocs(collection(bob(), 'couples', 'c1', 'engineFilters')));
    await assertFails(setDoc(doc(alice(), 'couples', 'c1', 'engineFilters', 'current'), { excludedThemes: [] }));
  });

  it('denies a member setting the couple ceiling (7.3 test H)', async () => {
    await seed();
    await assertFails(updateDoc(doc(alice(), 'couples', 'c1'), { contentLevelEffective: 5 }));
  });

  it('denies a member rewriting the member list', async () => {
    await seed();
    await assertFails(updateDoc(doc(alice(), 'couples', 'c1'), { memberUids: [ALICE, 'uid_mallory'] }));
  });

  it('shuts both partners out once unpaired', async () => {
    await seed('UNPAIRED');
    await assertFails(getDoc(doc(alice(), 'couples', 'c1')));
    await assertFails(getDoc(doc(bob(), 'couples', 'c1', 'members', ALICE)));
  });

  it('denies a client forging its own pairing state', async () => {
    // Otherwise a user could mark a request approved, or plant verification symbols.
    await assertFails(setDoc(doc(alice(), 'users', ALICE), {
      pairing: { role: 'CREATOR', status: 'REQUESTED', code: '123456', verification: ['x', 'y', 'z'] },
    }, { merge: true }));
  });
});

// Section 5.1. This is the promise the whole product rests on: individual answers are
// owner-only for life. There is deliberately no rule anywhere — not on match, not on
// reveal — that grants a partner read access to these paths.
describe('private, owner-only for life', () => {
  it('lets the owner write and read their own preferences', async () => {
    await assertSucceeds(
      setDoc(doc(alice(), 'users', ALICE, 'preferences', 'teasing_verbal'), answer('CURIOUS', true)),
    );
    await assertSucceeds(getDoc(doc(alice(), 'users', ALICE, 'preferences', 'teasing_verbal')));
    await assertSucceeds(getDocs(collection(alice(), 'users', ALICE, 'preferences')));
  });

  it('denies a partner reading private preferences (7.3 test A)', async () => {
    await assertFails(getDoc(doc(bob(), 'users', ALICE, 'preferences', 'teasing_verbal')));
  });

  it('denies a partner listing private preferences (7.3 test A)', async () => {
    await assertFails(getDocs(collection(bob(), 'users', ALICE, 'preferences')));
  });

  it('denies a partner writing or deleting private preferences', async () => {
    const path = ['users', ALICE, 'preferences', 'teasing_verbal'];
    await assertFails(setDoc(doc(bob(), ...path), answer('NEVER')));
    await assertFails(deleteDoc(doc(bob(), ...path)));
  });

  it('denies an unauthenticated read of private preferences', async () => {
    await assertFails(getDocs(collection(anon(), 'users', ALICE, 'preferences')));
  });

  it('denies a partner reading private boundaries (7.3 test B)', async () => {
    await assertSucceeds(setDoc(doc(alice(), 'users', ALICE, 'boundaries', 'power_play'), limit('NEVER')));
    await assertFails(getDoc(doc(bob(), 'users', ALICE, 'boundaries', 'power_play')));
  });

  it('denies a partner listing private boundaries (7.3 test B)', async () => {
    await assertFails(getDocs(collection(bob(), 'users', ALICE, 'boundaries')));
  });

  it('denies a partner overwriting or clearing private boundaries', async () => {
    await assertFails(setDoc(doc(bob(), 'users', ALICE, 'boundaries', 'power_play'), limit('ALWAYS_OK')));
    await assertFails(deleteDoc(doc(bob(), 'users', ALICE, 'boundaries', 'power_play')));
  });

  it('denies a client writing its own affinity scores', async () => {
    await assertFails(setDoc(doc(alice(), 'users', ALICE, 'affinity', 'teasing'), { score: 99 }));
  });
});

// Section 6.1. The shape of a private answer: exactly four fields, a known value, secret only
// with CURIOUS, and the server's time. Nothing may ride along in these documents.
describe('users/{uid}/preferences shape', () => {
  const pref = (id = 'mood_romantic') => doc(alice(), 'users', ALICE, 'preferences', id);

  it('accepts every answer the app can give', async () => {
    for (const value of ['YES', 'CURIOUS', 'MAYBE', 'NOT_FOR_ME', 'NEVER']) {
      await assertSucceeds(setDoc(pref(), answer(value)));
    }
    await assertSucceeds(setDoc(pref(), answer('CURIOUS', true)));
  });

  it('rejects secret on anything but CURIOUS', async () => {
    for (const value of ['YES', 'MAYBE', 'NOT_FOR_ME', 'NEVER']) {
      await assertFails(setDoc(pref(), answer(value, true)));
    }
  });

  it('rejects an unknown value', async () => {
    await assertFails(setDoc(pref(), answer('ABSOLUTELY')));
    await assertFails(setDoc(pref(), { ...answer('YES'), value: 1 }));
  });

  it('rejects extra fields riding along', async () => {
    await assertFails(setDoc(pref(), { ...answer('YES'), partnerUid: BOB }));
    await assertFails(setDoc(pref(), { ...answer('YES'), note: 'anything at all' }));
  });

  it('rejects a missing field', async () => {
    const { taxonomyVersion, ...withoutVersion } = answer('YES');
    await assertFails(setDoc(pref(), withoutVersion));
    const { secret, ...withoutSecret } = answer('YES');
    await assertFails(setDoc(pref(), withoutSecret));
  });

  it('rejects a client-chosen time', async () => {
    await assertFails(setDoc(pref(), { ...answer('YES'), updatedAt: Timestamp.fromMillis(Date.now()) }));
  });

  it('rejects a bad taxonomy version', async () => {
    await assertFails(setDoc(pref(), { ...answer('YES'), taxonomyVersion: 0 }));
    await assertFails(setDoc(pref(), { ...answer('YES'), taxonomyVersion: '1' }));
  });

  it('rejects an id the taxonomy could never produce', async () => {
    await assertFails(setDoc(pref('Mood-Romantic'), answer('YES')));
    await assertFails(setDoc(pref('a'.repeat(65)), answer('YES')));
  });

  it('lets the owner change an answer and forget it', async () => {
    await assertSucceeds(setDoc(pref(), answer('MAYBE')));
    await assertSucceeds(setDoc(pref(), answer('CURIOUS', true)));
    await assertSucceeds(deleteDoc(pref()));
  });
});

// Section 6.1. The shape of a private boundary: a known level, an optional short note to
// self, the server's time, and nothing else.
describe('users/{uid}/boundaries shape', () => {
  const theme = (id = 'sensory_blindfold') => doc(alice(), 'users', ALICE, 'boundaries', id);

  it('accepts every level, with or without a note', async () => {
    for (const level of ['ALWAYS_OK', 'CURIOUS', 'ASK_FIRST', 'NOT_TONIGHT', 'NEVER']) {
      await assertSucceeds(setDoc(theme(), limit(level)));
    }
    await assertSucceeds(setDoc(theme(), { ...limit('ASK_FIRST'), note: 'Only after we have talked.' }));
    await assertSucceeds(setDoc(theme(), { ...limit('ASK_FIRST'), note: null }));
  });

  it('rejects an unknown level', async () => {
    await assertFails(setDoc(theme(), limit('SOMETIMES')));
    await assertFails(setDoc(theme(), { ...limit('NEVER'), level: 5 }));
  });

  it('rejects extra fields, a missing level, and a long or non-text note', async () => {
    await assertFails(setDoc(theme(), { ...limit('NEVER'), partnerUid: BOB }));
    await assertFails(setDoc(theme(), { updatedAt: serverTimestamp() }));
    await assertFails(setDoc(theme(), { ...limit('NEVER'), note: 'x'.repeat(201) }));
    await assertFails(setDoc(theme(), { ...limit('NEVER'), note: 42 }));
  });

  it('rejects a client-chosen time', async () => {
    await assertFails(setDoc(theme(), { level: 'NEVER', updatedAt: Timestamp.fromMillis(Date.now()) }));
  });

  it('rejects a theme id the taxonomy could never produce', async () => {
    await assertFails(setDoc(theme('Sensory Blindfold'), limit('NEVER')));
  });

  it('lets the owner change a boundary and clear it', async () => {
    await assertSucceeds(setDoc(theme(), limit('NOT_TONIGHT')));
    await assertSucceeds(setDoc(theme(), limit('CURIOUS')));
    await assertSucceeds(deleteDoc(theme()));
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
