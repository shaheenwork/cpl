package com.shnapps.couple.core.model

/**
 * The five experience levels (BUILD_PROMPT.md §14.5, Appendix A).
 *
 * Effective intensity is always `min(requested, contentLevel A, contentLevel B, boundaryCeiling)`.
 * Nothing may exceed it and nothing may escalate it without an explicit user action.
 */
enum class Intensity(val level: Int) {
    SOFT(1),
    FLIRTY(2),
    NAUGHTY(3),
    BOLD(4),
    WILD(5),
    ;

    /** Levels 4 and 5 require an explicit "I'm in" from BOTH partners (§3.1). */
    val requiresBothPartyConsent: Boolean get() = level >= BOLD.level

    companion object {
        fun ofLevel(level: Int): Intensity =
            entries.firstOrNull { it.level == level }
                ?: error("No Intensity for level $level; valid levels are 1..5")

        /** Never exceed the lowest ceiling in play. */
        fun effective(requested: Intensity, vararg ceilings: Intensity): Intensity =
            ofLevel(minOf(requested.level, ceilings.minOfOrNull { it.level } ?: requested.level))
    }
}
