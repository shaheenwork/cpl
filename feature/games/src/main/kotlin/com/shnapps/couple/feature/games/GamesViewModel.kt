package com.shnapps.couple.feature.games

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.auth.AuthRepository
import com.shnapps.couple.core.data.game.DefaultGameRepository
import com.shnapps.couple.core.data.game.GameRepository
import com.shnapps.couple.core.data.night.NightRepository
import com.shnapps.couple.core.model.Game
import com.shnapps.couple.core.model.GamePlayers
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.NightSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Why a game could not start, in words the screen can say. */
enum class StartError { NOT_ENOUGH_CONTENT, RATE_LIMITED, OFFLINE, OUT_OF_DATE, OTHER }

data class GamesUiState(
    val games: List<Game> = emptyList(),
    /** The game being set up, or null while the couple is still choosing. */
    val selectedId: String? = null,
    val intensity: Intensity = Intensity.FLIRTY,
    /** The couple's shared level: the most any game can reach. Null until known. */
    val sharedLevel: Intensity? = null,
    /** Who goes first, then second: the names the phone is passed between. */
    val first: String = "",
    val second: String = "",
    /** A phone each, rather than one passed between them (§12). */
    val twoPhones: Boolean = false,
    /** Something already under way for this couple: there is one at a time (D-059). */
    val active: NightSession? = null,
    val starting: Boolean = false,
    /** Set once a game has started: the screen moves on to it and calls [GamesViewModel.onShown]. */
    val startedExperienceId: String? = null,
    val error: StartError? = null,
) {
    val selected: Game? get() = games.firstOrNull { it.id == selectedId }

    /** Only a game both answer at once can be played on a phone each: a turn needs the phone. */
    val canTwoPhones: Boolean get() = selected?.bothAtOnce == true

    /** How it will be played, whatever was chosen for a game that allows the choice. */
    val onTwoPhones: Boolean get() = twoPhones && canTwoPhones

    /** Both names given, and different: otherwise "pass the phone to…" means nothing. */
    val namesReady: Boolean
        get() = first.isNotBlank() && second.isNotBlank() && !first.trim().equals(second.trim(), ignoreCase = true)

    /** With a phone each, nobody passes anything: no names are needed, and none travel (§12). */
    val ready: Boolean get() = onTwoPhones || namesReady

    val canStart: Boolean get() = selected != null && ready && active == null && !starting
}

/**
 * The games (BUILD_PROMPT.md §11): which one, how far it may go, and who goes first. The
 * server then chooses its rounds behind both partners' boundaries. Nothing here decides what
 * the couple may see; the dial only shows how far their shared level reaches.
 */
@HiltViewModel
class GamesViewModel @Inject constructor(
    private val games: GameRepository,
    nights: NightRepository,
    auth: AuthRepository,
) : ViewModel() {

    private val local = MutableStateFlow(GamesUiState())

    val uiState: StateFlow<GamesUiState> = combine(local, games.games, nights.sharedLevel, nights.activeSession) {
            state, list, shared, active ->
        val ceiling = shared ?: Intensity.WILD
        state.copy(
            games = list,
            sharedLevel = shared,
            active = active,
            intensity = if (state.intensity.level > ceiling.level) ceiling else state.intensity,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), local.value)

    init {
        viewModelScope.launch {
            // The names the phone was last passed between, or the user's own to start with.
            val saved = games.players.first()
            val own = auth.currentProfile.first()?.displayName.orEmpty()
            local.update { it.copy(first = saved?.first ?: own, second = saved?.second.orEmpty()) }
        }
    }

    fun select(gameId: String) = local.update { it.copy(selectedId = gameId, error = null) }

    fun closeSetup() = local.update { it.copy(selectedId = null, error = null) }

    fun setIntensity(intensity: Intensity) = local.update { it.copy(intensity = intensity) }

    fun setFirst(name: String) = local.update { it.copy(first = name.take(MAX_NAME)) }

    fun setSecond(name: String) = local.update { it.copy(second = name.take(MAX_NAME)) }

    /** The other one goes first. */
    fun swapNames() = local.update { it.copy(first = it.second, second = it.first) }

    /** One phone passed between you, or a phone each (§12), where the game allows it. */
    fun setTwoPhones(twoPhones: Boolean) = local.update { it.copy(twoPhones = twoPhones) }

    fun start() {
        val state = uiState.value
        val game = state.selected ?: return
        if (!state.canStart) return
        local.update { it.copy(starting = true, error = null) }
        viewModelScope.launch {
            if (!state.onTwoPhones) games.rememberPlayers(GamePlayers(state.first.trim(), state.second.trim()))
            val result = games.start(game.id, state.intensity)
            local.update { current ->
                when (result) {
                    is Outcome.Success -> current.copy(
                        starting = false,
                        startedExperienceId = result.value.experienceId,
                    )
                    is Outcome.Failure -> current.copy(starting = false, error = result.error.toStartError())
                }
            }
        }
    }

    /** The screen has moved on to the started game. */
    fun onShown() = local.update { it.copy(startedExperienceId = null, selectedId = null) }

    fun dismissError() = local.update { it.copy(error = null) }

    private fun AppError.toStartError(): StartError = when {
        this is AppError.Validation && code == NOT_ENOUGH_CONTENT -> StartError.NOT_ENOUGH_CONTENT
        this is AppError.Validation && code == DefaultGameRepository.CONTENT_OUT_OF_DATE -> StartError.OUT_OF_DATE
        this is AppError.Validation && code == DefaultGameRepository.GAME_UNKNOWN -> StartError.OUT_OF_DATE
        this is AppError.RateLimited -> StartError.RATE_LIMITED
        this is AppError.Network -> StartError.OFFLINE
        else -> StartError.OTHER
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val NOT_ENOUGH_CONTENT = "not-enough-content"
        const val MAX_NAME = 24
    }
}
