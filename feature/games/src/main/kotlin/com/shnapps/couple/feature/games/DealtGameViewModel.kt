package com.shnapps.couple.feature.games

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shnapps.couple.core.data.instant.InstantPlayRepository
import com.shnapps.couple.core.engine.game.Answer
import com.shnapps.couple.core.engine.game.GameMoment
import com.shnapps.couple.core.engine.game.GameRun
import com.shnapps.couple.core.model.GamePlayers
import com.shnapps.couple.core.model.Heat
import com.shnapps.couple.core.navigation.Route
import com.shnapps.couple.core.ui.voice.VoiceRecorder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Immutable screen state (BUILD_PROMPT.md §4.1; enforced by :architecture). */
data class DealtGameUiState(
    val title: String = "",
    val moment: GameMoment? = null,
    val players: GamePlayers = GamePlayers.UNNAMED,
    val round: Int = 0,
    val rounds: Int = 0,
    val loading: Boolean = true,
    val nothingToPlay: Boolean = false,
    /** How far this phone is playing, so the screen can take its colour (DESIGN_PROMPT.md §4.4). */
    val heat: Heat = Heat.DEFAULT,
)

/**
 * A game dealt on this phone, with no session behind it (REDESIGN_PROMPT.md §5.2).
 *
 * The interpreter is the same `GameRun` every game has always used — the rule that no code knows a
 * game by name is untouched (D-072). What differs is where the rounds come from: `forHeat` deals
 * them on the device, inside this phone's heat and refusals, because there is no partner yet whose
 * limits could be leaked (D-100).
 *
 * Nothing is stored. A game lasts as long as it is on screen, which is also why leaving it is
 * simply leaving: there is no session to end and nobody waiting on the other side.
 */
@HiltViewModel
class DealtGameViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val instant: InstantPlayRepository,
    /** Handed to the screen for a round that records. Nothing here uses it. */
    val recorder: VoiceRecorder,
) : ViewModel() {

    private val gameId: String = checkNotNull(savedStateHandle.get<String>(Route.DealtGame::gameId.name)) {
        "Route.DealtGame needs a gameId"
    }

    private val _uiState = MutableStateFlow(DealtGameUiState())
    val uiState: StateFlow<DealtGameUiState> = _uiState.asStateFlow()

    private var run: GameRun? = null

    init {
        deal()
        // The heat is the phone's, not the game's: a game dealt at Wild should look like it.
        viewModelScope.launch {
            instant.heat.collect { heat -> _uiState.update { it.copy(heat = heat) } }
        }
    }

    /** Deals the game. Called again by "Play it again", which is a new deal rather than a rewind. */
    fun deal() {
        _uiState.update { it.copy(loading = true, nothingToPlay = false) }
        viewModelScope.launch {
            val released = instant.startGame(gameId)
            if (released == null || released.rounds.none { it.playable }) {
                _uiState.update { it.copy(loading = false, nothingToPlay = true) }
                return@launch
            }
            val started = GameRun.start(released, _uiState.value.players)
            run = started
            _uiState.update {
                it.copy(
                    title = released.game.title,
                    moment = started.moment,
                    round = started.round,
                    rounds = started.roundCount,
                    loading = false,
                )
            }
        }
    }

    /** Past a hand-off or a gathering: the phone changes hands, and the game moves on. */
    fun proceed() {
        show(run?.proceed())
    }

    /** An answer the primitive accepts moves the game on; one it refuses changes nothing. */
    fun answer(answer: Answer) {
        show(run?.submit(answer) ?: return)
    }

    /** "Not this one": a different prompt for this round, from what was dealt for it. */
    fun skipPrompt() {
        show(run?.skipPrompt())
    }

    fun nextRound() {
        show(run?.nextRound())
    }

    private fun show(next: GameRun?) {
        val moved = next ?: return
        run = moved
        _uiState.update {
            it.copy(moment = moved.moment, round = moved.round, rounds = moved.roundCount)
        }
    }
}
