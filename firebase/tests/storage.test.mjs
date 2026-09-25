/**
 * Storage rules (BUILD_PROMPT.md §5.8, §7.2, §7.3 rows O and P).
 *
 * Nothing in Storage is public and no download URL is ever minted into a shared document, so
 * these are the only thing standing between a private voice note and anyone who knows its path.
 * The rules ask Firestore two questions about every object: is this an ACTIVE couple you belong
 * to, and is there still a media document for this item? A burn or an expiry deletes that
 * document, which is what makes row P — reading a deleted media path — a denial rather than a
 * hope.
 */
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { after, before, describe, it } from 'node:test';
import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { deleteDoc, doc, setDoc } from 'firebase/firestore';
import { getBytes, ref, uploadBytes } from 'firebase/storage';

const here = dirname(fileURLToPath(import.meta.url));

const ALICE = 'uid_alice';
const BOB = 'uid_bob';
const MALLORY = 'uid_mallory';

/** Their couple, and a couple neither of them is in. */
const OURS = 'st_ours';
const THEIRS = 'st_theirs';

const audio = () => new Uint8Array([1, 2, 3, 4]);
const AUDIO = { contentType: 'audio/mp4' };
const IMAGE = { contentType: 'image/jpeg' };

/** A media document as the server writes it (media.ts). */
const media = (ownerUid, state, kind = 'VOICE') => ({
  ownerUid,
  kind,
  state,
  path: `couples/${OURS}/${kind === 'VOICE' ? 'voice' : 'media'}/x/note`,
  burnAfterView: false,
});

let testEnv;

const alice = () => testEnv.authenticatedContext(ALICE).storage();
const bob = () => testEnv.authenticatedContext(BOB).storage();
const mallory = () => testEnv.authenticatedContext(MALLORY).storage();

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: 'afterhours-dev-emulator',
    firestore: {
      host: '127.0.0.1',
      port: 8080,
      rules: readFileSync(resolve(here, '..', 'firestore.rules'), 'utf8'),
    },
    storage: {
      host: '127.0.0.1',
      port: 9199,
      rules: readFileSync(resolve(here, '..', 'storage.rules'), 'utf8'),
    },
  });

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'couples', OURS), { status: 'ACTIVE', memberUids: [ALICE, BOB] });
    await setDoc(doc(db, 'couples', THEIRS), { status: 'ACTIVE', memberUids: [MALLORY] });
    // Alice is mid-upload on one, has sent another, and a third belongs to the other couple.
    await setDoc(doc(db, 'couples', OURS, 'media', 'pending'), media(ALICE, 'PENDING'));
    await setDoc(doc(db, 'couples', OURS, 'media', 'sent'), media(ALICE, 'SENT'));
    await setDoc(doc(db, 'couples', OURS, 'media', 'burned'), media(ALICE, 'SENT'));
    await setDoc(doc(db, 'couples', THEIRS, 'media', 'theirs'), media(MALLORY, 'SENT'));

    const storage = ctx.storage();
    // A data export the server wrote for Alice (§14.9).
    await uploadBytes(ref(storage, `users/${ALICE}/exports/afterhours-1.json`), new Uint8Array([123, 125]), {
      contentType: 'application/json',
    });
    await uploadBytes(ref(storage, `couples/${OURS}/voice/sent/note`), audio(), AUDIO);
    await uploadBytes(ref(storage, `couples/${OURS}/voice/burned/note`), audio(), AUDIO);
    await uploadBytes(ref(storage, `couples/${THEIRS}/voice/theirs/note`), audio(), AUDIO);
  });
});

after(async () => {
  await testEnv?.cleanup();
});

describe('Storage: voice notes and photos (§5.8, §7.3 O, P)', () => {
  it('lets the author upload to the path the server reserved, and nowhere else', async () => {
    await assertSucceeds(uploadBytes(ref(alice(), `couples/${OURS}/voice/pending/note`), audio(), AUDIO));
    // Not to an item id the server never reserved…
    await assertFails(uploadBytes(ref(alice(), `couples/${OURS}/voice/unreserved/note`), audio(), AUDIO));
    // …not over one already sent…
    await assertFails(uploadBytes(ref(alice(), `couples/${OURS}/voice/sent/note`), audio(), AUDIO));
    // …and not into the other partner's reservation.
    await assertFails(uploadBytes(ref(bob(), `couples/${OURS}/voice/pending/note`), audio(), AUDIO));
  });

  it('holds the content type to what the folder is for', async () => {
    await assertFails(uploadBytes(ref(alice(), `couples/${OURS}/voice/pending/note`), audio(), IMAGE));
    await assertFails(uploadBytes(ref(alice(), `couples/${OURS}/media/pending/photo`), audio(), AUDIO));
  });

  it('lets both partners read what was sent, and the author read their own before it is', async () => {
    await assertSucceeds(getBytes(ref(alice(), `couples/${OURS}/voice/sent/note`)));
    await assertSucceeds(getBytes(ref(bob(), `couples/${OURS}/voice/sent/note`)));
    // Still uploading: the author's alone until the server says it is sent.
    await assertFails(getBytes(ref(bob(), `couples/${OURS}/voice/pending/note`)));
  });

  it("O: denies reading another couple's object, even to a signed-in stranger", async () => {
    await assertFails(getBytes(ref(alice(), `couples/${THEIRS}/voice/theirs/note`)));
    await assertFails(getBytes(ref(mallory(), `couples/${OURS}/voice/sent/note`)));
    await assertFails(getBytes(ref(testEnv.unauthenticatedContext().storage(), `couples/${OURS}/voice/sent/note`)));
  });

  it('P: denies reading a deleted media path — a burn takes the object with it', async () => {
    await assertSucceeds(getBytes(ref(bob(), `couples/${OURS}/voice/burned/note`)));

    // What viewMedia does on a burn: the object, then the document.
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await deleteDoc(doc(ctx.firestore(), 'couples', OURS, 'media', 'burned'));
    });

    await assertFails(getBytes(ref(bob(), `couples/${OURS}/voice/burned/note`)));
    await assertFails(getBytes(ref(alice(), `couples/${OURS}/voice/burned/note`)));
  });

  it('serves a data export to the person it is about, and to nobody else', async () => {
    await assertSucceeds(getBytes(ref(alice(), `users/${ALICE}/exports/afterhours-1.json`)));
    // Not the partner, and not a stranger: an export is one person's whole record (§14.9).
    await assertFails(getBytes(ref(bob(), `users/${ALICE}/exports/afterhours-1.json`)));
    await assertFails(getBytes(ref(mallory(), `users/${ALICE}/exports/afterhours-1.json`)));
    // And no client writes one — an export a phone could write is a file a phone could forge.
    await assertFails(uploadBytes(ref(alice(), `users/${ALICE}/exports/forged.json`), new Uint8Array([1]), {
      contentType: 'application/json',
    }));
  });

  it("denies a client overwriting or reaching past what it was given", async () => {
    await assertFails(uploadBytes(ref(bob(), `couples/${OURS}/voice/sent/note`), audio(), AUDIO));
    await assertFails(getBytes(ref(alice(), `couples/${OURS}/memories/m1/photo`)));
  });
});
