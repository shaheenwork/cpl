package com.shnapps.couple.feature.send

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.FakeClock
import com.shnapps.couple.core.model.OpenWhenTrigger
import com.shnapps.couple.core.model.SurpriseState
import com.shnapps.couple.core.model.SurpriseType
import com.shnapps.couple.core.testing.FakeSurpriseRepository
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
 * Leaving something for later (BUILD_PROMPT.md §5.7, §13): what is left waits, what has arrived
 * opens, and what has not arrived says so rather than showing anything.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LaterViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun test(body: suspend TestScope.() -> Unit) = runTest(mainDispatcherRule.testDispatcher) { body() }

    private val surprises = FakeSurpriseRepository()
    private val clock = FakeClock(NOW)

    private fun TestScope.screen() = LaterViewModel(surprises, clock).also { vm ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
    }

    private fun TestScope.act(action: () -> Unit) {
        action()
        advanceUntilIdle()
    }

    @Test
    fun `something left for tonight is scheduled for a time, not a feeling`() = test {
        val vm = screen()

        act { vm.setTitle("For tonight") }
        act { vm.setText("something to think about") }
        act { vm.setWhen(LeaveWhen.TONIGHT) }
        act(vm::leave)

        val left = surprises.left.single()
        assertThat(left.type).isEqualTo(SurpriseType.SCHEDULED)
        assertThat(left.title).isEqualTo("For tonight")
        assertThat(left.trigger).isNull()
        assertThat(left.scheduledForMillis).isGreaterThan(NOW)
        assertThat(vm.uiState.value.leftIt).isTrue()
    }

    @Test
    fun `an open when is kept for a moment, with no time on it at all`() = test {
        val vm = screen()

        act { vm.setTitle("When you can't sleep") }
        act { vm.setText("read this") }
        act { vm.setWhen(LeaveWhen.WHEN_THEY) }
        act { vm.setTrigger(OpenWhenTrigger.CANT_SLEEP) }
        act(vm::leave)

        val left = surprises.left.single()
        assertThat(left.type).isEqualTo(SurpriseType.OPEN_WHEN)
        assertThat(left.trigger).isEqualTo(OpenWhenTrigger.CANT_SLEEP)
        assertThat(left.scheduledForMillis).isNull()
    }

    @Test
    fun `what has not arrived cannot be opened, and says so`() = test {
        val waiting = surprises.theyLeave(title = "For Friday")
        val vm = screen()

        // It is not offered as arrived, and asking anyway is refused.
        assertThat(vm.uiState.value.arrived).isEmpty()
        act { vm.openArrived(waiting) }
        assertThat(vm.uiState.value.error).isEqualTo(LaterError.NOT_YET)
        assertThat(vm.uiState.value.open).isNull()
    }

    @Test
    fun `what has arrived opens, and shows what was inside`() = test {
        val waiting = surprises.theyLeave(title = "For Friday", text = "here it is")
        val vm = screen()
        act { surprises.deliver(waiting.id) }

        assertThat(vm.uiState.value.arrived.map { it.id }).containsExactly(waiting.id)
        act { vm.openArrived(waiting) }

        assertThat(vm.uiState.value.open?.second?.text).isEqualTo("here it is")
        assertThat(surprises.items.value.single().state).isEqualTo(SurpriseState.OPENED)
        act(vm::close)
        assertThat(vm.uiState.value.open).isNull()
    }

    @Test
    fun `pulling a moment hands over the one kept for it`() = test {
        surprises.theyLeave(
            type = SurpriseType.OPEN_WHEN,
            title = "When you miss me",
            text = "I miss you too",
            trigger = OpenWhenTrigger.MISS_ME,
        )
        val vm = screen()

        assertThat(vm.uiState.value.moments).containsExactly(OpenWhenTrigger.MISS_ME)
        act { vm.pull(OpenWhenTrigger.MISS_ME) }

        assertThat(surprises.pulled).containsExactly(OpenWhenTrigger.MISS_ME)
        assertThat(vm.uiState.value.open?.second?.text).isEqualTo("I miss you too")
        // Nothing is kept for that moment any more.
        assertThat(vm.uiState.value.moments).isEmpty()
    }

    @Test
    fun `a moment with nothing kept for it says so plainly`() = test {
        val vm = screen()
        act { vm.pull(OpenWhenTrigger.NEED_A_LAUGH) }
        assertThat(vm.uiState.value.error).isEqualTo(LaterError.NOTHING_WAITING)
    }

    @Test
    fun `a countdown is kept, and can be dropped by either of them`() = test {
        val vm = screen()

        act { vm.addCountdown("Until we meet", 7) }

        val countdown = vm.uiState.value.countdowns.single()
        assertThat(countdown.title).isEqualTo("Until we meet")
        assertThat(countdown.targetAtMillis).isGreaterThan(NOW)
        act { vm.removeCountdown(countdown.id) }
        assertThat(vm.uiState.value.countdowns).isEmpty()
    }

    @Test
    fun `a failure is said plainly, and what was written is kept`() = test {
        val vm = screen()
        act { vm.setTitle("For tonight") }
        act { vm.setText("something") }
        surprises.failNext = AppError.Network()

        act(vm::leave)

        assertThat(vm.uiState.value.error).isEqualTo(LaterError.OFFLINE)
        assertThat(vm.uiState.value.title).isEqualTo("For tonight")
        assertThat(vm.uiState.value.leftIt).isFalse()
        act(vm::dismissError)
        act(vm::leave)
        assertThat(surprises.left).hasSize(1)
    }

    private companion object {
        const val NOW = 1_790_000_000_000L
    }
}
