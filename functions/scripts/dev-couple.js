// A paired couple for manual checks against the Emulator Suite, and the partner's phone.
// Development only: it signs in to the Auth emulator and writes through the Admin SDK.
//
//   node functions/scripts/dev-couple.js seed     alex@example.com (sign in on the device) and
//                                                sam@example.com, attested, level 5, paired
//   node functions/scripts/dev-couple.js ready    sam says "I'm in" to the couple's night under way
//   node functions/scripts/dev-couple.js pause|resume|stop   sam, from "their phone"
//   node functions/scripts/dev-couple.js answer [0|1]  sam answers this round on their own phone
//   node functions/scripts/dev-couple.js seen     sam has seen the round's reveal
//   node functions/scripts/dev-couple.js present  sam's phone says it is still there (heartbeat)
//   node functions/scripts/dev-couple.js plans    what either of them has planned for the other
//   node functions/scripts/dev-couple.js media    voice notes and photos, and whether the bytes are still there
//   node functions/scripts/dev-couple.js leave [minutes]   sam leaves a surprise for alex, due in N minutes (default 1)
//   node functions/scripts/dev-couple.js openwhen [TRIGGER]  sam leaves an "open when…" for alex (default MISS_ME)
//   node functions/scripts/dev-couple.js deliver  runs the delivery sweep, as the schedule would
//   node functions/scripts/dev-couple.js waiting  what either of them has left, and where it stands
//   node functions/scripts/dev-couple.js state    the couple's sessions, nights, history, feedback
//
// Build the functions first (npm --prefix functions run build): it uses their compiled code.
// Password: DEV_COUPLE_PASSWORD, or the default below — emulator accounts only.
process.env.FIRESTORE_EMULATOR_HOST ??= '127.0.0.1:8080';
process.env.STORAGE_EMULATOR_HOST ??= 'http://127.0.0.1:9199';
const { join } = require('node:path');
const { initializeApp } = require('firebase-admin/app');
const { getFirestore, Timestamp } = require('firebase-admin/firestore');
const { getStorage } = require('firebase-admin/storage');
const pairing = require(join(__dirname, '..', 'lib', 'pairing', 'pairing'));
const { recomputeCouple } = require(join(__dirname, '..', 'lib', 'couple', 'recompute'));

const PROJECT = 'afterhours-dev-emulator';
const AUTH = 'http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1';
const FUNCTIONS = `http://127.0.0.1:5001/${PROJECT}/us-central1`;
const PASSWORD = process.env.DEV_COUPLE_PASSWORD ?? 'correct-horse-43';

/** How an answer's parts are separated on the wire, as :core:engine writes them. */
const SEPARATOR = '';

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
  // The bucket the phones upload to: the Emulator Suite still defaults to the older name.
  const app = initializeApp({ projectId: PROJECT, storageBucket: `${PROJECT}.firebasestorage.app` }, 'seed-night');
  const db = getFirestore(app);
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
  const couple = (await db.doc(`couples/${coupleId}`).get()).data() ?? {};
  const sessionId = couple.activeSessionId;
  const sessionRef = db.doc(`couples/${coupleId}/sessions/${sessionId}`);
  if (cmd === 'ready') console.log(JSON.stringify(await call('markReady', { sessionId }, sam)));
  else if (cmd === 'answer') {
    // Sam's own phone, in a game played on a phone each: one answer for the round it is on.
    // The trigger reads both and writes the reveal (section 12.2).
    const chapter = (await sessionRef.get()).get('currentChapter');
    const picked = process.argv[3] ?? '0';
    const answer = picked.includes(SEPARATOR) ? picked : `P${SEPARATOR}${picked}`;
    await sessionRef.collection('submissions').doc(`${chapter}_${sam.uid}`).set({ answer, at: Timestamp.now() });
    console.log(JSON.stringify({ chapter, answer: answer.replace(SEPARATOR, '|') }));
  } else if (cmd === 'seen') {
    const chapter = (await sessionRef.get()).get('currentChapter');
    console.log(JSON.stringify(await call('markSeen', { sessionId, chapter }, sam)));
  } else if (cmd === 'present') {
    await sessionRef.collection('presence').doc(sam.uid).set({ at: Timestamp.now() });
    console.log(JSON.stringify({ presence: sam.uid }));
  }
  else if (['stop', 'pause', 'resume'].includes(cmd)) {
    console.log(JSON.stringify(await call('endSession', { sessionId, action: cmd.toUpperCase() }, sam)));
  } else if (cmd === 'plans') {
    // What one partner planned for the other (section 2.5 #2), and what the server kept of it.
    const plans = await db.collection(`couples/${coupleId}/plans`).get();
    for (const plan of plans.docs) {
      const experience = await db.doc(`couples/${coupleId}/experiences/${plan.id}`).get();
      console.log(JSON.stringify({
        id: plan.id,
        state: plan.get('state'),
        chapters: plan.get('chapterCount'),
        by: plan.get('plannedBy') === alex.uid ? 'alex' : 'sam',
        for: plan.get('plannedFor') === alex.uid ? 'alex' : 'sam',
        experience: { plannedFor: experience.get('plannedFor') === sam.uid ? 'sam' : 'alex', status: experience.get('status') },
      }));
    }
  } else if (cmd === 'leave') {
    // Sam leaves something for Alex, due in a few minutes (section 13).
    const minutes = Number(process.argv[3] ?? 1);
    const scheduledFor = Date.now() + minutes * 60 * 1000;
    console.log(JSON.stringify(await call('createSurprise', {
      type: 'SCHEDULED',
      title: 'For you',
      text: 'I left this here for you.',
      scheduledFor,
    }, sam)));
  } else if (cmd === 'openwhen') {
    console.log(JSON.stringify(await call('createSurprise', {
      type: 'OPEN_WHEN',
      title: 'When you miss me',
      text: 'I miss you too.',
      trigger: process.argv[3] ?? 'MISS_ME',
    }, sam)));
  } else if (cmd === 'deliver') {
    // What the scheduled function does, on demand: deliver everything whose moment has come.
    const { deliverDue } = require(join(__dirname, '..', 'lib', 'surprises', 'surprises'));
    console.log(JSON.stringify(await deliverDue(db, Date.now())));
  } else if (cmd === 'waiting') {
    const surprises = await db.collection(`couples/${coupleId}/surprises`).get();
    for (const item of surprises.docs) {
      const payload = await db.doc(`couples/${coupleId}/surprises/${item.id}/payload/content`).get();
      console.log(JSON.stringify({
        id: item.id,
        type: item.get('type'),
        title: item.get('title'),
        state: item.get('state'),
        from: item.get('createdBy') === alex.uid ? 'alex' : 'sam',
        trigger: item.get('trigger'),
        payloadDelivered: payload.get('deliveredAt') ? 'yes' : 'no',
      }));
    }
  } else if (cmd === 'media') {
    // Voice notes and photos (section 5.8): the record, and whether the object is still beside it.
    const bucket = getStorage(app).bucket();
    const media = await db.collection(`couples/${coupleId}/media`).get();
    for (const item of media.docs) {
      const [there] = await bucket.file(item.get('path')).exists();
      console.log(JSON.stringify({
        id: item.id,
        kind: item.get('kind'),
        state: item.get('state'),
        burn: item.get('burnAfterView') === true,
        from: item.get('ownerUid') === alex.uid ? 'alex' : 'sam',
        note: item.get('note'),
        viewed: item.get('viewedAt') ? 'yes' : 'no',
        object: there ? 'there' : 'gone',
      }));
    }
  } else if (cmd === 'state') {
    const sessions = await db.collection(`couples/${coupleId}/sessions`).get();
    const experiences = await db.collection(`couples/${coupleId}/experiences`).get();
    const history = (await db.doc(`couples/${coupleId}/engineState/history`).get()).data();
    const feedback = await db.collection(`couples/${coupleId}/feedback`).get();
    const affinity = await db.collection(`users/${alex.uid}/affinity`).get();
    console.log(JSON.stringify({
      couple: { activeSessionId: couple.activeSessionId ?? null, stats: couple.stats ?? null },
      sessions: sessions.docs.map((d) => ({ id: d.id, kind: d.get('kind'), state: d.get('state'), chapter: d.get('currentChapter'), end: d.get('endReason'), acks: Object.keys(d.get('consentAcks') || {}).length, submitted: Object.keys(d.get('submittedBy') || {}).length, seen: Object.keys(d.get('seenBy') || {}).length })),
      experiences: experiences.docs.map((d) => ({ id: d.id, status: d.get('status'), played: d.get('playedThrough'), chapters: d.get('chapters').map((c) => `${c.state}:${c.contentIds[0]}`) })),
      history: Object.keys(history?.lastPlayed ?? {}).length,
      feedback: feedback.docs.map((d) => `${d.id}:${d.get('reaction')}:${d.get('processedAt') ? 'processed' : 'pending'}`),
      affinity: affinity.docs.map((d) => `${d.id}=${d.get('score').toFixed(2)}`),
    }, null, 1));
  }
})().catch((e) => { console.error(e); process.exit(1); });
