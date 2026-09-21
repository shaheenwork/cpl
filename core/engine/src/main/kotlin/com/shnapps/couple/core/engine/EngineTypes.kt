package com.shnapps.couple.core.engine

import com.shnapps.couple.core.model.ChapterKind
import com.shnapps.couple.core.model.Leadership
import com.shnapps.couple.core.model.Mode
import com.shnapps.couple.core.model.Mood
import com.shnapps.couple.core.model.Novelty

/** What a couple asked for (BUILD_PROMPT.md §14.5). The server echoes it with every night. */
data class NightRequest(
    val mode: Mode,
    val durationMin: Int,
    val moods: Set<Mood>,
    val intensity: Int,
    val leadership: Leadership,
    val novelty: Novelty,
    /** Unsigned 32-bit. */
    val seed: Long,
)

/** One chapter of a night: which kind it is, and the content item that plays it. */
data class Chapter(
    val index: Int,
    val kind: ChapterKind,
    val itemId: String,
    val intensity: Int,
)

/** The shape of a night before content is chosen (engine/SPEC.md §5). */
data class NightPlan(
    val kinds: List<ChapterKind>,
    val targets: List<Int>,
)

/** Why a night could not be planned. */
enum class PlanFailure { NO_FINALE, NO_KINDS }

/** The assembly rules a night can break (engine/SPEC.md §8). Names match the server's. */
enum class Violation {
    ABOVE_CEILING,
    CHAPTER_COUNT,
    DUPLICATE_ITEM,
    DURATION,
    ESCALATION,
    FINALE_NOT_PEAK,
    KIND_MISMATCH,
    MODE,
    MULTIPLE_YOUR_CHOICE,
    TOO_FEW_TYPES,
    TYPE_RUN,
    UNKNOWN_ITEM,
}

/**
 * Ranking weights w1–w7 (BUILD_PROMPT.md §10.3), tunable from Remote Config. The client only
 * ever uses the ones that need no private data (see [PublicScore]).
 */
data class EngineWeights(
    val mutualPreference: Double = 3.0,
    val affinity: Double = 2.0,
    val moodMatch: Double = 2.0,
    val novelty: Double = 1.5,
    val intensityFit: Double = 1.5,
    val recencyPenalty: Double = 2.0,
    val repetitionPenalty: Double = 3.0,
)

/** The chapter-count range for a requested duration (BUILD_PROMPT.md §10.4). */
@Suppress("MagicNumber") // The table is the spec.
internal fun chapterRange(durationMin: Int): IntRange = when {
    durationMin <= 20 -> 3..3
    durationMin <= 37 -> 4..5
    durationMin <= 52 -> 5..6
    durationMin <= 75 -> 6..7
    else -> 8..10
}

/** Assembly constants (engine/SPEC.md §7). Not tunable: they shape a night, not its taste. */
internal const val W_TARGET = 1.0
internal const val W_DURATION = 2.0
