package com.shnapps.couple.feature.home

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.model.AuthUser
import com.shnapps.couple.core.model.ExperienceType
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.NightSession
import com.shnapps.couple.core.model.SessionState
import com.shnapps.couple.core.model.UserProfile
import com.shnapps.couple.core.testing.FakeAuthRepository
import com.shnapps.couple.core.testing.FakeGameRepository
import com.shnapps.couple.core.testing.FakeInstantPlayRepository
import com.shnapps.couple.core.testing.FakeNightRepository
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
 * Home, the only one (BUILD_PROMPT.md §2.4, §14.2; REDESIGN_PROMPT.md §6).
 *
 * The rules worth holding here are the ones a person would notice if they broke: a phone with no
 * account can still play, a paired couple's game comes from the server and never from the phone,
 * and a couple has one experience under way at a time (D-059).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun test(body: suspend TestScope.() -> Unit) = runTest(mainDispatcherRule.testDispatcher) { body() }

    private val nights = FakeNightRepository()
    private val instant = FakeInstantPlayRepository()
    private val gameStarts = FakeGameRepository()
    private val auth = FakeAuthRepository()

    /** Signed in and joined to someone: everything the server does is open to them. */
    private fun paired() {
        auth.user.value = AuthUser(uid = "u1", email = "a@b.c", isEmailVerified = true)
        auth.profile.value = UserProfile(uid = "u1", coupleId = "c1")
    }

    private fun TestScope.home() = HomeViewModel(
        nights = nights,
        instant = instant,
        gameStarts = gameStarts,
        auth = auth,
    ).also { vm ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
    }

    private fun TestScope.act(action: () -> Unit) {
        action()
        advanceUntilIdle()
    }

    @Test
    fun `with no account the games are still listed, and one is dealt on this phone`() = test {
        val vm = home()

        assertThat(vm.uiState.value.company).isEqualTo(Company.ALONE)
        assertThat(vm.uiState.value.games).isNotEmpty()

        act { vm.play(TestGames.TRUTH_OR_DARE) }

        // Nothing was asked of any server, because there is nobody to ask for.
        assertThat(gameStarts.calls).isEmpty()
        assertThat(vm.uiState.value.opened).isEqualTo(Opened.Dealt(TestGames.TRUTH_OR_DARE))
        act(vm::onShown)
        assertThat(vm.uiState.value.opened).isNull()
    }

    @Test
    fun `a paired couple's game is the server's, at the tier they set`() = test {
        paired()
        val vm = home()
        act { vm.setTier(Intensity.EXTREME) }

        act { vm.play(TestGames.TRUTH_OR_DARE) }

        // The heat is a ceiling the server is asked for; what it releases is still its own choice.
        assertThat(gameStarts.calls).containsExactly("start:${TestGames.TRUTH_OR_DARE}:${Intensity.EXTREME.level}")
        assertThat(vm.uiState.value.opened)
            .isInstanceOf(Opened.Session::class.java)
        assertThat((vm.uiState.value.opened as Opened.Session).type).isEqualTo(ExperienceType.GAME)
    }

    @Test
    fun `signed in but nobody joined yet is its own answer`() = test {
        auth.user.value = AuthUser(uid = "u1", email = "a@b.c", isEmailVerified = true)
        auth.profile.value = UserProfile(uid = "u1", coupleId = null)
        val vm = home()

        assertThat(vm.uiState.value.company).isEqualTo(Company.SIGNED_IN)
        // Still no partner, so a game is still dealt here rather than by the server.
        act { vm.play(TestGames.TRUTH_OR_DARE) }
        assertThat(gameStarts.calls).isEmpty()
    }

    @Test
    fun `the tier this phone sets is kept`() = test {
        val vm = home()
        assertThat(vm.uiState.value.tier).isEqualTo(Intensity.DEFAULT)

        act { vm.setTier(Intensity.HOT) }

        assertThat(vm.uiState.value.tier).isEqualTo(Intensity.HOT)
        assertThat(instant.tier.value).isEqualTo(Intensity.HOT)
    }

    @Test
    fun `nothing starts while the couple already has something under way`() = test {
        paired()
        nights.session.value = NightSession(
            id = "s",
            experienceId = "e",
            state = SessionState.CHAPTER_ACTIVE,
            pausedFrom = null,
            currentChapter = 0,
            chapterCount = 4,
            consentRequired = false,
            consented = false,
            partnerConsented = false,
            isHost = true,
            endReason = null,
            experienceType = ExperienceType.NIGHT,
        )
        val vm = home()

        assertThat(vm.uiState.value.canStart).isFalse()
        act { vm.play(TestGames.TRUTH_OR_DARE) }
        assertThat(gameStarts.calls).isEmpty()
    }

    @Test
    fun `a game that cannot be started is said plainly, and nothing is left half-done`() = test {
        paired()
        val vm = home()
        gameStarts.startResult = Outcome.Failure(AppError.Validation(HomeViewModel.NOT_ENOUGH_CONTENT))

        act { vm.play(TestGames.TRUTH_OR_DARE) }

        assertThat(vm.uiState.value.error).isEqualTo(HomeError.NOT_ENOUGH_CONTENT)
        assertThat(vm.uiState.value.making).isFalse()
        assertThat(vm.uiState.value.opened).isNull()
        act(vm::dismissError)
        assertThat(vm.uiState.value.error).isNull()

        gameStarts.startResult = Outcome.Failure(AppError.Network())
        act { vm.play(TestGames.TRUTH_OR_DARE) }
        assertThat(vm.uiState.value.error).isEqualTo(HomeError.OFFLINE)
    }

    @Test
    fun `home reads nothing about messages, because there are none`() = test {
        paired()
        val vm = home()

        // V2 rule 3: the app is the games. There is no inbox, no thread and no "send a photo"
        // surface, so home does not hold a media or a surprise repository at all — the assertion
        // is that this screen still works having been given neither.
        assertThat(vm.uiState.value.games).isNotEmpty()
        assertThat(vm.uiState.value.company).isEqualTo(Company.PAIRED)
    }
}
