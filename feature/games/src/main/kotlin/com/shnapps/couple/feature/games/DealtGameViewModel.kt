package com.shnapps.couple.feature.games

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shnapps.couple.core.data.instant.InstantPlayRepository
import com.shnapps.couple.core.engine.game.Answer
import com.shnapps.couple.core.engine.game.GameMoment
import com.shnapps.couple.core.engine.game.GameRun
import com.shnapps.couple.core.model.GamePlayers
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.navigation.Route
import com.shnapps.couple.core.ui.namesReady
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
    /** The game in one line, shown while its first round is on screen (D-131). */
    val rule: String = "",
    val moment: GameMoment? = null,
    val players: GamePlayers = GamePlayers.UNNAMED,
    val round: Int = 0,
    val rounds: Int = 0,
    val loading: Boolean = true,
    val nothingToPlay: Boolean = false,
    /** Typed into the "who's playing" step, before it is remembered. */
    val firstName: String = "",
    val secondName: String = "",
    /** They said not now: the game carries on without names, this time and every time. */
    val skippedNames: Boolean = false,
    /** How far this phone is playing, so the screen can take its colour (DESIGN_PROMPT.md §4.4). */
    val tier: Intensity = Intensity.DEFAULT,
) {
    /**
     * Asked once, at the first hand-off, and never again (D-121).
     *
     * Only at a hand-off: that is the first moment the app is about to say a name out loud, and a
     * name asked for before then is a setup form — which is what the rebuild removed.
     */
    val askingNames: Boolean
        get() = !players.named && !skippedNames && moment is GameMoment.HandOff

    val namesReady: Boolean get() = namesReady(firstName, secondName)

    /**
     * Whether to say what this game is.
     *
     * The brief asks for no tutorials and a one-line rule inside each game, and the rule was the
     * half that went missing: a screen of three dice and a card, with nothing anywhere saying what
     * the dice were for (D-131). Shown on the first round only, and never over a hand-off, which
     * has no chrome on purpose.
     */
    val showsRule: Boolean get() = rule.isNotEmpty() && round == 0 && moment is GameMoment.Turn
}

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
        // Whether this phone knows who is holding it. Unnamed is a real state, not a placeholder.
        viewModelScope.launch {
            instant.players.collect { players -> _uiState.update { it.copy(players = players) } }
        }
        // The tier is the phone's, not the game's: a game dealt at Extreme should look like it.
        viewModelScope.launch {
            instant.tier.collect { tier -> _uiState.update { it.copy(tier = tier) } }
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
            val started = settled(GameRun.start(released, _uiState.value.players))
            run = started
            _uiState.update {
                it.copy(
                    title = released.game.title,
                    rule = released.game.description,
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

    private fun show(next: GameRun?) {
        val moved = settled(next ?: return)
        run = moved
        _uiState.update {
            it.copy(moment = moved.moment, round = moved.round, rounds = moved.roundCount)
        }
    }

    /**
     * The run, moved past anything there is nothing to show for.
     *
     * A round's last step **is** its end: the reveal, the reaction, the dare that was done. "That's
     * the round. Next" after it was a screen that said nothing and cost a tap every round, which is
     * exactly how it read on a device (D-130). A round nothing could play passes the same way, and
     * always did — it has nothing to show by definition.
     */
    private fun settled(run: GameRun): GameRun {
        var moved = run
        while (moved.moment is GameMoment.RoundOver) moved = moved.nextRound()
        return moved
    }

    fun setFirstName(name: String) = _uiState.update { it.copy(firstName = name) }

    fun setSecondName(name: String) = _uiState.update { it.copy(secondName = name) }

    /** Remembered on this phone and nowhere else, and the game carries on with them. */
    fun confirmNames() {
        val state = _uiState.value
        if (!state.namesReady) return
        val named = GamePlayers(state.firstName.trim(), state.secondName.trim())
        _uiState.update { it.copy(players = named) }
        // The run in hand is mid-round, so it carries across rather than restarting: the very
        // hand-off that asked is the first one to use the names.
        run = run?.withPlayers(named)
        viewModelScope.launch { instant.rememberPlayers(named) }
    }

    /** Not now. Nothing needs a name, and nothing will ask again this game. */
    fun skipNames() = _uiState.update { it.copy(skippedNames = true) }
}
