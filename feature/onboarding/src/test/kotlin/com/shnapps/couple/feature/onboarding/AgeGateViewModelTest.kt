package com.shnapps.couple.feature.onboarding

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.testing.FakeAuthRepository
import com.shnapps.couple.core.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * The 18+ gate (BUILD_PROMPT.md §3.1).
 *
 * The property that matters most: the gate does **not** advance on a failed write. The
 * whole point is that the attestation record exists, so letting someone through when it
 * did not save would leave an account with no record and no prompt to fix it.
 */
class AgeGateViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository().apply { user.value = FakeAuthRepository.DEFAULT_USER }

    @Test
    fun `confirming records the attestation and advances`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = AgeGateViewModel(auth)

        viewModel.confirm()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.confirmed).isTrue()
        assertThat(auth.serverAgeConfirmed).isTrue()
        assertThat(auth.confirmAgeCalls).isEqualTo(1)
    }

    @Test
    fun `a failed write keeps the user on the gate`() = runTest(mainDispatcherRule.testDispatcher) {
        auth.confirmAgeResult = Outcome.Failure(AppError.Network())
        val viewModel = AgeGateViewModel(auth)

        viewModel.confirm()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.confirmed).isFalse()
        assertThat(state.isSubmitting).isFalse()
        assertThat(state.errorMessage).isNotNull()
        assertThat(auth.serverAgeConfirmed).isFalse()
    }

    @Test
    fun `a permission failure also keeps the user on the gate`() = runTest(mainDispatcherRule.testDispatcher) {
        auth.confirmAgeResult = Outcome.Failure(AppError.PermissionDenied())
        val viewModel = AgeGateViewModel(auth)

        viewModel.confirm()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.confirmed).isFalse()
    }

    @Test
    fun `a signed-out user cannot confirm`() = runTest(mainDispatcherRule.testDispatcher) {
        auth.user.value = null
        val viewModel = AgeGateViewModel(auth)

        viewModel.confirm()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.confirmed).isFalse()
        assertThat(viewModel.uiState.value.errorMessage).isNotNull()
    }

    @Test
    fun `retrying after a failure can succeed`() = runTest(mainDispatcherRule.testDispatcher) {
        auth.confirmAgeResult = Outcome.Failure(AppError.Network())
        val viewModel = AgeGateViewModel(auth)
        viewModel.confirm()
        advanceUntilIdle()

        auth.confirmAgeResult = Outcome.Success(Unit)
        viewModel.confirm()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.confirmed).isTrue()
        assertThat(viewModel.uiState.value.errorMessage).isNull()
    }

    @Test
    fun `a double tap writes the attestation once`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = AgeGateViewModel(auth)

        viewModel.confirm()
        viewModel.confirm()
        advanceUntilIdle()

        assertThat(auth.confirmAgeCalls).isEqualTo(1)
    }
}
