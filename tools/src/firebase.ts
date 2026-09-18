/**
 * Connecting the content tools to a Firebase project, or to the local Emulator Suite.
 *
 * `--project` is always required, so a command never lands on a project by accident. Real
 * projects authenticate with Application Default Credentials: a service account key named
 * by GOOGLE_APPLICATION_CREDENTIALS, or `gcloud auth application-default login`
 * (HUMAN_SETUP.md). `--emulator` points everything at the ports in firebase.json.
 */
import { applicationDefault, initializeApp, type App } from 'firebase-admin/app';

export interface Target {
  projectId: string;
  emulator: boolean;
  bucket: string;
}

/** Splits `--project <id> [--emulator] [--bucket <name>]` from the command's own arguments. */
export function parseTarget(argv: string[]): { target: Target; rest: string[] } {
  const rest: string[] = [];
  let projectId: string | undefined;
  let bucket: string | undefined;
  let emulator = false;
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === '--project') projectId = argv[++i];
    else if (arg === '--bucket') bucket = argv[++i];
    else if (arg === '--emulator') emulator = true;
    else rest.push(arg);
  }
  if (!projectId) throw new Error('--project <id> is required');
  return { target: { projectId, emulator, bucket: bucket ?? `${projectId}.firebasestorage.app` }, rest };
}

export function connect(target: Target): App {
  if (target.emulator) {
    process.env.FIRESTORE_EMULATOR_HOST ??= '127.0.0.1:8080';
    process.env.FIREBASE_STORAGE_EMULATOR_HOST ??= '127.0.0.1:9199';
    // No credentials are needed, so do not go looking for a Google Cloud metadata server.
    process.env.METADATA_SERVER_DETECTION ??= 'none';
    return initializeApp({ projectId: target.projectId, storageBucket: target.bucket });
  }
  if (process.env.FIRESTORE_EMULATOR_HOST || process.env.FIREBASE_STORAGE_EMULATOR_HOST) {
    // The Admin SDK would quietly obey these and write to the emulator instead.
    throw new Error('Emulator variables are set but --emulator was not passed; unset them or add --emulator');
  }
  return initializeApp({ credential: applicationDefault(), projectId: target.projectId, storageBucket: target.bucket });
}

export function describe(target: Target): string {
  return target.emulator ? `${target.projectId} (emulator)` : target.projectId;
}
