// A paired couple for manual checks against the Emulator Suite, and the partner's phone.
// Development only: it signs in to the Auth emulator and writes through the Admin SDK.
//
//   node functions/scripts/dev-couple.js seed     alex@example.com (sign in on the device) and
//                                                sam@example.com, attested, level 5, paired
//   node functions/scripts/dev-couple.js ready    sam says "I'm in" to the couple's night under way
//   node functions/scripts/dev-couple.js pause|resume|stop   sam, from "their phone"
//   node functions/scripts/dev-couple.js state    the couple's sessions, nights, history, feedback
//
// Build the functions first (npm --prefix functions run build): it uses their compiled code.
// Password: DEV_COUPLE_PASSWORD, or the default below — emulator accounts only.
process.env.FIRESTORE_EMULATOR_HOST ??= '127.0.0.1:8080';
const { join } = require('node:path');
const { initializeApp } = require('firebase-admin/app');
const { getFirestore, Timestamp } = require('firebase-admin/firestore');
const pairing = require(join(__dirname, '..', 'lib', 'pairing', 'pairing'));
const { recomputeCouple } = require(join(__dirname, '..', 'lib', 'couple', 'recompute'));

const PROJECT = 'afterhours-dev-emulator';
const AUTH = 'http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1';
const FUNCTIONS = `http://127.0.0.1:5001/${PROJECT}/us-central1`;
const PASSWORD = process.env.DEV_COUPLE_PASSWORD ?? 'correct-horse-43';

async function account(email) {
  const body = JSON.stringify({ email, password: PASSWORD, returnSecureToken: true });
  const headers = { 'Content-Type': 'application/json' };
  let res = await fetch(`${AUTH}/accounts:signUp?key=emulator`, { method: 'POST', headers, body });
  if (!res.ok) res = await fetch(`${AUTH}/accounts:signInWithPassword?key=emulator`, { method: 'POST', headers, body });
  const json = await res.json();
  if (!json.localId) throw new Error(`no account for ${email}: ${JSON.stringify(json)}`);
  return { uid: json.localId, idToken: json.idToken };
}

async function call(name, data, user) {
  const res = await fetch(`${FUNCTIONS}/${name}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${user.idToken}` },
    body: JSON.stringify({ data }),
  });
  return res.json();
}

(async () => {
  const db = getFirestore(initializeApp({ projectId: PROJECT }, 'seed-night'));
  const [cmd] = process.argv.slice(2);
  const alex = await account('alex@example.com');
  const sam = await account('sam@example.com');

  if (cmd === 'seed') {
    const attested = { ageAttestation: { confirmed: true, at: Timestamp.now() }, contentLevel: 5, timezone: 'UTC' };
    await db.doc(`users/${alex.uid}`).set(attested, { merge: true });
    await db.doc(`users/${sam.uid}`).set(attested, { merge: true });
    let coupleId = (await db.doc(`users/${alex.uid}`).get()).get('coupleId');
    if (!coupleId) {
      const now = Date.now();
      const { code } = await pairing.createPairingCode(db, sam.uid, now);
      await pairing.requestPairing(db, alex.uid, code, now);
      coupleId = (await pairing.respondToPairing(db, sam.uid, true, now)).coupleId;
    }
    await recomputeCouple(db, coupleId, Date.now());
    console.log(JSON.stringify({ alex: alex.uid, sam: sam.uid, coupleId }));
    return;
  }

  const coupleId = (await db.doc(`users/${alex.uid}`).get()).get('coupleId');
  const couple = (await db.doc(`couples/${coupleId}`).get()).data();
  const sessionId = couple.activeSessionId;
  if (cmd === 'ready') console.log(JSON.stringify(await call('markReady', { sessionId }, sam)));
  else if (['stop', 'pause', 'resume'].includes(cmd)) {
    console.log(JSON.stringify(await call('endSession', { sessionId, action: cmd.toUpperCase() }, sam)));
  } else if (cmd === 'state') {
    const sessions = await db.collection(`couples/${coupleId}/sessions`).get();
    const experiences = await db.collection(`couples/${coupleId}/experiences`).get();
    const history = (await db.doc(`couples/${coupleId}/engineState/history`).get()).data();
    const feedback = await db.collection(`couples/${coupleId}/feedback`).get();
    const affinity = await db.collection(`users/${alex.uid}/affinity`).get();
    console.log(JSON.stringify({
      couple: { activeSessionId: couple.activeSessionId ?? null, stats: couple.stats ?? null },
      sessions: sessions.docs.map((d) => ({ id: d.id, state: d.get('state'), chapter: d.get('currentChapter'), end: d.get('endReason'), acks: Object.keys(d.get('consentAcks') || {}).length })),
      experiences: experiences.docs.map((d) => ({ id: d.id, status: d.get('status'), played: d.get('playedThrough'), chapters: d.get('chapters').map((c) => `${c.state}:${c.contentIds[0]}`) })),
      history: Object.keys(history?.lastPlayed ?? {}).length,
      feedback: feedback.docs.map((d) => `${d.id}:${d.get('reaction')}:${d.get('processedAt') ? 'processed' : 'pending'}`),
      affinity: affinity.docs.map((d) => `${d.id}=${d.get('score').toFixed(2)}`),
    }, null, 1));
  }
})().catch((e) => { console.error(e); process.exit(1); });
