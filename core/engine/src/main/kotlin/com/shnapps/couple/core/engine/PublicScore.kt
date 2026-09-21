package com.shnapps.couple.core.engine

import com.shnapps.couple.core.model.ContentItem
import com.shnapps.couple.core.model.MatchLevel
import com.shnapps.couple.core.model.Novelty
import kotlin.math.max

/**
 * What a device may rank by when it remixes locally: only what it is allowed to know
 * (BUILD_PROMPT.md §5.4). Revealed mutual matches are known to both partners; the couple's
 * own play history is on the device. Affinity and "curious" boundary themes are private to
 * each partner and stay on the server — here they are simply neutral.
 */
data class ClientContext(
    val mutual: Map<String, MatchLevel> = emptyMap(),
    /** Item id → epoch milliseconds last played on this couple's nights. */
    val lastPlayed: Map<String, Long> = emptyMap(),
    val nowMs: Long = 0L,
    val weights: EngineWeights = EngineWeights(),
)

internal object PublicScore {

    private const val DAY_MS = 86_400_000.0
    private const val RECENCY_DAYS = 90.0
    private const val NEUTRAL = 0.5
    private const val LEVELS = 4.0

    private const val CURIOUS_MATCH = 0.8
    private const val MIXED_MATCH = 0.6
    private const val HALF = 0.5

    private fun matchWeight(level: MatchLevel?): Double = when (level) {
        MatchLevel.BOTH_YES, MatchLevel.BOTH_SECRET -> 1.0
        MatchLevel.BOTH_CURIOUS -> CURIOUS_MATCH
        MatchLevel.MIXED_POSITIVE -> MIXED_MATCH
        null -> 0.0
    }

    /** The server's score (engine/SPEC.md §4) with every private term held neutral. */
    fun score(item: ContentItem, request: NightRequest, effective: Int, context: ClientContext): Double {
        val w = context.weights
        val boosted = item.boostedByPreferences
        val mutual = if (boosted.isEmpty()) 0.0 else boosted.sumOf { matchWeight(context.mutual[it]) } / boosted.size
        val mood = if (request.moods.isEmpty()) {
            NEUTRAL
        } else {
            request.moods.count { it in item.moods }.toDouble() / request.moods.size
        }
        val last = context.lastPlayed[item.id]
        val fresh = last == null
        val novelty = when (request.novelty) {
            Novelty.FAMILIAR -> HALF * (1 - item.noveltyWeight) + HALF * (if (fresh) 0 else 1)
            Novelty.MIXED -> NEUTRAL
            Novelty.SURPRISE_ME -> HALF * item.noveltyWeight + HALF * (if (fresh) 1 else 0)
        }
        val intensityFit = 1 - (effective - item.intensity.level) / LEVELS
        val recency = if (last == null) 0.0 else max(0.0, 1 - (context.nowMs - last) / DAY_MS / RECENCY_DAYS)
        return w.mutualPreference * mutual +
            w.affinity * NEUTRAL +
            w.moodMatch * mood +
            w.novelty * novelty +
            w.intensityFit * intensityFit -
            w.recencyPenalty * recency
    }
}
