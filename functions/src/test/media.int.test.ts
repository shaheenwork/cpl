/**
 * Voice notes and photos against the real emulators (BUILD_PROMPT.md sections 5.8, 13): the
 * document and the object are made, confirmed, burned and swept together, and a burn really
 * deletes rather than hides.
 *
 * Requires a running emulator:
 *   firebase emulators:start --only auth,firestore,storage --project afterhours-dev-emulator
 */
process.env.FIRESTORE_EMULATOR_HOST ??= '127.0.0.1:8080';
process.env.STORAGE_EMULATOR_HOST ??= 'http://127.0.0.1:9199';

import assert from 'node:assert/strict';
import { beforeEach, describe, it } from 'node:test';
import { initializeApp } from 'firebase-admin/app';
import { getFirestore, type DocumentData } from 'firebase-admin/firestore';
import { getStorage } from 'firebase-admin/storage';
import { recomputeCouple } from '../couple/recompute';
import { grantPremium } from './support';
import {
  DEFAULT_TTL_MS,
  MAX_NOTE,
  PENDING_TTL_MS,
  confirmMedia,
  expireMedia,
  reserveMedia,
  viewMedia,
} from '../media/media';
import { createPairingCode, requestPairing, respondToPairing } from '../pairing/pairing';
import { ServiceError } from '../shared/errors';

const PROJECT = 'afterhours-int-test';
const app = initializeApp({ projectId: PROJECT, storageBucket: `${PROJECT}.appspot.com` }, 'media-int-test');
const db = getFirestore(app);
const storage = getStorage(app);
const NOW = Date.UTC(2026, 8, 23, 20, 0, 0);

async function clearEmulator(): Promise<void> {
  const res = await fetch(
    `http://${process.env.FIRESTORE_EMULATOR_HOST}/emulator/v1/projects/${PROJECT}/databases/(default)/documents`,
    { method: 'DELETE' },
  );
  assert.ok(res.ok, `emulator clear failed: ${res.status}`);
}

let counter = 0;
async function couple() {
  counter += 1;
  const a = `sender_${counter}`;
  const b = `receiver_${counter}`;
  await db.doc(`users/${a}`).set({ contentLevel: 3 });
  await db.doc(`users/${b}`).set({ contentLevel: 3 });
  const { code } = await createPairingCode(db, a, NOW);
  await requestPairing(db, b, code, NOW);
  const { coupleId } = await respondToPairing(db, a, true, NOW);
  assert.ok(coupleId);
  await grantPremium(db, a, NOW);
  await recomputeCouple(db, coupleId, NOW);
  return { a, b, coupleId };
}

/** The phone's part: the bytes land at exactly the path the server named. */
async function upload(path: string, bytes = 'a voice note'): Promise<void> {
  await storage.bucket().file(path).save(Buffer.from(bytes));
}

async function objectExists(path: string): Promise<boolean> {
  const [exists] = await storage.bucket().file(path).exists();
  return exists;
}

async function read(path: string): Promise<DocumentData | undefined> {
  return (await db.doc(path).get()).data();
}

async function rejects(work: Promise<unknown>, code: string, reason?: string) {
  await assert.rejects(work, (error: unknown) => {
    assert.ok(error instanceof ServiceError, `expected a ServiceError, got ${error}`);
    assert.equal(error.code, code);
    if (reason) assert.equal(error.reason, reason);
    return true;
  });
}

describe('a voice note or a photo sent to the partner', () => {
  beforeEach(async () => {
    await clearEmulator();
  });

  it('is a path the server names, and a record made before anything is uploaded', async () => {
    const { a, coupleId } = await couple();

    const reserved = await reserveMedia(db, a, { kind: 'VOICE', note: 'thinking of you' }, NOW);

    assert.match(reserved.path, new RegExp(`^couples/${coupleId}/voice/${reserved.itemId}/`));
    assert.equal(reserved.expiresAt, NOW + DEFAULT_TTL_MS);
    const stored = (await read(`couples/${coupleId}/media/${reserved.itemId}`))!;
    assert.equal(stored.state, 'PENDING');
    assert.equal(stored.ownerUid, a);
    assert.equal(stored.kind, 'VOICE');
    assert.equal(stored.note, 'thinking of you');
    assert.equal(stored.sentAt, null);

    const photo = await reserveMedia(db, a, { kind: 'PHOTO' }, NOW);
    assert.match(photo.path, new RegExp(`^couples/${coupleId}/media/${photo.itemId}/`));
  });

  it('is only the couple\'s once the object is really there', async () => {
    const { a, b, coupleId } = await couple();
    const reserved = await reserveMedia(db, a, { kind: 'VOICE' }, NOW);

    await rejects(confirmMedia(db, storage, a, { itemId: reserved.itemId }, NOW), 'failed-precondition', 'nothing-uploaded');

    await upload(reserved.path);
    await confirmMedia(db, storage, a, { itemId: reserved.itemId }, NOW);
    const sent = (await read(`couples/${coupleId}/media/${reserved.itemId}`))!;
    assert.equal(sent.state, 'SENT');
    assert.ok(sent.sentAt);

    // Confirming twice changes nothing, and nobody else can confirm it at all.
    await confirmMedia(db, storage, a, { itemId: reserved.itemId }, NOW + 1000);
    assert.deepEqual((await read(`couples/${coupleId}/media/${reserved.itemId}`))!.sentAt, sent.sentAt);
    await rejects(confirmMedia(db, storage, b, { itemId: reserved.itemId }, NOW), 'permission-denied', 'not-yours');
  });

  it('burns on the partner\'s first look: the object first, then the record', async () => {
    const { a, b, coupleId } = await couple();
    const reserved = await reserveMedia(db, a, { kind: 'VOICE', burnAfterView: true }, NOW);
    await upload(reserved.path);
    await confirmMedia(db, storage, a, { itemId: reserved.itemId }, NOW);

    // The author looking at their own does not burn it.
    assert.deepEqual(await viewMedia(db, storage, a, { itemId: reserved.itemId }, NOW), {
      itemId: reserved.itemId,
      burned: false,
    });
    assert.ok(await objectExists(reserved.path));

    const view = await viewMedia(db, storage, b, { itemId: reserved.itemId }, NOW + 60_000);

    assert.equal(view.burned, true);
    assert.equal(await objectExists(reserved.path), false);
    assert.equal(await read(`couples/${coupleId}/media/${reserved.itemId}`), undefined);
    // And there is nothing left to open a second time (section 7.3 row P).
    await rejects(viewMedia(db, storage, b, { itemId: reserved.itemId }, NOW + 120_000), 'not-found', 'media');
  });

  it('remembers when one that does not burn was opened', async () => {
    const { a, b, coupleId } = await couple();
    const reserved = await reserveMedia(db, a, { kind: 'PHOTO' }, NOW);
    await upload(reserved.path, 'a photo');
    await confirmMedia(db, storage, a, { itemId: reserved.itemId }, NOW);

    const view = await viewMedia(db, storage, b, { itemId: reserved.itemId }, NOW + 5_000);

    assert.equal(view.burned, false);
    const stored = (await read(`couples/${coupleId}/media/${reserved.itemId}`))!;
    assert.ok(stored.viewedAt);
    assert.ok(await objectExists(reserved.path));
  });

  it('sweeps away what is past its time, and uploads that never finished', async () => {
    const { a, coupleId } = await couple();
    const kept = await reserveMedia(db, a, { kind: 'VOICE' }, NOW);
    await upload(kept.path);
    await confirmMedia(db, storage, a, { itemId: kept.itemId }, NOW);
    const shortLived = await reserveMedia(db, a, { kind: 'VOICE', ttlMs: 60_000 }, NOW);
    await upload(shortLived.path);
    await confirmMedia(db, storage, a, { itemId: shortLived.itemId }, NOW);
    const abandoned = await reserveMedia(db, a, { kind: 'PHOTO' }, NOW);
    await upload(abandoned.path, 'never confirmed');

    const swept = await expireMedia(db, storage, NOW + PENDING_TTL_MS + 60_000);

    assert.equal(swept.deleted, 2);
    assert.equal(await read(`couples/${coupleId}/media/${shortLived.itemId}`), undefined);
    assert.equal(await objectExists(shortLived.path), false);
    assert.equal(await read(`couples/${coupleId}/media/${abandoned.itemId}`), undefined);
    assert.equal(await objectExists(abandoned.path), false);
    // What is still in date stays, object and all.
    assert.ok(await read(`couples/${coupleId}/media/${kept.itemId}`));
    assert.ok(await objectExists(kept.path));
    // And running it again finds nothing to do.
    assert.deepEqual(await expireMedia(db, storage, NOW + PENDING_TTL_MS + 120_000), { deleted: 0 });
  });

  it('refuses what it cannot read, and a stranger with no couple', async () => {
    const { a } = await couple();
    await rejects(reserveMedia(db, a, { kind: 'FILM' }, NOW), 'invalid-argument', 'kind');
    await rejects(reserveMedia(db, a, { kind: 'VOICE', extra: true }, NOW), 'invalid-argument', 'unknown field extra');
    await rejects(reserveMedia(db, a, { kind: 'VOICE', ttlMs: -1 }, NOW), 'invalid-argument', 'ttlMs');
    await rejects(reserveMedia(db, a, { kind: 'VOICE', note: 'x'.repeat(MAX_NOTE + 1) }, NOW), 'invalid-argument', 'note');
    await rejects(reserveMedia(db, 'nobody', { kind: 'VOICE' }, NOW), 'failed-precondition', 'not-paired');
    await rejects(viewMedia(db, storage, a, { itemId: 'not_a_real_one' }, NOW), 'not-found', 'media');
  });
});
