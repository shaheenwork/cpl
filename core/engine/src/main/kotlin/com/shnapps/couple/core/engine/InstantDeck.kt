package com.shnapps.couple.core.engine

import com.shnapps.couple.core.model.ContentItem
import com.shnapps.couple.core.model.ContentStatus
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.Mode

/**
 * A deck for a couple the server knows nothing about (REDESIGN_PROMPT.md §4.1).
 *
 * The app's whole architecture assumes a pair of accounts: a night is filtered on the server from
 * both partners' private limits, and the device only ever plays what it was handed
 * (`PermittedPool.fromServer`, D-048). That is right, and it stays right — but it cannot produce a
 * card for two people who installed the app four minutes ago, and asking them to sign up, pair and
 * answer twenty questions first is exactly what made the app unusable.
 *
 * So this is the other path, and its safety rests on a different fact rather than on a weaker rule:
 * **there is no partner here.** Nothing this class reads belongs to anyone but the person holding
 * the phone — the heat they picked, and what they have said they never want to see. No private
 * document is consulted because none exists yet, and therefore nothing about anyone can leak
 * through it.
 *
 * The moment a couple is paired and signed in, this stops being the source: the deck comes from the
 * server through the chokepoint as it always has, and what is here becomes a second, narrower gate
 * on top of it. Never a looser one — [narrow] is how that is expressed, and it can only ever
 * remove.
 *
 * [cards] is the **pool**: everything this phone may be shown. Which of them actually comes up, and
 * in what order, is [next] — and that is a separate question with its own rules (D-124).
 */
class InstantDeck private constructor(val cards: List<InstantCard>) {

    val size: Int get() = cards.size

    /** The packs this deck can currently deal from: what a lane chooser may offer (D-125). */
    val packs: Set<String> get() = cards.mapTo(mutableSetOf()) { it.pack }

    /** The card at [index], wrapping round: a deck this size should never run out mid-evening. */
    fun cardAt(index: Int): InstantCard? = cards.getOrNull(index % cards.size.coerceAtLeast(1))

    /** This deck without [ids], and without anything touching [themes]: only ever fewer cards. */
    fun narrow(ids: Set<String> = emptySet(), themes: Set<String> = emptySet()): InstantDeck =
        InstantDeck(cards.filterNot { it.id in ids || it.themes.any { theme -> theme in themes } })

    /**
     * The card for [position] in this run: at the tier, and one they have not had (D-124).
     *
     * The deck used to be dealt with `cardAt(position)` from a position that started at zero on
     * every launch, over a pool in the order the bundle happens to be written in. Two consequences,
     * both found on a device: every couple on every install saw the same first card, and because
     * the bundle starts with the mildest pack, **the first card at Extreme was a level-one flirt
     * prompt**. Seven hundred and ninety items, and an evening only ever reached six of them.
     *
     * So the deal has rules now:
     *
     * - **The heat is what you get, not an average.** A run climbs over its first two cards and
     *   then sits at the ceiling. Picking Extreme means the cards are extreme; it is not a promise that
     *   nothing above level five turns up, which is what a ceiling with an even spread beneath it
     *   amounts to in practice.
     * - **Nothing repeats** while there is anything left to deal — [exclude] carries what this
     *   phone has already played, across launches, not only this run.
     * - **Random, but reproducible.** The draw comes from [seed], so a run can be asserted in a
     *   test and two phones dealt the same one, while two evenings differ.
     *
     * Null only when the deck is empty. When everything has been seen the pool opens up again
     * rather than the evening stopping.
     */
    fun next(position: Int, tier: Intensity, seed: Long, exclude: Set<String> = emptySet()): InstantCard? {
        if (cards.isEmpty()) return null
        val fresh = cards.filterNot { it.id in exclude }
        // Seen all of it: deal the whole deck again rather than leaving them on an empty screen.
        val pool = fresh.ifEmpty { cards }
        return pool.pick(targetFor(position, tier.level), Rng(seed + position * STRIDE))
    }

    /** One card at [target] if there is one, else the closest below it, drawn with [rng]. */
    private fun List<InstantCard>.pick(target: Int, rng: Rng): InstantCard {
        val atTarget = filter { it.intensity.level == target }
        if (atTarget.isNotEmpty()) return atTarget[rng.nextInt(atTarget.size)]

        val below = filter { it.intensity.level <= target }
        val nearest = below.maxOfOrNull { it.intensity.level }
        val candidates = if (nearest == null) this else below.filter { it.intensity.level == nearest }
        return candidates[rng.nextInt(candidates.size)]
    }

    companion object {
        /**
         * Everything the person holding the phone may be shown, in a stable order.
         *
         * Stable rather than shuffled: the order is the item's own, so two phones with the same
         * content build the same pool and a test can assert one. Where a card lands in an evening
         * is [next]'s business, not the pool's.
         */
        fun of(
            items: List<ContentItem>,
            tier: Intensity,
            mode: Mode = Mode.TOGETHER,
            skippedIds: Set<String> = emptySet(),
            refusedThemes: Set<String> = emptySet(),
            pack: String? = null,
        ): InstantDeck {
            val cards = items
                .asSequence()
                .filter { it.status == ContentStatus.PUBLISHED }
                .filter { mode in it.modes }
                .filter { tier.allows(it.intensity) }
                // One lane, when they asked for one: the packs are the only curation this content
                // has, and until now nothing in the app could ask for it (D-125).
                .filter { pack == null || it.pack == pack }
                // Anything that needs a recording or a photo needs an account and a partner to
                // send it to, so it is not part of a deck played on one phone.
                .filter { it.requiresMedia == null }
                // A card that only makes sense once both partners have answered something is not
                // playable here: nobody has answered anything yet.
                .filter { it.requiredMutualPreferences.isEmpty() }
                .map(::InstantCard)
                .toList()
            return InstantDeck(cards).narrow(skippedIds, refusedThemes)
        }

        /**
         * What [position] in a run aims at, given the heat's [ceiling].
         *
         * Two cards to climb, then the ceiling for the rest of it. The climb exists because opening
         * an evening at the top is a worse evening, not because the ceiling is meant to be an
         * average — after those two, every card is as far as this couple said they wanted to go.
         */
        internal fun targetFor(position: Int, ceiling: Int): Int =
            maxOf(1, ceiling - maxOf(0, CLIMB - position))

        /** How far below the ceiling a run opens before it gets there. */
        private const val CLIMB = 2

        /** Keeps consecutive positions from drawing off neighbouring seeds. */
        private const val STRIDE = 7919L
    }
}

/** One card: what to do, and what saying "not this kind of thing" to it would refuse. */
data class InstantCard(
    val id: String,
    val title: String,
    val body: String,
    val minutes: Int,
    val intensity: Intensity,
    /** The pack it was authored in: the lane a couple can ask for by name (D-125). */
    val pack: String,
    /** The boundary themes this card belongs to: what "never show me these again" writes down. */
    val themes: Set<String>,
) {
    constructor(item: ContentItem) : this(
        id = item.id,
        title = item.title,
        body = item.body,
        minutes = item.durationMin,
        intensity = item.intensity,
        pack = item.pack,
        themes = item.excludedByBoundaries,
    )
}
