/**
 * Publishing content (BUILD_PROMPT.md section 9.2): the bundle goes to Storage at
 * `content/bundles/v{n}/bundle.json`, then Remote Config's `content_version` moves to n.
 *
 * In that order, so no phone is ever pointed at a bundle that is not there yet. A published
 * version is immutable: republishing identical bytes is a no-op, different bytes under the
 * same version are refused. Clients cache by version, so a silently replaced bundle would
 * reach new installs and never reach existing ones.
 */
import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { BUNDLE_FILE, buildBundle, serializeBundle } from './bundle';
import type { ContentRepo } from './content';
import { validateContent } from './validate';

/** The Remote Config parameter the app reads (RemoteConfigKeys.CONTENT_VERSION). */
export const POINTER_PARAMETER = 'content_version';

/**
 * Remote Config has no emulator. Against the Emulator Suite the pointer is this Firestore
 * document instead, which the dev build reads in its place (DECISIONS.md D-041).
 */
export const EMULATOR_POINTER_DOC = 'contentMeta/pointer';

/** Firestore collection of admin deltas: disable flags and edited items (section 9.2). */
export const DELTA_COLLECTION = 'content';

export function bundleObjectPath(version: number): string {
  return `content/bundles/v${version}/bundle.json`;
}

/**
 * The committed bundle, if it is valid and current. Throws with every problem otherwise:
 * only what `check` would pass may be published.
 */
export function publishableBundle(repo: ContentRepo): { text: string; version: number } {
  const { problems } = validateContent(repo);
  const path = join(repo.root, BUNDLE_FILE);
  const expected = serializeBundle(buildBundle(repo));
  if (!existsSync(path)) {
    problems.push(`${BUNDLE_FILE}: missing — run: npm --prefix tools run bundle`);
  } else if (readFileSync(path, 'utf8').replace(/\r\n/g, '\n') !== expected) {
    problems.push(`${BUNDLE_FILE}: stale — run: npm --prefix tools run bundle`);
  }
  if (problems.length > 0) {
    throw new Error(`not publishable:\n${problems.map((line) => `  ✖ ${line}`).join('\n')}`);
  }
  return { text: expected, version: repo.contentVersion };
}
