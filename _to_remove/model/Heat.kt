package com.shnapps.couple.core.model

/**
 * How far tonight goes, as the app asks it (REDESIGN_PROMPT.md §2).
 *
 * Three levels, not five. [Intensity] still has five and the engine still works in them — a couple
 * meets three, because a person deciding at eleven at night can hold three in their head and reach
 * them with one thumb, and because "level 4 of 5" is a setting rather than a mood.
 *
 * Each heat names a ceiling, never a floor: picking [WILD] does not mean everything is wild, it
 * means nothing is ruled out below it. What a couple is actually shown is still the lower of this
 * and both partners' own limits, decided on the server once they are paired (§3.2).
 */
enum class Heat(val label: String, val ceiling: Intensity, val dots: String) {
    /** Warm, unhurried, nothing anyone would need to think twice about. */
    PLAYFUL("Playful", Intensity.FLIRTY, "●○○"),

    /** The evening has a direction. */
    HOT("Hot", Intensity.BOLD, "●●○"),

    /** Nothing ruled out in advance. */
    WILD("Wild", Intensity.WILD, "●●●"),
    ;

    /** Whether an item at [intensity] is inside this heat. */
    fun allows(intensity: Intensity): Boolean = intensity.level <= ceiling.level

    /** The next heat up, or null at the top: what "turn it up" offers. */
    fun hotter(): Heat? = entries.getOrNull(ordinal + 1)

    companion object {
        /** Where a couple starts before they have said anything: warm, and clearly not a limit. */
        val DEFAULT = PLAYFUL

        /** The heat that covers [intensity], for carrying a server ceiling back to the dial. */
        fun covering(intensity: Intensity): Heat = entries.first { it.allows(intensity) }
    }
}
