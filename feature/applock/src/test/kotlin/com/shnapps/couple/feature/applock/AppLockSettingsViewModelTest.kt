package com.shnapps.couple.feature.applock

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.common.FakeClock
import com.shnapps.couple.core.model.AppLockMode
import com.shnapps.couple.core.security.AppLockManager
import com.shnapps.couple.core.security.PinHasher
import com.shnapps.couple.core.testing.FakeAppPreferencesStore
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
 * Turning the app lock off, which had no way to happen at all (D-146).
 *
 * The lock could be switched on from Settings and then never off again: the only way back was
 * clearing the app's data. What is worth holding is that getting out of it takes the PIN — an
 * unlocked phone in someone else's hands is the case the lock is for — and that the sensor, which
 * can only ever add a way in beside the PIN, is the one change that asks for nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppLockSettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val store = FakeAppPreferencesStore()
    private val manager = AppLockManager(store, PinHasher(), FakeClock())

    private fun test(body: suspend TestScope.() -> Unit) = runTest(mainDispatcherRule.testDispatcher) { body() }

    private fun TestScope.screen() = AppLockSettingsViewModel(manager).also { vm ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
    }

    private fun TestScope.act(action: () -> Unit) {
        action()
        advanceUntilIdle()
    }

    private suspend fun lockWith(pin: String) = manager.setPin(pin)

    @Test
    fun `it says nothing about the lock until the store has said`() = test {
        // Never a guess on the first frame (D-027): a screen that drew "off" for a lock that is on
        // would offer to turn on something already running.
        val vm = AppLockSettingsViewModel(manager)

        assertThat(vm.uiState.value.mode).isNull()
    }

    @Test
    fun `it reports whether the lock is on`() = test {
        val vm = screen()
        assertThat(vm.uiState.value.mode).isEqualTo(AppLockMode.OFF)

        lockWith("2468")
        advanceUntilIdle()

        assertThat(vm.uiState.value.mode).isEqualTo(AppLockMode.PIN)
    }

    @Test
    fun `turning it off asks for the pin first, and does nothing until it is right`() = test {
        lockWith("2468")
        val vm = screen()

        act { vm.ask(LockGate.TURN_OFF) }
        assertThat(vm.uiState.value.asking).isEqualTo(LockGate.TURN_OFF)
        // Asking is not doing: the lock is exactly as it was.
        assertThat(store.state.value.lockMode).isEqualTo(AppLockMode.PIN)

        act { vm.onPin("1357") }
        act(vm::submit)

        assertThat(vm.uiState.value.error).isEqualTo("That PIN didn't match.")
        assertThat(vm.uiState.value.pin).isEmpty()
        assertThat(vm.uiState.value.asking).isEqualTo(LockGate.TURN_OFF)
        assertThat(store.state.value.lockMode).isEqualTo(AppLockMode.PIN)
    }

    @Test
    fun `the right pin turns the lock off, and forgets it`() = test {
        lockWith("2468")
        val vm = screen()
        act { vm.ask(LockGate.TURN_OFF) }
        act { vm.onPin("2468") }

        act(vm::submit)

        assertThat(store.state.value.lockMode).isEqualTo(AppLockMode.OFF)
        assertThat(manager.hasPinFallback()).isFalse()
        assertThat(vm.uiState.value.mode).isEqualTo(AppLockMode.OFF)
        assertThat(vm.uiState.value.asking).isNull()
        assertThat(vm.uiState.value.pin).isEmpty()
    }

    @Test
    fun `a pin that is too short cannot be submitted`() = test {
        lockWith("2468")
        val vm = screen()
        act { vm.ask(LockGate.TURN_OFF) }

        act { vm.onPin("24") }

        assertThat(vm.uiState.value.canSubmit).isFalse()
        act(vm::submit)
        assertThat(store.state.value.lockMode).isEqualTo(AppLockMode.PIN)
    }

    @Test
    fun `changing the pin asks for the current one, then opens setup`() = test {
        lockWith("2468")
        val vm = screen()
        act { vm.ask(LockGate.CHANGE_PIN) }
        act { vm.onPin("2468") }

        act(vm::submit)

        assertThat(vm.uiState.value.openSetup).isTrue()
        // Nothing has changed yet: the new PIN is chosen in setup, and replaces this one only there.
        assertThat(store.state.value.lockMode).isEqualTo(AppLockMode.PIN)
        assertThat(manager.verifyPin("2468")).isTrue()
        act(vm::onSetupOpened)
        assertThat(vm.uiState.value.openSetup).isFalse()
    }

    @Test
    fun `keeping it as it is closes the question and forgets what was typed`() = test {
        lockWith("2468")
        val vm = screen()
        act { vm.ask(LockGate.TURN_OFF) }
        act { vm.onPin("24") }

        act(vm::cancel)

        assertThat(vm.uiState.value.asking).isNull()
        assertThat(vm.uiState.value.pin).isEmpty()
        assertThat(store.state.value.lockMode).isEqualTo(AppLockMode.PIN)
    }

    @Test
    fun `the sensor goes on and off without asking, and the pin survives both`() = test {
        lockWith("2468")
        val vm = screen()

        act { vm.setBiometric(true) }
        assertThat(vm.uiState.value.mode).isEqualTo(AppLockMode.BIOMETRIC)

        act { vm.setBiometric(false) }

        assertThat(vm.uiState.value.mode).isEqualTo(AppLockMode.PIN)
        assertThat(manager.verifyPin("2468")).isTrue()
    }

    @Test
    fun `turning it off from the sensor's mode works the same way`() = test {
        lockWith("2468")
        manager.enableBiometric()
        val vm = screen()
        act { vm.ask(LockGate.TURN_OFF) }
        act { vm.onPin("2468") }

        act(vm::submit)

        assertThat(store.state.value.lockMode).isEqualTo(AppLockMode.OFF)
    }
}
