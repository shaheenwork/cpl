package com.shnapps.couple.core.engine

import com.shnapps.couple.core.model.ChapterKind
import com.shnapps.couple.core.model.ContentItem

/**
 * Every rule a chapter must keep against the chapters around it (engine/SPEC.md §7, "Slot
 * rules"). A night is a list the same length as the plan; during the fill, slots not yet
 * chosen are simply absent.
 */
internal class SlotRules(private val input: Assembler.Input) {

    /**
     * The highest intensity any finale in the pool can reach. No other chapter may go above
     * it, so the finale can always be the peak of the night (BUILD_PROMPT.md §10.4).
     */
    private val cap: Int = input.pool
        .filter { ChapterKind.FINALE in it.chapterKinds && it.intensity.level <= input.plan.targets.last() }
        .maxOfOrNull { it.intensity.level } ?: 0

    fun fits(night: List<ContentItem?>, slot: Int, item: ContentItem): Boolean =
        kindAndTarget(slot, item) &&
            peak(night, slot, item) &&
            neighbours(night, slot, item) &&
            !makesRun(night, slot, item)

    private fun kindAndTarget(slot: Int, item: ContentItem): Boolean =
        input.plan.kinds[slot] in item.chapterKinds && item.intensity.level <= input.plan.targets[slot]

    /** The finale is never below another chapter; no other chapter outranks what it can reach. */
    private fun peak(night: List<ContentItem?>, slot: Int, item: ContentItem): Boolean {
        val level = item.intensity.level
        return if (input.plan.kinds[slot] == ChapterKind.FINALE) {
            night.withIndex().none { (at, other) -> at != slot && other != null && other.intensity.level > level }
        } else {
            val finale = night.getOrNull(input.count - 1)
            level <= (if (finale != null) minOf(cap, finale.intensity.level) else cap)
        }
    }

    /** At most one level above the chapter before; the chapter after at most one above it. */
    private fun neighbours(night: List<ContentItem?>, slot: Int, item: ContentItem): Boolean {
        val level = item.intensity.level
        val previous = night.getOrNull(slot - 1)
        val next = night.getOrNull(slot + 1)
        val afterPrevious = previous == null || level <= previous.intensity.level + 1
        val beforeNext = next == null || next.intensity.level <= level + 1
        return afterPrevious && beforeNext
    }

    /** Would [item] in [slot] make three consecutive chapters of one interaction type? */
    private fun makesRun(night: List<ContentItem?>, slot: Int, item: ContentItem): Boolean {
        fun type(at: Int) = if (at == slot) item.interactionType else night.getOrNull(at)?.interactionType
        return (slot - 2..slot).any { start ->
            val inside = start >= 0 && start + 2 < input.count
            val first = if (inside) type(start) else null
            first != null && first == type(start + 1) && first == type(start + 2)
        }
    }
}
