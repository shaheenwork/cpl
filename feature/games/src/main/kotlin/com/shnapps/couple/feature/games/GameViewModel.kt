package com.shnapps.couple.feature.games

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shnapps.couple.core.analytics.AnalyticsEvent
import com.shnapps.couple.core.analytics.AnalyticsLogger
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.game.GameRepository
import com.shnapps.couple.core.data.game.StartedGame
import com.shnapps.couple.core.data.night.NightRepository
import com.shnapps.couple.core.data.taxonomy.TaxonomyRepository
import com.shnapps.couple.core.engine.game.Answer
import com.shnapps.couple.core.engine.game.GameMoment
import com.shnapps.couple.core.engine.game.GameRun
import com.shnapps.couple.core.model.CloseCheckIn
import com.shnapps.couple.core.model.GamePlayers
import com.shnapps.couple.core.model.NightSession
import com.shnapps.couple.core.model.SessionEndReason
import com.shnapps.couple.core.model.SessionState
import com.shnapps.couple.core.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where a game is, on this phone. */
sealed interface GameStage {
    data object Loading : GameStage

    data object Missing : GameStage

    /** The game's shape — how many rounds, how far, who goes first — before it starts. */
    data object Intro : GameStage

    /** BOLD or WILD: each partner's "I'm in", from their own phone (§3.1). */
    data object Consent : GameStage

    /** Both in, or no need to ask: the host starts it. */
    data object Ready : GameStage

    /** The game itself, moment by moment, on the phone it is played on. */
    data class Playing(val moment: GameMoment) : GameStage

    /** The other phone, while the game is played on this couple's other one (D-059). */
    data object Elsewhere : GameStage

    data class Ended(val reason: SessionEndReason) : GameStage

    /** "How are we doing?" — the check-in at the close (§3.1), before any score. */
    data object HowAreWe : GameStage

    data object Results : GameStage
}

/** After "Not this one": what the refused prompt was about, to keep out of this game. */
data class KeepOutOffer(val themes: List<Pair<String, String>>)

enum class GameError { OFFLINE, MOVED_ON, OTHER }

data class GameUiState(
    val stage: GameStage = GameStage.Loading,
    val started: StartedGame? = null,
    val players: GamePlayers = DEFAULT_PLAYERS,
    val session: NightSession? = null,
    val run: GameRun? = null,
    val paused: Boolean = false,
    val busy: Boolean = false,
    val error: GameError? = null,
    val keepOut: KeepOutOffer? = null,
    val closeAnswer: CloseCheckIn? = null,
    val closed: Boolean = false,
    val missing: Boolean = false,
) {
    val roundCount: Int get() = started?.released?.rounds?.size ?: session?.chapterCount ?: 0

    /** 1-based, for "Round 2 of 8". */
    val roundNumber: Int get() = (session?.currentChapter ?: -1) + 1

    companion object {
        val DEFAULT_PLAYERS = GamePlayers("Player one", "Player two")
    }
}

/**
 * A game being played (BUILD_PROMPT.md §3.1, §11), on the phone it is passed around on and on
 * the partner's own.
 *
 * The server's session is always the truth for where the game stands — begun, which round,
 * paused, stopped, "I'm in" — and every move of it is a request the server checks. Within a
 * round, the game is the interpreter's ([GameRun]): turns, hand-offs, reveals and scores, the
 * same for every game. What either partner answers stays in it, on this phone (D-069).
 */
// Its public functions are the player's controls, one each; the rules live in GameRun and
// GameStages, where they are tested on their own.
@Suppress("TooManyFunctions")
@HiltViewModel
class GameViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val games: GameRepository,
    nights: NightRepository,
    private val taxonomy: TaxonomyRepository,
    private val analytics: AnalyticsLogger,
) : ViewModel() {

    // Type-safe routes keep each argument under its property's name (Route.Game).
    private val experienceId: String = checkNotNull(savedStateHandle.get<String>(Route.Game::experienceId.name)) {
        "Route.Game needs an experienceId"
    }

    private val local = MutableStateFlow(GameUiState())
    private val sessionId = MutableStateFlow<String?>(null)
    private var completedLogged = false

    /** A passed-over round is waiting to be moved on. */
    private var passing = false

    /** The session, followed by its own id: the couple's pointer clears the moment it ends. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val session: Flow<NightSession?> =
        sessionId.flatMapLatest { id -> if (id == null) flowOf(null) else games.observeSession(id) }

    val uiState: StateFlow<GameUiState> = combine(local, session, sessionId) { state, session, id ->
        val current = state.copy(session = session)
        current.copy(
            stage = GameStages.stageOf(current, awaitingSession = id != null && session == null),
            paused = session?.state == SessionState.PAUSED,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), GameUiState())

    init {
        viewModelScope.launch {
            val players = games.players.first() ?: GameUiState.DEFAULT_PLAYERS
            when (val loaded = games.load(experienceId)) {
                is Outcome.Success -> local.update { it.copy(started = loaded.value, players = players) }
                is Outcome.Failure -> local.update { it.copy(missing = true) }
            }
        }
        // This game's session, if it is the couple's experience under way: opened on the other
        // phone, or on this one before the app was closed.
        viewModelScope.launch {
            nights.activeSession.collect { active ->
                if (active?.experienceId == experienceId && sessionId.value == null) sessionId.value = active.id
            }
        }
        viewModelScope.launch {
            // Whichever arrives first — the game, or where its session stands — both are needed.
            combine(local.map { it.started }.distinctUntilChanged(), session.filterNotNull(), ::Pair)
                .collect { (started, session) -> onSessionMoved(started, session) }
        }
    }

    /** A phone that restarted mid-game picks the game up at the round the server has. */
    private fun onSessionMoved(started: StartedGame?, session: NightSession) {
        if (started == null) return
        val state = local.value
        if (session.isHost && session.state == SessionState.CHAPTER_ACTIVE && state.run == null) {
            setRun(GameRun.start(started.released, state.players, fromRound = session.currentChapter))
        }
        if (session.state == SessionState.COMPLETED && session.isHost && !completedLogged) {
            completedLogged = true
            analytics.log(AnalyticsEvent.GameCompleted(started.released.game.id))
        }
    }

    // ---- before the first round ---------------------------------------------------------------

    fun open() = act {
        games.openSession(experienceId).map { id -> sessionId.value = id }
    }

    fun consent() = act { games.consent(requireSession()) }

    fun decline() = act { games.decline(requireSession()) }

    /** The first round: the server moves the session on, and says what is now ruled out. */
    fun play() = act {
        val started = local.value.started ?: return@act Outcome.Failure(AppError.NotFound())
        games.begin(requireSession()).map { blocked ->
            analytics.log(AnalyticsEvent.GameStarted(started.released.game.id))
            setRun(GameRun.start(started.released, local.value.players).withBlocked(blocked, fromRound = 0))
        }
    }

    // ---- within a round: the interpreter's -----------------------------------------------------

    /** Past a hand-off or a gathering. */
    fun proceed() = edit { it.proceed() }

    fun submit(answer: Answer) = edit { it.submit(answer) ?: it }

    /**
     * "Not this one" (§3.1): the prompt swapped at once for another the server released, and
     * the offer to keep what it was about out of the rest of the game.
     */
    fun skip() {
        val run = local.value.run ?: return
        val refused = run.promptInPlay() ?: return
        setRun(run.skipPrompt())
        viewModelScope.launch {
            val tax = taxonomy.taxonomy.first()
            val themes = refused.excludedByBoundaries.mapNotNull { id -> tax.theme(id)?.let { id to it.title } }
            if (themes.isNotEmpty()) local.update { it.copy(keepOut = KeepOutOffer(themes)) }
        }
    }

    /**
     * Keeps [themeId] out of the rest of this game. Not anyone's limit: the phone is in both
     * partners' hands, and a lasting limit is each partner's own to set (D-071).
     */
    fun keepOut(themeId: String) {
        local.update { it.copy(keepOut = null) }
        edit { run -> run.withBlocked(run.promptsAbout(themeId)) }
    }

    fun dismissKeepOut() = local.update { it.copy(keepOut = null) }

    /** The round is over: the server moves the session on, and the next round begins. */
    fun nextRound() {
        val run = local.value.run ?: return
        val over = run.moment as? GameMoment.RoundOver ?: return
        act {
            games.finishRound(requireSession(), over.round, over.playedPromptId).map { blocked ->
                val current = local.value.run ?: run
                setRun(current.nextRound().withBlocked(blocked, fromRound = over.round + 1))
            }
        }
    }

    // ---- PAUSE and STOP: always one tap (§3.1) --------------------------------------------------

    fun togglePause() = act(always = true) {
        val id = requireSession()
        if (uiState.value.paused) games.resume(id) else games.pause(id)
    }

    /** Never waits for anything else in flight, and asks nothing first. */
    fun stop() = act(always = true) { games.stop(requireSession()) }

    // ---- the close ----------------------------------------------------------------------------

    fun answerClose(answer: CloseCheckIn) = local.update { it.copy(closeAnswer = answer) }

    fun toResults() = local.update { it.copy(closed = true) }

    fun dismissError() = local.update { it.copy(error = null) }

    // ---- plumbing ---------------------------------------------------------------------------------

    private fun edit(change: (GameRun) -> GameRun) {
        val run = local.value.run ?: return
        setRun(change(run))
    }

    /**
     * Holds [run]. A round passed over — nothing released can play it within the couple's
     * limits any more — moves on unseen, as a night's does (D-062).
     */
    private fun setRun(run: GameRun) {
        local.update { it.copy(run = run) }
        val over = run.moment as? GameMoment.RoundOver ?: return
        if (over.playedPromptId != null || passing) return
        passing = true
        viewModelScope.launch {
            // After whatever request is in flight: one move of the session at a time.
            local.first { !it.busy }
            passing = false
            nextRound()
        }
    }

    private fun requireSession(): String = checkNotNull(sessionId.value) { "No session for $experienceId yet" }

    /** Runs [work], one at a time — except PAUSE and STOP ([always]), which never wait. */
    private fun act(always: Boolean = false, work: suspend () -> Outcome<Unit>) {
        if (local.value.busy && !always) return
        local.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = work()
            local.update { state ->
                when (result) {
                    is Outcome.Success -> state.copy(busy = false)
                    is Outcome.Failure -> state.copy(busy = false, error = result.error.toGameError())
                }
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

private fun AppError.toGameError(): GameError = when {
    this is AppError.Network -> GameError.OFFLINE
    // The game moved on without this phone — the other one paused or stopped it. The session
    // listener already shows where it is.
    this is AppError.Validation && code == "illegal-transition" -> GameError.MOVED_ON
    else -> GameError.OTHER
}
