package com.shnapps.couple.feature.applock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shnapps.couple.core.model.AppLockMode
import com.shnapps.couple.core.security.AppLockManager
import com.shnapps.couple.core.security.BiometricResult
import com.shnapps.couple.core.security.LockState
import com.shnapps.couple.core.security.PinRules
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppLockUiState(
    val mode: AppLockMode = AppLockMode.OFF,
    val pin: String = "",
    val unlocked: Boolean = false,
    val showPinEntry: Boolean = false,
    val hasPinFallback: Boolean = false,
    val errorMessage: String? = null,
    val attempts: Int = 0,
    /** After a lockout the sensor refuses every attempt, so retrying is a dead end. */
    val biometricLockedOut: Boolean = false,
) {
    val canSubmitPin: Boolean get() = PinRules.isValid(pin)
}

@HiltViewModel
class AppLockViewModel @Inject constructor(
    private val appLockManager: AppLockManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppLockUiState())
    val uiState: StateFlow<AppLockUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Never a dead end. The lock screen can be on screen while the app is in fact
            // unlocked — the lock was switched off, or it was shown before the lock state was
            // known. Whenever the manager says unlocked, leave.
            appLockManager.lockState.first { it == LockState.Unlocked }
            _uiState.update { it.copy(unlocked = true) }
        }
        viewModelScope.launch {
            val mode = appLockManager.lockMode.first()
            val hasPin = appLockManager.hasPinFallback()
            _uiState.update {
                it.copy(
                    mode = mode,
                    hasPinFallback = hasPin,
                    // With no biometric configured there is nothing to prompt for, so the
                    // PIN pad is the screen rather than a fallback behind a dismissal.
                    showPinEntry = mode == AppLockMode.PIN,
                )
            }
        }
    }

    fun onPinChange(value: String) {
        if (value.length > PinRules.MAX_LENGTH || !value.all { it.isDigit() }) return
        _uiState.update { it.copy(pin = value, errorMessage = null) }
    }

    fun showPinEntry() = _uiState.update { it.copy(showPinEntry = true, errorMessage = null) }

    fun submitPin() {
        val pin = _uiState.value.pin
        if (!PinRules.isValid(pin)) return

        viewModelScope.launch {
            if (appLockManager.verifyPin(pin)) {
                _uiState.update { it.copy(unlocked = true, pin = "", errorMessage = null) }
            } else {
                _uiState.update {
                    it.copy(
                        pin = "",
                        attempts = it.attempts + 1,
                        errorMessage = "That PIN didn't match.",
                    )
                }
            }
        }
    }

    /**
     * Records the outcome of a biometric prompt the screen ran.
     *
     * A cancelled prompt is **not** an error and must not lock anyone out: it usually
     * means the user chose the PIN instead. A lockout is also not a dead end — the PIN
     * fallback exists precisely so a failed sensor never strands someone outside their
     * own app (§3.4).
     */
    fun onBiometricResult(result: BiometricResult) {
        when (result) {
            BiometricResult.Success -> {
                appLockManager.markUnlocked()
                _uiState.update { it.copy(unlocked = true, errorMessage = null) }
            }

            BiometricResult.Cancelled ->
                _uiState.update { it.copy(showPinEntry = it.hasPinFallback) }

            BiometricResult.LockedOut -> _uiState.update {
                it.copy(
                    showPinEntry = true,
                    biometricLockedOut = true,
                    errorMessage = if (it.hasPinFallback) {
                        "Too many tries. Use your PIN."
                    } else {
                        "Too many tries. Wait a moment and try again."
                    },
                )
            }

            BiometricResult.Unavailable -> _uiState.update {
                it.copy(
                    showPinEntry = true,
                    errorMessage = if (it.hasPinFallback) null else "Biometrics aren't available.",
                )
            }

            is BiometricResult.Failed -> _uiState.update {
                it.copy(showPinEntry = it.hasPinFallback, errorMessage = result.message)
            }
        }
    }
}
