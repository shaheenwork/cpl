package com.shnapps.couple.feature.applock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shnapps.couple.core.model.AppLockMode
import com.shnapps.couple.core.security.AppLockManager
import com.shnapps.couple.core.security.PinRules
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the PIN is being asked for: the two things that weaken or replace the lock (D-146). */
enum class LockGate { TURN_OFF, CHANGE_PIN }

data class AppLockSettingsUiState(
    /** Null until the store has said: never a guess drawn on the first frame (D-027). */
    val mode: AppLockMode? = null,
    /** Which change is waiting for the PIN, or null when nothing is being asked. */
    val asking: LockGate? = null,
    val pin: String = "",
    val checking: Boolean = false,
    val error: String? = null,
    /** The PIN was proved for a new one: the screen opens setup, then calls `onSetupOpened`. */
    val openSetup: Boolean = false,
) {
    val canSubmit: Boolean get() = asking != null && !checking && PinRules.isValid(pin)
}

/**
 * The app lock, once it exists (D-146).
 *
 * Turning it on was always a screen; turning it off was nothing at all, so a lock that had been set
 * could only be got rid of by clearing the app's data. Both ways to weaken it — switching it off and
 * choosing another PIN — ask for the current PIN first. The phone is unlocked when somebody reaches
 * this screen, and an unlocked phone in someone else's hands is exactly the case the lock is for.
 *
 * The fingerprint is the one change that asks for nothing: it only ever adds a way in beside the
 * PIN, or goes back to the PIN alone.
 */
@HiltViewModel
class AppLockSettingsViewModel @Inject constructor(
    private val appLock: AppLockManager,
) : ViewModel() {

    private val local = MutableStateFlow(AppLockSettingsUiState())

    val uiState: StateFlow<AppLockSettingsUiState> = combine(appLock.lockMode, local) { mode, state ->
        state.copy(mode = mode)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppLockSettingsUiState())

    fun ask(gate: LockGate) = local.update { it.copy(asking = gate, pin = "", error = null) }

    fun cancel() = local.update { it.copy(asking = null, pin = "", error = null, checking = false) }

    fun onPin(value: String) {
        if (value.length > PinRules.MAX_LENGTH || !value.all { it.isDigit() }) return
        local.update { it.copy(pin = value, error = null) }
    }

    /** Checks the PIN, and only then does what it was asked for. */
    fun submit() {
        val state = local.value
        val gate = state.asking ?: return
        if (!state.canSubmit) return
        local.update { it.copy(checking = true) }
        viewModelScope.launch {
            if (!appLock.verifyPin(state.pin)) {
                local.update { it.copy(checking = false, pin = "", error = "That PIN didn't match.") }
                return@launch
            }
            when (gate) {
                LockGate.TURN_OFF -> {
                    appLock.disableLock()
                    local.update { it.copy(asking = null, pin = "", checking = false) }
                }
                LockGate.CHANGE_PIN -> local.update {
                    it.copy(asking = null, pin = "", checking = false, openSetup = true)
                }
            }
        }
    }

    /** The sensor on, or back to the PIN alone. Never asks: it cannot take the lock away. */
    fun setBiometric(on: Boolean) {
        viewModelScope.launch {
            if (on) appLock.enableBiometric() else appLock.disableBiometric()
        }
    }

    fun onSetupOpened() = local.update { it.copy(openSetup = false) }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
