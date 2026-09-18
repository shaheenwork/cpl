package com.shnapps.couple.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shnapps.couple.core.analytics.AnalyticsEvent
import com.shnapps.couple.core.analytics.AnalyticsLogger
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AuthMode { SignIn, SignUp }

data class AuthUiState(
    val mode: AuthMode = AuthMode.SignIn,
    val email: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val resetEmailSent: Boolean = false,
    val signedIn: Boolean = false,
) {
    val canSubmit: Boolean
        get() = !isSubmitting && email.isNotBlank() && password.length >= MIN_PASSWORD_LENGTH

    companion object {
        const val MIN_PASSWORD_LENGTH = 8
    }
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val analytics: AnalyticsLogger,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) =
        _uiState.update { it.copy(email = value, errorMessage = null) }

    fun onPasswordChange(value: String) =
        _uiState.update { it.copy(password = value, errorMessage = null) }

    fun toggleMode() = _uiState.update {
        it.copy(
            mode = if (it.mode == AuthMode.SignIn) AuthMode.SignUp else AuthMode.SignIn,
            errorMessage = null,
        )
    }

    fun submit() {
        val state = _uiState.value
        if (!state.canSubmit) return
        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }

        viewModelScope.launch {
            val result = when (state.mode) {
                AuthMode.SignIn -> authRepository.signIn(state.email, state.password)
                AuthMode.SignUp -> authRepository.signUp(state.email, state.password)
            }

            when (result) {
                is Outcome.Success -> {
                    // Count only. The email address is never an analytics parameter (§17.3).
                    analytics.log(AnalyticsEvent.AppOpen)
                    _uiState.update { it.copy(isSubmitting = false, signedIn = true) }
                }

                is Outcome.Failure -> _uiState.update {
                    it.copy(isSubmitting = false, errorMessage = result.error.toMessage(state.mode))
                }
            }
        }
    }

    /**
     * Sends a reset email, and reports success either way.
     *
     * Firebase distinguishes "no such account" from other failures; surfacing that here
     * would turn this form into a way to test whether a given address has an account on
     * *this* app — which for this product is itself sensitive.
     */
    fun sendPasswordReset() {
        val email = _uiState.value.email
        if (email.isBlank() || _uiState.value.isSubmitting) return
        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }

        viewModelScope.launch {
            authRepository.sendPasswordReset(email)
            _uiState.update { it.copy(isSubmitting = false, resetEmailSent = true) }
        }
    }

    fun dismissResetConfirmation() = _uiState.update { it.copy(resetEmailSent = false) }
}

private fun AppError.toMessage(mode: AuthMode): String = when (this) {
    is AppError.Network -> "No connection. Check your signal and try again."

    // Sign-in failures are deliberately not specific: distinguishing "no account" from
    // "wrong password" leaks whether an address is registered here.
    is AppError.Unauthenticated -> "That email and password don't match."

    // Sign-up is the one place existence cannot be fully hidden: Firebase rejects a
    // duplicate email synchronously. The copy stays soft rather than confirming "that
    // email has an account", and the residual risk is documented in DECISIONS.md D-012.
    is AppError.Conflict -> when (mode) {
        AuthMode.SignUp -> "We couldn't create that account. If you've been here before, try signing in."
        AuthMode.SignIn -> "Something went wrong. Try again."
    }

    is AppError.Validation -> "Pick a longer password — at least ${AuthUiState.MIN_PASSWORD_LENGTH} characters."
    is AppError.RateLimited -> "Too many attempts. Wait a minute and try again."
    else -> "Something went wrong. Try again."
}
