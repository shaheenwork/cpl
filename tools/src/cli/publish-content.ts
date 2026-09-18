/**
 * `npm --prefix tools run publish-content -- --project <id> [--emulator] [--bucket <name>]`
 *
 * Uploads the committed bundle for content.json's contentVersion, then moves the pointer
 * (BUILD_PROMPT.md section 9.2). See publish.ts for the rules it keeps.
 */
import { getFirestore, FieldValue } from 'firebase-admin/firestore';
import { getRemoteConfig } from 'firebase-admin/remote-config';
import { getStorage } from 'firebase-admin/storage';
import { DEFAULT_CONTENT_ROOT, loadContent } from '../content';
import { connect, describe, parseTarget } from '../firebase';
import { bundleObjectPath, EMULATOR_POINTER_DOC, POINTER_PARAMETER, publishableBundle } from '../publish';

async function main(): Promise<void> {
  const { target } = parseTarget(process.argv.slice(2));
  const { text, version } = publishableBundle(loadContent(DEFAULT_CONTENT_ROOT));
  const app = connect(target);
  console.log(`publishing content v${version} to ${describe(target)}`);

  // 1. The bundle, immutable once uploaded.
  const file = getStorage(app).bucket(target.bucket).file(bundleObjectPath(version));
  const [exists] = await file.exists();
  if (exists) {
    const [current] = await file.download();
    if (current.toString('utf8') !== text) {
      throw new Error(
        `${bundleObjectPath(version)} already holds different content. Published versions never change: ` +
          'raise contentVersion in content/content.json, rebuild the bundle and publish again.',
      );
    }
    console.log(`  bundle: ${bundleObjectPath(version)} already uploaded, unchanged`);
  } else {
    await file.save(Buffer.from(text, 'utf8'), {
      resumable: false,
      contentType: 'application/json; charset=utf-8',
    });
    console.log(`  bundle: uploaded ${bundleObjectPath(version)} (${text.length} bytes)`);
  }

  // 2. The pointer, only ever forwards.
  if (target.emulator) {
    const pointer = getFirestore(app).doc(EMULATOR_POINTER_DOC);
    const moved = await getFirestore(app).runTransaction(async (tx) => {
      const current = (await tx.get(pointer)).get('version');
      if (typeof current === 'number' && current >= version) return false;
      tx.set(pointer, { version, updatedAt: FieldValue.serverTimestamp() });
      return true;
    });
    console.log(`  pointer: ${EMULATOR_POINTER_DOC} ${moved ? `now v${version}` : 'already at or past this version'}`);
    return;
  }

  const remoteConfig = getRemoteConfig(app);
  const template = await remoteConfig.getTemplate();
  const existing = template.parameters[POINTER_PARAMETER];
  const currentValue = existing?.defaultValue && 'value' in existing.defaultValue ? Number(existing.defaultValue.value) : 0;
  if (currentValue >= version) {
    console.log(`  pointer: ${POINTER_PARAMETER} is already ${currentValue}`);
    return;
  }
  template.parameters[POINTER_PARAMETER] = {
    ...existing,
    defaultValue: { value: String(version) },
    valueType: 'NUMBER',
    description: 'The content bundle clients should be on (tools/publish-content). Only ever raised.',
  };
  await remoteConfig.publishTemplate(await remoteConfig.validateTemplate(template));
  console.log(`  pointer: ${POINTER_PARAMETER} ${currentValue} -> ${version}`);
}

main().catch((error: unknown) => {
  console.error(error instanceof Error ? error.message : error);
  process.exit(1);
});
