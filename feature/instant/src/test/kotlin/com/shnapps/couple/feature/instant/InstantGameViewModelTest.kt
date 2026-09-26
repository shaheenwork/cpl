package com.shnapps.couple.feature.instant

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.data.instant.InstantPlayRepository
import com.shnapps.couple.core.engine.InstantCard
import com.shnapps.couple.core.engine.InstantDeck
import com.shnapps.couple.core.engine.game.Answer
import com.shnapps.couple.core.engine.game.GameMoment
import com.shnapps.couple.core.engine.game.ReleasedGame
import com.shnapps.couple.core.model.ContentStatus
import com.shnapps.couple.core.model.Game
import com.shnapps.couple.core.model.GamePrimitive
import com.shnapps.couple.core.model.GamePrompt
import com.shnapps.couple.core.model.GameStep
import com.shnapps.couple.core.model.Heat
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.Mode
import com.shnapps.couple.core.model.Scoring
import com.shnapps.couple.core.model.StepAudience
import com.shnapps.couple.core.model.StepOptions
import com.shnapps.couple.core.model.StepText
import com.shnapps.couple.core.model.TurnOrder
import com.shnapps.couple.core.navigation.Route
import com.shnapps.couple.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * A game dealt and played with nobody signed in (REDESIGN_PROMPT.md §5.2).
 *
 * The interpreter is the one every game has always used, so what is worth asserting here is the
 * seam: a game starts from what this phone dealt itself, it plays through the primitives, and a
 * game the content cannot fill says so rather than showing an empty screen.
 */
class InstantGameViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val instant = FakeInstantPlayRepository()

    @Test
    fun `a game deals and is ready to play`() = runTest {
        val viewModel = viewModel()

        assertThat(viewModel.uiState.value.loading).isFalse()
        assertThat(viewModel.uiState.value.title).isEqualTo("Spin-like")
        assertThat(viewModel.uiState.value.rounds).isEqualTo(ROUNDS)
        assertThat(viewModel.uiState.value.moment).isInstanceOf(GameMoment.Turn::class.java)
    }

    @Test
    fun `answering moves the game on`() = runTest {
        val viewModel = viewModel()
        val first = viewModel.uiState.value.moment

        viewModel.answer(Answer.Done)

        assertThat(viewModel.uiState.value.moment).isNotEqualTo(first)
    }

    @Test
    fun `an answer the primitive refuses changes nothing`() = runTest {
        val viewModel = viewModel()
        val before = viewModel.uiState.value.moment

        // A challenge takes "done" and nothing else: a picked option is not an answer to it.
        viewModel.answer(Answer.Picked("nonsense"))

        assertThat(viewModel.uiState.value.moment).isEqualTo(before)
    }

    @Test
    fun `a game plays from its first round to its last`() = runTest {
        val viewModel = viewModel()

        repeat(MAX_MOVES) {
            when (viewModel.uiState.value.moment) {
                is GameMoment.Finished -> return@repeat
                is GameMoment.RoundOver -> viewModel.nextRound()
                is GameMoment.Turn -> viewModel.answer(Answer.Done)
                else -> viewModel.proceed()
            }
        }

        assertThat(viewModel.uiState.value.moment).isInstanceOf(GameMoment.Finished::class.java)
    }

    @Test
    fun `playing it again deals a new game rather than rewinding`() = runTest {
        val viewModel = viewModel()
        viewModel.answer(Answer.Done)
        advanceUntilIdle()

        viewModel.deal()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.round).isEqualTo(0)
        assertThat(instant.deals).isEqualTo(2)
    }

    @Test
    fun `a game this phone has nothing for says so`() = runTest {
        instant.released = null
        val viewModel = viewModel()

        assertThat(viewModel.uiState.value.nothingToPlay).isTrue()
        assertThat(viewModel.uiState.value.loading).isFalse()
    }

    private fun viewModel(): InstantGameViewModel {
        val handle = SavedStateHandle(mapOf(Route.InstantGame::gameId.name to game.id))
        val viewModel = InstantGameViewModel(handle, instant)
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
        return viewModel
    }

    private class FakeInstantPlayRepository : InstantPlayRepository {
        var released: ReleasedGame? = ReleasedGame.forHeat(game, prompts, ceiling = 5)
        var deals = 0
            private set

        override val deck: Flow<InstantDeck> = MutableStateFlow(InstantDeck.of(emptyList(), Heat.WILD))
        override val heat: Flow<Heat> = MutableStateFlow(Heat.WILD)
        override val games: Flow<List<Game>> = MutableStateFlow(listOf(game))

        override suspend fun setHeat(heat: Heat) = Unit

        override suspend fun refuse(card: InstantCard, alsoItsKind: Boolean) = Unit

        override suspend fun startGame(gameId: String): ReleasedGame? {
            deals += 1
            return released
        }
    }

    private companion object {
        const val ROUNDS = 4
        const val MAX_MOVES = 60

        val game = Game(
            id = "SHAPED_LIKE_SPIN",
            title = "Spin-like",
            tagline = "",
            description = "",
            modes = setOf(Mode.TOGETHER),
            rounds = ROUNDS,
            turns = TurnOrder.ALTERNATE,
            scoring = Scoring.NONE,
            promptKinds = emptyList(),
            steps = listOf(
                GameStep(
                    primitive = GamePrimitive.DRAW,
                    who = StepAudience.TOGETHER,
                    isPrivate = false,
                    text = StepText.Prompt,
                    options = StepOptions.FromPrompt,
                    answer = null,
                    of = emptyList(),
                    outcome = null,
                    seconds = null,
                    kindFrom = null,
                ),
                GameStep(
                    primitive = GamePrimitive.CHALLENGE,
                    who = StepAudience.ACTIVE,
                    isPrivate = false,
                    text = StepText.Prompt,
                    options = null,
                    answer = null,
                    of = emptyList(),
                    outcome = null,
                    seconds = null,
                    kindFrom = null,
                ),
            ),
        )

        val prompts = (1..8).map { index ->
            GamePrompt(
                id = "spin_$index",
                version = 1,
                game = game.id,
                kind = null,
                text = "Do the thing, number $index.",
                options = listOf("Here", "There", "Everywhere", "Nowhere"),
                seconds = null,
                category = "communication",
                tags = setOf("game"),
                intensity = Intensity.ofLevel((index % 5) + 1),
                modes = setOf(Mode.TOGETHER),
                moods = emptySet(),
                requiredMutualPreferences = emptySet(),
                boostedByPreferences = emptySet(),
                excludedByBoundaries = setOf("mood_gentle"),
                status = ContentStatus.PUBLISHED,
            )
        }
    }
}
