/**
 * The assembly rules (engine/SPEC.md section 8; BUILD_PROMPT.md sections 10.4 and 10.5): every
 * rule an assembled night breaks, so a bad night is rebuilt instead of shown. The server also
 * re-runs the chokepoint over the result (pipeline.ts): the filter runs twice, by design.
 */
import { chapterRange } from './plan';
import type { Chapter, EngineItem, Mode, Violation } from './types';

export interface NightToCheck {
  chapters: readonly Chapter[];
  durationMin: number;
  effective: number;
  mode: Mode;
}

export function validate(night: NightToCheck, items: ReadonlyMap<string, EngineItem>): Violation[] {
  const violations = new Set<Violation>();
  const { chapters } = night;
  const [lo, hi] = chapterRange(night.durationMin);
  if (chapters.length < lo || chapters.length > hi) violations.add('CHAPTER_COUNT');

  const resolved = chapters.map((chapter) => items.get(chapter.itemId));
  if (resolved.some((item) => item === undefined)) {
    // An id nobody can resolve cannot be checked, so it cannot be shown either.
    violations.add('UNKNOWN_ITEM');
    return [...violations].sort();
  }
  const nightItems = resolved as EngineItem[];

  if (new Set(chapters.map((chapter) => chapter.itemId)).size !== chapters.length) violations.add('DUPLICATE_ITEM');

  chapters.forEach((chapter, slot) => {
    const item = nightItems[slot];
    if (!item.chapterKinds.includes(chapter.kind)) violations.add('KIND_MISMATCH');
    if (!item.modes.includes(night.mode)) violations.add('MODE');
    if (item.intensity > night.effective) violations.add('ABOVE_CEILING');
    if (slot > 0 && item.intensity > nightItems[slot - 1].intensity + 1) violations.add('ESCALATION');
    if (
      slot >= 2 &&
      item.interactionType === nightItems[slot - 1].interactionType &&
      item.interactionType === nightItems[slot - 2].interactionType
    ) {
      violations.add('TYPE_RUN');
    }
  });

  if (chapters.length >= 6 && new Set(nightItems.map((item) => item.interactionType)).size < 4) {
    violations.add('TOO_FEW_TYPES');
  }
  if (chapters.filter((chapter) => chapter.kind === 'YOUR_CHOICE').length > 1) violations.add('MULTIPLE_YOUR_CHOICE');

  const minutes = nightItems.reduce((sum, item) => sum + item.durationMin, 0);
  if (minutes < 0.8 * night.durationMin || minutes > 1.2 * night.durationMin) violations.add('DURATION');

  const last = chapters[chapters.length - 1];
  const finale = nightItems[nightItems.length - 1];
  if (
    !last ||
    last.kind !== 'FINALE' ||
    nightItems.some((item) => item.intensity > finale.intensity)
  ) {
    violations.add('FINALE_NOT_PEAK');
  }
  return [...violations].sort();
}
