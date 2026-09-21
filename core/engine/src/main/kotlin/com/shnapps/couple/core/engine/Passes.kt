package com.shnapps.couple.core.engine

import com.shnapps.couple.core.model.ChapterKind
import com.shnapps.couple.core.model.ContentItem
import kotlin.math.abs

/**
 * The duration pass after the fill (engine/SPEC.md §7), and the single swap behind "Not this
 * one". Both keep every slot rule; neither reaches outside the pool.
 */
internal object Passes {

    private class Swap(val slot: Int, val item: ContentItem, val distance: Int)

    /** While outside the ±20% window, the single swap that brings the total closest to D. */
    fun duration(night: MutableList<ContentItem>, input: Assembler.Input, rules: SlotRules) {
        val finaleAtE = night.last().intensity.level == input.effective
        var round = 0
        while (round < 2 * night.size && !input.inWindow(night.sumOf { it.durationMin })) {
            val current = night.sumOf { it.durationMin }
            val swap = bestSwap(night, input, rules, finaleAtE, current)
            if (swap == null || swap.distance >= abs(current - input.durationMin)) return
            night[swap.slot] = swap.item
            round++
        }
    }

    private fun bestSwap(
        night: List<ContentItem>,
        input: Assembler.Input,
        rules: SlotRules,
        finaleAtE: Boolean,
        current: Int,
    ): Swap? {
        val used = night.mapTo(HashSet()) { it.id }
        val typesBefore = typeCount(night)
        var best: Swap? = null
        night.forEachIndexed { slot, existing ->
            for (item in input.pool) {
                val allowed = item.id !in used &&
                    rules.fits(night, slot, item) &&
                    keepsFinale(input, slot, item, finaleAtE) &&
                    keepsVariety(night, slot, item, typesBefore)
                if (allowed) {
                    val distance = abs(current - existing.durationMin + item.durationMin - input.durationMin)
                    val candidate = Swap(slot, item, distance)
                    if (isBetter(candidate, best, input)) best = candidate
                }
            }
        }
        return best
    }

    private fun keepsFinale(input: Assembler.Input, slot: Int, item: ContentItem, finaleAtE: Boolean): Boolean =
        input.plan.kinds[slot] != ChapterKind.FINALE || !finaleAtE || item.intensity.level == input.effective

    private fun keepsVariety(night: List<ContentItem>, slot: Int, item: ContentItem, typesBefore: Int): Boolean {
        if (night.size < VARIETY_FROM || typesBefore < MIN_TYPES) return true
        return typeCount(night.mapIndexed { at, chosen -> if (at == slot) item else chosen }) >= MIN_TYPES
    }

    /** Closer to D wins; ties go to the lower slot, then the higher score, then the lower id. */
    private fun isBetter(candidate: Swap, leader: Swap?, input: Assembler.Input): Boolean {
        if (leader == null) return true
        val score = input.score(candidate.item)
        val leaderScore = input.score(leader.item)
        return when {
            candidate.distance != leader.distance -> candidate.distance < leader.distance
            candidate.slot != leader.slot -> false
            score != leaderScore -> score > leaderScore
            else -> candidate.item.id < leader.item.id
        }
    }

    fun replacement(
        input: Assembler.Input,
        rules: SlotRules,
        night: List<ContentItem>,
        index: Int,
        exclude: Set<String>,
    ): ContentItem? {
        val total = night.sumOf { it.durationMin }
        val typesBefore = typeCount(night)
        val best = Best()
        for (item in input.pool) {
            val allowed = item.id !in exclude &&
                rules.fits(night, index, item) &&
                input.inWindow(total - night[index].durationMin + item.durationMin) &&
                keepsVariety(night, index, item, typesBefore)
            if (allowed) best.offer(item, targetValue(input, item, index))
        }
        return best.item
    }
}

/** A candidate's score plus the bonus for hitting its slot's target exactly (engine/SPEC.md §7). */
internal fun targetValue(input: Assembler.Input, item: ContentItem, slot: Int): Double =
    input.score(item) + W_TARGET * (if (item.intensity.level == input.plan.targets[slot]) 1 else 0)
