package com.shnapps.couple.feature.instant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shnapps.couple.core.data.instant.InstantPlayRepository
import com.shnapps.couple.core.engine.GameNight
import com.shnapps.couple.core.engine.NightCard
import com.shnapps.couple.core.engine.NightLength
import com.shnapps.couple.core.engine.NightPhase
import com.shnapps.couple.core.model.Intensity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Immutable screen state (BUILD_PROMPT.md §4.1; enforced by :architecture). */
data class GameNightUiState(
    /** Null until they have chosen a length: the screen opens on the choice, not on a card. */
    val night: GameNight? = null,
    val length: NightLength = NightLength.DEFAULT,
    val tier: Intensity = Intensity.DEFAULT,
    val position: Int = 0,
    val planning: Boolean = false,
    /** The plan was asked for and the content could not fill it. Said, never shown as blank. */
    val nothingToPlan: Boolean = false,
) {
    val card: NightCard? get() = night?.cardAt(position)
    val total: Int get() = night?.size ?: 0
    val finished: Boolean get() = night != null && position >= total

    /** Which part of the evening this is, for the card's own eyebrow. */
    val phase: NightPhase? get() = card?.phase

    /** How many cards are left, for the one line the screen says about progress. */
    val remaining: Int get() = (total - position).coerceAtLeast(0)
}

/**
 * Game Night (the brief's game 12), played.
 *
 * The plan is made once, by the engine, and then walked. Nothing is re-decided mid-evening: the
 * phases, the climb and the aftercare card at the end were all settled when the length was chosen,
 * which is what makes it a guided evening rather than a deck with labels on it.
 *
 * Every card that goes past is recorded as seen, exactly as a single card is (D-124), so a night
 * and an evening of cards draw from the same memory and neither repeats the other.
 */
@HiltViewModel
class GameNightViewModel @Inject constructor(
    private val instant: InstantPlayRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GameNightUiState())
    val uiState: StateFlow<GameNightUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            instant.tier.collect { tier -> _uiState.update { it.copy(tier = tier) } }
        }
    }

    fun setLength(length: NightLength) = _uiState.update { it.copy(length = length) }

    /** Plans the evening. Called again by "plan another", which is a new evening, not a rewind. */
    fun plan() {
        _uiState.update { it.copy(planning = true, nothingToPlan = false) }
        viewModelScope.launch {
            val night = instant.planNight(_uiState.value.length)
            _uiState.update {
                it.copy(
                    night = night.takeIf { planned -> planned.size > 0 },
                    position = 0,
                    planning = false,
                    nothingToPlan = night.size == 0,
                )
            }
        }
    }

    /** Done with this one. Recorded as seen, then on to the next part of the evening. */
    fun next() {
        val state = _uiState.value
        val card = state.card?.card
        val size = state.night?.size ?: return
        if (card != null) viewModelScope.launch { instant.markPlayed(card, size) }
        _uiState.update { it.copy(position = it.position + 1) }
    }

    /** Back to the choice, with the evening let go of. */
    fun again() = _uiState.update { GameNightUiState(length = it.length, tier = it.tier) }
}
