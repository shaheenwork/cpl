package com.shnapps.couple.feature.instant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shnapps.couple.core.data.instant.InstantPlayRepository
import com.shnapps.couple.core.engine.InstantCard
import com.shnapps.couple.core.engine.InstantDeck
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
data class InstantPlayUiState(
    val card: InstantCard? = null,
    val heat: Heat = Heat.DEFAULT,
    val played: Int = 0,
    val streak: Int = 0,
    val loading: Boolean = true,
    val refusing: InstantCard? = null,
    val offeringHotter: Heat? = null,
)

/**
 * The card, and the two taps that move it on (REDESIGN_PROMPT.md §5.3).
 *
 * Three rules are worth stating, because each replaces a screen the app used to have:
 *
 * - **Skip asks nothing.** No reason, no dialog, no record beyond this evening's position.
 * - **"Too far" asks one question**, and the answer is written to this phone alone. The partner is
 *   never told, and nothing about it leaves the device while there is no account.
 * - **The heat goes up when they are going well**, offered after a run of yeses rather than set in
 *   a form at the start. It is offered, never applied.
 */
@HiltViewModel
class InstantPlayViewModel @Inject constructor(
    private val instant: InstantPlayRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InstantPlayUiState())
    val uiState: StateFlow<InstantPlayUiState> = _uiState.asStateFlow()

    private var deck: InstantDeck? = null
    private var position = 0

    init {
        viewModelScope.launch {
            combine(instant.deck, instant.heat) { deck, heat -> deck to heat }.collect { (cards, heat) ->
                deck = cards
                _uiState.update {
                    it.copy(card = cards.cardAt(position), heat = heat, loading = false)
                }
            }
        }
    }

    /** They did it. Five in a row is when the app asks whether to turn things up. */
    fun done() {
        val streak = _uiState.value.streak + 1
        val hotter = _uiState.value.heat.hotter()
        if (streak >= STREAK_BEFORE_OFFERING && hotter != null) {
            _uiState.update { it.copy(streak = 0, offeringHotter = hotter) }
            return
        }
        _uiState.update { it.copy(streak = streak) }
        advance()
    }

    /** Not that one. Nothing is asked and nothing is recorded. */
    fun skip() {
        _uiState.update { it.copy(streak = 0) }
        advance()
    }

    fun tooFar() {
        _uiState.update { it.copy(refusing = it.card) }
    }

    fun dismissSheet() {
        _uiState.update { it.copy(refusing = null, offeringHotter = null) }
    }

    /** "Just this one", or "never show me these again" — the second writes the card's own themes. */
    fun refuse(alsoItsKind: Boolean) {
        val card = _uiState.value.refusing ?: return
        _uiState.update { it.copy(refusing = null, streak = 0) }
        viewModelScope.launch {
            instant.refuse(card, alsoItsKind)
            // The deck rebuilds itself from the refusal, so the next card comes from the narrower
            // one rather than from where this one was.
            advance()
        }
    }

    fun turnUp(heat: Heat) {
        _uiState.update { it.copy(offeringHotter = null) }
        viewModelScope.launch {
            instant.setHeat(heat)
            advance()
        }
    }

    fun keepHeat() {
        _uiState.update { it.copy(offeringHotter = null) }
        advance()
    }

    private fun advance() {
        position += 1
        _uiState.update { it.copy(card = deck?.cardAt(position), played = it.played + 1) }
    }

    private companion object {
        /** Long enough to mean they are enjoying it, short enough to arrive in one evening. */
        const val STREAK_BEFORE_OFFERING = 5
    }
}
