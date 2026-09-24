package com.shnapps.couple.feature.send

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.common.FakeClock
import com.shnapps.couple.core.model.ArcDayState
import com.shnapps.couple.core.testing.FakeArcRepository
import com.shnapps.couple.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * A few days of this (BUILD_PROMPT.md §13): the plan is visible from the start, a day opens when
 * it is due, and not before the day before it has been played.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArcViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun test(body: suspend TestScope.() -> Unit) = runTest(mainDispatcherRule.testDispatcher) { body() }

    private val clock = FakeClock(NOW)
    private val arcs = FakeArcRepository(clock)

    private fun TestScope.screen() = ArcViewModel(arcs, clock).also { vm ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
    }

    private fun TestScope.act(action: () -> Unit) {
        action()
        advanceUntilIdle()
    }

    @Test
    fun `the whole plan is visible, and only the first day is open`() = test {
        val vm = screen()

        act { vm.start(3) }

        val arc = vm.uiState.value.arc!!
        assertThat(arc.days.map { it.state })
            .containsExactly(ArcDayState.READY, ArcDayState.LOCKED, ArcDayState.LOCKED).inOrder()
        assertThat(arc.days.last().title).isEqualTo("The last night")
        // Nothing has been built for any of them yet.
        assertThat(arc.days.mapNotNull { it.experienceId }).isEmpty()
    }

    @Test
    fun `today's day opens into its night`() = test {
        val vm = screen()
        act { vm.start(3) }

        act { vm.openDay(vm.dayDue()!!) }

        assertThat(arcs.opened).containsExactly(0)
        assertThat(vm.uiState.value.openedExperienceId).isEqualTo("night_0")
        act(vm::onShown)
        assertThat(vm.uiState.value.openedExperienceId).isNull()
    }

    @Test
    fun `tomorrow stays shut today, and shut again until last night was played`() = test {
        val vm = screen()
        act { vm.start(3) }
        val tomorrow = vm.uiState.value.arc!!.days[1]

        act { vm.openDay(tomorrow) }
        assertThat(vm.uiState.value.error).isEqualTo(ArcError.NOT_YET)
        act(vm::dismissError)

        // A day later, with last night unfinished.
        clock.advanceBy(DAY_MS)
        val later = ArcViewModel(arcs, clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { later.uiState.collect {} }
        advanceUntilIdle()
        act { later.openDay(tomorrow) }
        assertThat(later.uiState.value.error).isEqualTo(ArcError.FINISH_YESTERDAY)
    }

    @Test
    fun `ending it leaves nothing running`() = test {
        val vm = screen()
        act { vm.start(7) }
        act(vm::end)
        assertThat(vm.uiState.value.arc).isNull()
    }

    private companion object {
        const val NOW = 1_790_000_000_000L
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
