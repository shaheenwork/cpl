/**
 * The content validator (BUILD_PROMPT.md section 9.4). Fails the build on schema violations,
 * duplicate ids, unknown tags/moods/packs, intensity out of range, missing intensity coverage
 * per pack, near-duplicate bodies, prohibited-theme terms (section 3.3), items with no valid
 * mode, and items excluded by their own required preferences.
 *
 * Beyond the spec's list, two checks exist because the boundary engine depends on them:
 *  - an item leaning on a preference must be excludable by that preference's boundary
 *    theme, so a partner's NEVER always reaches it (section 3.2);
 *  - an item that mentions a sensitive theme (blindfolds, strangers, instructions...) must
 *    list a boundary that covers it, so a careless edit cannot slip past someone's limit.
 *
 * It also checks the content can feed the engine: enough items, in enough chapter kinds, at
 * every intensity, in both modes (section 10.4).
 */
import type { ContentItem, ContentRepo, TaxonomyItem } from './content';

export interface ValidationResult {
  problems: string[];
  report: string;
}

const ID = /^[a-z0-9_]{3,64}$/;
const MODES = ['TOGETHER', 'APART'];
const FIELDS = [
  'id', 'version', 'title', 'subtitle', 'body', 'pack', 'category', 'tags', 'intensity', 'modes',
  'interactionType', 'durationMin', 'moods', 'requiredMutualPreferences', 'boostedByPreferences',
  'excludedByBoundaries', 'chapterKinds', 'noveltyWeight', 'repeatCooldownDays', 'requiresMedia',
  'status', 'locale', 'createdAt', 'updatedAt',
];

/** How similar two bodies may be before they count as near-duplicates (word-trigram Jaccard). */
export const NEAR_DUPLICATE_THRESHOLD = 0.4;

const LIMITS = {
  title: 40,
  subtitle: 70,
  bodyMin: 25,
  bodyMax: 320,
  durationMax: 30,
  cooldownMax: 365,
};

/** What the engine needs to assemble any night (section 10.4), per mode. */
export const COVERAGE = {
  finalePerIntensity: 3,
  warmUpAtLowIntensity: 6,
  perChapterKind: 8,
  totalPublished: 600,
};

export function validateContent(repo: ContentRepo): ValidationResult {
  const problems: string[] = [];
  const problem = (where: string, message: string) => problems.push(`${where}: ${message}`);

  const taxonomyItems = new Map<string, TaxonomyItem & { themeId: string }>();
  const themeIds = new Set<string>();
  for (const category of repo.taxonomy.categories) {
    for (const theme of category.themes) {
      themeIds.add(theme.id);
      for (const item of theme.items) taxonomyItems.set(item.id, { ...item, themeId: theme.id });
    }
  }
  const categoryIds = new Set(repo.taxonomy.categories.map((c) => c.id));
  const vocab = repo.vocabulary;
  const prohibited = repo.policy.groups.flatMap((group) =>
    group.patterns.map((pattern) => ({ reason: group.reason, pattern, regex: new RegExp(`\\b(?:${pattern})\\b`, 'i') })),
  );
  const keywords = vocab.boundaryKeywords.map((k) => ({ ...k, regex: new RegExp(`\\b(?:${k.pattern})\\b`, 'i') }));

  const all: ContentItem[] = [];
  const seenIds = new Map<string, string>();

  for (const pack of repo.packs) {
    const raw = repo.itemsByPack.get(pack.id) ?? [];
    const titles = new Map<string, string>();

    raw.forEach((candidate, index) => {
      const where = `${pack.file}[${index}]`;
      if (typeof candidate !== 'object' || candidate === null || Array.isArray(candidate)) {
        problem(where, 'not an object');
        return;
      }
      const item = candidate as ContentItem;
      const at = `${pack.file} ${typeof item.id === 'string' ? item.id : `[${index}]`}`;
      const check = (ok: boolean, message: string) => {
        if (!ok) problem(at, message);
      };

      // --- shape -----------------------------------------------------------------------
      const keys = Object.keys(item);
      for (const key of keys) check(FIELDS.includes(key), `unknown field "${key}"`);
      for (const key of FIELDS) check(key in item, `missing field "${key}"`);

      check(typeof item.id === 'string' && ID.test(item.id), 'id must match ^[a-z0-9_]{3,64}$');
      check(typeof item.id === 'string' && item.id.startsWith(`${pack.prefix}_`), `id must start with "${pack.prefix}_"`);
      if (typeof item.id === 'string') {
        const earlier = seenIds.get(item.id);
        check(earlier === undefined, `duplicate id (also in ${earlier})`);
        seenIds.set(item.id, pack.file);
      }
      check(Number.isInteger(item.version) && item.version >= 1, 'version must be a positive integer');
      checkText(check, item.title, 'title', 1, LIMITS.title);
      checkText(check, item.subtitle, 'subtitle', 1, LIMITS.subtitle);
      checkText(check, item.body, 'body', LIMITS.bodyMin, LIMITS.bodyMax);
      check(item.pack === pack.id, `pack must be "${pack.id}"`);
      check(categoryIds.has(item.category), `unknown category "${item.category}"`);

      checkList(check, item.tags, 'tags', vocab.tags, 1);
      checkList(check, item.moods, 'moods', vocab.moods, 1);
      checkList(check, item.modes, 'modes', MODES, 1);
      checkList(check, item.chapterKinds, 'chapterKinds', vocab.chapterKinds, 1);
      checkList(check, item.boostedByPreferences, 'boostedByPreferences', [...taxonomyItems.keys()], 0);
      checkList(check, item.requiredMutualPreferences, 'requiredMutualPreferences', [...taxonomyItems.keys()], 0);
      checkList(check, item.excludedByBoundaries, 'excludedByBoundaries', [...themeIds], 1);

      const [low, high] = pack.intensity;
      check(
        Number.isInteger(item.intensity) && item.intensity >= Math.max(1, low) && item.intensity <= Math.min(5, high),
        `intensity must be ${low}..${high}`,
      );
      check(vocab.interactionTypes.includes(item.interactionType), `unknown interactionType "${item.interactionType}"`);
      check(
        Number.isInteger(item.durationMin) && item.durationMin >= 1 && item.durationMin <= LIMITS.durationMax,
        `durationMin must be 1..${LIMITS.durationMax}`,
      );
      check(
        typeof item.noveltyWeight === 'number' && item.noveltyWeight >= 0 && item.noveltyWeight <= 1,
        'noveltyWeight must be 0..1',
      );
      check(
        Number.isInteger(item.repeatCooldownDays) && item.repeatCooldownDays >= 0 && item.repeatCooldownDays <= LIMITS.cooldownMax,
        `repeatCooldownDays must be 0..${LIMITS.cooldownMax}`,
      );
      check(item.requiresMedia === null || vocab.media.includes(item.requiresMedia), `unknown requiresMedia "${item.requiresMedia}"`);
      check(vocab.statuses.includes(item.status), `unknown status "${item.status}"`);
      check(item.locale === 'en', 'locale must be "en"');
      const created = Date.parse(item.createdAt);
      const updated = Date.parse(item.updatedAt);
      check(!Number.isNaN(created) && !Number.isNaN(updated) && updated >= created, 'createdAt/updatedAt must be dates, updated not before created');

      if (typeof item.title === 'string') {
        const key = item.title.trim().toLowerCase();
        check(!titles.has(key), `duplicate title "${item.title}" (also ${titles.get(key)})`);
        titles.set(key, item.id);
      }

      // --- safety and the boundary engine ----------------------------------------------
      const text = [item.title, item.subtitle, item.body].filter((s) => typeof s === 'string').join(' \n ');
      for (const term of prohibited) {
        check(!term.regex.test(text), `prohibited term /${term.pattern}/ (${term.reason})`);
      }

      const excluded = new Set(Array.isArray(item.excludedByBoundaries) ? item.excludedByBoundaries : []);
      const leansOn = [
        ...(Array.isArray(item.boostedByPreferences) ? item.boostedByPreferences : []),
        ...(Array.isArray(item.requiredMutualPreferences) ? item.requiredMutualPreferences : []),
      ];
      for (const preference of leansOn) {
        const pref = taxonomyItems.get(preference);
        if (!pref) continue;
        check(excluded.has(pref.themeId), `leans on "${preference}" but is not excluded by its theme "${pref.themeId}"`);
        check(
          Array.isArray(item.modes) && item.modes.some((mode) => pref.modes.includes(mode)),
          `leans on "${preference}", which applies to none of this item's modes`,
        );
      }
      for (const required of Array.isArray(item.requiredMutualPreferences) ? item.requiredMutualPreferences : []) {
        const pref = taxonomyItems.get(required);
        if (!pref) continue;
        // Asked only at content levels >= its floor: requiring it below that makes the item
        // unreachable at its own intensity — excluded by its own requirement.
        check(pref.intensityFloor <= item.intensity, `requires "${required}", which is only asked at level ${pref.intensityFloor}+`);
      }
      for (const keyword of keywords) {
        if (keyword.regex.test(text)) {
          check(
            keyword.themes.some((theme) => excluded.has(theme)),
            `mentions /${keyword.pattern}/ but no covering boundary is listed (one of ${keyword.themes.join(', ')})`,
          );
        }
      }

      all.push(item);
    });
  }

  // --- near-duplicates across everything --------------------------------------------------
  const shingles = all.map((item) => ({ id: item.id, set: trigrams(typeof item.body === 'string' ? item.body : '') }));
  for (let i = 0; i < shingles.length; i++) {
    for (let j = i + 1; j < shingles.length; j++) {
      const similarity = jaccard(shingles[i].set, shingles[j].set);
      if (similarity >= NEAR_DUPLICATE_THRESHOLD) {
        problem(shingles[i].id, `near-duplicate of ${shingles[j].id} (similarity ${similarity.toFixed(2)})`);
      }
    }
  }

  // --- packs --------------------------------------------------------------------------
  const published = all.filter((item) => item.status === 'published');
  const lines: string[] = [];
  lines.push(`content v${repo.contentVersion} · taxonomy v${repo.taxonomy.version}`);
  lines.push('pack               items   1    2    3    4    5    together apart  types');

  for (const pack of repo.packs) {
    const items = published.filter((item) => item.pack === pack.id);
    const byLevel = [1, 2, 3, 4, 5].map((level) => items.filter((item) => item.intensity === level).length);
    const together = items.filter((item) => item.modes?.includes('TOGETHER')).length;
    const apart = items.filter((item) => item.modes?.includes('APART')).length;
    const types = new Set(items.map((item) => item.interactionType)).size;

    if (items.length < pack.minItems) problem(pack.id, `${items.length} published items, needs at least ${pack.minItems}`);
    for (let level = pack.intensity[0]; level <= pack.intensity[1]; level++) {
      const count = byLevel[level - 1];
      if (count < pack.minPerIntensity) {
        problem(pack.id, `only ${count} items at intensity ${level}, needs ${pack.minPerIntensity}`);
      }
    }
    if (together === 0) problem(pack.id, 'no items for TOGETHER');
    if (apart === 0) problem(pack.id, 'no items for APART');
    if (types < 3) problem(pack.id, `only ${types} interaction types; a pack needs at least 3`);

    lines.push(
      `${pack.id.padEnd(18)} ${String(items.length).padStart(5)} ${byLevel.map((n) => String(n).padStart(4)).join(' ')}` +
        `   ${String(together).padStart(6)} ${String(apart).padStart(6)}  ${types}`,
    );
  }

  // --- engine readiness ---------------------------------------------------------------
  if (published.length < COVERAGE.totalPublished) {
    problem('content', `${published.length} published items, needs at least ${COVERAGE.totalPublished}`);
  }
  lines.push('');
  lines.push(`chapter coverage (published items per mode)  ${vocab.chapterKinds.join(' ')}`);
  for (const mode of MODES) {
    const inMode = published.filter((item) => item.modes?.includes(mode));
    const perKind = vocab.chapterKinds.map((kind) => inMode.filter((item) => item.chapterKinds?.includes(kind)).length);
    lines.push(`${mode.padEnd(9)} ${perKind.join(' ')}`);
    vocab.chapterKinds.forEach((kind, index) => {
      if (perKind[index] < COVERAGE.perChapterKind) {
        problem('coverage', `${mode}: only ${perKind[index]} ${kind} items, needs ${COVERAGE.perChapterKind}`);
      }
    });
    for (let level = 1; level <= 5; level++) {
      const finales = inMode.filter((item) => item.intensity === level && item.chapterKinds?.includes('FINALE')).length;
      if (finales < COVERAGE.finalePerIntensity) {
        problem('coverage', `${mode}: only ${finales} FINALE items at intensity ${level}, needs ${COVERAGE.finalePerIntensity}`);
      }
    }
    const warmUps = inMode.filter((item) => item.intensity <= 2 && item.chapterKinds?.includes('WARM_UP')).length;
    if (warmUps < COVERAGE.warmUpAtLowIntensity) {
      problem('coverage', `${mode}: only ${warmUps} WARM_UP items at intensity 1-2, needs ${COVERAGE.warmUpAtLowIntensity}`);
    }
  }
  lines.push(`total published: ${published.length}`);

  return { problems, report: lines.join('\n') };
}

function checkText(check: (ok: boolean, message: string) => void, value: unknown, field: string, min: number, max: number) {
  const ok = typeof value === 'string' && value.trim().length >= min && value.length <= max && value === value.trim();
  check(ok, `${field} must be ${min}..${max} characters, with no surrounding spaces`);
}

function checkList(
  check: (ok: boolean, message: string) => void,
  value: unknown,
  field: string,
  allowed: readonly string[],
  minLength: number,
) {
  if (!Array.isArray(value)) {
    check(false, `${field} must be an array`);
    return;
  }
  check(value.length >= minLength, `${field} needs at least ${minLength} entr${minLength === 1 ? 'y' : 'ies'}`);
  check(new Set(value).size === value.length, `${field} has duplicates`);
  for (const entry of value) check(allowed.includes(entry), `${field}: unknown "${entry}"`);
}

/** Word trigrams of a body, lowercased, punctuation ignored. */
export function trigrams(body: string): Set<string> {
  const words = body.toLowerCase().replace(/[^a-z0-9' ]+/g, ' ').split(/\s+/).filter(Boolean);
  const out = new Set<string>();
  for (let i = 0; i + 2 < words.length; i++) out.add(`${words[i]} ${words[i + 1]} ${words[i + 2]}`);
  return out;
}

export function jaccard(a: Set<string>, b: Set<string>): number {
  if (a.size === 0 || b.size === 0) return 0;
  let shared = 0;
  for (const x of a) if (b.has(x)) shared += 1;
  return shared / (a.size + b.size - shared);
}
