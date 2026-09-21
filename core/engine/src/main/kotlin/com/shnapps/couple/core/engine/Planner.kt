package com.shnapps.couple.core.engine

import com.shnapps.couple.core.model.ChapterKind
import com.shnapps.couple.core.model.Leadership
import com.shnapps.couple.core.model.Mood

/**
 * The shape of a night (engine/SPEC.md §5; BUILD_PROMPT.md §10.4): how many chapters, which
 * kind each is, and the intensity each aims for. The same draws, in the same order, as the
 * server's plan.ts — so a seed plans the same night on either side.
 */
internal object Planner {

    /** The middle-slot kinds, in the order the plan walks them. */
    private val MIDDLE = listOf(
        ChapterKind.CURIOSITY,
        ChapterKind.TEASE,
        ChapterKind.CHALLENGE,
        ChapterKind.CONTROL,
        ChapterKind.CONFESSION,
        ChapterKind.ROLEPLAY,
        ChapterKind.WILDCARD,
        ChapterKind.YOUR_CHOICE,
    )

    private val MOOD_KINDS: Map<Mood, List<ChapterKind>> = mapOf(
        Mood.ROMANTIC to listOf(ChapterKind.CONFESSION, ChapterKind.CURIOSITY),
        Mood.NAUGHTY to listOf(ChapterKind.TEASE, ChapterKind.CHALLENGE),
        Mood.BOLD to listOf(ChapterKind.CONTROL, ChapterKind.CHALLENGE),
        Mood.UNPREDICTABLE to listOf(ChapterKind.WILDCARD, ChapterKind.YOUR_CHOICE),
        Mood.MYSTERIOUS to listOf(ChapterKind.TEASE, ChapterKind.WILDCARD),
        Mood.ROLEPLAY to listOf(ChapterKind.ROLEPLAY),
        Mood.TALK to listOf(ChapterKind.CONFESSION, ChapterKind.CURIOSITY),
        Mood.EXPERIMENTAL to listOf(ChapterKind.CHALLENGE, ChapterKind.WILDCARD),
    )

    private const val MOOD_WEIGHT = 1.0
    private const val LEAD_WEIGHT = 1.0
    private const val RANDOM_WEIGHT = 0.5

    /** How far below E the first chapter aims (SPEC §5). Remixes may ask for another start. */
    const val DEFAULT_START_OFFSET = 2

    private fun kindWeights(moods: Collection<Mood>, leadership: Leadership): Map<ChapterKind, Double> {
        val weights = LinkedHashMap<ChapterKind, Double>().apply { MIDDLE.forEach { put(it, 1.0) } }
        // Moods in the order they were requested: adding 1.0s is exact in any order anyway.
        for (mood in moods) {
            for (kind in MOOD_KINDS[mood].orEmpty()) weights[kind] = weights.getValue(kind) + MOOD_WEIGHT
        }
        if (leadership == Leadership.RANDOM) {
            weights[ChapterKind.WILDCARD] = weights.getValue(ChapterKind.WILDCARD) + RANDOM_WEIGHT
            weights[ChapterKind.YOUR_CHOICE] = weights.getValue(ChapterKind.YOUR_CHOICE) + RANDOM_WEIGHT
        } else {
            weights[ChapterKind.CONTROL] = weights.getValue(ChapterKind.CONTROL) + LEAD_WEIGHT
        }
        return weights
    }

    /** Intensity targets: rise to [effective] and never by more than one per chapter. */
    fun targets(count: Int, effective: Int, startOffset: Int = DEFAULT_START_OFFSET): List<Int> {
        val start = maxOf(1, effective - startOffset)
        if (count == 1) return listOf(effective)
        return List(count) { i -> start + ((effective - start) * i) / (count - 1) }
    }

    sealed interface Result {
        data class Planned(val plan: NightPlan) : Result

        data class Failed(val reason: PlanFailure) : Result
    }

    @Suppress("ReturnCount", "CyclomaticComplexMethod") // One early exit per spec'd failure.
    fun plan(
        durationMin: Int,
        moods: Collection<Mood>,
        leadership: Leadership,
        effective: Int,
        available: Set<ChapterKind>,
        rng: Rng,
        startOffset: Int = DEFAULT_START_OFFSET,
    ): Result {
        val range = chapterRange(durationMin)
        val count = range.first + rng.nextInt(range.last - range.first + 1)
        if (ChapterKind.FINALE !in available) return Result.Failed(PlanFailure.NO_FINALE)

        val weights = kindWeights(moods, leadership)
        val kinds = mutableListOf<ChapterKind>()
        val used = mutableSetOf<ChapterKind>()

        for (slot in 0 until count - 1) {
            if (slot == 0 && ChapterKind.WARM_UP in available) {
                kinds += ChapterKind.WARM_UP
                continue
            }
            val previous = kinds.lastOrNull()
            var candidates = MIDDLE.filter { kind ->
                kind in available &&
                    kind != previous &&
                    !(kind == ChapterKind.YOUR_CHOICE && ChapterKind.YOUR_CHOICE in used)
            }
            val unused = candidates.filter { it !in used }
            if (unused.isNotEmpty()) candidates = unused
            if (candidates.isEmpty()) return Result.Failed(PlanFailure.NO_KINDS)

            var total = 0.0
            for (kind in candidates) total += weights.getValue(kind)
            val r = rng.next() * total
            var running = 0.0
            var chosen = candidates.last()
            for (kind in candidates) {
                running += weights.getValue(kind)
                if (running > r) {
                    chosen = kind
                    break
                }
            }
            kinds += chosen
            used += chosen
        }
        kinds += ChapterKind.FINALE
        return Result.Planned(NightPlan(kinds, targets(count, effective, startOffset)))
    }
}
