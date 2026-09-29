package com.shnapps.couple.feature.instant

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.data.instant.InstantPlayRepository
import com.shnapps.couple.core.engine.GameNight
import com.shnapps.couple.core.engine.InstantCard
import com.shnapps.couple.core.engine.InstantDeck
import com.shnapps.couple.core.engine.NightLength
import com.shnapps.couple.core.engine.game.ReleasedGame
import com.shnapps.couple.core.model.ChapterKind
import com.shnapps.couple.core.model.ContentItem
import com.shnapps.couple.core.model.ContentPack
import com.shnapps.couple.core.model.ContentStatus
import com.shnapps.couple.core.model.Game
import com.shnapps.couple.core.model.GamePlayers
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

        assertThat(viewModel.uiState.value.offeringHotter).isEqualTo(Intensity.SPICY)
        assertThat(instant.tierSet).isNull()
    }

    @Test
    fun `at the top, nothing is offered`() = runTest {
        instant.tierState.value = Intensity.EXTREME
        val viewModel = viewModel()

        repeat(6) { viewModel.done() }
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.offeringHotter).isNull()
    }

    @Test
    fun `keeping the tier carries on rather than stopping`() = runTest {
        val viewModel = viewModel()
        repeat(5) { viewModel.done() }
        advanceUntilIdle()

        viewModel.keepTier()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.offeringHotter).isNull()
        assertThat(viewModel.uiState.value.card).isNotNull()
        assertThat(instant.tierSet).isNull()
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
    fun `no clock runs until someone starts one`() = runTest {
        val viewModel = viewModel()

        assertThat(viewModel.uiState.value.timer).isNull()

        viewModel.startTimer()

        assertThat(viewModel.uiState.value.timer?.remainingSeconds).isEqualTo(5 * 60)
    }

    @Test
    fun `the clock counts down, and stops when it is stopped`() = runTest {
        val viewModel = viewModel()
        viewModel.startTimer()

        mainDispatcherRule.testDispatcher.scheduler.advanceTimeBy(3_100)

        assertThat(viewModel.uiState.value.timer?.remainingSeconds).isEqualTo(5 * 60 - 3)

        viewModel.stopTimer()
        mainDispatcherRule.testDispatcher.scheduler.advanceTimeBy(5_000)

        assertThat(viewModel.uiState.value.timer).isNull()
    }

    @Test
    fun `moving to the next card puts the clock away`() = runTest {
        val viewModel = viewModel()
        viewModel.startTimer()

        viewModel.done()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.timer).isNull()
    }

    @Test
    fun `a run of six asks whether to carry on, rather than ending`() = runTest {
        val viewModel = viewModel()

        repeat(4) { viewModel.skip() }
        assertThat(viewModel.uiState.value.runComplete).isFalse()

        repeat(2) { viewModel.skip() }
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.runComplete).isTrue()
        assertThat(viewModel.uiState.value.position).isEqualTo(6)
    }

    @Test
    fun `keeping going starts the count again and deals on`() = runTest {
        val viewModel = viewModel()
        repeat(6) { viewModel.skip() }
        advanceUntilIdle()
        val seen = instant.seen.map { it.first }

        viewModel.keepGoing()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.runComplete).isFalse()
        assertThat(viewModel.uiState.value.position).isEqualTo(0)
        assertThat(viewModel.uiState.value.played).isEqualTo(6)
        // A second run is a second run: not card seven of one list, and not one they just had.
        assertThat(seen).doesNotContain(viewModel.uiState.value.card!!.id)
    }

    @Test
    fun `a card never comes round twice while there is anything left`() = runTest {
        val viewModel = viewModel()

        // The bug this replaces: the deck was dealt from index zero in bundle order on every
        // launch, so an evening only ever reached the first few cards of the first pack (D-124).
        repeat(items.size) {
            viewModel.done()
            viewModel.keepGoing()
            advanceUntilIdle()
        }

        val ids = instant.seen.map { it.first }
        assertThat(ids).containsNoDuplicates()
        assertThat(ids).hasSize(items.size)
    }

    @Test
    fun `both done and skip count as seen, and a refusal does too`() = runTest {
        val viewModel = viewModel()

        viewModel.done()
        advanceUntilIdle()
        viewModel.skip()
        advanceUntilIdle()

        assertThat(instant.seen).hasSize(2)
        // Measured against the deck, so the record can reset once it covers all of it.
        assertThat(instant.seen.map { it.second }.toSet()).containsExactly(items.size)
    }

    @Test
    fun `asking for a lane deals from it, and starts the run again`() = runTest {
        val viewModel = viewModel()
        repeat(3) { viewModel.skip() }
        advanceUntilIdle()

        viewModel.setLane("AFTER_DARK")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.lane).isEqualTo("AFTER_DARK")
        assertThat(viewModel.uiState.value.laneLabel).isEqualTo("After dark")
        assertThat(viewModel.uiState.value.position).isEqualTo(0)
        assertThat(viewModel.uiState.value.card!!.pack).isEqualTo("AFTER_DARK")
    }

    @Test
    fun `dealing from everything again is one tap back`() = runTest {
        val viewModel = viewModel()
        viewModel.setLane("AFTER_DARK")
        advanceUntilIdle()

        viewModel.setLane(null)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.lane).isNull()
        assertThat(viewModel.uiState.value.laneLabel).isNull()
        assertThat(viewModel.uiState.value.lanes.map { it.id })
            .containsExactly("FLIRT", "AFTER_DARK")
    }

    @Test
    fun `a deck with nothing left says so instead of showing nothing`() = runTest {
        instant.deckState.value = InstantDeck.of(emptyList(), Intensity.MILD)
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
        val deckState = MutableStateFlow(InstantDeck.of(items, Intensity.EXTREME))
        val tierState = MutableStateFlow(Intensity.DEFAULT)

        var tierSet: Intensity? = null
            private set
        val refusals = mutableListOf<Pair<InstantCard, Boolean>>()

        val laneState = MutableStateFlow<String?>(null)
        val playedState = MutableStateFlow(emptySet<String>())
        val packsState = MutableStateFlow(
            listOf(
                ContentPack("FLIRT", "Flirt", "The fun of wanting each other.", 1..5),
                ContentPack("AFTER_DARK", "After dark", "Later, bolder.", 3..5),
            ),
        )

        /** Every card the player said was seen, with the deck size it was measured against. */
        val seen = mutableListOf<Pair<String, Int>>()

        override val deck: Flow<InstantDeck> = deckState
        override val tier: Flow<Intensity> = tierState
        override val packs: Flow<List<ContentPack>> = packsState
        override val lane: Flow<String?> = laneState
        override val played: Flow<Set<String>> = playedState

        override suspend fun setLane(pack: String?) {
            laneState.value = pack
            deckState.value = InstantDeck.of(items, Intensity.EXTREME, pack = pack)
        }

        override suspend fun markPlayed(card: InstantCard, deckSize: Int) {
            seen += card.id to deckSize
            playedState.value = playedState.value + card.id
        }

        /** The card player never asks for names; it is kept here only to satisfy the contract. */
        override val players: Flow<GamePlayers> = MutableStateFlow(GamePlayers.UNNAMED)

        override suspend fun rememberPlayers(players: GamePlayers) = Unit

        override suspend fun setTier(tier: Intensity) {
            tierSet = tier
            tierState.value = tier
        }

        override suspend fun refuse(card: InstantCard, alsoItsKind: Boolean) {
            refusals += card to alsoItsKind
        }

        // The card player never starts a game; these exist so the fake is the whole repository.
        override val games: Flow<List<Game>> = MutableStateFlow(emptyList())

        // The card player never plans an evening; these exist so the fake is the whole repository.
        override suspend fun planNight(length: NightLength): GameNight =
            GameNight.plan(deckState.value, Intensity.DEFAULT, length)

        override suspend fun startGame(gameId: String, autoHeat: Boolean): ReleasedGame? = null
    }

    private companion object {
        fun item(id: String, intensity: Intensity, pack: String = "FLIRT") = ContentItem(
            id = id,
            version = 1,
            pack = pack,
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

        val items = List(12) { item("card_$it", Intensity.MILD) } +
            List(4) { item("dark_$it", Intensity.EXTREME, pack = "AFTER_DARK") }
    }
}
