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

// Section 16. Where a phone can be reached, and what the server checks before reaching it.
describe('users/{uid}/fcmTokens and notificationSettings (§16)', () => {
  const token = () => ({ token: 'fcm-token-abc', platform: 'ANDROID', updatedAt: serverTimestamp() });

  it('lets a user register their own phone, and read and drop it', async () => {
    await assertSucceeds(setDoc(doc(alice(), 'users', ALICE, 'fcmTokens', 't1'), token()));
    await assertSucceeds(getDocs(collection(alice(), 'users', ALICE, 'fcmTokens')));
    await assertSucceeds(deleteDoc(doc(alice(), 'users', ALICE, 'fcmTokens', 't1')));
  });

  it("denies writing a token into someone else's list, which would forward their notifications", async () => {
    await assertFails(setDoc(doc(bob(), 'users', ALICE, 'fcmTokens', 't2'), token()));
    await assertFails(getDocs(collection(bob(), 'users', ALICE, 'fcmTokens')));
  });

  it("holds a token to its shape, and its time to the server's", async () => {
    const at = (fields) => setDoc(doc(alice(), 'users', ALICE, 'fcmTokens', 't3'), fields);
    await assertFails(at({ token: 'fcm-token-abc', platform: 'ANDROID', updatedAt: Timestamp.now() }));
    await assertFails(at({ token: 'fcm-token-abc', platform: 'IOS', updatedAt: serverTimestamp() }));
    await assertFails(at({ token: '', platform: 'ANDROID', updatedAt: serverTimestamp() }));
    await assertFails(at({ ...token(), coupleId: 'couple_1' }));
    await assertFails(at({ platform: 'ANDROID', updatedAt: serverTimestamp() }));
  });

  it('holds notification settings to a shape the server can read', async () => {
    const settings = (notificationSettings) => updateDoc(doc(alice(), 'users', ALICE), { notificationSettings });
    await assertSucceeds(settings({ enabled: true, showContent: false, quietHours: { from: '22:00', to: '07:00' } }));
    await assertSucceeds(settings({ enabled: false, showContent: false, quietHours: null }));
    await assertFails(settings({ enabled: 'yes' }));
    await assertFails(settings({ enabled: true, quietHours: { from: '22:00' } }));
    await assertFails(settings({ enabled: true, quietHours: { from: '10pm', to: '7am' } }));
    await assertFails(settings({ enabled: true, mute: 'everything' }));
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

  // Section 5.2 and 7.3 row R.
  const seedMatch = async () => {
    await seed();
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'couples', 'c1', 'mutualPreferences', 'sensory_massage'), {
        prefId: 'sensory_massage', matchLevel: 'BOTH_YES', revealedAt: Timestamp.now(), seenBy: {}, sourceVersion: 1,
      });
      await setDoc(doc(ctx.firestore(), 'couples', 'c1', 'revealQueue', 'mood_intense'), {
        op: 'REVEAL', matchLevel: 'BOTH_YES', queuedAt: Timestamp.now(), releaseAfter: Timestamp.now(),
      });
    });
  };
  const match = (db) => doc(db, 'couples', 'c1', 'mutualPreferences', 'sensory_massage');

  it('lets both members read their revealed matches', async () => {
    await seedMatch();
    await assertSucceeds(getDoc(match(alice())));
    await assertSucceeds(getDocs(collection(bob(), 'couples', 'c1', 'mutualPreferences')));
  });

  it('denies anyone outside the couple, and everyone once unpaired', async () => {
    await seedMatch();
    await assertFails(getDoc(match(mallory())));
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await updateDoc(doc(ctx.firestore(), 'couples', 'c1'), { status: 'UNPAIRED' });
    });
    await assertFails(getDoc(match(alice())));
  });

  it('denies a client creating a match (7.3 test R)', async () => {
    await seed();
    await assertFails(setDoc(doc(alice(), 'couples', 'c1', 'mutualPreferences', 'forged'), {
      prefId: 'forged', matchLevel: 'BOTH_YES', revealedAt: Timestamp.now(), seenBy: {}, sourceVersion: 1,
    }));
  });

  it('lets a member mark a match seen for themselves, and nothing more', async () => {
    await seedMatch();
    await assertSucceeds(updateDoc(match(alice()), { [`seenBy.${ALICE}`]: true }));
    // Not for their partner, not as false, not alongside any other change, and no deleting.
    await assertFails(updateDoc(match(alice()), { [`seenBy.${BOB}`]: true }));
    await assertFails(updateDoc(match(bob()), { [`seenBy.${BOB}`]: false }));
    await assertFails(updateDoc(match(bob()), { [`seenBy.${BOB}`]: true, matchLevel: 'BOTH_SECRET' }));
    await assertFails(updateDoc(match(bob()), { revealedAt: Timestamp.now() }));
    await assertFails(deleteDoc(match(alice())));
  });

  it('denies every client any look at the reveal queue, members included', async () => {
    await seedMatch();
    await assertFails(getDoc(doc(alice(), 'couples', 'c1', 'revealQueue', 'mood_intense')));
    await assertFails(getDocs(collection(bob(), 'couples', 'c1', 'revealQueue')));
    await assertFails(setDoc(doc(alice(), 'couples', 'c1', 'revealQueue', 'mood_intense'), { op: 'REVEAL' }));
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

describe('couples/{coupleId}/experiences (§6.1, §10)', () => {
  const MALLORY = 'uid_mallory';
  const mallory = () => testEnv.authenticatedContext(MALLORY).firestore();
  const night = { status: 'READY', chapters: [{ index: 0, kind: 'WARM_UP', contentIds: ['flirt_one_look'], intensity: 1 }] };

  before(async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      await setDoc(doc(db, 'couples', 'e1'), { status: 'ACTIVE', memberUids: [ALICE, BOB] });
      await setDoc(doc(db, 'couples', 'e1', 'experiences', 'x1'), night);
      await setDoc(doc(db, 'couples', 'e1', 'experiences', 'x1', 'askFirst', BOB), { chapters: [0] });
      // Something Alice left for Bob, before and after the server delivers it (§5.7, row S).
      await setDoc(doc(db, 'couples', 'e1', 'surprises', 's_waiting'), {
        type: 'SCHEDULED', title: 'For Friday', createdBy: ALICE, forUid: BOB, state: 'SCHEDULED',
      });
      await setDoc(doc(db, 'couples', 'e1', 'surprises', 's_waiting', 'payload', 'content'), {
        ownerUid: ALICE, text: 'the secret', deliveredAt: null,
      });
      await setDoc(doc(db, 'couples', 'e1', 'surprises', 's_delivered'), {
        type: 'SCHEDULED', title: 'For last Friday', createdBy: ALICE, forUid: BOB, state: 'DELIVERED',
      });
      await setDoc(doc(db, 'couples', 'e1', 'surprises', 's_delivered', 'payload', 'content'), {
        ownerUid: ALICE, text: 'no longer a secret', deliveredAt: Timestamp.now(),
      });
      // A memory of a night they played, as the server writes it (§14.8).
      await setDoc(doc(db, 'couples', 'e1', 'memories', 'm1'), {
        experienceId: 'n1', title: 'The Tuesday one', note: 'we talked until two',
        mood: ['naughty'], durationMin: 60, favorite: false, createdBy: ALICE, mediaIds: [],
      });
      // A few days of this, as the server writes it (§13).
      await setDoc(doc(db, 'couples', 'e1', 'arcs', 'a1'), {
        title: '7 days', lengthDays: 7, state: 'ACTIVE', createdBy: ALICE, days: [],
      });
      // A night Alice planned for Bob (§2.5 #2), and the plan both of them may see.
      await setDoc(doc(db, 'couples', 'e1', 'experiences', 'p1'), { ...night, plannedBy: ALICE, plannedFor: BOB });
      await setDoc(doc(db, 'couples', 'e1', 'plans', 'p1'), {
        experienceId: 'p1', plannedBy: ALICE, plannedFor: BOB, chapterCount: 1, state: 'WAITING',
      });
      await setDoc(doc(db, 'couples', 'e1', 'engineState', 'history'), { lastPlayed: { flirt_one_look: 1 } });
      await setDoc(doc(db, 'couples', 'gone'), { status: 'UNPAIRED', memberUids: [ALICE, BOB] });
      await setDoc(doc(db, 'couples', 'gone', 'experiences', 'x2'), night);
    });
  });

  it('lets both members read their nights, and nobody list them', async () => {
    await assertSucceeds(getDoc(doc(alice(), 'couples', 'e1', 'experiences', 'x1')));
    await assertSucceeds(getDoc(doc(bob(), 'couples', 'e1', 'experiences', 'x1')));
    // Listing would hand a planner every night they must not read (§2.5 #2).
    await assertFails(getDocs(collection(alice(), 'couples', 'e1', 'experiences')));
  });

  it('keeps a planned night from the partner who planned it (§2.5 #2)', async () => {
    await assertSucceeds(getDoc(doc(bob(), 'couples', 'e1', 'experiences', 'p1')));
    await assertFails(getDoc(doc(alice(), 'couples', 'e1', 'experiences', 'p1')));
    await assertFails(getDoc(doc(mallory(), 'couples', 'e1', 'experiences', 'p1')));
  });

  it('S: denies reading a surprise payload before the server delivers it (§5.7)', async () => {
    // That something is waiting is the hook: both may see the surprise itself.
    await assertSucceeds(getDoc(doc(bob(), 'couples', 'e1', 'surprises', 's_waiting')));
    await assertSucceeds(getDocs(collection(bob(), 'couples', 'e1', 'surprises')));

    // What is in it is another matter.
    await assertFails(getDoc(doc(bob(), 'couples', 'e1', 'surprises', 's_waiting', 'payload', 'content')));
    await assertFails(getDocs(collection(bob(), 'couples', 'e1', 'surprises', 's_waiting', 'payload')));
    // Its author may look at what they left, whenever they like.
    await assertSucceeds(getDoc(doc(alice(), 'couples', 'e1', 'surprises', 's_waiting', 'payload', 'content')));
    // Delivered, it is theirs to read — and nobody outside the couple can, either way.
    await assertSucceeds(getDoc(doc(bob(), 'couples', 'e1', 'surprises', 's_delivered', 'payload', 'content')));
    await assertFails(getDoc(doc(mallory(), 'couples', 'e1', 'surprises', 's_delivered', 'payload', 'content')));
  });

  it("denies every client write to a surprise: delivery is the server's (§5.7)", async () => {
    await assertFails(setDoc(doc(bob(), 'couples', 'e1', 'surprises', 'forged'), { title: 'mine now' }));
    await assertFails(updateDoc(doc(bob(), 'couples', 'e1', 'surprises', 's_waiting'), { state: 'DELIVERED' }));
    await assertFails(
      updateDoc(doc(bob(), 'couples', 'e1', 'surprises', 's_waiting', 'payload', 'content'), {
        deliveredAt: Timestamp.now(),
      }),
    );
    await assertFails(deleteDoc(doc(alice(), 'couples', 'e1', 'surprises', 's_waiting')));
  });

  it('lets both read a memory and mark it a favourite, and nothing else', async () => {
    await assertSucceeds(getDoc(doc(bob(), 'couples', 'e1', 'memories', 'm1')));
    await assertSucceeds(getDocs(collection(alice(), 'couples', 'e1', 'memories')));
    await assertFails(getDoc(doc(mallory(), 'couples', 'e1', 'memories', 'm1')));
    // The one field a phone may change.
    await assertSucceeds(updateDoc(doc(bob(), 'couples', 'e1', 'memories', 'm1'), { favorite: true }));
    await assertFails(updateDoc(doc(alice(), 'couples', 'e1', 'memories', 'm1'), { title: 'rewritten' }));
    await assertFails(updateDoc(doc(alice(), 'couples', 'e1', 'memories', 'm1'), { favorite: 'yes' }));
    await assertFails(updateDoc(doc(alice(), 'couples', 'e1', 'memories', 'm1'), { favorite: true, note: 'and this' }));
    // Keeping and forgetting are callables: a phone writes neither.
    await assertFails(setDoc(doc(alice(), 'couples', 'e1', 'memories', 'forged'), { title: 'made up' }));
    await assertFails(deleteDoc(doc(alice(), 'couples', 'e1', 'memories', 'm1')));
  });

  it('lets both read an arc, and neither write one', async () => {
    await assertSucceeds(getDoc(doc(bob(), 'couples', 'e1', 'arcs', 'a1')));
    await assertSucceeds(getDocs(collection(alice(), 'couples', 'e1', 'arcs')));
    await assertFails(getDoc(doc(mallory(), 'couples', 'e1', 'arcs', 'a1')));
    // Which day is open is the server's to say (§13).
    await assertFails(updateDoc(doc(alice(), 'couples', 'e1', 'arcs', 'a1'), { days: [{ state: 'READY' }] }));
    await assertFails(setDoc(doc(bob(), 'couples', 'e1', 'arcs', 'forged'), { lengthDays: 14 }));
  });

  it('lets either partner keep a countdown, and holds it to its shape', async () => {
    const countdown = (by) => ({ title: 'Until we meet', targetAt: Timestamp.now(), createdBy: by, createdAt: serverTimestamp() });
    await assertSucceeds(setDoc(doc(alice(), 'couples', 'e1', 'countdowns', 'c1'), countdown(ALICE)));
    await assertSucceeds(getDocs(collection(bob(), 'couples', 'e1', 'countdowns')));
    await assertSucceeds(deleteDoc(doc(bob(), 'couples', 'e1', 'countdowns', 'c1')));

    await assertFails(setDoc(doc(alice(), 'couples', 'e1', 'countdowns', 'c2'), countdown(BOB)));
    await assertFails(setDoc(doc(alice(), 'couples', 'e1', 'countdowns', 'c3'), {
      ...countdown(ALICE), secret: 'not part of a countdown',
    }));
    await assertFails(setDoc(doc(mallory(), 'couples', 'e1', 'countdowns', 'c4'), countdown(MALLORY)));
  });

  it('lets both see that a plan exists, and neither write one', async () => {
    await assertSucceeds(getDoc(doc(alice(), 'couples', 'e1', 'plans', 'p1')));
    await assertSucceeds(getDocs(collection(bob(), 'couples', 'e1', 'plans')));
    await assertFails(getDoc(doc(mallory(), 'couples', 'e1', 'plans', 'p1')));
    await assertFails(setDoc(doc(alice(), 'couples', 'e1', 'plans', 'forged'), { plannedBy: ALICE, plannedFor: BOB }));
    await assertFails(updateDoc(doc(bob(), 'couples', 'e1', 'plans', 'p1'), { state: 'OPENED' }));
  });

  it('denies anyone outside the couple, and everyone once unpaired', async () => {
    await assertFails(getDoc(doc(mallory(), 'couples', 'e1', 'experiences', 'x1')));
    await assertFails(getDoc(doc(alice(), 'couples', 'gone', 'experiences', 'x2')));
  });

  it('denies every client write: nights are the server’s', async () => {
    await assertFails(setDoc(doc(alice(), 'couples', 'e1', 'experiences', 'forged'), night));
    await assertFails(updateDoc(doc(alice(), 'couples', 'e1', 'experiences', 'x1'), { status: 'COMPLETED' }));
    await assertFails(deleteDoc(doc(bob(), 'couples', 'e1', 'experiences', 'x1')));
  });

  it('shows ask-first chapters to their owner alone (§3.2)', async () => {
    await assertSucceeds(getDoc(doc(bob(), 'couples', 'e1', 'experiences', 'x1', 'askFirst', BOB)));
    // The partner must not learn a limit exists: not by reading, not by listing.
    await assertFails(getDoc(doc(alice(), 'couples', 'e1', 'experiences', 'x1', 'askFirst', BOB)));
    await assertFails(getDocs(collection(alice(), 'couples', 'e1', 'experiences', 'x1', 'askFirst')));
    await assertFails(getDocs(collection(bob(), 'couples', 'e1', 'experiences', 'x1', 'askFirst')));
    await assertFails(setDoc(doc(bob(), 'couples', 'e1', 'experiences', 'x1', 'askFirst', BOB), { chapters: [] }));
  });

  it('keeps the engine state server-only, members included', async () => {
    await assertFails(getDoc(doc(alice(), 'couples', 'e1', 'engineState', 'history')));
    await assertFails(setDoc(doc(alice(), 'couples', 'e1', 'engineState', 'history'), { lastPlayed: {} }));
  });
});

describe('couples/{coupleId}/sessions/{id}/submissions, rounds and presence (§7.3 D–G, §12)', () => {
  const MALLORY = 'uid_mallory';
  const mallory = () => testEnv.authenticatedContext(MALLORY).firestore();
  const live = (db) => doc(db, 'couples', 'rt1', 'sessions', 'live');
  const submission = (db, id) => doc(db, 'couples', 'rt1', 'sessions', 'live', 'submissions', id);
  const answerFor = (uid, chapter = 1, overrides = {}) => ({
    chapter,
    byUid: uid,
    answer: 'a slow dance in the kitchen',
    at: serverTimestamp(),
    ...overrides,
  });

  before(async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      await setDoc(doc(db, 'couples', 'rt1'), { status: 'ACTIVE', memberUids: [ALICE, BOB], activeSessionId: 'live' });
      await setDoc(live(db), {
        experienceId: 'game1',
        experienceType: 'GAME',
        kind: 'TWO_DEVICE',
        hostUid: ALICE,
        state: 'CHAPTER_ACTIVE',
        currentChapter: 1,
        chapterCount: 4,
        consentAcks: {},
        submittedBy: {},
        seenBy: {},
      });
      // What the server writes once both have answered, and what Bob wrote to get there.
      await setDoc(doc(db, 'couples', 'rt1', 'sessions', 'live', 'rounds', '0'), {
        chapter: 0,
        answers: { [ALICE]: 'stay in', [BOB]: 'go out' },
        at: Timestamp.now(),
      });
      await setDoc(doc(db, 'couples', 'rt1', 'sessions', 'live', 'submissions', `0_${BOB}`), {
        chapter: 0, byUid: BOB, answer: 'go out', at: Timestamp.now(),
      });
      // Chapter 1 starts unanswered by both, so "written once" is tested on a fresh document
      // however many times the suite has run before against this emulator.
      await deleteDoc(doc(db, 'couples', 'rt1', 'sessions', 'live', 'submissions', `1_${ALICE}`));
      await deleteDoc(doc(db, 'couples', 'rt1', 'sessions', 'live', 'submissions', `1_${BOB}`));
    });
  });

  it('takes a partner\'s own answer for the chapter they are on', async () => {
    await assertSucceeds(setDoc(submission(alice(), `1_${ALICE}`), answerFor(ALICE)));
  });

  it('D: denies writing the other partner\'s submission', async () => {
    await assertFails(setDoc(submission(alice(), `1_${BOB}`), answerFor(BOB)));
    await assertFails(setDoc(submission(alice(), `1_${BOB}`), answerFor(ALICE)));
    await assertFails(setDoc(submission(mallory(), `1_${MALLORY}`), answerFor(MALLORY)));
  });

  it('E and F: denies reading a submission — the other\'s or their own, before or after the reveal', async () => {
    await assertFails(getDoc(submission(alice(), `0_${BOB}`)));
    await assertFails(getDoc(submission(bob(), `0_${BOB}`)));
    await assertFails(getDocs(collection(alice(), 'couples', 'rt1', 'sessions', 'live', 'submissions')));
  });

  it('G: denies setting the reveal, or any other state, from a client', async () => {
    await assertFails(setDoc(doc(alice(), 'couples', 'rt1', 'sessions', 'live', 'rounds', '1'), {
      chapter: 1, answers: { [ALICE]: 'mine', [BOB]: 'theirs' }, at: serverTimestamp(),
    }));
    await assertFails(updateDoc(live(alice()), { state: 'REVEAL' }));
    await assertFails(updateDoc(live(alice()), { [`submittedBy.${BOB}`]: serverTimestamp() }));
    await assertFails(updateDoc(live(alice()), { [`seenBy.${ALICE}`]: serverTimestamp() }));
  });

  it('lets both partners read a reveal the server wrote, and no one else', async () => {
    await assertSucceeds(getDoc(doc(alice(), 'couples', 'rt1', 'sessions', 'live', 'rounds', '0')));
    await assertSucceeds(getDoc(doc(bob(), 'couples', 'rt1', 'sessions', 'live', 'rounds', '0')));
    await assertFails(getDoc(doc(mallory(), 'couples', 'rt1', 'sessions', 'live', 'rounds', '0')));
  });

  it('refuses an answer to another chapter, a malformed one, or a second one', async () => {
    await assertFails(setDoc(submission(bob(), `2_${BOB}`), answerFor(BOB, 2)));
    await assertFails(setDoc(submission(bob(), `1_${BOB}`), answerFor(BOB, 1, { chapter: 2 })));
    await assertFails(setDoc(submission(bob(), `1_${BOB}`), answerFor(BOB, 1, { byUid: ALICE })));
    await assertFails(setDoc(submission(bob(), `1_${BOB}`), answerFor(BOB, 1, { at: Timestamp.now() })));
    await assertFails(setDoc(submission(bob(), `1_${BOB}`), answerFor(BOB, 1, { extra: true })));
    await assertFails(setDoc(submission(bob(), `1_${BOB}`), answerFor(BOB, 1, { answer: 'x'.repeat(701) })));
    // Written once: the answer already given cannot be rewritten after seeing anything.
    await assertSucceeds(setDoc(submission(bob(), `1_${BOB}`), answerFor(BOB)));
    await assertFails(setDoc(submission(bob(), `1_${BOB}`), answerFor(BOB, 1, { answer: 'changed' })));
  });

  it('lets each phone say it is still there, and read the other, but not speak for it', async () => {
    await assertSucceeds(setDoc(doc(alice(), 'couples', 'rt1', 'sessions', 'live', 'presence', ALICE), { at: serverTimestamp() }));
    await assertSucceeds(getDoc(doc(bob(), 'couples', 'rt1', 'sessions', 'live', 'presence', ALICE)));
    await assertFails(setDoc(doc(alice(), 'couples', 'rt1', 'sessions', 'live', 'presence', BOB), { at: serverTimestamp() }));
    await assertFails(setDoc(doc(alice(), 'couples', 'rt1', 'sessions', 'live', 'presence', ALICE), { at: Timestamp.now() }));
    await assertFails(setDoc(doc(alice(), 'couples', 'rt1', 'sessions', 'live', 'presence', ALICE), {
      at: serverTimestamp(), state: 'REVEAL',
    }));
    await assertFails(getDoc(doc(mallory(), 'couples', 'rt1', 'sessions', 'live', 'presence', ALICE)));
    await assertFails(deleteDoc(doc(alice(), 'couples', 'rt1', 'sessions', 'live', 'presence', ALICE)));
  });
});

describe('couples/{coupleId}/sessions and feedback (§3.1, §6.1, §12)', () => {
  const MALLORY = 'uid_mallory';
  const mallory = () => testEnv.authenticatedContext(MALLORY).firestore();
  const played = {
    status: 'COMPLETED',
    playedThrough: 2,
    chapters: [
      { index: 0, kind: 'WARM_UP', contentIds: ['flirt_one_look'], intensity: 1, state: 'DONE' },
      { index: 1, kind: 'TEASE', contentIds: ['tease_slow'], intensity: 2, state: 'DONE' },
      { index: 2, kind: 'FINALE', contentIds: ['finale_x'], intensity: 2, state: 'LOCKED' },
    ],
  };
  // A reaction the app would write: its own uid, its own chapter, the server's time.
  const reaction = (overrides = {}) => ({
    experienceId: 'night1',
    chapterIndex: 1,
    contentId: 'tease_slow',
    reaction: 'LOVED',
    byUid: ALICE,
    at: serverTimestamp(),
    ...overrides,
  });
  const feedbackDoc = (db, id) => doc(db, 'couples', 's1', 'feedback', id);

  before(async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      await setDoc(doc(db, 'couples', 's1'), { status: 'ACTIVE', memberUids: [ALICE, BOB], activeSessionId: 'live' });
      await setDoc(doc(db, 'couples', 's1', 'sessions', 'live'), {
        experienceId: 'night2', hostUid: ALICE, state: 'WAITING_FOR_PARTNER', currentChapter: -1, consentAcks: {},
      });
      await setDoc(doc(db, 'couples', 's1', 'experiences', 'night1'), played);
      await setDoc(doc(db, 'couples', 's1', 'experiences', 'night2'), { ...played, status: 'IN_PROGRESS', playedThrough: 0 });
      await setDoc(doc(db, 'couples', 's1', 'feedback', 'night1_0_uid_bob'), {
        experienceId: 'night1', chapterIndex: 0, contentId: 'flirt_one_look', reaction: 'NOT_FOR_US', byUid: BOB, at: Timestamp.now(),
      });
      // Feedback is create-only, so a reaction a previous run left behind would turn the
      // create below into a (refused) update.
      await deleteDoc(doc(db, 'couples', 's1', 'feedback', `night1_1_${ALICE}`));
    });
  });

  it('lets both members read the session, and no one else', async () => {
    await assertSucceeds(getDoc(doc(alice(), 'couples', 's1', 'sessions', 'live')));
    await assertSucceeds(getDoc(doc(bob(), 'couples', 's1', 'sessions', 'live')));
    await assertFails(getDoc(doc(mallory(), 'couples', 's1', 'sessions', 'live')));
    await assertFails(getDoc(doc(anon(), 'couples', 's1', 'sessions', 'live')));
    await assertFails(getDocs(collection(alice(), 'couples', 's1', 'sessions')));
  });

  it('denies every client write to a session: no forged consent, state or stop (§12.2)', async () => {
    const live = doc(bob(), 'couples', 's1', 'sessions', 'live');
    await assertFails(updateDoc(live, { [`consentAcks.${ALICE}`]: serverTimestamp() }));
    await assertFails(updateDoc(live, { [`consentAcks.${BOB}`]: serverTimestamp() }));
    await assertFails(updateDoc(live, { state: 'CHAPTER_ACTIVE' }));
    await assertFails(updateDoc(live, { state: 'ABANDONED' }));
    await assertFails(setDoc(doc(alice(), 'couples', 's1', 'sessions', 'forged'), { state: 'READY' }));
    await assertFails(deleteDoc(live));
    await assertFails(updateDoc(doc(alice(), 'couples', 's1'), { activeSessionId: 'forged' }));
  });

  it('accepts a reaction to a played chapter, from the partner who gives it', async () => {
    await assertSucceeds(setDoc(feedbackDoc(alice(), `night1_1_${ALICE}`), reaction()));
    await assertSucceeds(getDoc(feedbackDoc(alice(), `night1_1_${ALICE}`)));
  });

  it('lets a partner check whether their own reaction is there yet, and only their own', async () => {
    await assertSucceeds(getDoc(feedbackDoc(alice(), `night1_2_${ALICE}`)));
    // Probing the other's id says nothing, whether or not they reacted.
    await assertFails(getDoc(feedbackDoc(alice(), 'night1_2_uid_bob')));
    await assertFails(getDoc(feedbackDoc(alice(), 'night1_0_uid_bob')));
  });

  it("keeps a reaction private to whoever gave it (D-063)", async () => {
    await assertFails(getDoc(feedbackDoc(alice(), 'night1_0_uid_bob')));
    await assertSucceeds(getDoc(feedbackDoc(bob(), 'night1_0_uid_bob')));
    await assertFails(getDocs(collection(bob(), 'couples', 's1', 'feedback')));
    await assertFails(getDoc(feedbackDoc(mallory(), 'night1_0_uid_bob')));
  });

  it('refuses a reaction for someone else, twice for one chapter, or edited after', async () => {
    await assertFails(setDoc(feedbackDoc(alice(), `night1_0_${BOB}`), reaction({ chapterIndex: 0, contentId: 'flirt_one_look', byUid: BOB })));
    await assertFails(setDoc(feedbackDoc(alice(), `night1_0_${ALICE}`), reaction({ chapterIndex: 0, contentId: 'flirt_one_look', byUid: BOB })));
    await assertFails(setDoc(feedbackDoc(bob(), 'night1_0_uid_bob'), reaction({ chapterIndex: 0, contentId: 'flirt_one_look', byUid: BOB })));
    await assertFails(updateDoc(feedbackDoc(bob(), 'night1_0_uid_bob'), { reaction: 'LOVED' }));
    await assertFails(deleteDoc(feedbackDoc(bob(), 'night1_0_uid_bob')));
  });

  it('refuses a reaction to a chapter never played, or not what was played', async () => {
    // Chapter 2 was never reached.
    await assertFails(setDoc(feedbackDoc(alice(), `night1_2_${ALICE}`), reaction({ chapterIndex: 2, contentId: 'finale_x' })));
    // The content must be what that chapter was.
    await assertFails(setDoc(feedbackDoc(alice(), `night1_0_${ALICE}`), reaction({ chapterIndex: 0, contentId: 'tease_slow' })));
    // A night still being played takes no feedback yet; one that does not exist, none at all.
    await assertFails(setDoc(feedbackDoc(alice(), `night2_0_${ALICE}`), reaction({ experienceId: 'night2', chapterIndex: 0, contentId: 'flirt_one_look' })));
    await assertFails(setDoc(feedbackDoc(alice(), `night9_0_${ALICE}`), reaction({ experienceId: 'night9', chapterIndex: 0, contentId: 'flirt_one_look' })));
  });

  it('refuses a malformed reaction: its id, its words, its time, anything extra', async () => {
    const good = `night1_1_${ALICE}`;
    await assertFails(setDoc(feedbackDoc(alice(), 'whatever'), reaction()));
    await assertFails(setDoc(feedbackDoc(alice(), good), reaction({ reaction: 'MEH' })));
    await assertFails(setDoc(feedbackDoc(alice(), good), reaction({ at: Timestamp.fromMillis(0) })));
    await assertFails(setDoc(feedbackDoc(alice(), good), reaction({ note: 'loved the blindfold' })));
    await assertFails(setDoc(feedbackDoc(alice(), good), reaction({ chapterIndex: '1' })));
    await assertFails(setDoc(feedbackDoc(mallory(), `night1_1_${MALLORY}`), reaction({ byUid: MALLORY })));
  });
});

describe('content deltas (§9.2, §9.6)', () => {
  before(async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await setDoc(doc(db, 'content', 'flirt_one_look'), { status: 'disabled', version: 1, item: null });
      await setDoc(doc(db, 'contentMeta', 'pointer'), { version: 2 });
    });
  });

  it('lets any signed-in app read and query the deltas', async () => {
    await assertSucceeds(getDoc(doc(alice(), 'content', 'flirt_one_look')));
    await assertSucceeds(getDocs(collection(bob(), 'content')));
  });

  it('denies the deltas to anyone signed out', async () => {
    await assertFails(getDoc(doc(anon(), 'content', 'flirt_one_look')));
  });

  it('denies every client write, so only tools/ can disable or edit content', async () => {
    await assertFails(setDoc(doc(alice(), 'content', 'flirt_one_look'), { status: 'published', version: 9 }));
    await assertFails(updateDoc(doc(alice(), 'content', 'flirt_one_look'), { status: 'published' }));
    await assertFails(deleteDoc(doc(alice(), 'content', 'flirt_one_look')));
    await assertFails(setDoc(doc(alice(), 'content', 'flirt_new'), { status: 'published', version: 1 }));
  });

  it('lets a signed-in app read the emulator content pointer, and nothing else there', async () => {
    await assertSucceeds(getDoc(doc(alice(), 'contentMeta', 'pointer')));
    await assertFails(getDoc(doc(anon(), 'contentMeta', 'pointer')));
    await assertFails(setDoc(doc(alice(), 'contentMeta', 'pointer'), { version: 99 }));
    await assertFails(getDocs(collection(alice(), 'contentMeta')));
    await assertFails(getDoc(doc(alice(), 'contentMeta', 'other')));
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
