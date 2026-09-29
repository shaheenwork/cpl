package com.shnapps.couple.core.engine

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.model.ChapterKind
import com.shnapps.couple.core.model.ContentItem
import com.shnapps.couple.core.model.ContentStatus
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.InteractionType
import com.shnapps.couple.core.model.Mode
import org.junit.Test

/**
 * Game Night (the brief's game 12): an evening in four phases rather than a card at a time.
 *
 * What has to hold is the shape. It climbs and stops at the couple's tier; every phase has
 * something in it; it lands on an aftercare card rather than on one more peak; and nothing it deals
 * has been dealt before. The length is a budget in minutes, so the card count follows the content
 * rather than a constant.
 */
class GameNightTest {

    @Test
    fun `the evening climbs to the tier and stops there`() {
        val night = GameNight.plan(deck(), tier = Intensity.EXTREME, seed = 1)

        val peak = night.cards.filter { it.phase == NightPhase.PEAK }
        val warmUp = night.cards.filter { it.phase == NightPhase.WARM_UP }

        assertThat(warmUp).isNotEmpty()
        assertThat(peak).isNotEmpty()
        assertThat(warmUp.maxOf { it.card.intensity.level })
            .isLessThan(peak.maxOf { it.card.intensity.level })
        assertThat(night.cards.maxOf { it.card.intensity.level }).isAtMost(Intensity.EXTREME.level)
    }

    @Test
    fun `nothing in it is above the tier, however short the evening`() {
        NightLength.entries.forEach { length ->
            val night = GameNight.plan(deck(), tier = Intensity.SPICY, length = length, seed = 2)
            assertThat(night.cards.map { it.card.intensity.level }.max()).isAtMost(Intensity.SPICY.level)
        }
    }

    @Test
    fun `every phase has at least one card, even when the cards are long`() {
        val night = GameNight.plan(deck(), tier = Intensity.EXTREME, length = NightLength.SHORT, seed = 3)

        // A plan with an empty middle is not a shape, it is a gap.
        listOf(NightPhase.WARM_UP, NightPhase.BUILD, NightPhase.PEAK).forEach { phase ->
            assertThat(night.cards.filter { it.phase == phase }).isNotEmpty()
        }
    }

    @Test
    fun `a longer evening is a longer evening`() {
        val short = GameNight.plan(deck(), tier = Intensity.EXTREME, length = NightLength.SHORT, seed = 4)
        val long = GameNight.plan(deck(), tier = Intensity.EXTREME, length = NightLength.LONG, seed = 4)

        assertThat(long.minutes).isGreaterThan(short.minutes)
    }

    @Test
    fun `it lands on a card written to land on`() {
        val night = GameNight.plan(deck(), tier = Intensity.EXTREME, seed = 5)

        assertThat(night.cards.last().phase).isEqualTo(NightPhase.AFTERCARE)
        assertThat(night.aftercare).isNotNull()
        assertThat(night.aftercare!!.aftercare).isTrue()
    }

    @Test
    fun `with nothing written for it, the plan says so rather than ending on a peak`() {
        val withoutCare = everything.filterNot { "aftercare" in it.tags }
        val night = GameNight.plan(InstantDeck.of(withoutCare, Intensity.EXTREME), Intensity.EXTREME, seed = 6)

        assertThat(night.cards).isNotEmpty()
        assertThat(night.aftercare).isNull()
        assertThat(night.cards.none { it.phase == NightPhase.AFTERCARE }).isTrue()
    }

    @Test
    fun `no card is dealt twice, and none they have already had`() {
        val night = GameNight.plan(deck(), tier = Intensity.EXTREME, seed = 7)
        assertThat(night.cards.map { it.card.id }).containsNoDuplicates()

        val already = night.cards.map { it.card.id }.toSet()
        val next = GameNight.plan(deck(), tier = Intensity.EXTREME, seed = 8, exclude = already)

        // The aftercare card is the one thing that may come round again: there are few of them, and
        // an evening that ends without one ends worse.
        val repeated = next.cards.filterNot { it.phase == NightPhase.AFTERCARE }.map { it.card.id }
        assertThat(already).containsNoneIn(repeated)
    }

    @Test
    fun `an empty deck plans nothing rather than crashing`() {
        val night = GameNight.plan(InstantDeck.of(emptyList(), Intensity.EXTREME), Intensity.EXTREME)

        assertThat(night.size).isEqualTo(0)
        assertThat(night.cardAt(0)).isNull()
    }

    private fun deck() = InstantDeck.of(everything, Intensity.EXTREME)

    private companion object {
        fun item(
            id: String,
            intensity: Intensity,
            minutes: Int = 5,
            tags: Set<String> = setOf("flirting"),
        ) = ContentItem(
            id = id,
            version = 1,
            pack = "FLIRT",
            category = "communication",
            title = "Card $id",
            subtitle = "",
            body = "Something to do, for $id.",
            tags = tags,
            intensity = intensity,
            modes = setOf(Mode.TOGETHER),
            interactionType = InteractionType.CHALLENGE,
            durationMin = minutes,
            moods = emptySet(),
            requiredMutualPreferences = emptySet(),
            boostedByPreferences = emptySet(),
            excludedByBoundaries = setOf("mood_gentle"),
            chapterKinds = setOf(ChapterKind.WARM_UP),
            noveltyWeight = 0.5,
            repeatCooldownDays = 30,
            requiresMedia = null,
            status = ContentStatus.PUBLISHED,
        )

        /** Enough at every level to fill an hour, and two cards written to land on. */
        val everything: List<ContentItem> = Intensity.entries.flatMap { level ->
            (1..8).map { n -> item("${level.name.lowercase()}_$n", level, minutes = 5 + n % 3) }
        } + listOf(
            item("care_1", Intensity.HOT, tags = setOf("aftercare")),
            item("care_2", Intensity.EXTREME, tags = setOf("aftercare")),
        )
    }
}
