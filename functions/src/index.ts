/**
 * Callable entry points. Deliberately thin: authenticate, delegate to the core logic, map
 * errors. Everything that can race lives in the core modules, where it is tested directly.
 *
 * Every callable:
 *  - enforces App Check in production (BUILD_PROMPT.md section 8), relaxed only under the
 *    Functions emulator, where no real attestation exists;
 *  - takes the caller's identity from the verified auth token and never from the payload —
 *    a client-supplied uid or coupleId is never trusted.
 */
import { initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { HttpsError, onCall, type CallableRequest } from 'firebase-functions/v2/https';
import * as pairing from './pairing/pairing';
import { toHttpsError } from './shared/errors';

initializeApp();

const enforceAppCheck = process.env.FUNCTIONS_EMULATOR !== 'true';
const options = { enforceAppCheck, maxInstances: 10 };

function callerUid(request: CallableRequest): string {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError('unauthenticated', 'Sign in first.');
  return uid;
}

async function guarded<T>(work: () => Promise<T>): Promise<T> {
  try {
    return await work();
  } catch (error) {
    throw toHttpsError(error);
  }
}

export const createPairingCode = onCall(options, (request) =>
  guarded(() => pairing.createPairingCode(getFirestore(), callerUid(request), Date.now())),
);

export const requestPairing = onCall(options, (request) =>
  guarded(() => pairing.requestPairing(getFirestore(), callerUid(request), request.data?.code, Date.now())),
);

export const respondToPairing = onCall(options, (request) =>
  guarded(() => {
    const approve = request.data?.approve;
    if (typeof approve !== 'boolean') throw new HttpsError('invalid-argument', 'approve must be a boolean');
    return pairing.respondToPairing(getFirestore(), callerUid(request), approve, Date.now());
  }),
);

export const cancelPairing = onCall(options, (request) =>
  guarded(() => pairing.cancelPairing(getFirestore(), callerUid(request))),
);

export const unpairCouple = onCall(options, (request) =>
  guarded(() => {
    // An explicit confirmation flag, so an accidental or replayed empty call cannot end a
    // couple.
    if (request.data?.confirm !== true) throw new HttpsError('invalid-argument', 'confirm must be true');
    return pairing.unpairCouple(getFirestore(), callerUid(request), Date.now());
  }),
);
