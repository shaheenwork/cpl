package com.shnapps.couple.feature.instant

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.data.instant.InstantPlayRepository
import com.shnapps.couple.core.engine.InstantCard
import com.shnapps.couple.core.engine.InstantDeck
import com.shnapps.couple.core.model.ChapterKind
import com.shnapps.couple.core.model.ContentItem
import com.shnapps.couple.core.model.ContentStatus
import com.shnapps.couple.core.model.Heat
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.InteractionType
import com.shnapps.couple.core.model.Mode
import com.shnapps.couple.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * The card, and the three things a person can do about it (REDESIGN_PROMPT.md §5.3).
 *
 * The assertions worth having are about what is *not* asked and *not* recorded: a skip records
 * nothing, a refusal is written to this phone alone, and the heat is offered rather than applied.
 */
class InstantPlayViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val instant = FakeInstantPlayRepository()

    @Test
    fun `a card is on screen without an account, a partner, or a network`() = runTest {
        val viewModel = viewModel()

        assertThat(viewModel.uiState.value.card).isNotNull()
        assertThat(viewModel.uiState.value.loading).isFalse()
    }

    @Test
    fun `done moves on, and asks nothing`() = runTest {
        val viewModel = viewModel()
        val first = viewModel.uiState.value.card

        viewModel.done()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.card).isNotEqualTo(first)
        assertThat(instant.refusals).isEmpty()
    }

    @Test
    fun `skip records nothing at all`() = runTest {
        val viewModel = viewModel()

        viewModel.skip()
        advanceUntilIdle()

        assertThat(instant.refusals).isEmpty()
        assertThat(viewModel.uiState.value.streak).isEqualTo(0)
    }

    @Test
    fun `five in a row offers to turn it up, and does not turn it up`() = runTest {
        val viewModel = viewModel()

        repeat(5) { viewModel.done() }
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.offeringHotter).isEqualTo(Heat.HOT)
        assertThat(instant.heatSet).isNull()
    }

    @Test
    fun `at the top, nothing is offered`() = runTest {
        instant.heatState.value = Heat.WILD
        val viewModel = viewModel()

        repeat(6) { viewModel.done() }
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.offeringHotter).isNull()
    }

    @Test
    fun `keeping the heat carries on rather than stopping`() = runTest {
        val viewModel = viewModel()
        repeat(5) { viewModel.done() }
        advanceUntilIdle()

        viewModel.keepHeat()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.offeringHotter).isNull()
        assertThat(viewModel.uiState.value.card).isNotNull()
        assertThat(instant.heatSet).isNull()
    }

    @Test
    fun `too far asks one question and sends nothing yet`() = runTest {
        val viewModel = viewModel()

        viewModel.tooFar()

        assertThat(viewModel.uiState.value.refusing).isNotNull()
        assertThat(instant.refusals).isEmpty()
    }

    @Test
    fun `just this one refuses the card and not its kind`() = runTest {
        val viewModel = viewModel()
        val card = viewModel.uiState.value.card

        viewModel.tooFar()
        viewModel.refuse(alsoItsKind = false)
        advanceUntilIdle()

        assertThat(instant.refusals).containsExactly(card to false)
        assertThat(viewModel.uiState.value.refusing).isNull()
    }

    @Test
    fun `never again refuses everything of its kind`() = runTest {
        val viewModel = viewModel()
        val card = viewModel.uiState.value.card

        viewModel.tooFar()
        viewModel.refuse(alsoItsKind = true)
        advanceUntilIdle()

        assertThat(instant.refusals).containsExactly(card to true)
    }

    @Test
    fun `a deck with nothing left says so instead of showing nothing`() = runTest {
        instant.deckState.value = InstantDeck.of(emptyList(), Heat.PLAYFUL)
        val viewModel = viewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.card).isNull()
        assertThat(viewModel.uiState.value.loading).isFalse()
    }

    private fun viewModel(): InstantPlayViewModel {
        val viewModel = InstantPlayViewModel(instant)
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
        return viewModel
    }

    /** A real object with real state, not a mock: the fakes rule (DECISIONS.md D-013). */
    private class FakeInstantPlayRepository : InstantPlayRepository {
        val deckState = MutableStateFlow(InstantDeck.of(items, Heat.WILD))
        val heatState = MutableStateFlow(Heat.PLAYFUL)

        var heatSet: Heat? = null
            private set
        val refusals = mutableListOf<Pair<InstantCard, Boolean>>()

        override val deck: Flow<InstantDeck> = deckState
        override val heat: Flow<Heat> = heatState

        override suspend fun setHeat(heat: Heat) {
            heatSet = heat
            heatState.value = heat
        }

        override suspend fun refuse(card: InstantCard, alsoItsKind: Boolean) {
            refusals += card to alsoItsKind
        }
    }

    private companion object {
        fun item(id: String, intensity: Intensity) = ContentItem(
            id = id,
            version = 1,
            pack = "FLIRT",
            category = "communication",
            title = "Card $id",
            subtitle = "",
            body = "Something to do, for $id.",
            tags = setOf("flirting"),
            intensity = intensity,
            modes = setOf(Mode.TOGETHER),
            interactionType = InteractionType.CHALLENGE,
            durationMin = 5,
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

        val items = List(12) { item("card_$it", Intensity.SOFT) }
    }
}
