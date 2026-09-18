package com.shnapps.couple.feature.auth

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.analytics.AnalyticsEvent
import com.shnapps.couple.core.analytics.AnalyticsLogger
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.testing.FakeAuthRepository
import com.shnapps.couple.core.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * Sign in, sign up and reset (BUILD_PROMPT.md §7).
 *
 * The privacy properties get the most attention. For this product, learning that a given
 * email has an account here is itself sensitive — "is my partner using a secret intimacy
 * app?" — so the sign-in and reset flows must answer identically whether or not the
 * account exists.
 */
class AuthViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository()
    private val logged = mutableListOf<AnalyticsEvent>()
    private val analytics = AnalyticsLogger { logged += it }

    private fun viewModel() = AuthViewModel(auth, analytics)

    @Test
    fun `cannot submit with a short password`() {
        val vm = viewModel()
        vm.onEmailChange("a@b.co")
        vm.onPasswordChange("short")
        assertThat(vm.uiState.value.canSubmit).isFalse()
    }

    @Test
    fun `cannot submit with a blank email`() {
        val vm = viewModel()
        vm.onEmailChange("   ")
        vm.onPasswordChange("long-enough-password")
        assertThat(vm.uiState.value.canSubmit).isFalse()
    }

    @Test
    fun `a successful sign in signs the user in`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel().apply {
            onEmailChange("a@b.co")
            onPasswordChange("long-enough-password")
        }

        vm.submit()
        advanceUntilIdle()

        assertThat(vm.uiState.value.signedIn).isTrue()
        assertThat(vm.uiState.value.errorMessage).isNull()
    }

    @Test
    fun `a failed sign in keeps the user signed out with a message`() = runTest(mainDispatcherRule.testDispatcher) {
        auth.signInResult = Outcome.Failure(AppError.Unauthenticated())
        val vm = viewModel().apply {
            onEmailChange("a@b.co")
            onPasswordChange("long-enough-password")
        }

        vm.submit()
        advanceUntilIdle()

        assertThat(vm.uiState.value.signedIn).isFalse()
        assertThat(vm.uiState.value.isSubmitting).isFalse()
        assertThat(vm.uiState.value.errorMessage).isNotNull()
    }

    @Test
    fun `sign-in failure copy never reveals whether the account exists`() =
        runTest(mainDispatcherRule.testDispatcher) {
            auth.signInResult = Outcome.Failure(AppError.Unauthenticated())
            val vm = viewModel().apply {
                onEmailChange("someone@example.com")
                onPasswordChange("long-enough-password")
            }

            vm.submit()
            advanceUntilIdle()

            val message = vm.uiState.value.errorMessage.orEmpty().lowercase()
            for (leak in listOf("no account", "not found", "doesn't exist", "does not exist", "unknown")) {
                assertThat(message).doesNotContain(leak)
            }
        }

    @Test
    fun `password reset reports success even for an address with no account`() =
        runTest(mainDispatcherRule.testDispatcher) {
            // The repository says the address is unknown. The screen must not.
            auth.resetResult = Outcome.Failure(AppError.NotFound())
            val vm = viewModel().apply { onEmailChange("nobody@example.com") }

            vm.sendPasswordReset()
            advanceUntilIdle()

            assertThat(vm.uiState.value.resetEmailSent).isTrue()
            assertThat(vm.uiState.value.errorMessage).isNull()
        }

    @Test
    fun `password reset responds identically whether or not the account exists`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val known = viewModel().apply { onEmailChange("known@example.com") }
            auth.resetResult = Outcome.Success(Unit)
            known.sendPasswordReset()
            advanceUntilIdle()

            val unknown = viewModel().apply { onEmailChange("unknown@example.com") }
            auth.resetResult = Outcome.Failure(AppError.NotFound())
            unknown.sendPasswordReset()
            advanceUntilIdle()

            assertThat(unknown.uiState.value.copy(email = "")).isEqualTo(known.uiState.value.copy(email = ""))
        }

    @Test
    fun `password reset does nothing without an email`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        vm.sendPasswordReset()
        advanceUntilIdle()

        assertThat(auth.resetCalls).isEqualTo(0)
        assertThat(vm.uiState.value.resetEmailSent).isFalse()
    }

    @Test
    fun `the email address never reaches analytics`() = runTest(mainDispatcherRule.testDispatcher) {
        val email = "private.person@example.com"
        val vm = viewModel().apply {
            onEmailChange(email)
            onPasswordChange("long-enough-password")
        }

        vm.submit()
        advanceUntilIdle()

        // §17.3: no user-authored text, no identifiers, in any analytics parameter.
        logged.forEach { event ->
            assertThat(event.params.values.map { it.toString() }).doesNotContain(email)
        }
    }

    @Test
    fun `switching mode clears a stale error`() = runTest(mainDispatcherRule.testDispatcher) {
        auth.signInResult = Outcome.Failure(AppError.Unauthenticated())
        val vm = viewModel().apply {
            onEmailChange("a@b.co")
            onPasswordChange("long-enough-password")
        }
        vm.submit()
        advanceUntilIdle()
        assertThat(vm.uiState.value.errorMessage).isNotNull()

        vm.toggleMode()

        assertThat(vm.uiState.value.errorMessage).isNull()
        assertThat(vm.uiState.value.mode).isEqualTo(AuthMode.SignUp)
    }
}
