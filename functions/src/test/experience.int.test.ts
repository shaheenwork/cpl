/**
 * generateExperience against the real Firestore emulator (BUILD_PROMPT.md sections 3.2, 5.4,
 * 5.5, 8, 10): the couple's real filters, history and deltas, and the real catalogue the
 * functions deploy with.
 *
 * Requires a running emulator:
 *   firebase emulators:start --only auth,firestore,storage --project afterhours-dev-emulator
 */
process.env.FIRESTORE_EMULATOR_HOST ??= '127.0.0.1:8080';

import assert from 'node:assert/strict';
import { beforeEach, describe, it } from 'node:test';
import { initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { recomputeCouple } from '../couple/recompute';
import { grantPremium } from './support';
import { loadCatalogue, resetCatalogueCache } from '../engine/catalogue';
import { chapterRange } from '../engine/plan';
import { DEFAULT_WEIGHTS, ENGINE_VERSION, type EngineItem } from '../engine/types';
import { GENERATE_LIMIT, generateExperience, type GenerateDeps } from '../experience/generate';
import { openPlan, planNight } from '../experience/plan';
import { remixExperience } from '../experience/remix';
import { surpriseExperience } from '../experience/surprise';
import { createPairingCode, requestPairing, respondToPairing, unpairCouple } from '../pairing/pairing';
import { ServiceError } from '../shared/errors';

// Its own project, so no trigger running in the Functions emulator touches this data.
const PROJECT = 'afterhours-int-test';
const db = getFirestore(initializeApp({ projectId: PROJECT }, 'experience-int-test'));
const NOW = Date.UTC(2026, 8, 22, 21, 0, 0);

async function clearEmulator(): Promise<void> {
  const res = await fetch(
    `http://${process.env.FIRESTORE_EMULATOR_HOST}/emulator/v1/projects/${PROJECT}/databases/(default)/documents`,
    { method: 'DELETE' },
  );
  assert.ok(res.ok, `emulator clear failed: ${res.status}`);
}

const deps = (seed = 12345): GenerateDeps => ({
  catalogue: loadCatalogue,
  weights: async () => DEFAULT_WEIGHTS,
  seed: () => seed,
});

const request = (overrides: Record<string, unknown> = {}) => ({
  mode: 'TOGETHER',
  durationMin: 45,
  moods: ['naughty'],
  intensity: 5,
  leadership: 'SWITCH',
  novelty: 'MIXED',
  ...overrides,
});

let counter = 0;
async function couple(levels: [number, number] = [5, 5]) {
  counter += 1;
  const a = `alex_${counter}`;
  const b = `sam_${counter}`;
  await db.doc(`users/${a}`).set({ contentLevel: levels[0] });
  await db.doc(`users/${b}`).set({ contentLevel: levels[1] });
  const { code } = await createPairingCode(db, a, NOW);
  await requestPairing(db, b, code, NOW);
  const { coupleId } = await respondToPairing(db, a, true, NOW);
  assert.ok(coupleId);
  await grantPremium(db, a, NOW);
  await recomputeCouple(db, coupleId, NOW);
  return { a, b, coupleId };
}

async function catalogueById(): Promise<Map<string, EngineItem>> {
  return new Map((await loadCatalogue(db, NOW)).items.map((item) => [item.id, item]));
}

async function rejects(work: Promise<unknown>, code: string, reason?: string) {
  await assert.rejects(work, (error: unknown) => {
    assert.ok(error instanceof ServiceError, `expected a ServiceError, got ${error}`);
    assert.equal(error.code, code);
    if (reason) assert.equal(error.reason, reason);
    return true;
  });
}

describe('generateExperience', () => {
  beforeEach(async () => {
    await clearEmulator();
    resetCatalogueCache();
  });

  it('builds a night for a paired couple, and stores it for both', async () => {
    const { a, coupleId } = await couple();
    const result = await generateExperience(db, a, request(), NOW, deps());

    const [lo, hi] = chapterRange(45);
    assert.ok(result.chapters.length >= lo && result.chapters.length <= hi);
    assert.equal(result.chapters.at(-1)!.kind, 'FINALE');
    assert.ok(result.alternates.length > 0 && result.alternates.length <= 3 * result.chapters.length);
    assert.equal(result.engineVersion, ENGINE_VERSION);
    assert.equal(result.seed, 12345);
    assert.equal(result.contentVersion, (await loadCatalogue(db, NOW)).contentVersion);

    const stored = (await db.doc(`couples/${coupleId}/experiences/${result.experienceId}`).get()).data()!;
    assert.equal(stored.status, 'READY');
    assert.equal(stored.createdBy, a);
    assert.equal(stored.seed, 12345);
    assert.deepEqual(
      stored.chapters.map((chapter: { contentIds: string[] }) => chapter.contentIds[0]),
      result.chapters.map((chapter) => chapter.contentId),
    );
    assert.deepEqual(stored.alternates, result.alternates);
    assert.equal(stored.playedThrough, 0);
    assert.equal(stored.focus, null);
    // Nothing about either partner's limits rides along.
    for (const key of Object.keys(stored)) assert.ok(!/filter|boundar|exclud|askFirst/i.test(key), `stored ${key}`);
  });

  it("keeps a whole theme out on either partner's NEVER", async () => {
    const { a, b, coupleId } = await couple();
    const theme = 'teasing_words';
    await db.doc(`users/${b}/boundaries/${theme}`).set({ level: 'NEVER', updatedAt: new Date(NOW) });
    await recomputeCouple(db, coupleId, NOW);
    const byId = await catalogueById();

    for (let seed = 1; seed <= 12; seed++) {
      const result = await generateExperience(db, a, request({ moods: ['naughty', 'mysterious'] }), NOW, deps(seed));
      for (const id of [...result.chapters.map((chapter) => chapter.contentId), ...result.alternates]) {
        assert.ok(!byId.get(id)!.excludedByBoundaries.includes(theme), `${id} carries ${theme}`);
      }
    }
  });

  it("the more careful partner's content level caps the night, whatever is asked", async () => {
    const { a } = await couple([5, 2]);
    const byId = await catalogueById();
    const result = await generateExperience(db, a, request({ intensity: 5 }), NOW, deps());
    assert.equal(result.effectiveIntensity, 2);
    for (const id of [...result.chapters.map((chapter) => chapter.contentId), ...result.alternates]) {
      assert.ok(byId.get(id)!.intensity <= 2, `${id} above the ceiling`);
    }
  });

  it("leaves a partner's ask-first themes out of a night played on the other's phone", async () => {
    const { a, b, coupleId } = await couple();
    const byId = await catalogueById();
    // A theme this very night touches, so leaving it out certainly changes something.
    const probe = await generateExperience(db, a, request(), NOW, deps(7));
    const theme = byId.get(probe.chapters[0].contentId)!.excludedByBoundaries[0];
    await db.doc(`users/${b}/boundaries/${theme}`).set({ level: 'ASK_FIRST', updatedAt: new Date(NOW) });
    await recomputeCouple(db, coupleId, NOW);

    // Asking on a's phone would tell a that b's limit exists (D-060), so the theme stays out.
    const result = await generateExperience(db, a, request(), NOW, deps(7));
    for (const id of [...result.chapters.map((chapter) => chapter.contentId), ...result.alternates]) {
      assert.ok(!byId.get(id)!.excludedByBoundaries.includes(theme), `${id} carries ${theme}`);
    }
    assert.deepEqual(result.askFirstItems, [], 'nothing of b ever reaches a');
    const asks = await db.collection(`couples/${coupleId}/experiences/${result.experienceId}/askFirst`).get();
    assert.equal(asks.size, 0);
  });

  it("keeps the host's own ask-first items, flags them for the host alone, and tells no one else", async () => {
    const { a, b, coupleId } = await couple();
    const byId = await catalogueById();
    const probe = await generateExperience(db, a, request(), NOW, deps(7));
    const theme = byId.get(probe.chapters[0].contentId)!.excludedByBoundaries[0];
    await db.doc(`users/${a}/boundaries/${theme}`).set({ level: 'ASK_FIRST', updatedAt: new Date(NOW) });
    await recomputeCouple(db, coupleId, NOW);

    const result = await generateExperience(db, a, request(), NOW, deps(7));
    assert.deepEqual(result.chapters, probe.chapters, 'a flag, not a penalty: the same night');
    const released = [...result.chapters.map((chapter) => chapter.contentId), ...result.alternates];
    const expected = released.filter((id) => byId.get(id)!.excludedByBoundaries.includes(theme));
    assert.ok(expected.includes(probe.chapters[0].contentId));
    assert.deepEqual(result.askFirstItems, expected);
    const asks = await db.collection(`couples/${coupleId}/experiences/${result.experienceId}/askFirst`).get();
    assert.deepEqual(asks.docs.map((doc) => doc.id), [a]);
    assert.deepEqual(asks.docs[0].get('items'), expected);

    // On b's phone, a's ask-first theme is the partner's, and stays out.
    const theirs = await generateExperience(db, b, request(), NOW, deps(7));
    assert.deepEqual(theirs.askFirstItems, []);
    for (const id of [...theirs.chapters.map((chapter) => chapter.contentId), ...theirs.alternates]) {
      assert.ok(!byId.get(id)!.excludedByBoundaries.includes(theme), `${id} carries ${theme}`);
    }
  });

  it('builds a night around a revealed match when asked, and only around a revealed one', async () => {
    const { a, coupleId } = await couple();
    const items = (await loadCatalogue(db, NOW)).items.filter((item) => item.status === 'published' && item.modes.includes('TOGETHER'));
    const counts = new Map<string, number>();
    for (const item of items) {
      for (const pref of new Set([...item.boostedByPreferences, ...item.requiredMutualPreferences])) counts.set(pref, (counts.get(pref) ?? 0) + 1);
    }
    const focus = [...counts].sort((x, y) => y[1] - x[1] || x[0].localeCompare(y[0]))[0][0];

    await rejects(generateExperience(db, a, request({ focus }), NOW, deps()), 'invalid-argument', 'focus');
    await rejects(generateExperience(db, a, request({ focus: 'Not An Id!' }), NOW, deps()), 'invalid-argument', 'focus');

    await db.doc(`couples/${coupleId}/mutualPreferences/${focus}`).set({ matchLevel: 'BOTH_YES', revealedAt: new Date(NOW), seenBy: {} });
    const byId = await catalogueById();
    let included = 0;
    for (let seed = 1; seed <= 6; seed++) {
      const result = await generateExperience(db, a, request({ focus }), NOW, deps(seed));
      const about = result.chapters.filter((chapter) => {
        const item = byId.get(chapter.contentId)!;
        return item.boostedByPreferences.includes(focus) || item.requiredMutualPreferences.includes(focus);
      });
      assert.equal(result.focusIncluded, about.length > 0);
      if (result.focusIncluded) included += 1;
      const stored = (await db.doc(`couples/${coupleId}/experiences/${result.experienceId}`).get()).data()!;
      assert.equal(stored.focus, focus);
      assert.equal(stored.focusIncluded, result.focusIncluded);
    }
    assert.equal(included, 6, 'every night asked to be built around the match holds it');
  });

  it('never picks an item an admin disabled, from the next generation on', async () => {
    const { a } = await couple();
    const first = await generateExperience(db, a, request(), NOW, deps(99));
    const target = first.chapters[1].contentId;
    await db.doc(`content/${target}`).set({ status: 'disabled', version: 1, item: null, updatedAt: new Date(NOW) });
    resetCatalogueCache();

    const second = await generateExperience(db, a, request(), NOW, deps(99));
    assert.ok(![...second.chapters.map((chapter) => chapter.contentId), ...second.alternates].includes(target));
  });

  it('cools down what the couple played recently', async () => {
    const { a, coupleId } = await couple();
    const first = await generateExperience(db, a, request(), NOW, deps(5));
    const byId = await catalogueById();
    const played = first.chapters.map((chapter) => chapter.contentId).filter((id) => byId.get(id)!.repeatCooldownDays > 0);
    await db.doc(`couples/${coupleId}/engineState/history`).set({ lastPlayed: Object.fromEntries(played.map((id) => [id, NOW - 60_000])) });

    const second = await generateExperience(db, a, request(), NOW, deps(5));
    for (const id of [...second.chapters.map((chapter) => chapter.contentId), ...second.alternates]) {
      assert.ok(!played.includes(id), `${id} still cooling down`);
    }
  });

  it('gives the same night for the same seed and state, and another for another seed', async () => {
    const { a } = await couple();
    const once = await generateExperience(db, a, request(), NOW, deps(4242));
    const twice = await generateExperience(db, a, request(), NOW, deps(4242));
    const other = await generateExperience(db, a, request(), NOW, deps(4243));
    assert.deepEqual(twice.chapters, once.chapters);
    assert.deepEqual(twice.alternates, once.alternates);
    assert.notDeepEqual(other.alternates, once.alternates);
    assert.notEqual(twice.experienceId, once.experienceId);
  });

  it('computes missing filters rather than building without them', async () => {
    const { a, coupleId } = await couple();
    await db.doc(`couples/${coupleId}/engineFilters/current`).delete();
    await generateExperience(db, a, request(), NOW, deps());
    assert.ok((await db.doc(`couples/${coupleId}/engineFilters/current`).get()).exists);
  });

  it('refuses a payload that tries to decide what only the server decides', async () => {
    const { a } = await couple();
    await rejects(generateExperience(db, a, request({ seed: 1 }), NOW, deps()), 'invalid-argument', 'unknown field seed');
    await rejects(generateExperience(db, a, request({ coupleId: 'other' }), NOW, deps()), 'invalid-argument', 'unknown field coupleId');
    await rejects(generateExperience(db, a, request({ moods: ['sleepy'] }), NOW, deps()), 'invalid-argument', 'moods');
    await rejects(generateExperience(db, a, request({ intensity: 9 }), NOW, deps()), 'invalid-argument', 'intensity');
    await rejects(generateExperience(db, a, request({ durationMin: 5 }), NOW, deps()), 'invalid-argument', 'durationMin');
    await rejects(generateExperience(db, a, 'a night please', NOW, deps()), 'invalid-argument', 'request');
  });

  it('refuses a caller with no couple, and a couple that has ended', async () => {
    await rejects(generateExperience(db, 'nobody', request(), NOW, deps()), 'failed-precondition', 'not-paired');
    const { a } = await couple();
    await unpairCouple(db, a, NOW);
    await rejects(generateExperience(db, a, request(), NOW, deps()), 'failed-precondition', 'not-paired');
  });

  it('is rate-limited', async () => {
    const { a } = await couple();
    for (let i = 0; i < GENERATE_LIMIT; i++) await generateExperience(db, a, request({ durationMin: 15 }), NOW, deps(i));
    await rejects(generateExperience(db, a, request({ durationMin: 15 }), NOW, deps()), 'resource-exhausted');
  });

  it('says when a couple has excluded too much for any night', async () => {
    const { a, b, coupleId } = await couple();
    const themes = [...new Set((await loadCatalogue(db, NOW)).items.flatMap((item) => item.excludedByBoundaries))];
    await Promise.all(themes.map((theme) => db.doc(`users/${b}/boundaries/${theme}`).set({ level: 'NEVER', updatedAt: new Date(NOW) })));
    await recomputeCouple(db, coupleId, NOW);
    await rejects(generateExperience(db, a, request(), NOW, deps()), 'failed-precondition', 'not-enough-content');
  });
});

describe('Surprise Us and Mystery Night', () => {
  beforeEach(async () => {
    await clearEmulator();
    resetCatalogueCache();
  });

  it('builds a whole night from a phone that asked for nothing', async () => {
    const { a, coupleId } = await couple();

    const result = await surpriseExperience(db, a, {}, NOW, deps());

    const [lo, hi] = chapterRange(45);
    assert.ok(result.chapters.length >= lo && result.chapters.length <= hi);
    assert.equal(result.chapters.at(-1)!.kind, 'FINALE');
    const stored = (await db.doc(`couples/${coupleId}/experiences/${result.experienceId}`).get()).data()!;
    assert.equal(stored.status, 'READY');
    assert.equal(stored.durationMin, 45);
    assert.equal(stored.novelty, 'SURPRISE_ME');
    assert.equal(stored.leadership, 'RANDOM');
    assert.equal(stored.mood.length, 1);
    assert.equal(stored.mystery, false);
    // The couple's ceiling, never past it: theirs is 5 here, and the night warms up to it.
    assert.equal(stored.intensity, 5);
  });

  it('stays under the lower of the two ceilings', async () => {
    const { a, coupleId } = await couple([2, 4]);
    const result = await surpriseExperience(db, a, {}, NOW, deps());
    const stored = (await db.doc(`couples/${coupleId}/experiences/${result.experienceId}`).get()).data()!;
    assert.equal(stored.intensity, 2);
    assert.ok(result.chapters.every((chapter) => chapter.intensity <= 2));
  });

  it('marks a mystery night as one, and is otherwise the same night', async () => {
    const { a, coupleId } = await couple();
    const mystery = await surpriseExperience(db, a, { mystery: true }, NOW, deps());
    assert.equal(mystery.mystery, true);
    const stored = (await db.doc(`couples/${coupleId}/experiences/${mystery.experienceId}`).get()).data()!;
    assert.equal(stored.mystery, true);
    assert.ok(stored.chapters.length > 0);
  });

  it('does not open on the mood the last night used', async () => {
    const { a, coupleId } = await couple();
    await generateExperience(db, a, request({ moods: ['romantic'] }), NOW, deps(7));
    const result = await surpriseExperience(db, a, {}, NOW + 1000, deps(7));
    const stored = (await db.doc(`couples/${coupleId}/experiences/${result.experienceId}`).get()).data()!;
    assert.ok(!stored.mood.includes('romantic'), 'repeated the mood of the night before');
  });

  it('refuses a phone that tries to choose, and counts against the same budget', async () => {
    const { a } = await couple();
    await rejects(surpriseExperience(db, a, { moods: ['bold'] }, NOW, deps()), 'invalid-argument', 'unknown field moods');
    await rejects(surpriseExperience(db, a, { intensity: 5 }, NOW, deps()), 'invalid-argument', 'unknown field intensity');
    await rejects(surpriseExperience(db, 'nobody', {}, NOW, deps()), 'failed-precondition', 'not-paired');

    for (let i = 0; i < GENERATE_LIMIT; i++) await generateExperience(db, a, request({ durationMin: 15 }), NOW, deps(i));
    await rejects(surpriseExperience(db, a, {}, NOW, deps()), 'resource-exhausted');
  });
});

describe('Make it more…', () => {
  beforeEach(async () => {
    await clearEmulator();
    resetCatalogueCache();
  });

  /** The night's own chapters, as the phone would send them back after rearranging them. */
  const asChapters = (stored: Record<string, unknown>) =>
    (stored.chapters as { kind: string; contentIds: string[] }[]).map((chapter) => ({
      contentId: chapter.contentIds[0],
      kind: chapter.kind,
    }));

  it('takes a rearrangement of what it released, and stores it as the night', async () => {
    const { a, coupleId } = await couple();
    const built = await generateExperience(db, a, request(), NOW, deps());
    const ref = db.doc(`couples/${coupleId}/experiences/${built.experienceId}`);
    const before = asChapters((await ref.get()).data()!);
    // The same chapters, one swapped for an alternate the server released for this night.
    const remix = [...before.slice(0, -1), { contentId: built.alternates[0], kind: before.at(-1)!.kind }];

    const result = await remixExperience(db, a, { experienceId: built.experienceId, chapters: remix }, NOW, deps());

    assert.equal(result.chapterCount, remix.length);
    const stored = (await ref.get()).data()!;
    assert.deepEqual(asChapters(stored), remix);
    assert.ok(stored.remixedAt);
  });

  it('refuses anything it did not release for that night', async () => {
    const { a, coupleId } = await couple();
    const built = await generateExperience(db, a, request(), NOW, deps());
    const ref = db.doc(`couples/${coupleId}/experiences/${built.experienceId}`);
    const mine = asChapters((await ref.get()).data()!);
    const stranger = (await loadCatalogue(db, NOW)).items
      .map((item) => item.id)
      .find((id) => !mine.some((chapter) => chapter.contentId === id) && !built.alternates.includes(id))!;

    await rejects(
      remixExperience(db, a, { experienceId: built.experienceId, chapters: [{ contentId: stranger, kind: 'FINALE' }] }, NOW, deps()),
      'permission-denied',
      'not-released',
    );
    // And the night is untouched.
    assert.deepEqual(asChapters((await ref.get()).data()!), mine);
  });

  it('refuses once the night has started, and one built by the other partner', async () => {
    const { a, b, coupleId } = await couple();
    const built = await generateExperience(db, a, request(), NOW, deps());
    const ref = db.doc(`couples/${coupleId}/experiences/${built.experienceId}`);
    const chapters = asChapters((await ref.get()).data()!);

    await rejects(
      remixExperience(db, b, { experienceId: built.experienceId, chapters }, NOW, deps()),
      'permission-denied',
      'not-yours',
    );

    await ref.update({ status: 'IN_PROGRESS', playedThrough: 1 });
    await rejects(
      remixExperience(db, a, { experienceId: built.experienceId, chapters }, NOW, deps()),
      'failed-precondition',
      'already-started',
    );
  });

  it('refuses a limit set since the night was built, and a payload it cannot read', async () => {
    const { a, b, coupleId } = await couple();
    const built = await generateExperience(db, a, request(), NOW, deps());
    const ref = db.doc(`couples/${coupleId}/experiences/${built.experienceId}`);
    const chapters = asChapters((await ref.get()).data()!);

    await rejects(remixExperience(db, a, { experienceId: built.experienceId }, NOW, deps()), 'invalid-argument', 'chapters');
    await rejects(
      remixExperience(db, a, { experienceId: built.experienceId, chapters, extra: true }, NOW, deps()),
      'invalid-argument',
      'unknown field extra',
    );
    await rejects(
      remixExperience(db, a, { experienceId: built.experienceId, chapters: [chapters[0], chapters[0]] }, NOW, deps()),
      'invalid-argument',
      'chapters',
    );

    const themes = (await catalogueById()).get(chapters[0].contentId)!.excludedByBoundaries;
    await Promise.all(themes.map((theme) => db.doc(`users/${b}/boundaries/${theme}`).set({ level: 'NEVER', updatedAt: new Date(NOW) })));
    await recomputeCouple(db, coupleId, NOW);
    await rejects(
      remixExperience(db, a, { experienceId: built.experienceId, chapters }, NOW, deps()),
      'failed-precondition',
      'not-permitted',
    );
  });
});

describe('a night one partner plans for the other', () => {
  beforeEach(async () => {
    await clearEmulator();
    resetCatalogueCache();
  });

  it('is built for the partner who will play it, and told back as a shape alone', async () => {
    const { a, b, coupleId } = await couple();

    const planned = await planNight(db, a, request(), NOW, deps());

    assert.equal(planned.plannedFor, b);
    assert.ok(planned.chapterCount > 0);
    // Nothing the planner could read the night from came back with it.
    assert.deepEqual(Object.keys(planned).sort(), ['chapterCount', 'experienceId', 'plannedFor']);

    const stored = (await db.doc(`couples/${coupleId}/experiences/${planned.experienceId}`).get()).data()!;
    assert.equal(stored.plannedBy, a);
    assert.equal(stored.plannedFor, b);
    assert.equal(stored.createdBy, a);
    assert.equal(stored.status, 'READY');

    const plan = (await db.doc(`couples/${coupleId}/plans/${planned.experienceId}`).get()).data()!;
    assert.equal(plan.state, 'WAITING');
    assert.equal(plan.chapterCount, planned.chapterCount);
    // The plan says nothing about what is in the night.
    for (const key of Object.keys(plan)) assert.ok(!/content|chapters|alternates/i.test(key), `plan has ${key}`);
  });

  it("asks about the partner's own ask-first themes, and leaves the planner's out", async () => {
    const { a, coupleId } = await couple();
    const catalogue = await catalogueById();
    const theme = [...new Set([...catalogue.values()].flatMap((item) => item.excludedByBoundaries))][0];
    // Each partner asks to be asked first about a different theme.
    await db.doc(`users/${a}/boundaries/${theme}`).set({ level: 'ASK_FIRST', updatedAt: new Date(NOW) });
    await recomputeCouple(db, coupleId, NOW);

    const planned = await planNight(db, a, request(), NOW, deps());
    const stored = (await db.doc(`couples/${coupleId}/experiences/${planned.experienceId}`).get()).data()!;
    const ids = stored.chapters.map((chapter: { contentIds: string[] }) => chapter.contentIds[0]);

    // The planner's ask-first theme cannot be asked about on the partner's phone, so it is out.
    for (const id of ids) assert.ok(!catalogue.get(id)!.excludedByBoundaries.includes(theme), id);
    // And the flags that do exist are stored for the partner, never the planner.
    assert.ok(!(await db.doc(`couples/${coupleId}/experiences/${planned.experienceId}/askFirst/${a}`).get()).exists);
  });

  it('is opened once by the partner it was planned for, and by nobody else', async () => {
    const { a, b, coupleId } = await couple();
    const planned = await planNight(db, a, request(), NOW, deps());
    const ref = db.doc(`couples/${coupleId}/plans/${planned.experienceId}`);

    await rejects(openPlan(db, a, { experienceId: planned.experienceId }, NOW), 'permission-denied', 'not-yours');
    assert.equal((await ref.get()).get('state'), 'WAITING');

    await openPlan(db, b, { experienceId: planned.experienceId }, NOW);
    const opened = await ref.get();
    assert.equal(opened.get('state'), 'OPENED');
    const at = opened.get('openedAt');
    // Opening it again changes nothing: the planner sees when it landed, once.
    await openPlan(db, b, { experienceId: planned.experienceId }, NOW + 60_000);
    assert.deepEqual((await ref.get()).get('openedAt'), at);

    await rejects(openPlan(db, b, { experienceId: 'nothing_here' }, NOW), 'not-found', 'plan');
    await rejects(openPlan(db, b, { experienceId: planned.experienceId, extra: 1 }, NOW), 'invalid-argument', 'unknown field extra');
  });
});
