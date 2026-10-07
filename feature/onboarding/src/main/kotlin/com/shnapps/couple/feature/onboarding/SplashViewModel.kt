package com.shnapps.couple.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shnapps.couple.core.data.identity.Identity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    /** Nobody on this phone has said they are 18 yet. The one screen before the app (§3.1). */
    data object AgeGate : StartDestination

    data object Home : StartDestination
}

data class SplashUiState(val destination: StartDestination = StartDestination.Loading)

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val identity: Identity,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SplashUiState())
    val uiState: StateFlow<SplashUiState> = _uiState.asStateFlow()

    init {
        resolve()
    }

    /**
     * Two destinations, and one fact decides between them.
     *
     * There is no session to validate, no profile to fetch and no welcome to have seen: v2 has no
     * accounts at all. The only thing the app can know on open is whether somebody on this phone has
     * already said they are eighteen — kept on the phone that said it, because there is nowhere else
     * for it to live and nobody else it would be true of.
     */
    private fun resolve() {
        viewModelScope.launch {
            _uiState.value = SplashUiState(
                if (identity.hasConfirmedAge()) StartDestination.Home else StartDestination.AgeGate,
            )
        }
    }
}
