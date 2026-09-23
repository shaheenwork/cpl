package com.shnapps.couple.feature.home

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.analytics.AnalyticsEvent
import com.shnapps.couple.core.analytics.AnalyticsLogger
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.model.ExperienceType
import com.shnapps.couple.core.model.MediaKind
import com.shnapps.couple.core.model.Mode
import com.shnapps.couple.core.model.NightPlan
import com.shnapps.couple.core.model.NightSession
import com.shnapps.couple.core.model.SessionState
import com.shnapps.couple.core.testing.FakeAppPreferencesStore
import com.shnapps.couple.core.testing.FakeMediaRepository
import com.shnapps.couple.core.testing.FakeNightRepository
import com.shnapps.couple.core.testing.MainDispatcherRule
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
 * Home (BUILD_PROMPT.md §2.4, §14.2): the two taps that need no thought, the switch between
 * the same room and apart, and the one rule that outranks both — a couple has one experience
 * under way at a time (D-059).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun test(body: suspend TestScope.() -> Unit) = runTest(mainDispatcherRule.testDispatcher) { body() }

    private val logged = mutableListOf<AnalyticsEvent>()
    private val nights = FakeNightRepository()
    private val preferences = FakeAppPreferencesStore()
    private val media = FakeMediaRepository()

    private fun TestScope.home() = HomeViewModel(
        nights = nights,
        preferences = preferences,
        analytics = AnalyticsLogger { logged += it },
        media = media,
    ).also { vm ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
    }

    private fun TestScope.act(action: () -> Unit) {
        action()
        advanceUntilIdle()
    }

    @Test
    fun `one tap makes a whole night, and the screen opens it`() = test {
        val vm = home()

        act { vm.surprise() }

        assertThat(nights.calls).containsExactly("surprise")
        assertThat(vm.uiState.value.startedExperienceId).isEqualTo(nights.night.experienceId)
        assertThat(logged).containsExactly(AnalyticsEvent.SurpriseCreated)
        act(vm::onShown)
        assertThat(vm.uiState.value.startedExperienceId).isNull()
    }

    @Test
    fun `a mystery night is asked for as one`() = test {
        val vm = home()
        act { vm.surprise(mystery = true) }
        assertThat(nights.calls).containsExactly("surprise:mystery")
    }

    @Test
    fun `nothing starts while the couple already has something under way`() = test {
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
    }

    @Test
    fun `a night that cannot be built is said plainly, and nothing is left half-done`() = test {
        val vm = home()
        nights.surpriseResult = Outcome.Failure(AppError.Validation(HomeViewModel.NOT_ENOUGH_CONTENT))

        act { vm.surprise() }

        assertThat(vm.uiState.value.error).isEqualTo(HomeError.NOT_ENOUGH_CONTENT)
        assertThat(vm.uiState.value.making).isFalse()
        assertThat(vm.uiState.value.startedExperienceId).isNull()
        act(vm::dismissError)
        assertThat(vm.uiState.value.error).isNull()

        nights.surpriseResult = Outcome.Failure(AppError.Network())
        act { vm.surprise() }
        assertThat(vm.uiState.value.error).isEqualTo(HomeError.OFFLINE)
    }

    @Test
    fun `a night the partner planned waits on this screen, and opening it says so`() = test {
        nights.plannedNights.value = listOf(
            NightPlan(experienceId = "planned_night", mine = false, chapterCount = 5, opened = false),
        )
        val vm = home()

        assertThat(vm.uiState.value.waitingForMe?.chapterCount).isEqualTo(5)
        assertThat(vm.uiState.value.plannedByMe).isNull()

        act(vm::openWaiting)

        assertThat(nights.calls).containsExactly("openPlan:planned_night")
        assertThat(vm.uiState.value.startedExperienceId).isEqualTo("planned_night")
    }

    @Test
    fun `a night planned on this phone is only ever a shape, and says whether it landed`() = test {
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
    fun `the switch between the same room and apart is kept on the phone`() = test {
        val vm = home()
        assertThat(vm.uiState.value.mode).isEqualTo(Mode.TOGETHER)

        act { vm.setMode(Mode.APART) }

        assertThat(vm.uiState.value.mode).isEqualTo(Mode.APART)
        assertThat(preferences.state.value.mode).isEqualTo(Mode.APART)
    }

    @Test
    fun `a voice note they sent waits here until it is opened`() = test {
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
