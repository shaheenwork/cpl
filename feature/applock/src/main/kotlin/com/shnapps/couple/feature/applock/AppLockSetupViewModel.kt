package com.shnapps.couple.feature.applock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shnapps.couple.core.security.AppLockManager
import com.shnapps.couple.core.security.PinRules
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LockSetupStep { EnterPin, ConfirmPin, OfferBiometric, Done }

data class AppLockSetupUiState(
    val step: LockSetupStep = LockSetupStep.EnterPin,
    val pin: String = "",
    val confirmation: String = "",
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
) {
    val canContinue: Boolean
        get() = !isSaving && when (step) {
            LockSetupStep.EnterPin -> PinRules.isValid(pin)
            LockSetupStep.ConfirmPin -> PinRules.isValid(confirmation)
            LockSetupStep.OfferBiometric, LockSetupStep.Done -> true
        }
}

/**
 * Turning the app lock on (BUILD_PROMPT.md §3.4, §57).
 *
 * A PIN is always set first, even when the user wants biometrics: the sensor can fail or
 * lock out, and a biometric-only lock would strand them outside their own app. Biometric
 * unlock is offered afterwards as a convenience on top of the PIN, never instead of it.
 *
 * The PIN is entered twice. A typo here is not recoverable from inside the app — the lock
 * is local, so there is no "forgot PIN" path that would not also be a way around it.
 */
@HiltViewModel
class AppLockSetupViewModel @Inject constructor(
    private val appLockManager: AppLockManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppLockSetupUiState())
    val uiState: StateFlow<AppLockSetupUiState> = _uiState.asStateFlow()

    fun onDigits(value: String) {
        if (value.length > PinRules.MAX_LENGTH || !value.all { it.isDigit() }) return
        _uiState.update {
            when (it.step) {
                LockSetupStep.EnterPin -> it.copy(pin = value, errorMessage = null)
                LockSetupStep.ConfirmPin -> it.copy(confirmation = value, errorMessage = null)
                else -> it
            }
        }
    }

    fun onContinue() {
        val state = _uiState.value
        if (!state.canContinue) return
        when (state.step) {
            LockSetupStep.EnterPin -> _uiState.update { it.copy(step = LockSetupStep.ConfirmPin) }
            LockSetupStep.ConfirmPin -> confirmAndSave(state)
            LockSetupStep.OfferBiometric, LockSetupStep.Done -> Unit
        }
    }

    private fun confirmAndSave(state: AppLockSetupUiState) {
        if (state.confirmation != state.pin) {
            // Start over rather than let them retype only the confirmation: if the first
            // entry was the typo, retyping the second to match it would lock in the wrong PIN.
            _uiState.update {
                AppLockSetupUiState(errorMessage = "Those didn't match. Let's start again.")
            }
            return
        }
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            appLockManager.setPin(state.pin)
            _uiState.update {
                it.copy(
                    isSaving = false,
                    pin = "",
                    confirmation = "",
                    step = LockSetupStep.OfferBiometric,
                )
            }
        }
    }

    fun enableBiometric() {
        viewModelScope.launch {
            appLockManager.enableBiometric()
            _uiState.update { it.copy(step = LockSetupStep.Done) }
        }
    }

    fun skipBiometric() = _uiState.update { it.copy(step = LockSetupStep.Done) }
}
