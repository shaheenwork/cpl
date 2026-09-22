package com.shnapps.couple.feature.games

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.analytics.AnalyticsEvent
import com.shnapps.couple.core.analytics.AnalyticsLogger
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.engine.game.Answer
import com.shnapps.couple.core.engine.game.GameMoment
import com.shnapps.couple.core.engine.game.RoundPrimitive
import com.shnapps.couple.core.model.ChapterReaction
import com.shnapps.couple.core.model.CloseCheckIn
import com.shnapps.couple.core.model.ExperienceType
import com.shnapps.couple.core.model.GamePlayers
import com.shnapps.couple.core.model.NightSession
import com.shnapps.couple.core.model.SessionEndReason
import com.shnapps.couple.core.model.SessionState
import com.shnapps.couple.core.testing.FakeGameRepository
import com.shnapps.couple.core.testing.FakeNightRepository
import com.shnapps.couple.core.testing.FakeTaxonomyRepository
import com.shnapps.couple.core.testing.MainDispatcherRule
import com.shnapps.couple.core.testing.TestGames
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * The game player on both phones (BUILD_PROMPT.md §3.1, §11), against a backend that moves
 * sessions the way the server does: a whole game passed between the partners, consent from
 * each phone, PAUSE and STOP from either, "Not this one", and limits set mid-game.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun test(body: suspend TestScope.() -> Unit) = runTest(mainDispatcherRule.testDispatcher) { body() }

    private val logged = mutableListOf<AnalyticsEvent>()
    private val games = FakeGameRepository().apply { savedPlayers.value = GamePlayers("Alex", "Sam") }
    private val nights = FakeNightRepository()

    private fun TestScope.player(experienceId: String = CHOICES) = GameViewModel(
        savedStateHandle = SavedStateHandle(mapOf("experienceId" to experienceId)),
        games = games,
        nights = nights,
        taxonomy = FakeTaxonomyRepository(),
        analytics = AnalyticsLogger { logged += it },
    ).also { vm ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
    }

    private fun TestScope.act(action: () -> Unit) {
        action()
        advanceUntilIdle()
    }

    private val GameViewModel.stage get() = uiState.value.stage
    private val GameViewModel.moment get() = (stage as GameStage.Playing).moment
    private val GameViewModel.primitive get() = (moment as GameMoment.Turn).primitive

    /** One round of the choices game: both choose [mine] and [theirs], then the reveal. */
    private fun TestScope.playChoices(vm: GameViewModel, mine: String, theirs: String) {
        assertThat(vm.moment).isInstanceOf(GameMoment.HandOff::class.java)
        act(vm::proceed)
        act { vm.submit(Answer.Picked(mine)) }
        act(vm::proceed)
        act { vm.submit(Answer.Picked(theirs)) }
        assertThat(vm.moment).isInstanceOf(GameMoment.Gather::class.java)
        act(vm::proceed)
        assertThat(vm.primitive).isInstanceOf(RoundPrimitive.Reveal::class.java)
        act { vm.submit(Answer.Done) }
        assertThat(vm.moment).isInstanceOf(GameMoment.RoundOver::class.java)
    }

    @Test
    fun `plays a whole game on one phone, passed between the two of them`() = test {
        val vm = player()
        assertThat(vm.stage).isEqualTo(GameStage.Intro)

        act(vm::open)
        assertThat(vm.stage).isEqualTo(GameStage.Ready)
        act(vm::play)
        assertThat(vm.moment).isEqualTo(GameMoment.HandOff(0, seat = 0))

        repeat(FakeGameRepository.ROUNDS) { round ->
            playChoices(vm, mine = "0", theirs = if (round == 0) "0" else "1")
            act(vm::nextRound)
        }

        assertThat(vm.stage).isEqualTo(GameStage.HowAreWe)
        assertThat(games.calls).containsExactly(
            "load",
            "open",
            "begin",
            "finish:0:choices_1",
            "finish:1:choices_2",
            "finish:2:choices_3",
        ).inOrder()
        act { vm.answerClose(CloseCheckIn.GLOWING) }
        act(vm::toResults)
        assertThat(vm.stage).isEqualTo(GameStage.Results)
        assertThat(vm.uiState.value.run!!.score.matches).isEqualTo(1)
        assertThat(logged).containsExactly(
            AnalyticsEvent.GameStarted(TestGames.CHOICES),
            AnalyticsEvent.GameCompleted(TestGames.CHOICES),
        )
    }

    @Test
    fun `a BOLD game waits for both partners' consent, each from their own phone`() = test {
        val vm = player(BOLD_TRUTH_OR_DARE)
        act(vm::open)
        assertThat(vm.stage).isEqualTo(GameStage.Consent)
        act(vm::consent)
        assertThat(vm.stage).isEqualTo(GameStage.Consent)
        act(games::partnerConsents)
        assertThat(vm.stage).isEqualTo(GameStage.Ready)
    }

    @Test
    fun `truth or dare reports the dare as what was played`() = test {
        val vm = player(TRUTH_OR_DARE)
        act(vm::open)
        act(vm::play)
        val choice = vm.primitive as RoundPrimitive.Choice
        assertThat(choice.seat).isEqualTo(0)
        act { vm.submit(Answer.Picked("DARE")) }
        assertThat((vm.primitive as RoundPrimitive.Challenge).text).isEqualTo(TestGames.dares[0].text)
        act { vm.submit(Answer.Done) }
        act { vm.submit(Answer.Reacted(ChapterReaction.LOVED)) }
        act(vm::nextRound)
        assertThat(games.calls).contains("finish:0:tod_d1")
    }

    @Test
    fun `not this one swaps the prompt, and offers to keep its theme out of the game`() = test {
        val vm = player()
        act(vm::open)
        act(vm::play)
        act(vm::proceed)
        val before = (vm.primitive as RoundPrimitive.Choice).text
        act(vm::skip)
        assertThat(vm.moment).isEqualTo(GameMoment.HandOff(0, seat = 0))
        act(vm::proceed)
        assertThat((vm.primitive as RoundPrimitive.Choice).text).isNotEqualTo(before)
        assertThat(vm.uiState.value.keepOut).isEqualTo(KeepOutOffer(listOf("mood_gentle" to "Gentle")))

        // Every prompt is about that theme: keeping it out passes every round over, unseen.
        act { vm.keepOut("mood_gentle") }
        assertThat(vm.uiState.value.keepOut).isNull()
        assertThat(vm.stage).isEqualTo(GameStage.HowAreWe)
        assertThat(games.calls.filter { it.startsWith("finish") }).containsExactly(
            "finish:0:null",
            "finish:1:null",
            "finish:2:null",
        )
            .inOrder()
    }

    @Test
    fun `a limit set on the other phone mid-game takes its prompts out of the rounds to come`() = test {
        val vm = player()
        act(vm::open)
        act(vm::play)
        games.blockedNext = setOf("choices_2")
        playChoices(vm, "0", "0")
        act(vm::nextRound)
        assertThat(vm.uiState.value.run!!.promptInPlay()!!.id).isEqualTo("choices_4")
    }

    @Test
    fun `STOP from either phone ends it for both, and names no one`() = test {
        val vm = player()
        act(vm::open)
        act(vm::play)
        act(games::partnerStops)
        assertThat(vm.stage).isEqualTo(GameStage.Ended(SessionEndReason.STOPPED))

        val other = player()
        games.session.value = null
        act(other::open)
        act(other::play)
        act(other::stop)
        assertThat(other.stage).isEqualTo(GameStage.Ended(SessionEndReason.STOPPED))
    }

    @Test
    fun `a pause veils the game on this phone, and it carries on where it was`() = test {
        val vm = player()
        act(vm::open)
        act(vm::play)
        act(vm::proceed)
        act(vm::togglePause)
        assertThat(vm.uiState.value.paused).isTrue()
        act(vm::togglePause)
        assertThat(vm.uiState.value.paused).isFalse()
        assertThat(vm.primitive).isInstanceOf(RoundPrimitive.Choice::class.java)
    }

    @Test
    fun `a phone restarted mid-game picks up at the server's round`() = test {
        nights.session.value = session(state = SessionState.CHAPTER_ACTIVE, currentChapter = 1, isHost = true)
        games.session.value = nights.session.value
        val vm = player()
        assertThat(vm.moment).isEqualTo(GameMoment.HandOff(1, seat = 1))
    }

    @Test
    fun `the partner's phone never shows the game, and can still stop it`() = test {
        nights.session.value = session(state = SessionState.CHAPTER_ACTIVE, currentChapter = 0, isHost = false)
        games.session.value = nights.session.value
        val vm = player()
        assertThat(vm.stage).isEqualTo(GameStage.Elsewhere)
        assertThat(vm.uiState.value.run).isNull()
        act(vm::stop)
        assertThat(vm.stage).isEqualTo(GameStage.Ended(SessionEndReason.STOPPED))
    }

    @Test
    fun `a failed move is said plainly, and nothing is lost`() = test {
        val vm = player()
        act(vm::open)
        games.failNext = AppError.Network()
        act(vm::play)
        assertThat(vm.uiState.value.error).isEqualTo(GameError.OFFLINE)
        assertThat(vm.stage).isEqualTo(GameStage.Ready)
        act(vm::dismissError)
        act(vm::play)
        assertThat(vm.moment).isEqualTo(GameMoment.HandOff(0, seat = 0))
    }

    private fun session(state: SessionState, currentChapter: Int, isHost: Boolean) = NightSession(
        id = FakeGameRepository.SESSION_ID,
        experienceId = CHOICES,
        state = state,
        pausedFrom = null,
        currentChapter = currentChapter,
        chapterCount = FakeGameRepository.ROUNDS,
        consentRequired = false,
        consented = false,
        partnerConsented = false,
        isHost = isHost,
        endReason = null,
        experienceType = ExperienceType.GAME,
    )

    private companion object {
        const val CHOICES = "${FakeGameRepository.EXPERIENCE_PREFIX}${TestGames.CHOICES}"
        const val TRUTH_OR_DARE = "${FakeGameRepository.EXPERIENCE_PREFIX}${TestGames.TRUTH_OR_DARE}"
        const val BOLD_TRUTH_OR_DARE = "$TRUTH_OR_DARE${FakeGameRepository.BOLD_SUFFIX}"
    }
}
