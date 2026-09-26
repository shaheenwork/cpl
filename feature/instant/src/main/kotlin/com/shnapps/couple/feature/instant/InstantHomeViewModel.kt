package com.shnapps.couple.feature.instant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shnapps.couple.core.data.instant.InstantPlayRepository
import com.shnapps.couple.core.model.Game
import com.shnapps.couple.core.model.Heat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Immutable screen state (BUILD_PROMPT.md §4.1; enforced by :architecture). */
data class InstantHomeUiState(
    val games: List<Game> = emptyList(),
    val heat: Heat = Heat.DEFAULT,
)

/**
 * Home for a couple the server knows nothing about (REDESIGN_PROMPT.md §5.2).
 *
 * The games come from the content in the app, so the list is right before anything has loaded from
 * anywhere. The heat is the only setting, and changing it here changes what every game deals.
 */
@HiltViewModel
class InstantHomeViewModel @Inject constructor(
    private val instant: InstantPlayRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InstantHomeUiState())
    val uiState: StateFlow<InstantHomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(instant.games, instant.heat) { games, heat -> games to heat }.collect { (games, heat) ->
                _uiState.update { it.copy(games = games, heat = heat) }
            }
        }
    }

    fun setHeat(heat: Heat) {
        _uiState.update { it.copy(heat = heat) }
        viewModelScope.launch { instant.setHeat(heat) }
    }
}
