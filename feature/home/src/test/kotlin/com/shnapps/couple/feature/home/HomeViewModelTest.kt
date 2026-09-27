package com.shnapps.couple.feature.home

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.analytics.AnalyticsEvent
import com.shnapps.couple.core.analytics.AnalyticsLogger
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Clock
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.model.AuthUser
import com.shnapps.couple.core.model.ExperienceType
import com.shnapps.couple.core.model.Heat
import com.shnapps.couple.core.model.MediaKind
import com.shnapps.couple.core.model.NightPlan
import com.shnapps.couple.core.model.NightSession
import com.shnapps.couple.core.model.SessionState
import com.shnapps.couple.core.model.UserProfile
import com.shnapps.couple.core.testing.FakeAuthRepository
import com.shnapps.couple.core.testing.FakeGameRepository
import com.shnapps.couple.core.testing.FakeInstantPlayRepository
import com.shnapps.couple.core.testing.FakeMediaRepository
import com.shnapps.couple.core.testing.FakeNightRepository
import com.shnapps.couple.core.testing.FakePreferenceRepository
import com.shnapps.couple.core.testing.FakeSurpriseRepository
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
import java.io.File

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

    private val logged = mutableListOf<AnalyticsEvent>()
    private val nights = FakeNightRepository()
    private val instant = FakeInstantPlayRepository()
    private val gameStarts = FakeGameRepository()
    private val auth = FakeAuthRepository()
    private val media = FakeMediaRepository()
    private val surprises = FakeSurpriseRepository()
    private val curiosities = FakePreferenceRepository()

    /** Signed in and joined to someone: everything the server does is open to them. */
    private fun paired() {
        auth.user.value = AuthUser(uid = "u1", email = "a@b.c", isEmailVerified = true)
        auth.profile.value = UserProfile(uid = "u1", coupleId = "c1")
    }

    private fun TestScope.home() = HomeViewModel(
        nights = nights,
        instant = instant,
        gameStarts = gameStarts,
        analytics = AnalyticsLogger { logged += it },
        clock = Clock { NOW },
        auth = auth,
        curiosities = curiosities,
        media = media,
        surprises = surprises,
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
    fun `a paired couple's game is the server's, at the heat they set`() = test {
        paired()
        val vm = home()
        act { vm.setHeat(Heat.WILD) }

        act { vm.play(TestGames.TRUTH_OR_DARE) }

        // The heat is a ceiling the server is asked for; what it releases is still its own choice.
        assertThat(gameStarts.calls).containsExactly("start:${TestGames.TRUTH_OR_DARE}:${Heat.WILD.ceiling.level}")
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
    fun `the heat this phone sets is kept`() = test {
        val vm = home()
        assertThat(vm.uiState.value.heat).isEqualTo(Heat.DEFAULT)

        act { vm.setHeat(Heat.HOT) }

        assertThat(vm.uiState.value.heat).isEqualTo(Heat.HOT)
        assertThat(instant.heat.value).isEqualTo(Heat.HOT)
    }

    @Test
    fun `one tap makes a whole evening, and the screen opens it`() = test {
        paired()
        val vm = home()

        act { vm.surprise() }

        assertThat(nights.calls).containsExactly("surprise")
        assertThat(vm.uiState.value.opened)
            .isEqualTo(Opened.Session(nights.night.experienceId, ExperienceType.NIGHT))
        assertThat(logged).containsExactly(AnalyticsEvent.SurpriseCreated)
    }

    @Test
    fun `a mystery is asked for as one`() = test {
        paired()
        val vm = home()
        act { vm.surprise(mystery = true) }
        assertThat(nights.calls).containsExactly("surprise:mystery")
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
        act { vm.surprise() }
        assertThat(nights.calls).isEmpty()
        act { vm.play(TestGames.TRUTH_OR_DARE) }
        assertThat(gameStarts.calls).isEmpty()
    }

    @Test
    fun `an evening that cannot be built is said plainly, and nothing is left half-done`() = test {
        paired()
        val vm = home()
        nights.surpriseResult = Outcome.Failure(AppError.Validation(HomeViewModel.NOT_ENOUGH_CONTENT))

        act { vm.surprise() }

        assertThat(vm.uiState.value.error).isEqualTo(HomeError.NOT_ENOUGH_CONTENT)
        assertThat(vm.uiState.value.making).isFalse()
        assertThat(vm.uiState.value.opened).isNull()
        act(vm::dismissError)
        assertThat(vm.uiState.value.error).isNull()

        nights.surpriseResult = Outcome.Failure(AppError.Network())
        act { vm.surprise() }
        assertThat(vm.uiState.value.error).isEqualTo(HomeError.OFFLINE)
    }

    @Test
    fun `an evening the partner planned waits on this screen, and opening it says so`() = test {
        paired()
        nights.plannedNights.value = listOf(
            NightPlan(experienceId = "planned_night", mine = false, chapterCount = 5, opened = false),
        )
        val vm = home()

        assertThat(vm.uiState.value.waitingForMe?.chapterCount).isEqualTo(5)
        assertThat(vm.uiState.value.plannedByMe).isNull()

        act(vm::openWaiting)

        assertThat(nights.calls).containsExactly("openPlan:planned_night")
        assertThat(vm.uiState.value.opened).isEqualTo(Opened.Session("planned_night", ExperienceType.NIGHT))
    }

    @Test
    fun `an evening planned on this phone is only ever a shape, and says whether it landed`() = test {
        paired()
        nights.plannedNights.value = listOf(
            NightPlan(experienceId = "for_them", mine = true, chapterCount = 4, opened = false),
        )
        val vm = home()

        assertThat(vm.uiState.value.plannedByMe?.opened).isFalse()
        // Nothing about it is waiting for this phone: it is theirs to find.
        assertThat(vm.uiState.value.waitingForMe).isNull()
        act(vm::openWaiting)
        assertThat(nights.calls).isEmpty()

        act { nights.plannedNights.value = listOf(NightPlan("for_them", mine = true, chapterCount = 4, opened = true)) }
        assertThat(vm.uiState.value.plannedByMe?.opened).isTrue()
    }

    @Test
    fun `a voice note they sent waits here until it is opened`() = test {
        paired()
        media.theySend(MediaKind.VOICE, note = "listen to this", burnAfterView = true)
        val vm = home()

        assertThat(vm.uiState.value.unopenedMedia?.note).isEqualTo("listen to this")
        assertThat(vm.uiState.value.unopenedMedia?.burnAfterView).isTrue()

        // Once it has been opened, it stops asking to be.
        media.open(media.items.value.single().id, File.createTempFile("open", ".bin"))
        advanceUntilIdle()
        assertThat(vm.uiState.value.unopenedMedia).isNull()
    }
}

/** A fixed clock: the thread orders by time, and a test must not race a real one. */
private const val NOW = 1_800_000_000_000L
