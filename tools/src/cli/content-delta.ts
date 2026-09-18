/**
 * `npm --prefix tools run content-delta -- <command> --project <id> [--emulator]`
 *
 *   disable <itemId>       take an item out of every night, now, without an app release
 *   enable <itemId>        undo a disable
 *   replace <item.json>    ship an edited (or new) item before the next bundle
 *   list                   show every delta in force
 *
 * Deltas live in Firestore `content/{id}` (BUILD_PROMPT.md section 9.2, 9.6). A delta
 * applies to its item's bundled version and every earlier one, so it keeps working after
 * later bundles until someone bakes the change into the pack files and raises the item's
 * version past it. Nothing here edits the pack files: git stays the record of authored
 * content, and the delta is the override on top.
 */
import { readFileSync } from 'node:fs';
import { FieldValue, getFirestore } from 'firebase-admin/firestore';
import { type ContentItem, DEFAULT_CONTENT_ROOT, loadContent } from '../content';
import { connect, describe, parseTarget } from '../firebase';
import { withItem } from '../packfile';
import { DELTA_COLLECTION } from '../publish';
import { validateContent } from '../validate';

const USAGE = 'usage: content-delta <disable <id> | enable <id> | replace <item.json> | list> --project <id> [--emulator]';

async function main(): Promise<void> {
  const { target, rest } = parseTarget(process.argv.slice(2));
  const [command, argument] = rest;
  const repo = loadContent(DEFAULT_CONTENT_ROOT);
  const bundled = new Map(
    [...repo.itemsByPack.values()].flat().map((item) => [(item as ContentItem).id, item as ContentItem]),
  );
  const db = getFirestore(connect(target));
  const deltas = db.collection(DELTA_COLLECTION);

  switch (command) {
    case 'disable':
    case 'enable': {
      if (!argument) throw new Error(USAGE);
      const existing = await deltas.doc(argument).get();
      const version = bundled.get(argument)?.version ?? existing.get('version');
      if (typeof version !== 'number') throw new Error(`${argument}: no such item in the packs or the deltas`);
      const status = command === 'disable' ? 'disabled' : 'published';
      await deltas.doc(argument).set(
        { status, version, item: existing.get('item') ?? null, updatedAt: FieldValue.serverTimestamp() },
      );
      console.log(`${argument} v${version}: ${status} on ${describe(target)}`);
      if (command === 'disable') {
        console.log('Also set its status to "disabled" in its pack file before the next content release.');
      }
      return;
    }

    case 'replace': {
      if (!argument) throw new Error(USAGE);
      const item = JSON.parse(readFileSync(argument, 'utf8')) as ContentItem;
      const current = bundled.get(item.id);
      if (current && item.version <= current.version) {
        throw new Error(`${item.id}: version must be above the bundled v${current.version}`);
      }
      // Held to the same standard as authored content, against the whole catalogue, so a
      // delta can never ship what the build would have refused.
      const before = new Set(validateContent(repo).problems);
      const introduced = validateContent(withItem(repo, item)).problems.filter((line) => !before.has(line));
      if (introduced.length > 0) {
        throw new Error(`refused:\n${introduced.map((line) => `  ✖ ${line}`).join('\n')}`);
      }
      await deltas.doc(item.id).set({
        status: item.status,
        version: item.version,
        item,
        updatedAt: FieldValue.serverTimestamp(),
      });
      console.log(`${item.id} v${item.version}: replaced on ${describe(target)}`);
      console.log('Copy the same edit into its pack file before the next content release.');
      return;
    }

    case 'list': {
      const snapshot = await deltas.orderBy('updatedAt').get();
      if (snapshot.empty) console.log('no deltas');
      for (const doc of snapshot.docs) {
        const edited = doc.get('item') ? ' (edited)' : '';
        console.log(`${doc.id} v${doc.get('version')}: ${doc.get('status')}${edited}`);
      }
      return;
    }

    default:
      throw new Error(USAGE);
  }
}

main().catch((error: unknown) => {
  console.error(error instanceof Error ? error.message : error);
  process.exit(1);
});
