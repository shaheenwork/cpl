package com.shnapps.couple.core.data.night

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.data.night.NightFixtures.byId
import com.shnapps.couple.core.engine.Night
import com.shnapps.couple.core.engine.PermittedPool
import com.shnapps.couple.core.engine.Remixer
import com.shnapps.couple.core.engine.Validator
import com.shnapps.couple.core.model.ChapterKind
import com.shnapps.couple.core.model.Mode
import org.junit.Test

/**
 * What a phone may do with the night it holds (BUILD_PROMPT.md §3.1, §5.5; DECISIONS.md D-048,
 * D-062): rearrange what the server released, keep every rule of the night, and take out of
 * the rest of it whatever the couple's limits no longer allow.
 */
class HeldNightTest {

    private val server = NightFixtures.server
    private val pool = PermittedPool.fromServer(server) { byId[it] }
    private val remixer = Remixer()

    private fun held() = HeldNight(
        experienceId = "night1",
        settings = NightFixtures.settings,
        pool = pool,
        night = Night(server.request, server.chapters, server.effectiveIntensity),
        serverIds = server.chapters.map { it.itemId },
        askFirstItems = setOf(server.chapters[1].itemId),
        focusIncluded = false,
    )

    private fun assertValid(night: HeldNight) {
        assertThat(Validator.validate(night.night.chapters, 15, NightFixtures.CEILING, Mode.TOGETHER, byId)).isEmpty()
        assertThat(night.night.chapters.map { it.itemId }).containsNoneIn(byId.keys - pool.ids)
    }

    @Test
    fun `shows the night it was given, chapter for chapter`() {
        val planned = held().planned()
        val expected = server.chapters
        assertThat(planned.chapters.map { it.item.id }).containsExactlyElementsIn(expected.map { it.itemId }).inOrder()
        assertThat(planned.chapters.map { it.kind }).containsExactlyElementsIn(expected.map { it.kind }).inOrder()
        assertThat(planned.effectiveIntensity.level).isEqualTo(NightFixtures.CEILING)
        assertThat(planned.askFirstItems).containsExactly(server.chapters[1].itemId)
        assertThat(planned.unplayable).isEmpty()
    }

    @Test
    fun `not this one swaps in something released, and never the refused one again`() {
        val first = requireNotNull(held().swapped(1, remixer))
        val refused = server.chapters[1].itemId
        assertThat(first.night.chapters[1].itemId).isNotEqualTo(refused)
        assertThat(first.night.chapters[1].itemId).isIn(pool.ids)
        assertThat(first.night.chapters[0]).isEqualTo(server.chapters[0])
        assertThat(first.night.chapters[2]).isEqualTo(server.chapters[2])
        assertValid(first)

        var night = first
        repeat(pool.ids.size) {
            night = night.swapped(1, remixer) ?: return@repeat
            assertThat(night.night.chapters[1].itemId).isNotEqualTo(refused)
        }
    }

    @Test
    fun `a swap is reported once, when its chapter is played`() {
        val swapped = requireNotNull(held().swapped(0, remixer))
        assertThat(held().unreportedSwap(0)).isNull()
        assertThat(swapped.unreportedSwap(0)).isEqualTo(swapped.night.chapters[0].itemId)
        assertThat(swapped.reported(0).unreportedSwap(0)).isNull()
        assertThat(swapped.unreportedSwap(1)).isNull()
    }

    @Test
    fun `a limit set mid-night takes its items out of the rest of it`() {
        val finale = server.chapters[2].itemId
        val next = held().withBlocked(setOf(finale), fromIndex = 1, remixer)
        assertThat(next.night.chapters[2].itemId).isNotEqualTo(finale)
        assertThat(next.unplayable).isEmpty()
        assertValid(next)
        // Nothing swapped later can bring it back.
        val again = next.swapped(2, remixer) ?: next
        assertThat(again.night.chapters.map { it.itemId }).doesNotContain(finale)
    }

    @Test
    fun `what was already played stays as it was`() {
        val first = server.chapters[0].itemId
        val next = held().withBlocked(setOf(first), fromIndex = 1, remixer)
        assertThat(next.night.chapters[0].itemId).isEqualTo(first)
    }

    @Test
    fun `a chapter nothing released can replace is passed over, never shown`() {
        val finales = pool.items.filter { ChapterKind.FINALE in it.chapterKinds }.map { it.id }
        val next = held().withBlocked(finales.toSet(), fromIndex = 0, remixer)
        assertThat(next.unplayable).contains(2)
        assertThat(next.planned().unplayable).contains(2)
    }

    @Test
    fun `finds what a theme covers among everything the night could still show`() {
        val theme = byId.getValue(server.chapters[1].itemId).excludedByBoundaries.first()
        val about = held().itemsAbout(theme)
        assertThat(about).contains(server.chapters[1].itemId)
        assertThat(about.all { theme in byId.getValue(it).excludedByBoundaries }).isTrue()
    }
}
