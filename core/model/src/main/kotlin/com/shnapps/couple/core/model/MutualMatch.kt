package com.shnapps.couple.core.model

/**
 * Something both partners chose, revealed by the server (BUILD_PROMPT.md §5.2, §14.7).
 *
 * All the app ever learns about a match is that it exists and its [level] — never either
 * partner's answer, and never when either of them answered: reveals arrive in batches, at
 * random times, so the moment one appears says nothing about when it was chosen (§5.3).
 */
data class MutualMatch(
    val itemId: String,
    val level: MatchLevel,
    val revealedAtEpochMillis: Long,
    /** Whether the signed-in user has already had this one revealed to them. */
    val seen: Boolean,
)

enum class MatchLevel {
    /** Both said yes. */
    BOTH_YES,

    /** Both said curious. */
    BOTH_CURIOUS,

    /** Both positive, not in the same words. */
    MIXED_POSITIVE,

    /**
     * Both were secretly curious — the reveal §14.7 calls the most addictive moment in the
     * app. A secret answer only ever matches another secret answer (DECISIONS.md D-021).
     */
    BOTH_SECRET,
}
