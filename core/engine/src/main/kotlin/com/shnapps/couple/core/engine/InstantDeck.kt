package com.shnapps.couple.core.engine

import com.shnapps.couple.core.model.ContentItem
import com.shnapps.couple.core.model.ContentStatus
import com.shnapps.couple.core.model.Heat
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
 */
class InstantDeck private constructor(val cards: List<InstantCard>) {

    val size: Int get() = cards.size

    /** The card at [index], wrapping round: a deck this size should never run out mid-evening. */
    fun cardAt(index: Int): InstantCard? = cards.getOrNull(index % cards.size.coerceAtLeast(1))

    /** This deck without [ids], and without anything touching [themes]: only ever fewer cards. */
    fun narrow(ids: Set<String> = emptySet(), themes: Set<String> = emptySet()): InstantDeck =
        InstantDeck(cards.filterNot { it.id in ids || it.themes.any { theme -> theme in themes } })

    companion object {
        /**
         * Everything the person holding the phone may be shown, in a stable order.
         *
         * Stable rather than shuffled: the order is the item's own, so two phones with the same
         * content build the same deck, a test can assert one, and a reopened app does not start
         * again somewhere else. Where a card actually lands in an evening is the player's business,
         * not the deck's.
         */
        fun of(
            items: List<ContentItem>,
            heat: Heat,
            mode: Mode = Mode.TOGETHER,
            skippedIds: Set<String> = emptySet(),
            refusedThemes: Set<String> = emptySet(),
        ): InstantDeck {
            val cards = items
                .asSequence()
                .filter { it.status == ContentStatus.PUBLISHED }
                .filter { mode in it.modes }
                .filter { heat.allows(it.intensity) }
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
    }
}

/** One card: what to do, and what saying "not this kind of thing" to it would refuse. */
data class InstantCard(
    val id: String,
    val title: String,
    val body: String,
    val minutes: Int,
    val intensity: Intensity,
    /** The boundary themes this card belongs to: what "never show me these again" writes down. */
    val themes: Set<String>,
) {
    constructor(item: ContentItem) : this(
        id = item.id,
        title = item.title,
        body = item.body,
        minutes = item.durationMin,
        intensity = item.intensity,
        themes = item.excludedByBoundaries,
    )
}
