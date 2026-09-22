/**
 * Builds engine/engine-fixtures.json: the test vectors both engine implementations must
 * reproduce exactly (engine/SPEC.md). The TypeScript suite checks the committed file still
 * matches what this builds; the Kotlin suite (:core:engine) checks it computes the same
 * answers independently. A mismatch in either is a bug in one implementation or the spec.
 *
 * The catalogue here is synthetic and fixed, so editing real content never churns the
 * fixtures. The real catalogue is exercised by property tests on both sides instead.
 */
import { assemble } from '../../engine/assemble';
import { effectiveIntensity, hardFilter, permits } from '../../engine/chokepoint';
import { generate } from '../../engine/pipeline';
import { plan } from '../../engine/plan';
import { Rng, attemptSeed } from '../../engine/rng';
import { samplePool } from '../../engine/sample';
import { scoreAll } from '../../engine/score';
import {
  CHAPTER_KINDS,
  DEFAULT_WEIGHTS,
  ENGINE_VERSION,
  type Chapter,
  type CoupleContext,
  type EngineItem,
  type EngineRequest,
  type Leadership,
  type Mode,
  type Mood,
  type Novelty,
} from '../../engine/types';
import { validate } from '../../engine/validate';

const TYPES = [
  'CONVERSATION', 'CONFESSION', 'CHALLENGE', 'CHOICE', 'PREDICTION', 'REVEAL',
  'SURPRISE', 'CONTROL', 'MEMORY', 'ROLEPLAY', 'SENSORY', 'MESSAGE',
];
const MOOD_IDS: Mood[] = ['romantic', 'naughty', 'bold', 'unpredictable', 'mysterious', 'roleplay', 'talk', 'experimental'];
const THEMES = ['theme_a', 'theme_b', 'theme_c', 'theme_d', 'theme_e', 'theme_f'];
const PREFS = ['pref_a', 'pref_b', 'pref_c', 'pref_d', 'pref_e'];
const DURATIONS = [3, 5, 8, 10, 15, 20, 4, 6];
const DAY = 86_400_000;
const NOW = 1_790_000_000_000;

/** A fixed, varied catalogue: every kind, type, level and mode, and a few awkward items. */
export function fixtureCatalogue(): EngineItem[] {
  const items: EngineItem[] = [];
  // Each block of ten shares an intensity and covers all ten kinds, so every kind exists at
  // every level twice over; modes, types and lengths cycle on other periods to mix them.
  for (let n = 0; n < 96; n++) {
    const kinds = [...new Set([CHAPTER_KINDS[n % 10], CHAPTER_KINDS[(n * 7 + 3) % 10]])];
    items.push({
      id: `fx_${String(n).padStart(3, '0')}`,
      version: 1,
      pack: n % 2 === 0 ? 'EVEN' : 'ODD',
      category: ['teasing', 'sensory', 'communication'][n % 3],
      intensity: 1 + (Math.floor(n / 10) % 5),
      modes: n % 3 === 0 ? ['TOGETHER'] : n % 3 === 1 ? ['APART'] : ['TOGETHER', 'APART'],
      interactionType: TYPES[(n * 5 + Math.floor(n / 12)) % TYPES.length],
      durationMin: DURATIONS[(n * 3) % DURATIONS.length],
      moods: [...new Set([MOOD_IDS[n % 8], MOOD_IDS[(n + 3) % 8]])],
      chapterKinds: kinds,
      tags: ['fixture'],
      noveltyWeight: [0.25, 0.5, 0.75, 1][n % 4],
      repeatCooldownDays: [0, 7, 14, 30][n % 4],
      requiresMedia: n % 11 === 0 ? 'VOICE' : n % 13 === 0 ? 'PHOTO' : null,
      status: n % 23 === 22 ? 'disabled' : 'published',
      excludedByBoundaries: [THEMES[n % THEMES.length]],
      requiredMutualPreferences: n % 17 === 0 ? [PREFS[(n + 2) % PREFS.length]] : [],
      boostedByPreferences: [PREFS[n % PREFS.length]],
    });
  }
  return items;
}

export function openContext(overrides: Partial<CoupleContext> = {}): CoupleContext {
  return {
    filters: { maxIntensity: 5, excludedThemes: [], askFirstThemes: {}, curiousThemes: [], excludedItems: [] },
    mutual: {},
    lastPlayed: {},
    nowMs: NOW,
    affinity: {},
    weights: DEFAULT_WEIGHTS,
    ...overrides,
  };
}

export function request(overrides: Partial<EngineRequest> = {}): EngineRequest {
  return {
    mode: 'TOGETHER',
    durationMin: 45,
    moods: [],
    intensity: 3,
    leadership: 'ME',
    novelty: 'MIXED',
    seed: 42,
    media: { VOICE: true, PHOTO: true },
    ...overrides,
  };
}

interface PlanCase {
  name: string;
  request: EngineRequest;
  effective: number;
  available: string[];
  expected: { kinds: string[]; targets: number[] } | string;
}

interface AssembleCase {
  name: string;
  mode: Mode;
  pool: string[];
  scores: Record<string, number>;
  plan: { kinds: string[]; targets: number[] };
  durationMin: number;
  effective: number;
  repetitionWeight: number;
  expected: Chapter[] | null;
}

interface ValidateCase {
  name: string;
  chapters: Chapter[];
  durationMin: number;
  effective: number;
  mode: Mode;
  expected: string[];
}

function rngCases() {
  return [0, 1, 42, 0x7fffffff, 0x80000000, 0xffffffff, 123456789].map((seed) => {
    const rng = new Rng(seed);
    const draws = Array.from({ length: 6 }, () => rng.next());
    const ints = Array.from({ length: 6 }, () => rng.nextInt(10));
    return { seed, draws, ints, attemptSeeds: [0, 1, 2, 3, 4].map((attempt) => attemptSeed(seed, attempt)) };
  });
}

function planCases(): PlanCase[] {
  const all = [...CHAPTER_KINDS];
  const cases: PlanCase[] = [];
  const moodSets: Mood[][] = [[], ['romantic'], ['naughty', 'bold'], ['roleplay', 'talk'], ['unpredictable', 'mysterious', 'experimental']];
  const leaderships: Leadership[] = ['ME', 'YOU', 'SWITCH', 'RANDOM'];
  let n = 0;
  for (const durationMin of [15, 30, 45, 60, 90, 180]) {
    for (const effective of [1, 3, 5]) {
      const moods = moodSets[n % moodSets.length];
      const leadership = leaderships[n % leaderships.length];
      const req = request({ durationMin, moods, leadership, seed: 1000 + n * 7919 });
      const shape = plan(req, effective, new Set(all), new Rng(req.seed));
      cases.push({ name: `d${durationMin} E${effective} ${moods.join('+') || 'no mood'} ${leadership}`, request: req, effective, available: all, expected: shape });
      n++;
    }
  }
  const narrow: [string, string[]][] = [
    ['no warm-up available', all.filter((kind) => kind !== 'WARM_UP')],
    ['no finale available', all.filter((kind) => kind !== 'FINALE')],
    ['only two middle kinds', ['WARM_UP', 'TEASE', 'CHALLENGE', 'FINALE']],
    ['one middle kind cannot fill two slots in a row', ['WARM_UP', 'TEASE', 'FINALE']],
    ['your choice at most once', ['WARM_UP', 'YOUR_CHOICE', 'TEASE', 'FINALE']],
  ];
  for (const [name, available] of narrow) {
    const req = request({ durationMin: 60, seed: 77 });
    cases.push({ name, request: req, effective: 4, available, expected: plan(req, 4, new Set(available), new Rng(req.seed)) });
  }
  return cases;
}

/** Assembly cases built the way the server builds them, recorded with explicit inputs. */
function assembleCases(catalogue: EngineItem[]): AssembleCase[] {
  const cases: AssembleCase[] = [];
  const modes: Mode[] = ['TOGETHER', 'APART'];
  const novelties: Novelty[] = ['FAMILIAR', 'MIXED', 'SURPRISE_ME'];
  let n = 0;
  for (const mode of modes) {
    for (const durationMin of [15, 30, 45, 60, 90, 150]) {
      for (const intensity of [2, 4, 5]) {
        const req = request({
          mode,
          durationMin,
          intensity,
          moods: [MOOD_IDS[n % 8]],
          leadership: n % 2 === 0 ? 'ME' : 'RANDOM',
          novelty: novelties[n % 3],
          seed: 9000 + n * 104729,
        });
        const context = openContext();
        const permitted = hardFilter(catalogue, req, context);
        const scores = scoreAll(permitted, req, context);
        const effective = effectiveIntensity(req, context);
        const rng = new Rng(req.seed);
        const shape = plan(req, effective, new Set(permitted.flatMap((item) => item.chapterKinds)), rng);
        n++;
        if (typeof shape === 'string') continue;
        const pool = samplePool(permitted, scores, shape, durationMin, rng);
        const chapters = assemble({ pool, scores, plan: shape, durationMin, effective, repetitionWeight: 3 }) ?? null;
        cases.push({
          name: `${mode} d${durationMin} E${effective} seed ${req.seed}`,
          mode,
          pool: pool.map((item) => item.id),
          scores: Object.fromEntries(pool.map((item) => [item.id, scores.get(item.id)!])),
          plan: shape,
          durationMin,
          effective,
          repetitionWeight: 3,
          expected: chapters,
        });
      }
    }
  }
  // A pool that cannot fill the plan: nothing in it carries CONTROL.
  const noControl = catalogue
    .filter((item) => item.modes.includes('TOGETHER') && item.status === 'published' && !item.chapterKinds.includes('CONTROL'))
    .slice(0, 12);
  const noControlScores = new Map(noControl.map((item, index) => [item.id, 5 - index / 10]));
  const noControlPlan = { kinds: ['WARM_UP', 'CONTROL', 'FINALE'] as Chapter['kind'][], targets: [1, 2, 3] };
  cases.push({
    name: 'unfillable: no item for a CONTROL slot',
    mode: 'TOGETHER',
    pool: noControl.map((item) => item.id),
    scores: Object.fromEntries(noControlScores),
    plan: noControlPlan,
    durationMin: 15,
    effective: 3,
    repetitionWeight: 3,
    expected: assemble({ pool: noControl, scores: noControlScores, plan: noControlPlan, durationMin: 15, effective: 3, repetitionWeight: 3 }) ?? null,
  });
  return cases;
}

/** A small hand-built item, for validation cases that must break exactly one rule. */
function handItem(
  id: string,
  kinds: string[],
  intensity: number,
  interactionType: string,
  durationMin: number,
  modes: string[] = ['TOGETHER', 'APART'],
): EngineItem {
  return {
    id,
    version: 1,
    pack: 'HAND',
    category: 'teasing',
    intensity,
    modes,
    interactionType,
    durationMin,
    moods: ['romantic'],
    chapterKinds: kinds,
    tags: ['fixture'],
    noveltyWeight: 0.5,
    repeatCooldownDays: 0,
    requiresMedia: null,
    status: 'published',
    excludedByBoundaries: ['theme_a'],
    requiredMutualPreferences: [],
    boostedByPreferences: [],
  };
}

function validationItems(): EngineItem[] {
  return [
    handItem('v_warm1', ['WARM_UP'], 1, 'CONVERSATION', 5),
    handItem('v_tease2', ['TEASE'], 2, 'CHALLENGE', 5),
    handItem('v_tease2b', ['TEASE'], 2, 'CHALLENGE', 5),
    handItem('v_finale1', ['FINALE'], 1, 'SURPRISE', 5),
    handItem('v_finale2', ['FINALE'], 2, 'SURPRISE', 5),
    handItem('v_finale2c', ['FINALE'], 2, 'CHALLENGE', 5),
    handItem('v_finale3', ['FINALE'], 3, 'SURPRISE', 5),
    handItem('v_finale5', ['FINALE'], 5, 'SURPRISE', 5),
    handItem('v_choice2', ['YOUR_CHOICE'], 2, 'CHOICE', 8),
    handItem('v_choice2b', ['YOUR_CHOICE'], 2, 'CHOICE', 8),
    handItem('v_apart2', ['TEASE'], 2, 'MESSAGE', 5, ['APART']),
    handItem('v6_warm', ['WARM_UP'], 1, 'CONVERSATION', 10),
    handItem('v6_tease', ['TEASE'], 1, 'CHALLENGE', 10),
    handItem('v6_curiosity', ['CURIOSITY'], 2, 'CONVERSATION', 10),
    handItem('v6_challenge', ['CHALLENGE'], 2, 'CHALLENGE', 10),
    handItem('v6_confession', ['CONFESSION'], 3, 'CONVERSATION', 10),
    handItem('v6_finale', ['FINALE'], 3, 'CHALLENGE', 10),
  ];
}

function validateCases(catalogue: EngineItem[]) {
  const items = validationItems();
  const handById = new Map(items.map((item) => [item.id, item]));
  const night = (...entries: [string, string][]): Chapter[] =>
    entries.map(([kind, itemId], index) => ({
      index,
      kind: kind as Chapter['kind'],
      itemId,
      intensity: handById.get(itemId)?.intensity ?? 0,
    }));
  const base = night(['WARM_UP', 'v_warm1'], ['TEASE', 'v_tease2'], ['FINALE', 'v_finale3']);
  const together = (chapters: Chapter[], durationMin: number, effective = 3) => ({ chapters, durationMin, effective, mode: 'TOGETHER' as Mode });

  const hand: (Omit<ValidateCase, 'expected'>)[] = [
    { name: 'a well-formed three-chapter night', ...together(base, 15) },
    { name: 'over the duration window', ...together(base, 10) },
    { name: 'the same item twice', ...together(night(['WARM_UP', 'v_warm1'], ['WARM_UP', 'v_warm1'], ['FINALE', 'v_finale2']), 15) },
    { name: 'a kind the item does not carry', ...together(night(['CONTROL', 'v_warm1'], ['TEASE', 'v_tease2'], ['FINALE', 'v_finale3']), 15) },
    { name: 'a jump of three levels, above the ceiling', ...together(night(['WARM_UP', 'v_warm1'], ['TEASE', 'v_tease2'], ['FINALE', 'v_finale5']), 15, 4) },
    { name: 'a finale below the chapter before it', ...together(night(['WARM_UP', 'v_warm1'], ['TEASE', 'v_tease2'], ['FINALE', 'v_finale1']), 15) },
    { name: 'no finale last', ...together(night(['WARM_UP', 'v_warm1'], ['FINALE', 'v_finale2'], ['TEASE', 'v_tease2']), 15) },
    { name: 'an item for the other mode', ...together(night(['WARM_UP', 'v_warm1'], ['TEASE', 'v_apart2'], ['FINALE', 'v_finale3']), 15) },
    { name: 'three chapters for an hour', ...together(base, 60) },
    { name: 'an id nobody knows', ...together(night(['WARM_UP', 'v_warm1'], ['TEASE', 'v_missing'], ['FINALE', 'v_finale3']), 15) },
    { name: 'three of one interaction type in a row', ...together(night(['TEASE', 'v_tease2'], ['TEASE', 'v_tease2b'], ['FINALE', 'v_finale2c']), 15) },
    {
      name: 'two your-choice chapters',
      ...together(night(['WARM_UP', 'v_warm1'], ['YOUR_CHOICE', 'v_choice2'], ['YOUR_CHOICE', 'v_choice2b'], ['FINALE', 'v_finale3']), 30),
    },
    {
      name: 'six chapters, two interaction types',
      ...together(
        night(
          ['WARM_UP', 'v6_warm'],
          ['TEASE', 'v6_tease'],
          ['CURIOSITY', 'v6_curiosity'],
          ['CHALLENGE', 'v6_challenge'],
          ['CONFESSION', 'v6_confession'],
          ['FINALE', 'v6_finale'],
        ),
        60,
      ),
    },
  ];
  const cases: ValidateCase[] = hand.map((entry) => ({ ...entry, expected: validate(entry, handById) }));

  // Every night the fixtures' assembler built is checked too, against the catalogue.
  const catalogueById = new Map(catalogue.map((item) => [item.id, item]));
  const assembled: ValidateCase[] = [];
  for (const entry of assembleCases(catalogue)) {
    if (!entry.expected) continue;
    const check = { chapters: entry.expected, durationMin: entry.durationMin, effective: entry.effective, mode: entry.mode };
    assembled.push({ name: `assembled: ${entry.name}`, ...check, expected: validate(check, catalogueById) });
  }
  return { items, cases, assembled };
}

/** Server-only vectors: the chokepoint's reasons, and whole generations. */
function serverCases(catalogue: EngineItem[]) {
  const byId = new Map(catalogue.map((item) => [item.id, item]));
  const first = (predicate: (item: EngineItem) => boolean): EngineItem => {
    const found = catalogue.find(predicate);
    if (!found) throw new Error('fixture catalogue lacks an item a chokepoint case needs');
    return found;
  };
  const clean = (item: EngineItem) => item.status === 'published' && item.requiresMedia === null && item.requiredMutualPreferences.length === 0;
  const plain = first((item) => clean(item) && item.repeatCooldownDays > 0 && item.modes.length === 2);
  const disabled = first((item) => item.status !== 'published');
  const togetherOnly = first((item) => clean(item) && item.modes.length === 1 && item.modes[0] === 'TOGETHER');
  const intense = first((item) => clean(item) && item.intensity === 4 && item.modes.length === 2);
  const needsMutual = first((item) => item.status === 'published' && item.requiredMutualPreferences.length > 0 && item.requiresMedia === null);
  const voice = first((item) => item.status === 'published' && item.requiresMedia === 'VOICE');
  const photo = first((item) => item.status === 'published' && item.requiresMedia === 'PHOTO');
  const modeOf = (item: EngineItem): Mode => item.modes[0] as Mode;
  const withFilters = (filters: Partial<CoupleContext['filters']>, rest: Partial<CoupleContext> = {}) =>
    openContext({ filters: { ...openContext().filters, ...filters }, ...rest });
  const mutualFor = (item: EngineItem) => Object.fromEntries(item.requiredMutualPreferences.map((pref) => [pref, 'BOTH_YES' as const]));

  const explicit: { name: string; itemId: string; request: EngineRequest; context: CoupleContext }[] = [
    { name: 'permitted', itemId: plain.id, request: request({ intensity: 5 }), context: openContext() },
    { name: 'disabled content', itemId: disabled.id, request: request({ mode: modeOf(disabled), intensity: 5 }), context: openContext() },
    {
      name: 'disabled and in the wrong mode: the first reason wins',
      itemId: disabled.id,
      request: request({ mode: modeOf(disabled) === 'TOGETHER' ? 'APART' : 'TOGETHER', intensity: 5 }),
      context: openContext(),
    },
    { name: 'the other mode', itemId: togetherOnly.id, request: request({ mode: 'APART', intensity: 5 }), context: openContext() },
    { name: 'above the requested intensity', itemId: intense.id, request: request({ intensity: 3 }), context: openContext() },
    { name: 'above the couple ceiling, though requested', itemId: intense.id, request: request({ intensity: 5 }), context: withFilters({ maxIntensity: 3 }) },
    { name: 'a theme either partner said NEVER to', itemId: plain.id, request: request({ intensity: 5 }), context: withFilters({ excludedThemes: [plain.excludedByBoundaries[0]] }) },
    { name: 'a boosted preference someone said NEVER to', itemId: plain.id, request: request({ intensity: 5 }), context: withFilters({ excludedItems: [plain.boostedByPreferences[0]] }) },
    {
      name: 'a required preference someone said NEVER to, even though mutual',
      itemId: needsMutual.id,
      request: request({ mode: modeOf(needsMutual), intensity: 5 }),
      context: withFilters({ excludedItems: [needsMutual.requiredMutualPreferences[0]] }, { mutual: mutualFor(needsMutual) }),
    },
    { name: 'a required preference not yet mutual', itemId: needsMutual.id, request: request({ mode: modeOf(needsMutual), intensity: 5 }), context: openContext() },
    {
      name: 'a required preference revealed as mutual',
      itemId: needsMutual.id,
      request: request({ mode: modeOf(needsMutual), intensity: 5 }),
      context: openContext({ mutual: { [needsMutual.requiredMutualPreferences[0]]: 'MIXED_POSITIVE' } }),
    },
    {
      name: 'played inside its cooldown',
      itemId: plain.id,
      request: request({ intensity: 5 }),
      context: openContext({ lastPlayed: { [plain.id]: NOW - plain.repeatCooldownDays * DAY + 1 } }),
    },
    {
      name: 'played exactly one cooldown ago',
      itemId: plain.id,
      request: request({ intensity: 5 }),
      context: openContext({ lastPlayed: { [plain.id]: NOW - plain.repeatCooldownDays * DAY } }),
    },
    {
      name: 'needs a voice note on a device that cannot record',
      itemId: voice.id,
      request: request({ mode: modeOf(voice), intensity: 5, media: { VOICE: false, PHOTO: true } }),
      context: openContext({ mutual: mutualFor(voice) }),
    },
    {
      name: 'needs a photo on a device that cannot take one',
      itemId: photo.id,
      request: request({ mode: modeOf(photo), intensity: 5, media: { VOICE: true, PHOTO: false } }),
      context: openContext({ mutual: mutualFor(photo) }),
    },
  ];
  const combinations = catalogue.slice(0, 40).map((candidate, n) => ({
    name: `${candidate.id} #${n}`,
    itemId: candidate.id,
    request: request({ mode: n % 2 === 0 ? 'TOGETHER' : 'APART', intensity: 1 + (n % 5), media: { VOICE: n % 3 !== 0, PHOTO: n % 4 !== 0 } }),
    context: openContext({
      filters: {
        maxIntensity: 5 - (n % 3),
        excludedThemes: n % 5 === 0 ? ['theme_a', 'theme_c'] : [],
        askFirstThemes: {},
        curiousThemes: [],
        excludedItems: n % 7 === 0 ? ['pref_b'] : [],
      },
      mutual: n % 2 === 0 ? { pref_c: 'BOTH_YES', pref_d: 'BOTH_CURIOUS' } : {},
      lastPlayed: n % 6 === 0 ? { [candidate.id]: NOW - 3 * DAY } : {},
    }),
  }));
  const chokepoint = [...explicit, ...combinations].map((entry) => ({
    ...entry,
    expected: permits(byId.get(entry.itemId)!, entry.request, entry.context) ?? null,
  }));

  const generations = [
    { name: 'open couple, three quarters of an hour', request: request({ durationMin: 45, intensity: 3, seed: 5 }), context: openContext() },
    {
      name: 'a NEVER, a ceiling, ask-first and curious themes',
      request: request({ durationMin: 45, intensity: 5, seed: 6 }),
      context: withFilters({
        maxIntensity: 4,
        excludedThemes: ['theme_a'],
        askFirstThemes: { theme_c: ['uid_b'], theme_d: ['uid_a', 'uid_b'] },
        curiousThemes: ['theme_e'],
        excludedItems: ['pref_e'],
      }),
    },
    {
      name: 'apart, surprise me, mutual matches',
      request: request({ mode: 'APART', durationMin: 30, intensity: 4, novelty: 'SURPRISE_ME', seed: 7 }),
      context: openContext({ mutual: { pref_a: 'BOTH_YES', pref_b: 'MIXED_POSITIVE', pref_c: 'BOTH_SECRET' } }),
    },
    {
      name: 'long night, familiar, with history',
      request: request({ durationMin: 150, intensity: 5, novelty: 'FAMILIAR', seed: 8 }),
      context: openContext({
        lastPlayed: { fx_000: NOW - 40 * DAY, fx_005: NOW - 100 * DAY },
        affinity: { 'category:sensory': 0.8, 'interaction:CHALLENGE': -0.5 },
      }),
    },
    {
      name: 'built around a revealed match',
      request: request({ durationMin: 45, intensity: 4, focus: 'pref_b', seed: 10 }),
      context: openContext({ mutual: { pref_b: 'BOTH_CURIOUS' } }),
    },
    { name: 'everything excluded', request: request({ durationMin: 30, seed: 9 }), context: withFilters({ excludedThemes: [...THEMES] }) },
  ].map((entry) => ({ ...entry, expected: generate(catalogue, entry.request, entry.context) }));

  return { chokepoint, generations };
}

export function buildFixtures() {
  const catalogue = fixtureCatalogue();
  return {
    description:
      'Shared test vectors for the experience engine (engine/SPEC.md). Generated by ' +
      'npm --prefix functions run engine:fixtures; do not edit by hand. Both functions/ and ' +
      ':core:engine must reproduce every expected value exactly.',
    engineVersion: ENGINE_VERSION,
    catalogue,
    rng: rngCases(),
    plan: planCases(),
    assemble: assembleCases(catalogue),
    validate: validateCases(catalogue),
    server: serverCases(catalogue),
  };
}
