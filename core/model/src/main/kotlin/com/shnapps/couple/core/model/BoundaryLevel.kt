package com.shnapps.couple.core.model

/**
 * A single partner's private boundary for one theme (BUILD_PROMPT.md §3.2).
 *
 * [NEVER] from EITHER partner is an absolute exclusion. It cannot be overridden by any
 * mood, parameter, random draw, personalization score or partner-planned choice.
 */
enum class BoundaryLevel {
    ALWAYS_OK,
    CURIOUS,
    ASK_FIRST,
    NOT_TONIGHT,
    NEVER,
    ;

    /** Hard exclusion: removed before ranking ever runs (§10.3). */
    val isHardExclusion: Boolean get() = this == NEVER

    /** Requires an explicit in-session prompt to the boundary holder before appearing. */
    val requiresPrompt: Boolean get() = this == ASK_FIRST
}
