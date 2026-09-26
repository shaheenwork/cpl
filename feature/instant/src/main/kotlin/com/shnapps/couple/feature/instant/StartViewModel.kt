package com.shnapps.couple.feature.instant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shnapps.couple.core.data.instant.InstantPlayRepository
import com.shnapps.couple.core.datastore.AppPreferencesStore
import com.shnapps.couple.core.model.Heat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Immutable screen state (BUILD_PROMPT.md §4.1; enforced by :architecture). */
data class StartUiState(
    val heat: Heat = Heat.DEFAULT,
    val started: Boolean = false,
)

/**
 * First launch, which is now one screen (REDESIGN_PROMPT.md §5.1).
 *
 * The 18+ attestation is still the gate §3.1 requires, and the screen still refuses to advance
 * without it. What has gone is everything that used to stand between the attestation and something
 * to do: there is no account here, so there is nothing to write to a server and nothing to wait for.
 *
 * The record is kept on the phone and reconciled the moment an account exists — a local flag can
 * never pass the gate on a signed-in device, because the splash checks the **server** record there
 * (D-027 and §3.1 both hold).
 */
@HiltViewModel
class StartViewModel @Inject constructor(
    private val instant: InstantPlayRepository,
    private val preferences: AppPreferencesStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StartUiState())
    val uiState: StateFlow<StartUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(heat = preferences.preferences.first().heat) }
        }
    }

    /** Changed with one thumb, and remembered: this is the only setting the app asks for. */
    fun setHeat(heat: Heat) {
        _uiState.update { it.copy(heat = heat) }
        viewModelScope.launch { instant.setHeat(heat) }
    }

    /**
     * Confirmed, and through.
     *
     * The heat is written before the flag, so a person who gets exactly this far and closes the app
     * still finds their answer waiting rather than the default.
     */
    fun confirmAge() {
        if (_uiState.value.started) return
        viewModelScope.launch {
            instant.setHeat(_uiState.value.heat)
            preferences.setAgeConfirmedHint(true)
            _uiState.update { it.copy(started = true) }
        }
    }
}
