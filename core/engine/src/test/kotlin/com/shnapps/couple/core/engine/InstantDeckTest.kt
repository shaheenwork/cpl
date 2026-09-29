package com.shnapps.couple.core.engine

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.model.ChapterKind
import com.shnapps.couple.core.model.ContentItem
import com.shnapps.couple.core.model.ContentStatus
import com.shnapps.couple.core.model.Heat
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.InteractionType
import com.shnapps.couple.core.model.MediaKind
import com.shnapps.couple.core.model.Mode
import org.junit.Test

/**
 * The deck a couple gets before the server knows they exist (REDESIGN_PROMPT.md §4.1).
 *
 * Its safety does not come from being filtered like a night — it cannot be, because there is no
 * partner and no private document to filter against. It comes from what it is allowed to contain:
 * nothing above the heat on this phone, nothing this person has refused, and nothing that needs an
 * account or a partner to make sense.
 *
 * The test that matters most is the last one: narrowing can only ever remove. The day this deck is
 * layered over a server-filtered pool, that is what stops it widening one.
 */
class InstantDeckTest {

    @Test
    fun `nothing above the heat on this phone`() {
        val deck = InstantDeck.of(everything, Heat.PLAYFUL)

        assertThat(deck.cards).isNotEmpty()
        assertThat(deck.cards.map { it.intensity.level }.max()).isAtMost(Heat.PLAYFUL.ceiling.level)
    }

    @Test
    fun `turning the heat up only ever adds`() {
        val playful = InstantDeck.of(everything, Heat.PLAYFUL).cards.map { it.id }
        val wild = InstantDeck.of(everything, Heat.WILD).cards.map { it.id }

        assertThat(wild).containsAtLeastElementsIn(playful)
        assertThat(wild.size).isGreaterThan(playful.size)
    }

    @Test
    fun `a card they refused never comes back`() {
        val deck = InstantDeck.of(everything, Heat.WILD, skippedIds = setOf("soft_1"))

        assertThat(deck.cards.map { it.id }).doesNotContain("soft_1")
    }

    @Test
    fun `refusing a kind of thing refuses all of it`() {
        val deck = InstantDeck.of(everything, Heat.WILD, refusedThemes = setOf("power_yielding"))

        assertThat(deck.cards.map { it.id }).doesNotContain("bold_1")
        assertThat(deck.cards.map { it.themes }.flatten()).doesNotContain("power_yielding")
    }

    @Test
    fun `nothing that needs an account or a partner is in it`() {
        val deck = InstantDeck.of(everything, Heat.WILD)
        val ids = deck.cards.map { it.id }

        // A voice note needs somewhere to send it; a mutual match needs two people who have
        // answered something. Neither exists on a phone nobody has signed in to.
        assertThat(ids).doesNotContain("needs_media")
        assertThat(ids).doesNotContain("needs_match")
        assertThat(ids).doesNotContain("draft_1")
        assertThat(ids).doesNotContain("apart_1")
    }

    @Test
    fun `the same content builds the same deck, every time`() {
        val once = InstantDeck.of(everything, Heat.HOT).cards.map { it.id }
        val again = InstantDeck.of(everything, Heat.HOT).cards.map { it.id }

        assertThat(again).isEqualTo(once)
    }

    @Test
    fun `the deck keeps the content's own order and shuffles nothing`() {
        // Nothing random here: a reopened app does not start somewhere else, and where a card
        // lands in an evening is the player's business rather than the deck's.
        val deck = InstantDeck.of(everything, Heat.WILD)
        val expected = everything
            .filter { it.status == ContentStatus.PUBLISHED && Mode.TOGETHER in it.modes }
            .filter { it.requiresMedia == null && it.requiredMutualPreferences.isEmpty() }
            .map { it.id }

        assertThat(deck.cards.map { it.id }).isEqualTo(expected)
    }

    @Test
    fun `an empty deck does not crash the player`() {
        val deck = InstantDeck.of(emptyList(), Heat.WILD)

        assertThat(deck.size).isEqualTo(0)
        assertThat(deck.cardAt(0)).isNull()
    }

    @Test
    fun `cards run on rather than running out`() {
        val deck = InstantDeck.of(everything, Heat.PLAYFUL)

        assertThat(deck.cardAt(deck.size)).isEqualTo(deck.cardAt(0))
    }

    @Test
    fun `narrowing can only ever remove`() {
        // The rule the day this sits on top of a server-filtered pool: a second gate, never a
        // wider one (D-048).
        val full = InstantDeck.of(everything, Heat.WILD)
        val narrowed = full.narrow(ids = setOf("soft_1"), themes = setOf("power_yielding"))

        assertThat(full.cards).containsAtLeastElementsIn(narrowed.cards)
        assertThat(narrowed.cards.size).isLessThan(full.cards.size)
    }

    @Test
    fun `a run climbs to the heat and then stays there`() {
        // The bug this exists for: the deck was dealt in bundle order from index zero, so the
        // first card at Wild was a level-one flirt prompt — the same one on every install (D-124).
        val deck = InstantDeck.of(everything, Heat.WILD)
        val run = (0 until RUN).map { deck.next(it, Heat.WILD, seed = 1) }

        assertThat(run.map { it!!.intensity }).containsExactly(
            Intensity.NAUGHTY, Intensity.BOLD,
            Intensity.WILD, Intensity.WILD, Intensity.WILD, Intensity.WILD,
        ).inOrder()
    }

    @Test
    fun `the heat is the ceiling of the climb, not an average under it`() {
        val deck = InstantDeck.of(everything, Heat.HOT)
        val run = (0 until RUN).map { deck.next(it, Heat.HOT, seed = 2)!! }

        // Past the climb, every card is as far as this couple said they wanted to go.
        assertThat(run.drop(2).map { it.intensity }.toSet()).containsExactly(Intensity.BOLD)
    }

    @Test
    fun `nothing comes twice while there is anything left`() {
        val deck = InstantDeck.of(everything, Heat.WILD)
        val dealt = mutableSetOf<String>()
        repeat(deck.size) { position ->
            val card = deck.next(position, Heat.WILD, seed = 3, exclude = dealt)!!
            assertThat(dealt).doesNotContain(card.id)
            dealt += card.id
        }

        assertThat(dealt).hasSize(deck.size)
    }

    @Test
    fun `seen everything deals the deck again rather than stopping`() {
        val deck = InstantDeck.of(everything, Heat.WILD)
        val all = deck.cards.map { it.id }.toSet()

        assertThat(deck.next(0, Heat.WILD, seed = 4, exclude = all)).isNotNull()
    }

    @Test
    fun `the same seed deals the same run, and a different one does not`() {
        val deck = InstantDeck.of(everything, Heat.WILD)
        fun run(seed: Long) = (0 until RUN).map { deck.next(it, Heat.WILD, seed)!!.id }

        assertThat(run(5)).isEqualTo(run(5))
        // Not a guarantee for every pair of seeds, but these two differ, which is the point:
        // two evenings are not the same evening.
        assertThat(run(5)).isNotEqualTo(run(6))
    }

    @Test
    fun `a lane deals only what was authored in it`() {
        val deck = InstantDeck.of(everything, Heat.WILD, pack = "AFTER_DARK")

        assertThat(deck.cards).isNotEmpty()
        assertThat(deck.cards.map { it.pack }.toSet()).containsExactly("AFTER_DARK")
        assertThat(deck.packs).containsExactly("AFTER_DARK")
    }

    @Test
    fun `a lane is still only ever narrower than the whole deck`() {
        // Same rule as narrowing: asking for one pack can never reach a card the open deck
        // would not have dealt.
        val open = InstantDeck.of(everything, Heat.WILD).cards.map { it.id }
        val lane = InstantDeck.of(everything, Heat.WILD, pack = "AFTER_DARK").cards.map { it.id }

        assertThat(open).containsAtLeastElementsIn(lane)
    }

    @Test
    fun `an empty deck deals nothing and does not crash`() {
        assertThat(InstantDeck.of(emptyList(), Heat.WILD).next(0, Heat.WILD, seed = 7)).isNull()
    }

    private companion object {
        /** A run's length on the screen this deals for. */
        const val RUN = 6

        @Suppress("LongParameterList") // A fixture: every field a filter looks at, named.
        fun item(
            id: String,
            intensity: Intensity,
            themes: Set<String> = setOf("mood_gentle"),
            status: ContentStatus = ContentStatus.PUBLISHED,
            modes: Set<Mode> = setOf(Mode.TOGETHER),
            media: MediaKind? = null,
            required: Set<String> = emptySet(),
            pack: String = "FLIRT",
        ) = ContentItem(
            id = id,
            version = 1,
            pack = pack,
            category = "communication",
            title = "Card $id",
            subtitle = "",
            body = "Something to do, for $id.",
            tags = setOf("flirting"),
            intensity = intensity,
            modes = modes,
            interactionType = InteractionType.CHALLENGE,
            durationMin = 5,
            moods = emptySet(),
            requiredMutualPreferences = required,
            boostedByPreferences = emptySet(),
            excludedByBoundaries = themes,
            chapterKinds = setOf(ChapterKind.WARM_UP),
            noveltyWeight = 0.5,
            repeatCooldownDays = 30,
            requiresMedia = media,
            status = status,
        )

        val everything = listOf(
            item("soft_1", Intensity.SOFT),
            item("soft_2", Intensity.SOFT),
            item("flirty_1", Intensity.FLIRTY),
            item("flirty_2", Intensity.FLIRTY, pack = "AFTER_DARK"),
            item("naughty_1", Intensity.NAUGHTY),
            item("naughty_2", Intensity.NAUGHTY, pack = "AFTER_DARK"),
            item("bold_1", Intensity.BOLD, themes = setOf("power_yielding")),
            item("bold_2", Intensity.BOLD, pack = "AFTER_DARK"),
            item("bold_3", Intensity.BOLD),
            item("wild_1", Intensity.WILD),
            item("wild_2", Intensity.WILD, pack = "AFTER_DARK"),
            item("wild_3", Intensity.WILD),
            item("draft_1", Intensity.SOFT, status = ContentStatus.DRAFT),
            item("apart_1", Intensity.SOFT, modes = setOf(Mode.APART)),
            item("needs_media", Intensity.SOFT, media = MediaKind.VOICE),
            item("needs_match", Intensity.SOFT, required = setOf("teasing_verbal")),
        )
    }
}
