import { HttpsError } from 'firebase-functions/v2/https';

/**
 * Typed failures thrown from inside a transaction. The callable wrapper turns them into
 * HttpsErrors; the core logic stays free of the functions SDK so it can be tested directly
 * against the emulator.
 */
export class PairingError extends Error {
  constructor(
    readonly code:
      | 'invalid-argument'
      | 'failed-precondition'
      | 'not-found'
      | 'resource-exhausted'
      | 'permission-denied',
    readonly reason: string,
  ) {
    super(reason);
  }
}

export function toHttpsError(error: unknown): HttpsError {
  if (error instanceof HttpsError) return error;
  if (error instanceof PairingError) return new HttpsError(error.code, error.reason);
  // Never leak internals to a client.
  console.error('Unexpected pairing failure', error);
  return new HttpsError('internal', 'Something went wrong.');
}
