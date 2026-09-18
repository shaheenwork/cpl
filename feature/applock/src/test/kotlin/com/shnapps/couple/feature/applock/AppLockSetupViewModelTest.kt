package com.shnapps.couple.feature.applock

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.common.FakeClock
import com.shnapps.couple.core.model.AppLockMode
import com.shnapps.couple.core.security.AppLockManager
import com.shnapps.couple.core.security.LockState
import com.shnapps.couple.core.security.PinHasher
import com.shnapps.couple.core.testing.FakeAppPreferencesStore
import com.shnapps.couple.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * Turning the app lock on (BUILD_PROMPT.md §3.4).
 *
 * A mistake here is not recoverable from inside the app — there is no "forgot PIN" path,
 * because the lock is local and any reset would also be a way around it. So the flow has
 * to make the PIN hard to get wrong, and must never leave someone locked out of the
 * screen they are using.
 */
class AppLockSetupViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val store = FakeAppPreferencesStore()
    private val hasher = PinHasher()
    private val manager = AppLockManager(store, hasher, FakeClock())

    private fun viewModel() = AppLockSetupViewModel(manager)

    @Test
    fun `a short pin cannot continue`() {
        val vm = viewModel()
        vm.onDigits("12")
        assertThat(vm.uiState.value.canContinue).isFalse()
    }

    @Test
    fun `non-digits are ignored`() {
        val vm = viewModel()
        vm.onDigits("12ab")
        assertThat(vm.uiState.value.pin).isEmpty()
    }

    @Test
    fun `a mismatched confirmation starts over from the first entry`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        vm.onDigits("2468")
        vm.onContinue()
        vm.onDigits("2469")
        vm.onContinue()
        advanceUntilIdle()

        val state = vm.uiState.value
        // Back to the start, with both entries cleared. Letting them retype only the second
        // field would lock in the first entry even if that was the typo.
        assertThat(state.step).isEqualTo(LockSetupStep.EnterPin)
        assertThat(state.pin).isEmpty()
        assertThat(state.confirmation).isEmpty()
        assertThat(state.errorMessage).isNotNull()
        assertThat(store.state.value.lockMode).isEqualTo(AppLockMode.OFF)
    }

    @Test
    fun `matching pins turn the lock on and offer biometrics`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        vm.onDigits("2468")
        vm.onContinue()
        vm.onDigits("2468")
        vm.onContinue()
        advanceUntilIdle()

        assertThat(vm.uiState.value.step).isEqualTo(LockSetupStep.OfferBiometric)
        assertThat(store.state.value.lockMode).isEqualTo(AppLockMode.PIN)
        assertThat(manager.verifyPin("2468")).isTrue()
    }

    @Test
    fun `setting up the lock does not lock the user out`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        vm.onDigits("2468")
        vm.onContinue()
        vm.onDigits("2468")
        vm.onContinue()
        advanceUntilIdle()

        assertThat(manager.lockState.first()).isEqualTo(LockState.Unlocked)
    }

    @Test
    fun `the pin itself is never stored`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        vm.onDigits("24681357")
        vm.onContinue()
        vm.onDigits("24681357")
        vm.onContinue()
        advanceUntilIdle()

        val saved = store.state.value
        assertThat(saved.pinHash).isNotNull()
        assertThat(saved.pinHash).doesNotContain("24681357")
        assertThat(saved.pinSalt).doesNotContain("24681357")
        // And the form forgets it once saved.
        assertThat(vm.uiState.value.pin).isEmpty()
        assertThat(vm.uiState.value.confirmation).isEmpty()
    }

    @Test
    fun `choosing biometrics keeps the pin as the fallback`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        vm.onDigits("2468")
        vm.onContinue()
        vm.onDigits("2468")
        vm.onContinue()
        advanceUntilIdle()

        vm.enableBiometric()
        advanceUntilIdle()

        assertThat(vm.uiState.value.step).isEqualTo(LockSetupStep.Done)
        assertThat(store.state.value.lockMode).isEqualTo(AppLockMode.BIOMETRIC)
        assertThat(manager.hasPinFallback()).isTrue()
    }

    @Test
    fun `skipping biometrics leaves a pin lock`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        vm.onDigits("2468")
        vm.onContinue()
        vm.onDigits("2468")
        vm.onContinue()
        advanceUntilIdle()

        vm.skipBiometric()

        assertThat(vm.uiState.value.step).isEqualTo(LockSetupStep.Done)
        assertThat(store.state.value.lockMode).isEqualTo(AppLockMode.PIN)
    }
}
