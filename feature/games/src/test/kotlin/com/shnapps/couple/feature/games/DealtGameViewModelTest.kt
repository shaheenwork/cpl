package com.shnapps.couple.feature.games

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.engine.game.Answer
import com.shnapps.couple.core.engine.game.GameMoment
import com.shnapps.couple.core.engine.game.ReleasedGame
import com.shnapps.couple.core.model.ContentStatus
import com.shnapps.couple.core.model.Game
import com.shnapps.couple.core.model.GamePlayers
import com.shnapps.couple.core.model.GamePrimitive
import com.shnapps.couple.core.model.GamePrompt
import com.shnapps.couple.core.model.GameStep
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.Mode
import com.shnapps.couple.core.model.Scoring
import com.shnapps.couple.core.model.StepAudience
import com.shnapps.couple.core.model.StepOptions
import com.shnapps.couple.core.model.StepText
import com.shnapps.couple.core.model.TurnOrder
import com.shnapps.couple.core.navigation.Route
import com.shnapps.couple.core.testing.FakeInstantPlayRepository
import com.shnapps.couple.core.testing.MainDispatcherRule
import com.shnapps.couple.core.ui.voice.VoiceRecorder
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * A game dealt and played with nobody signed in (REDESIGN_PROMPT.md §5.2).
 *
 * The interpreter is the one every game has always used, so what is worth asserting here is the
 * seam: a game starts from what this phone dealt itself, it plays through the primitives, and a
 * game the content cannot fill says so rather than showing an empty screen.
 */
class DealtGameViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val instant = FakeInstantPlayRepository().apply {
        released = ReleasedGame.forHeat(game, prompts, ceiling = 5)
    }

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
    fun `a game plays from its first round to its last, and never stops to say a round ended`() = runTest {
        val viewModel = viewModel()
        val seen = mutableListOf<GameMoment>()

        repeat(MAX_MOVES) {
            val moment = viewModel.uiState.value.moment ?: return@repeat
            seen += moment
            when (moment) {
                is GameMoment.Finished -> return@repeat
                is GameMoment.Turn -> viewModel.answer(Answer.Done)
                else -> viewModel.proceed()
            }
        }

        assertThat(viewModel.uiState.value.moment).isInstanceOf(GameMoment.Finished::class.java)
        // "That's the round. Next" was a screen that said nothing and cost a tap a round (D-130).
        // A round's own last step is its end.
        assertThat(seen.filterIsInstance<GameMoment.RoundOver>()).isEmpty()
        // And every round was played: the taps went somewhere.
        assertThat(seen.filterIsInstance<GameMoment.Turn>().map { it.round }.distinct()).hasSize(ROUNDS)
    }

    @Test
    fun `playing it again deals a new game rather than rewinding`() = runTest {
        val viewModel = viewModel()
        viewModel.answer(Answer.Done)
        advanceUntilIdle()

        viewModel.deal()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.round).isEqualTo(0)
        assertThat(instant.dealt).hasSize(2)
    }

    @Test
    fun `a game this phone has nothing for says so`() = runTest {
        instant.released = null
        val viewModel = viewModel()

        assertThat(viewModel.uiState.value.nothingToPlay).isTrue()
        assertThat(viewModel.uiState.value.loading).isFalse()
    }

    /**
     * The bug a couple found, and the answer to it (D-120, D-121).
     *
     * Names are asked for at the first hand-off — not before, because a name asked for before a
     * game has started is the setup form the rebuild removed — and the hand-off that asked is the
     * first one to use them.
     */
    @Test
    fun `names are asked for at the first hand-off, and remembered`() = runTest {
        val viewModel = handingOver()

        assertThat(viewModel.uiState.value.players.named).isFalse()
        assertThat(viewModel.uiState.value.askingNames).isTrue()

        // Two people with the same name is worse than two with none: "over to Sam" would be
        // true of either of them.
        viewModel.setFirstName("Alex")
        viewModel.setSecondName("alex")
        assertThat(viewModel.uiState.value.namesReady).isFalse()

        viewModel.setSecondName("Sam")
        assertThat(viewModel.uiState.value.namesReady).isTrue()
        viewModel.confirmNames()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.players).isEqualTo(GamePlayers("Alex", "Sam"))
        assertThat(viewModel.uiState.value.askingNames).isFalse()
        // Kept on this phone, so the next game does not ask again.
        assertThat(instant.players.value).isEqualTo(GamePlayers("Alex", "Sam"))
    }

    @Test
    fun `not now is a real answer, and it is not asked again`() = runTest {
        val viewModel = handingOver()
        assertThat(viewModel.uiState.value.askingNames).isTrue()

        viewModel.skipNames()

        assertThat(viewModel.uiState.value.askingNames).isFalse()
        assertThat(viewModel.uiState.value.players.named).isFalse()
        // Nothing was written: they said not now, not never.
        assertThat(instant.players.value).isEqualTo(GamePlayers.UNNAMED)
    }

    /** The naming tests need a game that actually passes the phone. */
    private fun handingOver(): DealtGameViewModel {
        instant.released = ReleasedGame.forHeat(handsOver, prompts.map { it.copy(game = handsOver.id) }, ceiling = 5)
        return viewModel(handsOver.id)
    }

    private fun viewModel(gameId: String = game.id): DealtGameViewModel {
        val handle = SavedStateHandle(mapOf(Route.DealtGame::gameId.name to gameId))
        val viewModel = DealtGameViewModel(handle, instant, NoRecorder)
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
        return viewModel
    }

    /** No microphone in a test, which is the same as a phone that refuses one. */
    private object NoRecorder : VoiceRecorder {
        override fun start(): Outcome<Unit> = Outcome.Failure(AppError.Unknown())

        override fun stop(): Outcome<File> = Outcome.Failure(AppError.Unknown())

        override fun cancel() = Unit
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

        /**
         * A game that hands the phone over before its first step.
         *
         * The fixture above opens on a TOGETHER step, so it never passes the phone, and the ask
         * for names has nowhere to appear. This one is what a "who's more likely" game looks
         * like: one person answers in private, so the phone changes hands first.
         */
        val handsOver = game.copy(
            id = "SHAPED_LIKE_A_VOTE",
            steps = listOf(
                GameStep(
                    primitive = GamePrimitive.CHALLENGE,
                    who = StepAudience.ACTIVE,
                    isPrivate = true,
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
