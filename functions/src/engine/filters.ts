/**
 * The boundary intersection (BUILD_PROMPT.md sections 3.2, 5.4, 10.3).
 *
 * Combines both partners' private boundaries, content levels and "Never" answers into the
 * one set of filters the experience engine applies before it ranks anything. The result is
 * written only to `couples/{cid}/engineFilters/current`, which no client can read: a user who
 * could see the combined set could diff it against their own boundaries and work out their
 * partner's hard limits.
 *
 * Pure, with no Firestore types, so every rule below is unit-tested directly.
 *
 * Two principles govern it:
 *  - **The stricter partner wins.** Either partner's NEVER or NOT_TONIGHT removes a theme,
 *    whatever the other said.
 *  - **Fail closed.** A value this code does not recognise is treated as the strictest one
 *    it could have been: an unknown boundary level excludes the theme, and a malformed
 *    content level becomes the lowest.
 */

export const BOUNDARY_LEVELS = ['ALWAYS_OK', 'CURIOUS', 'ASK_FIRST', 'NOT_TONIGHT', 'NEVER'] as const;
export type BoundaryLevel = (typeof BOUNDARY_LEVELS)[number];

/** A missing content level means the account predates the field; the app writes FLIRTY. */
export const DEFAULT_CONTENT_LEVEL = 2;
export const MIN_CONTENT_LEVEL = 1;
export const MAX_CONTENT_LEVEL = 5;

/** Bumped whenever the shape of the filters document changes. */
export const FILTERS_VERSION = 1;

/** One partner's inputs, exactly as read from their private documents. Untrusted in shape. */
export interface MemberInput {
  uid: string;
  contentLevel: unknown;
  /** themeId → the `level` field of `users/{uid}/boundaries/{themeId}`. */
  boundaries: Record<string, unknown>;
  /** Taxonomy items this partner answered NEVER. */
  neverItems: string[];
}

export interface EngineFilters {
  version: number;
  /** The couple's ceiling: never above the more careful partner's content level. */
  maxIntensity: number;
  /** Themes nothing may come from: either partner said NEVER or NOT_TONIGHT. */
  excludedThemes: string[];
  /** Themes that need an in-session "is this OK?" to the partner(s) listed first (section 3.2). */
  askFirstThemes: Record<string, string[]>;
  /** Themes someone leans into and nobody has excluded. A ranking signal only. */
  curiousThemes: string[];
  /** Items either partner answered NEVER. Content tied to them is removed like a boundary. */
  excludedItems: string[];
}

const EXCLUDING: ReadonlySet<string> = new Set(['NEVER', 'NOT_TONIGHT']);

function isKnownLevel(value: unknown): value is BoundaryLevel {
  return typeof value === 'string' && (BOUNDARY_LEVELS as readonly string[]).includes(value);
}

/** A content level the couple can trust: missing means the default, anything malformed the lowest. */
export function contentLevelOf(value: unknown): number {
  if (value === undefined || value === null) return DEFAULT_CONTENT_LEVEL;
  if (typeof value !== 'number' || !Number.isInteger(value)) return MIN_CONTENT_LEVEL;
  if (value < MIN_CONTENT_LEVEL || value > MAX_CONTENT_LEVEL) return MIN_CONTENT_LEVEL;
  return value;
}

export function computeEngineFilters(members: readonly MemberInput[]): EngineFilters {
  if (members.length === 0) throw new Error('A couple has members');

  const excluded = new Set<string>();
  const askFirst = new Map<string, Set<string>>();
  const curious = new Set<string>();

  for (const member of members) {
    for (const [themeId, raw] of Object.entries(member.boundaries)) {
      // Fail closed: a level this code cannot read is treated as NEVER.
      const level: BoundaryLevel = isKnownLevel(raw) ? raw : 'NEVER';
      if (EXCLUDING.has(level)) {
        excluded.add(themeId);
      } else if (level === 'ASK_FIRST') {
        if (!askFirst.has(themeId)) askFirst.set(themeId, new Set());
        askFirst.get(themeId)!.add(member.uid);
      } else if (level === 'CURIOUS') {
        curious.add(themeId);
      }
    }
  }

  // Exclusion beats everything: an excluded theme is never also asked about or leaned into.
  const askFirstThemes: Record<string, string[]> = {};
  for (const themeId of [...askFirst.keys()].sort()) {
    if (!excluded.has(themeId)) askFirstThemes[themeId] = [...askFirst.get(themeId)!].sort();
  }

  return {
    version: FILTERS_VERSION,
    maxIntensity: Math.min(...members.map((member) => contentLevelOf(member.contentLevel))),
    excludedThemes: [...excluded].sort(),
    askFirstThemes,
    curiousThemes: [...curious].filter((themeId) => !excluded.has(themeId)).sort(),
    excludedItems: [...new Set(members.flatMap((member) => member.neverItems))].sort(),
  };
}
