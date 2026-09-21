package com.shnapps.couple.core.engine

import com.shnapps.couple.core.model.ChapterKind
import com.shnapps.couple.core.model.ContentItem

/**
 * The fill (engine/SPEC.md §7): the slots in order, candidates best first, backing up when a
 * later slot cannot be filled — so one greedy choice never sinks a night another order could
 * build. Bounded by [Assembler.MAX_FILL_STEPS], and deterministic.
 */
internal class Filler(private val input: Assembler.Input, private val rules: SlotRules) {

    private val night = mutableListOf<ContentItem>()
    private val used = mutableSetOf<String>()
    private var steps = 0

    /** The first complete night reached, in case none within the step limit fits the window. */
    private var fallback: List<ContentItem>? = null

    fun fill(): MutableList<ContentItem>? = if (place(0, 0)) night else fallback?.toMutableList()

    private fun place(slot: Int, minutes: Int): Boolean {
        if (slot == input.count) {
            if (fallback == null) fallback = night.toList()
            return input.inWindow(minutes)
        }
        for (item in ranked(slot, minutes)) {
            steps += 1
            if (steps > Assembler.MAX_FILL_STEPS) return false
            night += item
            used += item.id
            if (place(slot + 1, minutes + item.durationMin)) return true
            night.removeAt(night.size - 1)
            used -= item.id
        }
        return false
    }

    /** Candidates for [slot]: preferred first (rank), then by value, then by id. */
    private fun ranked(slot: Int, minutes: Int): List<ContentItem> {
        val budget = maxOf(1.0, (input.durationMin - minutes).toDouble() / (input.count - slot))
        val candidates = input.pool.filter { it.id !in used && rules.fits(night, slot, it) }
        val values = candidates.associate { it.id to value(it, slot, budget) }
        return candidates.sortedWith { a, b ->
            val byRank = rank(a, slot, minutes) - rank(b, slot, minutes)
            // The difference, not compareTo: exactly the server's comparison, -0.0 included.
            val byValue = values.getValue(b.id) - values.getValue(a.id)
            when {
                byRank != 0 -> byRank
                byValue > 0 -> 1
                byValue < 0 -> -1
                else -> a.id.compareTo(b.id)
            }
        }
    }

    private fun value(item: ContentItem, slot: Int, budget: Double): Double {
        val sameType = night.count { it.interactionType == item.interactionType }
        return input.score(item) +
            W_TARGET * (if (item.intensity.level == input.plan.targets[slot]) 1 else 0) +
            W_DURATION * Assembler.durationFit(item.durationMin, budget) -
            (input.repetitionWeight * sameType) / input.count
    }

    /**
     * 0 for the preferred: on the finale, intensity E; everywhere, a length that keeps the
     * ±20% window reachable. Higher ranks are tried later, never excluded.
     */
    private fun rank(item: ContentItem, slot: Int, minutes: Int): Int {
        val remaining = input.count - slot - 1
        val longest = Assembler.WINDOW_HIGH * input.durationMin - minutes - remaining * MIN_ITEM_MINUTES
        val shortest = Assembler.WINDOW_LOW * input.durationMin - minutes - remaining * MAX_ITEM_MINUTES
        val offTarget = input.plan.kinds[slot] == ChapterKind.FINALE && item.intensity.level != input.effective
        val lengthOk = item.durationMin >= shortest && item.durationMin <= longest
        return (if (offTarget) FINALE_OFF_TARGET else 0) + (if (lengthOk) 0 else 1)
    }

    private companion object {
        /** The shortest and longest any item may be (the content validator's limits). */
        const val MIN_ITEM_MINUTES = 1
        const val MAX_ITEM_MINUTES = 30
        const val FINALE_OFF_TARGET = 2
    }
}
