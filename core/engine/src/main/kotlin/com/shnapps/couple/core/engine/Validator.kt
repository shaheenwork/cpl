package com.shnapps.couple.core.engine

import com.shnapps.couple.core.model.ChapterKind
import com.shnapps.couple.core.model.ContentItem
import com.shnapps.couple.core.model.Mode

/**
 * The assembly rules (engine/SPEC.md §8; BUILD_PROMPT.md §10.4–10.5). Every rule a night
 * breaks, sorted by name, exactly as the server's validate.ts reports them.
 *
 * The client runs it on every night the server sends and every night it remixes: a night
 * that breaks a rule is never shown, whoever built it.
 */
object Validator {

    private const val WINDOW_LOW = 0.8
    private const val WINDOW_HIGH = 1.2
    private const val MIN_TYPES = 4
    private const val VARIETY_FROM = 6

    @Suppress("CyclomaticComplexMethod", "ReturnCount") // One line per rule in the spec's table.
    fun validate(
        chapters: List<Chapter>,
        durationMin: Int,
        effective: Int,
        mode: Mode,
        items: Map<String, ContentItem>,
    ): List<Violation> {
        val violations = sortedSetOf<Violation>(compareBy { it.name })
        val range = chapterRange(durationMin)
        if (chapters.size !in range) violations += Violation.CHAPTER_COUNT

        val resolved = chapters.map { items[it.itemId] }
        if (resolved.any { it == null }) {
            // An id nobody can resolve cannot be checked, so it cannot be shown either.
            violations += Violation.UNKNOWN_ITEM
            return violations.toList()
        }
        val night = resolved.requireNoNulls()

        if (chapters.mapTo(HashSet()) { it.itemId }.size != chapters.size) violations += Violation.DUPLICATE_ITEM

        chapters.forEachIndexed { slot, chapter ->
            val item = night[slot]
            if (chapter.kind !in item.chapterKinds) violations += Violation.KIND_MISMATCH
            if (mode !in item.modes) violations += Violation.MODE
            if (item.intensity.level > effective) violations += Violation.ABOVE_CEILING
            val jumps = slot > 0 && item.intensity.level > night[slot - 1].intensity.level + 1
            if (jumps) violations += Violation.ESCALATION
            if (slot >= 2 &&
                item.interactionType == night[slot - 1].interactionType &&
                item.interactionType == night[slot - 2].interactionType
            ) {
                violations += Violation.TYPE_RUN
            }
        }

        if (chapters.size >= VARIETY_FROM && night.mapTo(HashSet()) { it.interactionType }.size < MIN_TYPES) {
            violations += Violation.TOO_FEW_TYPES
        }
        if (chapters.count { it.kind == ChapterKind.YOUR_CHOICE } > 1) violations += Violation.MULTIPLE_YOUR_CHOICE

        val minutes = night.sumOf { it.durationMin }
        if (minutes < WINDOW_LOW * durationMin || minutes > WINDOW_HIGH * durationMin) violations += Violation.DURATION

        val last = chapters.lastOrNull()
        val finale = night.lastOrNull()
        val finaleIsPeak = last?.kind == ChapterKind.FINALE &&
            finale != null &&
            night.none { it.intensity.level > finale.intensity.level }
        if (!finaleIsPeak) violations += Violation.FINALE_NOT_PEAK
        return violations.toList()
    }
}
