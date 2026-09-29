package com.shnapps.couple.core.engine

import com.shnapps.couple.core.model.Intensity

/**
 * How long a guided evening runs for. The brief's three.
 *
 * The number is a budget in minutes, not a card count, because every card already says how long it
 * takes. Five minutes of cards and thirty minutes of cards are both "six cards" if you count cards.
 */
enum class NightLength(val minutes: Int, val label: String) {
    SHORT(30, "30 minutes"),
    MEDIUM(45, "45 minutes"),
    LONG(60, "An hour"),
    ;

    companion object {
        val DEFAULT = SHORT
    }
}

/**
 * The shape of a guided evening: warm-up, build, peak, then aftercare.
 *
 * [share] is how much of the budget the phase gets. Aftercare has none: it is one card, and it
 * happens whatever time is left, because an evening that ran long is exactly the one that needs it.
 */
enum class NightPhase(val label: String, val share: Double) {
    WARM_UP("Warm-up", 0.25),
    BUILD("Building", 0.35),
    PEAK("Peak", 0.40),
    AFTERCARE("Afterwards", 0.0),
}

/** One card in the plan, and which part of the evening it belongs to. */
data class NightCard(val phase: NightPhase, val card: InstantCard)

/**
 * Game Night (the brief's game 12): an evening planned in four phases rather than a card at a time.
 *
 * It is not one of the prompt-based games, and it could not be. Those draw from a pool written for
 * one game; this draws from the whole catalogue — 790 items across twelve packs — which is the only
 * pool large enough to fill an hour without repeating itself. So it is a plan over [InstantDeck]
 * rather than an entry in `games.json`, and it reuses the deck's own dealing: the tier ceiling, the
 * limits, the lane, and the no-repeats record all apply exactly as they do to a single card
 * (D-124, M3).
 *
 * The tier climbs by phase and stops at the couple's own: warm-up two below, building one below,
 * peak at it. Nothing may exceed it, here or anywhere.
 *
 * **The last card is aftercare**, and it is not optional. The brief asks for one after any Hot or
 * Extreme session and this is the surface where a session has a known end, so it is planned in
 * rather than offered. Where the content has nothing tagged for it, the plan says so instead of
 * quietly finishing on the peak.
 */
class GameNight private constructor(
    val length: NightLength,
    val tier: Intensity,
    val cards: List<NightCard>,
) {
    val size: Int get() = cards.size

    /** Roughly how long the plan actually runs, which is not always what was asked for. */
    val minutes: Int get() = cards.sumOf { it.card.minutes }

    /** The aftercare card, or null when the content had none to give. */
    val aftercare: InstantCard? get() = cards.lastOrNull { it.phase == NightPhase.AFTERCARE }?.card

    fun cardAt(index: Int): NightCard? = cards.getOrNull(index)

    companion object {
        /**
         * Plans an evening from [deck], inside [tier], for about [length].
         *
         * [exclude] is what this phone has already been dealt, so an evening does not open on a
         * card they had yesterday. The draw is seeded, so the same evening can be asserted in a
         * test and a reopened phone gets the plan it was given rather than a new one.
         */
        fun plan(
            deck: InstantDeck,
            tier: Intensity,
            length: NightLength = NightLength.DEFAULT,
            seed: Long = 0,
            exclude: Set<String> = emptySet(),
        ): GameNight {
            if (deck.size == 0) return GameNight(length, tier, emptyList())

            val used = exclude.toMutableSet()
            val cards = mutableListOf<NightCard>()
            var step = 0

            for (phase in listOf(NightPhase.WARM_UP, NightPhase.BUILD, NightPhase.PEAK)) {
                step = fill(phase, deck, tier, length, seed, step, used, cards)
            }

            aftercareCard(deck, tier, used)?.let { cards += NightCard(NightPhase.AFTERCARE, it) }
            return GameNight(length, tier, cards)
        }

        /**
         * One phase's worth of cards, appended to [cards]. Returns where the deal has got to.
         *
         * A phase spends its share of the budget in minutes, and always takes at least one card
         * however long the cards happen to be: a plan with an empty middle is not a shape, it is a
         * gap. It stops early when the deck has nothing left to give or the evening is already
         * longer than an evening.
         */
        @Suppress("LongParameterList") // A phase, the pool it draws from, and where the deal is.
        private fun fill(
            phase: NightPhase,
            deck: InstantDeck,
            tier: Intensity,
            length: NightLength,
            seed: Long,
            from: Int,
            used: MutableSet<String>,
            cards: MutableList<NightCard>,
        ): Int {
            val budget = length.minutes * phase.share
            var step = from
            var spent = 0.0
            var taking = true
            while (taking) {
                val enough = spent >= budget && cards.any { it.phase == phase }
                val card = if (enough || cards.size >= MAX_CARDS) {
                    null
                } else {
                    deck.next(step, phaseTier(phase, tier), seed, used)
                }
                if (card == null || card.id in used) {
                    taking = false
                } else {
                    step += 1
                    used += card.id
                    cards += NightCard(phase, card)
                    spent += card.minutes
                }
            }
            return step
        }

        /**
         * A phase's own ceiling: the evening climbs to the couple's tier and stops there.
         *
         * The same climb a game's deal makes (`ReleasedGame.forHeat`), for the same reason —
         * opening an evening at the top is a worse evening — and with the same hard stop.
         */
        internal fun phaseTier(phase: NightPhase, tier: Intensity): Intensity = when (phase) {
            NightPhase.WARM_UP -> Intensity.ofLevel(maxOf(1, tier.level - 2))
            NightPhase.BUILD -> Intensity.ofLevel(maxOf(1, tier.level - 1))
            NightPhase.PEAK, NightPhase.AFTERCARE -> tier
        }

        /**
         * Something to land on, from the cards written for it.
         *
         * Never from the general pool: a peak card chosen as an ending is not aftercare, it is one
         * more peak card with a different label on it.
         *
         * **And never above the tier.** The deck is built at the couple's ceiling, so anything in
         * it is allowed — but a plan made at Spicy must not end on an Extreme card just because the
         * only thing written to land on happened to be one. A test caught exactly that.
         */
        private fun aftercareCard(deck: InstantDeck, tier: Intensity, used: Set<String>): InstantCard? {
            val allowed = deck.cards.filter { it.aftercare && tier.allows(it.intensity) }
            return allowed.firstOrNull { it.id !in used } ?: allowed.firstOrNull()
        }

        /** However long the cards are, an evening is not forty of them. */
        private const val MAX_CARDS = 12
    }
}
