package com.shnapps.couple.feature.applock

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.common.FakeClock
import com.shnapps.couple.core.datastore.AppPreferences
import com.shnapps.couple.core.model.AppLockMode
import com.shnapps.couple.core.security.AppLockManager
import com.shnapps.couple.core.security.BiometricResult
import com.shnapps.couple.core.security.PinHasher
import com.shnapps.couple.core.testing.FakeAppPreferencesStore
import com.shnapps.couple.core.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * The lock screen's decisions (BUILD_PROMPT.md §3.4).
 *
 * Uses a real [AppLockManager] over an in-memory preferences store rather than mocks, so
 * these tests exercise the actual unlock path (DECISIONS.md D-013).
 *
 * The recurring theme: a failed or dismissed biometric must never strand someone outside
 * their own app when a PIN fallback exists.
 */
class AppLockViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val hasher = PinHasher()
    private val salt = hasher.newSalt()
    private val preferences = FakeAppPreferencesStore(
        AppPreferences(
            lockMode = AppLockMode.BIOMETRIC,
            pinHash = hasher.hash("2468", salt),
            pinSalt = salt,
        ),
    )
    private val preferencesFlow = preferences.state
    private val manager = AppLockManager(preferences, hasher, FakeClock())

    private fun viewModel() = AppLockViewModel(manager).also {
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `biometric mode starts on the prompt, not the pin pad`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        assertThat(vm.uiState.value.mode).isEqualTo(AppLockMode.BIOMETRIC)
        assertThat(vm.uiState.value.showPinEntry).isFalse()
        assertThat(vm.uiState.value.hasPinFallback).isTrue()
    }

    @Test
    fun `pin mode starts on the pin pad`() = runTest(mainDispatcherRule.testDispatcher) {
        preferencesFlow.value = preferencesFlow.value.copy(lockMode = AppLockMode.PIN)
        assertThat(viewModel().uiState.value.showPinEntry).isTrue()
    }

    @Test
    fun `biometric success unlocks`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        vm.onBiometricResult(BiometricResult.Success)
        assertThat(vm.uiState.value.unlocked).isTrue()
    }

    @Test
    fun `dismissing the prompt falls back to the pin rather than an error`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val vm = viewModel()
            vm.onBiometricResult(BiometricResult.Cancelled)

            assertThat(vm.uiState.value.showPinEntry).isTrue()
            assertThat(vm.uiState.value.errorMessage).isNull()
            assertThat(vm.uiState.value.unlocked).isFalse()
        }

    @Test
    fun `a biometric lockout routes to the pin`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        vm.onBiometricResult(BiometricResult.LockedOut)

        assertThat(vm.uiState.value.showPinEntry).isTrue()
        assertThat(vm.uiState.value.unlocked).isFalse()
        // The retry button is hidden once the sensor has locked out.
        assertThat(vm.uiState.value.biometricLockedOut).isTrue()
    }

    @Test
    fun `no enrolled biometrics routes to the pin`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        vm.onBiometricResult(BiometricResult.Unavailable)
        assertThat(vm.uiState.value.showPinEntry).isTrue()
    }

    @Test
    fun `the correct pin unlocks`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        vm.showPinEntry()
        vm.onPinChange("2468")
        vm.submitPin()
        advanceUntilIdle()

        assertThat(vm.uiState.value.unlocked).isTrue()
        assertThat(vm.uiState.value.pin).isEmpty()
    }

    @Test
    fun `a wrong pin stays locked, clears the field and counts the attempt`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val vm = viewModel()
            vm.showPinEntry()
            vm.onPinChange("1111")
            vm.submitPin()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertThat(state.unlocked).isFalse()
            assertThat(state.pin).isEmpty()
            assertThat(state.attempts).isEqualTo(1)
            assertThat(state.errorMessage).isNotNull()
        }

    @Test
    fun `the pin field rejects non-digits and overlong input`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        vm.onPinChange("12a4")
        assertThat(vm.uiState.value.pin).isEmpty()

        vm.onPinChange("123456789")
        assertThat(vm.uiState.value.pin).isEmpty()

        vm.onPinChange("1234")
        assertThat(vm.uiState.value.pin).isEqualTo("1234")
    }
}
