package com.shnapps.couple.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shnapps.couple.core.data.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Where the app should send someone on open (BUILD_PROMPT.md §14.1).
 *
 * A closed set rather than a pile of booleans, so every path is exhaustive at the call
 * site and a new state cannot be silently unhandled.
 */
sealed interface StartDestination {
    data object Loading : StartDestination

    /** Not signed in, and nothing set up: age and heat, then a card (REDESIGN_PROMPT.md §5.1). */
    data object Start : StartDestination

    /** Not signed in, but already through the gate: straight back to playing. */
    data object InstantPlay : StartDestination

    /** Not signed in, and asking to be: the sign-in screen. */
    data object Auth : StartDestination

    /** Signed in, but with no 18+ record. Always re-prompted rather than assumed (§3.1). */
    data object AgeGate : StartDestination

    /** Signed in and confirmed, but has not seen the welcome beats. */
    data object Welcome : StartDestination

    /** Signed in, confirmed, not yet paired. */
    data object CoupleSetup : StartDestination

    data object Home : StartDestination
}

data class SplashUiState(val destination: StartDestination = StartDestination.Loading)

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SplashUiState())
    val uiState: StateFlow<SplashUiState> = _uiState.asStateFlow()

    init {
        resolve()
    }

    /**
     * Resolution order matters, and it is the order of the guarantees.
     *
     * The session is checked before anything else, because everything after it assumes a
     * session that works. The age gate is then checked against the **server** record, not the
     * local hint. The hint exists only to avoid a blank screen; it can never let someone past
     * the gate, because a device-local flag is exactly what an attacker or a curious teenager
     * would clear or forge.
     */
    private fun resolve() {
        viewModelScope.launch {
            val user = authRepository.authState.first()
            if (user == null) {
                // No account is no longer a dead end. Someone who has already said they are 18
                // goes back to where they were; someone new gets the one screen that asks.
                val confirmed = authRepository.ageConfirmedHint()
                _uiState.value = SplashUiState(
                    if (confirmed) StartDestination.InstantPlay else StartDestination.Start,
                )
                return@launch
            }

            // A stored session that is no longer real: the account went, the password changed
            // elsewhere, or the session was revoked. It is signed out here rather than carried
            // into the app, where every call would fail and a queued write would never land.
            if (!authRepository.validateSession()) {
                _uiState.value = SplashUiState(StartDestination.Auth)
                return@launch
            }

            if (!authRepository.hasConfirmedAge()) {
                _uiState.value = SplashUiState(StartDestination.AgeGate)
                return@launch
            }

            val profile = authRepository.currentProfile.first()
            _uiState.value = SplashUiState(
                when {
                    profile == null -> StartDestination.Welcome
                    profile.coupleId == null -> StartDestination.CoupleSetup
                    else -> StartDestination.Home
                },
            )
        }
    }
}
