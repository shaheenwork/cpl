package com.shnapps.couple.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

/** Immutable screen state (BUILD_PROMPT.md §4.1; enforced by :architecture). */
data class AgeGateUiState(
    val isSubmitting: Boolean = false,
    val confirmed: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class AgeGateViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AgeGateUiState())
    val uiState: StateFlow<AgeGateUiState> = _uiState.asStateFlow()

    /**
     * Writes the attestation, then advances.
     *
     * The screen does **not** advance optimistically. If the write fails the user stays
     * here, because the whole point of the gate is that the record exists (§3.1) — letting
     * them through on a failed write would leave an account with no attestation and no
     * prompt to fix it.
     */
    fun confirm() {
        if (_uiState.value.isSubmitting) return
        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }

        viewModelScope.launch {
            when (val result = authRepository.confirmAge()) {
                is Outcome.Success ->
                    _uiState.update { it.copy(isSubmitting = false, confirmed = true) }

                is Outcome.Failure ->
                    _uiState.update {
                        it.copy(isSubmitting = false, errorMessage = result.error.toMessage())
                    }
            }
        }
    }
}

private fun AppError.toMessage(): String = when (this) {
    is AppError.Network -> "No connection. Check your signal and try again."
    is AppError.Unauthenticated -> "Something went wrong with your session. Try signing in again."
    is AppError.PermissionDenied -> "We couldn't save that. Try again in a moment."
    else -> "That didn't save. Try again."
}
