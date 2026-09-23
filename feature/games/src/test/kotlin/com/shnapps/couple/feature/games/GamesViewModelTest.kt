package com.shnapps.couple.feature.games

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.model.GamePlayers
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.UserProfile
import com.shnapps.couple.core.testing.FakeAuthRepository
import com.shnapps.couple.core.testing.FakeGameRepository
import com.shnapps.couple.core.testing.FakeNightRepository
import com.shnapps.couple.core.testing.MainDispatcherRule
import com.shnapps.couple.core.testing.TestGames
import com.shnapps.couple.core.testing.TestNights
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** Choosing and starting a game (BUILD_PROMPT.md §11): which one, how far, and who goes first. */
@OptIn(ExperimentalCoroutinesApi::class)
class GamesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun test(body: suspend TestScope.() -> Unit) = runTest(mainDispatcherRule.testDispatcher) { body() }

    private val games = FakeGameRepository()
    private val nights = FakeNightRepository()
    private val auth = FakeAuthRepository().apply { profile.value = UserProfile(uid = "me", displayName = "Alex") }

    private fun TestScope.screen() = GamesViewModel(games, nights, auth).also { vm ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
    }

    private fun TestScope.act(action: () -> Unit) {
        action()
        advanceUntilIdle()
    }

    private val GamesViewModel.state get() = uiState.value

    @Test
    fun `lists every game, and starts the one chosen at the level chosen`() = test {
        val vm = screen()
        assertThat(vm.state.games.map { it.id }).containsExactly(TestGames.CHOICES, TestGames.TRUTH_OR_DARE).inOrder()
        // The user's own name to start with: the partner's is theirs to type.
        assertThat(vm.state.first).isEqualTo("Alex")
        assertThat(vm.state.canStart).isFalse()

        act { vm.select(TestGames.TRUTH_OR_DARE) }
        act { vm.setIntensity(Intensity.NAUGHTY) }
        act { vm.setSecond("Sam") }
        assertThat(vm.state.canStart).isTrue()
        act(vm::start)

        assertThat(games.calls).containsExactly("players", "start:${TestGames.TRUTH_OR_DARE}:3").inOrder()
        assertThat(games.savedPlayers.value).isEqualTo(GamePlayers("Alex", "Sam"))
        assertThat(vm.state.startedExperienceId).isEqualTo("game_${TestGames.TRUTH_OR_DARE}")
        act(vm::onShown)
        assertThat(vm.state.startedExperienceId).isNull()
    }

    @Test
    fun `remembers the names from last time, and who goes first can swap`() = test {
        games.savedPlayers.value = GamePlayers("Sam", "Alex")
        val vm = screen()
        assertThat(vm.state.first).isEqualTo("Sam")
        act(vm::swapNames)
        assertThat(vm.state.first to vm.state.second).isEqualTo("Alex" to "Sam")
    }

    @Test
    fun `two names are needed, and they must differ`() = test {
        val vm = screen()
        act { vm.select(TestGames.CHOICES) }
        act { vm.setSecond(" alex ") }
        assertThat(vm.state.namesReady).isFalse()
        act { vm.setSecond("") }
        assertThat(vm.state.namesReady).isFalse()
        act { vm.setSecond("Sam") }
        assertThat(vm.state.namesReady).isTrue()
    }

    @Test
    fun `a phone each needs no names, and is offered only where the game allows it`() = test {
        val vm = screen()
        act { vm.select(TestGames.CHOICES) }
        assertThat(vm.state.canTwoPhones).isTrue()
        act { vm.setTwoPhones(true) }
        act { vm.setFirst("") }
        assertThat(vm.state.namesReady).isFalse()
        assertThat(vm.state.canStart).isTrue()

        // Truth or dare is played in turns: being handed the phone is the turn.
        act { vm.select(TestGames.TRUTH_OR_DARE) }
        assertThat(vm.state.canTwoPhones).isFalse()
        assertThat(vm.state.onTwoPhones).isFalse()
        assertThat(vm.state.canStart).isFalse()
    }

    @Test
    fun `with a phone each, no names are kept`() = test {
        val vm = screen()
        act { vm.select(TestGames.CHOICES) }
        act { vm.setTwoPhones(true) }
        act(vm::start)
        assertThat(games.calls).doesNotContain("players")
        assertThat(games.savedPlayers.value).isNull()
    }

    @Test
    fun `the dial never reaches past the couple's shared level`() = test {
        nights.level.value = Intensity.FLIRTY
        val vm = screen()
        act { vm.setIntensity(Intensity.WILD) }
        assertThat(vm.state.intensity).isEqualTo(Intensity.FLIRTY)
    }

    @Test
    fun `nothing starts while something is already under way`() = test {
        val vm = screen()
        act { vm.select(TestGames.CHOICES) }
        act { vm.setSecond("Sam") }
        nights.start(TestNights.gentle.experienceId)
        advanceUntilIdle()
        assertThat(vm.state.active).isNotNull()
        assertThat(vm.state.canStart).isFalse()
        act(vm::start)
        assertThat(games.calls).doesNotContain("start:${TestGames.CHOICES}:2")
    }

    @Test
    fun `a game the couple's limits leave too little of says so`() = test {
        games.startResult = Outcome.Failure(AppError.Validation("not-enough-content"))
        val vm = screen()
        act { vm.select(TestGames.CHOICES) }
        act { vm.setSecond("Sam") }
        act(vm::start)
        assertThat(vm.state.error).isEqualTo(StartError.NOT_ENOUGH_CONTENT)
        assertThat(vm.state.startedExperienceId).isNull()
        act(vm::dismissError)
        assertThat(vm.state.error).isNull()
    }
}
