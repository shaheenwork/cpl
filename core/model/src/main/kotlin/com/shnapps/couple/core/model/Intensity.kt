package com.shnapps.couple.core.model

/**
 * How far tonight goes: the one setting the app asks for.
 *
 * Five tiers, and a couple meets all five by name. The app used to show three — `Heat`, a
 * separate enum that named a *ceiling* over these five — on the reasoning that a person deciding
 * at eleven at night can hold three in their head. That reasoning was sound and the result was
 * not: three labels over five levels meant Wild and Bold were the same word, and no screen could
 * say which tier a card was. One enum, five names, and the word on the picker is the word on the
 * card.
 *
 * A tier is a **ceiling, never a floor**: picking Extreme rules nothing out in advance, it does
 * not promise that everything will be extreme. What a couple is actually shown is still the lower
 * of this and both partners' own limits, decided on the server once they are paired (§3.2).
 */
enum class Intensity(val level: Int, val label: String) {
    MILD(1, "Mild"),
    FLIRTY(2, "Flirty"),
    SPICY(3, "Spicy"),
    HOT(4, "Hot"),
    EXTREME(5, "Extreme"),
    ;

    /** The top two require an explicit "both of us want this" every session (§3.1). */
    val requiresBothPartyConsent: Boolean get() = level >= HOT.level

    /** Filled dots out of five: how far along the ramp this is, at a glance. */
    val dots: String get() = "●".repeat(level) + "○".repeat(entries.size - level)

    /** Whether a card at [intensity] is inside this tier. */
    fun allows(intensity: Intensity): Boolean = intensity.level <= level

    /** The next tier up, or null at the top: what "turn it up" offers. */
    fun hotter(): Intensity? = entries.getOrNull(ordinal + 1)

    companion object {
        /**
         * Where a couple starts before they have said anything.
         *
         * Flirty rather than Mild: the brief's Play Now opens between Flirty and Spicy, and a
         * couple who installed this app did not do it for the mildest thing in it. It is still a
         * ceiling and still one thumb away from either direction.
         */
        val DEFAULT = FLIRTY

        fun ofLevel(level: Int): Intensity =
            entries.firstOrNull { it.level == level }
                ?: error("No Intensity for level $level; valid levels are 1..5")

        /** Never exceed the lowest ceiling in play. */
        fun effective(requested: Intensity, vararg ceilings: Intensity): Intensity =
            ofLevel(minOf(requested.level, ceilings.minOfOrNull { it.level } ?: requested.level))
    }
}
