package com.shnapps.couple.core.engine

import com.shnapps.couple.core.model.ContentItem
import kotlin.math.abs

/**
 * Assembly (engine/SPEC.md §7; BUILD_PROMPT.md §10.4–10.5): fill → variety pass → duration
 * fit. The same algorithm as the server's assemble.ts, step for step, so the same pool and
 * scores give the same night on either side — checked against the shared vectors.
 *
 * Internal: app code reaches it only through [Remixer], and so only ever with a
 * [PermittedPool] the server released.
 */
internal object Assembler {

    /** How many candidate placements the fill may try before it gives up. */
    const val MAX_FILL_STEPS = 2000

    class Input(
        val pool: List<ContentItem>,
        val scores: Map<String, Double>,
        val plan: NightPlan,
        val durationMin: Int,
        val effective: Int,
        val repetitionWeight: Double,
    ) {
        val count: Int get() = plan.kinds.size

        fun score(item: ContentItem): Double = scores.getValue(item.id)

        /** Is [minutes] inside the night's ±20% window? */
        fun inWindow(minutes: Int): Boolean =
            minutes >= WINDOW_LOW * durationMin && minutes <= WINDOW_HIGH * durationMin
    }

    /** 1 when an item's length is exactly the budget, falling towards 0 as it strays. */
    fun durationFit(durationMin: Int, budget: Double): Double =
        1 - abs(durationMin - budget) / maxOf(durationMin.toDouble(), budget)

    fun assemble(input: Input): List<Chapter>? {
        val rules = SlotRules(input)
        val night = Filler(input, rules).fill() ?: return null
        VarietyPass.run(night, input, rules)
        Passes.duration(night, input, rules)
        return night.mapIndexed { index, item ->
            Chapter(index, input.plan.kinds[index], item.id, item.intensity.level)
        }
    }

    /**
     * The best pool item to stand in chapter [index] instead of the one there (a "Not this
     * one" swap): every slot rule kept, the night still inside its duration window and its
     * variety, never one of [exclude]. Highest score plus the target bonus, ties by id.
     */
    fun bestReplacement(input: Input, night: List<ContentItem>, index: Int, exclude: Set<String>): ContentItem? =
        Passes.replacement(input, SlotRules(input), night, index, exclude)

    const val WINDOW_LOW = 0.8
    const val WINDOW_HIGH = 1.2
}

/** The most chapters a night may have with too few types before variety is enforced. */
internal const val VARIETY_FROM = 6

/** Interaction types a night of [VARIETY_FROM]+ chapters must mix (BUILD_PROMPT.md §10.5). */
internal const val MIN_TYPES = 4

/** Distinct interaction types in a night. */
internal fun typeCount(night: List<ContentItem>): Int = night.mapTo(HashSet()) { it.interactionType }.size

/**
 * The best candidate by value, ties going to the lower id — every tie in the engine is
 * broken by id (engine/SPEC.md), which is what keeps it deterministic.
 */
internal class Best {
    var item: ContentItem? = null
        private set
    private var value = Double.NEGATIVE_INFINITY

    fun offer(candidate: ContentItem, candidateValue: Double) {
        val current = item
        val winsTie = candidateValue == value && current != null && candidate.id < current.id
        if (candidateValue > value || winsTie) {
            item = candidate
            value = candidateValue
        }
    }
}
