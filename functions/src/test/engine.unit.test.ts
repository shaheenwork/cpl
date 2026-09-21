/**
 * The experience engine, server half (BUILD_PROMPT.md sections 3.2, 5.5, 10; Phase 9 exit:
 * "full engine test suite green on both implementations; determinism proven").
 *
 * Three kinds of test:
 *  - the shared vectors in engine/engine-fixtures.json, which :core:engine must reproduce too;
 *  - adversarial sweeps: no parameter, mood, seed, score or history can bring back an item
 *    either partner excluded (section 3.2: "NEVER cannot be overridden by any parameter");
 *  - the real catalogue: nights build, validate and stay inside every rule.
 */
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, it } from 'node:test';
import { assemble } from '../engine/assemble';
import { askFirstUids, effectiveIntensity, hardFilter, permits } from '../engine/chokepoint';
import { generate, type GeneratedNight } from '../engine/pipeline';
import { plan } from '../engine/plan';
import { Rng, attemptSeed } from '../engine/rng';
import { scoreAll } from '../engine/score';
import {
  LEADERSHIPS,
  MOODS,
  NOVELTIES,
  type CoupleContext,
  type EngineItem,
  type EngineRequest,
  type Mood,
} from '../engine/types';
import { validate } from '../engine/validate';
import { buildFixtures, fixtureCatalogue, openContext, request } from './fixtures/engineFixtures';

const ROOT = join(__dirname, '..', '..', '..');
const committed = JSON.parse(readFileSync(join(ROOT, 'engine', 'engine-fixtures.json'), 'utf8'));
const realCatalogue: EngineItem[] = JSON.parse(readFileSync(join(ROOT, 'content', 'dist', 'bundle.json'), 'utf8')).items;

function isNight(result: GeneratedNight | string): result is GeneratedNight {
  return typeof result !== 'string';
}

/** Every item id a generation lets out of the server: the night and its alternates. */
function released(result: GeneratedNight | string): string[] {
  return isNight(result) ? [...result.chapters.map((chapter) => chapter.itemId), ...result.alternates] : [];
}

/** A spread of requests: both modes, every length, level, leadership and novelty, many moods. */
function* requests(seeds = 3): Generator<EngineRequest> {
  const moodSets: Mood[][] = [[], ['romantic'], ['naughty', 'bold'], ['roleplay'], ['talk', 'mysterious'], [...MOODS]];
  let n = 0;
  for (const mode of ['TOGETHER', 'APART'] as const) {
    for (const durationMin of [15, 30, 45, 60, 90]) {
      for (let intensity = 1; intensity <= 5; intensity++) {
        for (let s = 0; s < seeds; s++) {
          n++;
          yield request({
            mode,
            durationMin,
            intensity,
            moods: moodSets[n % moodSets.length],
            leadership: LEADERSHIPS[n % LEADERSHIPS.length],
            novelty: NOVELTIES[n % NOVELTIES.length],
            seed: (n * 2654435761) >>> 0,
          });
        }
      }
    }
  }
}

describe('shared vectors (engine/engine-fixtures.json)', () => {
  it('are exactly what this implementation computes', () => {
    // If this fails after a deliberate change: npm run engine:fixtures, raise ENGINE_VERSION
    // if output changed for identical inputs, and make :core:engine agree.
    assert.deepStrictEqual(JSON.parse(JSON.stringify(buildFixtures())), committed);
  });

  it('cover every chokepoint reason and every validation rule', () => {
    const reasons = new Set(committed.server.chokepoint.map((entry: { expected: string | null }) => entry.expected));
    for (const reason of ['NOT_PUBLISHED', 'MODE', 'ABOVE_CEILING', 'EXCLUDED_THEME', 'EXCLUDED_ITEM', 'NOT_MUTUAL', 'COOLDOWN', 'MEDIA', null]) {
      assert.ok(reasons.has(reason), `no chokepoint case for ${reason}`);
    }
    const violations = new Set(committed.validate.cases.flatMap((entry: { expected: string[] }) => entry.expected));
    for (const rule of [
      'CHAPTER_COUNT', 'DUPLICATE_ITEM', 'KIND_MISMATCH', 'TYPE_RUN', 'TOO_FEW_TYPES', 'MULTIPLE_YOUR_CHOICE',
      'DURATION', 'ESCALATION', 'ABOVE_CEILING', 'FINALE_NOT_PEAK', 'MODE', 'UNKNOWN_ITEM',
    ]) {
      assert.ok(violations.has(rule), `no validation case for ${rule}`);
    }
  });
});

describe('the chokepoint: NEVER cannot be overridden (section 3.2)', () => {
  const catalogue = realCatalogue;
  const themes = [...new Set(catalogue.flatMap((item) => item.excludedByBoundaries))].sort();

  it('keeps every item of an excluded theme out, whatever else is asked for', () => {
    for (const theme of themes) {
      const tainted = new Set(catalogue.filter((item) => item.excludedByBoundaries.includes(theme)).map((item) => item.id));
      // Everything else pushes towards the excluded items: they are the couple's favourites,
      // both partners are curious about the theme, it is flagged ask-first, and the moods
      // requested are exactly theirs.
      const favourite = catalogue.find((item) => tainted.has(item.id))!;
      const context = openContext({
        filters: {
          maxIntensity: 5,
          excludedThemes: [theme],
          askFirstThemes: { [theme]: ['uid_a'] },
          curiousThemes: [theme],
          excludedItems: [],
        },
        mutual: Object.fromEntries(favourite.boostedByPreferences.map((pref) => [pref, 'BOTH_YES'])),
        affinity: { [`category:${favourite.category}`]: 1, [`interaction:${favourite.interactionType}`]: 1 },
      });
      for (const req of requests(1)) {
        const leaked = released(generate(catalogue, { ...req, moods: favourite.moods as Mood[] }, context)).filter((id) => tainted.has(id));
        assert.deepEqual(leaked, [], `theme ${theme} leaked for ${JSON.stringify(req)}`);
      }
    }
  });

  it('keeps out everything leaning on a preference either partner answered NEVER', () => {
    const prefs = [...new Set(catalogue.flatMap((item) => [...item.boostedByPreferences, ...item.requiredMutualPreferences]))].sort();
    for (const pref of prefs.slice(0, 25)) {
      const tainted = new Set(
        catalogue.filter((item) => item.boostedByPreferences.includes(pref) || item.requiredMutualPreferences.includes(pref)).map((item) => item.id),
      );
      const context = openContext({
        filters: { maxIntensity: 5, excludedThemes: [], askFirstThemes: {}, curiousThemes: [], excludedItems: [pref] },
        // Even as a revealed mutual match — a NEVER from either side outranks it.
        mutual: { [pref]: 'BOTH_YES' },
      });
      for (const req of requests(1)) {
        assert.deepEqual(released(generate(catalogue, req, context)).filter((id) => tainted.has(id)), [], `${pref} leaked`);
      }
    }
  });

  it('never goes above the lower of the request and the couple ceiling', () => {
    for (let ceiling = 1; ceiling <= 5; ceiling++) {
      const context = openContext({ filters: { ...openContext().filters, maxIntensity: ceiling } });
      for (const req of requests(1)) {
        const limit = Math.min(req.intensity, ceiling);
        const byId = new Map(catalogue.map((item) => [item.id, item]));
        for (const id of released(generate(catalogue, req, context))) {
          assert.ok(byId.get(id)!.intensity <= limit, `${id} above ${limit}`);
        }
      }
    }
  });

  it('respects cooldowns, required mutual matches, media and publication', () => {
    const byId = new Map(catalogue.map((item) => [item.id, item]));
    const now = openContext().nowMs;
    const lastPlayed = Object.fromEntries(catalogue.filter((_, index) => index % 3 === 0).map((item) => [item.id, now - 86_400_000]));
    const context = openContext({ lastPlayed });
    for (const req of requests(1)) {
      const noMedia = { ...req, media: { VOICE: false, PHOTO: false } };
      for (const id of released(generate(catalogue, noMedia, context))) {
        const item = byId.get(id)!;
        assert.equal(item.requiresMedia, null, `${id} needs media`);
        assert.deepEqual(item.requiredMutualPreferences, [], `${id} needs a mutual match`);
        assert.equal(item.status, 'published');
        const last = lastPlayed[id];
        assert.ok(last === undefined || now - last >= item.repeatCooldownDays * 86_400_000, `${id} in cooldown`);
      }
    }
  });

  it('reports the first failing reason, in the spec order', () => {
    const item = fixtureCatalogue().find((candidate) => candidate.status !== 'published')!;
    const wrongMode = item.modes.includes('TOGETHER') ? 'APART' : 'TOGETHER';
    assert.equal(permits(item, request({ mode: wrongMode, intensity: 1 }), openContext()), 'NOT_PUBLISHED');
    assert.equal(permits({ ...item, status: 'published' }, request({ mode: wrongMode, intensity: 1 }), openContext()), 'MODE');
  });

  it('fails closed on media this server does not know', () => {
    const item = { ...fixtureCatalogue()[1], requiresMedia: 'HOLOGRAM', status: 'published' };
    assert.equal(permits(item, request({ mode: item.modes[0] as 'TOGETHER' | 'APART', intensity: 5 }), openContext({ mutual: { pref_b: 'BOTH_YES', pref_c: 'BOTH_YES', pref_d: 'BOTH_YES' } })), 'MEDIA');
  });
});

describe('ask-first (section 3.2)', () => {
  it('flags exactly the partners who asked, per chapter, and removes nothing', () => {
    const catalogue = realCatalogue;
    const theme = 'sensory_atmosphere';
    const context = openContext({ filters: { ...openContext().filters, askFirstThemes: { [theme]: ['uid_b'] } } });
    const plainContext = openContext();
    for (const req of [...requests(1)].slice(0, 30)) {
      const flagged = generate(catalogue, req, context);
      const plain = generate(catalogue, req, plainContext);
      // A flag, not a penalty: the same night either way.
      assert.deepEqual(isNight(flagged) && flagged.chapters, isNight(plain) && plain.chapters);
      if (!isNight(flagged)) continue;
      const byId = new Map(catalogue.map((item) => [item.id, item]));
      const expected = flagged.chapters.filter((chapter) => byId.get(chapter.itemId)!.excludedByBoundaries.includes(theme)).map((chapter) => chapter.index);
      assert.deepEqual(flagged.askFirst, expected.length > 0 ? { uid_b: expected } : {});
    }
    assert.deepEqual(askFirstUids({ ...catalogue[0], excludedByBoundaries: ['x', 'y'] }, openContext({ filters: { ...openContext().filters, askFirstThemes: { x: ['b', 'a'], y: ['a'] } } })), ['a', 'b']);
  });
});

describe('determinism (section 10.6)', () => {
  it('gives byte-identical output for the same inputs and seed', () => {
    for (const req of [...requests(2)].slice(0, 60)) {
      const first = JSON.stringify(generate(realCatalogue, req, openContext()));
      const second = JSON.stringify(generate(realCatalogue, { ...req }, openContext()));
      assert.equal(first, second);
    }
  });

  it('gives different nights for different seeds', () => {
    const nights = new Set<string>();
    for (let seed = 1; seed <= 40; seed++) {
      const result = generate(realCatalogue, request({ durationMin: 60, intensity: 4, seed }), openContext());
      assert.ok(isNight(result));
      nights.add(result.chapters.map((chapter) => chapter.itemId).join(','));
    }
    assert.ok(nights.size >= 30, `only ${nights.size} distinct nights from 40 seeds`);
  });

  it('reproduces the reference random sequence exactly', () => {
    const rng = new Rng(42);
    assert.deepEqual([rng.next(), rng.next()], committed.rng.find((entry: { seed: number }) => entry.seed === 42).draws.slice(0, 2));
    assert.equal(attemptSeed(0xffffffff, 1), (0xffffffff + 0x9e3779b9) % 2 ** 32);
  });
});

describe('the alternates pool (section 5.5)', () => {
  it('is bounded, disjoint from the night, never the whole permitted set, and resampled per seed', () => {
    for (const req of [...requests(1)].slice(0, 80)) {
      const context = openContext();
      const result = generate(realCatalogue, req, context);
      if (!isNight(result)) continue;
      const permitted = hardFilter(realCatalogue, req, context);
      const chosen = new Set(result.chapters.map((chapter) => chapter.itemId));
      assert.ok(result.alternates.length <= 3 * result.chapters.length, 'more than three times the night');
      assert.ok(result.alternates.every((id) => !chosen.has(id)), 'an alternate is already in the night');
      assert.ok(result.alternates.length + chosen.size < permitted.length, 'the whole permitted set was released');
      const other = generate(realCatalogue, { ...req, seed: (req.seed + 1) >>> 0 }, context);
      if (isNight(other)) assert.notDeepEqual(other.alternates, result.alternates, 'the pool did not change with the seed');
    }
  });

  it('never releases every permitted item even when there are only a few', () => {
    const catalogue = fixtureCatalogue();
    const req = request({ durationMin: 15, intensity: 5, seed: 3 });
    const context = openContext({
      filters: { ...openContext().filters, excludedThemes: ['theme_b', 'theme_c', 'theme_d', 'theme_e'] },
    });
    const permitted = hardFilter(catalogue, req, context);
    const result = generate(catalogue, req, context);
    if (isNight(result)) assert.ok(released(result).length < permitted.length);
  });
});

describe('the real catalogue', () => {
  it('builds a valid night for almost every request, and every night keeps every rule', () => {
    const byId = new Map(realCatalogue.map((item) => [item.id, item]));
    let total = 0;
    let failed = 0;
    for (const req of requests(4)) {
      total++;
      const context = openContext();
      const result = generate(realCatalogue, req, context);
      if (!isNight(result)) {
        failed++;
        continue;
      }
      const effective = effectiveIntensity(req, context);
      assert.deepEqual(
        validate({ chapters: result.chapters, durationMin: req.durationMin, effective, mode: req.mode }, byId),
        [],
        JSON.stringify(req),
      );
      for (const id of released(result)) assert.equal(permits(byId.get(id)!, req, context), undefined);
    }
    assert.ok(failed / total <= 0.01, `${failed} of ${total} requests found no night`);
  });

  it('the finale is the requested intensity whenever the catalogue has one to give', () => {
    let atE = 0;
    let total = 0;
    for (const req of requests(2)) {
      const result = generate(realCatalogue, req, openContext());
      if (!isNight(result)) continue;
      total++;
      if (result.chapters[result.chapters.length - 1].intensity === result.effectiveIntensity) atE++;
    }
    assert.ok(atE / total >= 0.95, `finale at the requested intensity in only ${atE} of ${total}`);
  });

  it('plans kinds the moods ask for more often', () => {
    const count = (moods: Mood[]) => {
      let roleplay = 0;
      for (let seed = 1; seed <= 200; seed++) {
        // A short night: a long one uses nearly every kind once, whatever the mood.
        const shape = plan(request({ durationMin: 30, moods, seed }), 3, new Set(realCatalogue.flatMap((item) => item.chapterKinds)), new Rng(seed));
        if (typeof shape !== 'string') roleplay += shape.kinds.filter((kind) => kind === 'ROLEPLAY').length;
      }
      return roleplay;
    };
    assert.ok(count(['roleplay']) > count([]) * 1.3);
  });

  it('assembles only from the pool it is given', () => {
    const req = request({ durationMin: 45, intensity: 3, seed: 11 });
    const context: CoupleContext = openContext();
    const permitted = hardFilter(realCatalogue, req, context);
    const pool = permitted.filter((_, index) => index % 4 === 0);
    const scores = scoreAll(pool, req, context);
    const shape = plan(req, 3, new Set(pool.flatMap((item) => item.chapterKinds)), new Rng(11));
    assert.notEqual(typeof shape, 'string');
    const chapters = assemble({ pool, scores, plan: shape as never, durationMin: 45, effective: 3, repetitionWeight: 3 });
    const inPool = new Set(pool.map((item) => item.id));
    for (const chapter of chapters ?? []) assert.ok(inPool.has(chapter.itemId));
  });
});
