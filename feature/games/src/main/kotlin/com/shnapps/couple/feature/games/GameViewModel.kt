package com.shnapps.couple.feature.games

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shnapps.couple.core.analytics.AnalyticsEvent
import com.shnapps.couple.core.analytics.AnalyticsLogger
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Clock
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.game.GameRepository
import com.shnapps.couple.core.data.game.SessionLink
import com.shnapps.couple.core.data.game.StartedGame
import com.shnapps.couple.core.data.night.NightRepository
import com.shnapps.couple.core.data.taxonomy.TaxonomyRepository
import com.shnapps.couple.core.engine.game.Answer
import com.shnapps.couple.core.engine.game.GameMoment
import com.shnapps.couple.core.engine.game.GameRun
import com.shnapps.couple.core.engine.game.RoundPrimitive
import com.shnapps.couple.core.engine.game.decodeAnswer
import com.shnapps.couple.core.engine.game.encode
import com.shnapps.couple.core.model.CloseCheckIn
import com.shnapps.couple.core.model.GamePlayers
import com.shnapps.couple.core.model.NightSession
import com.shnapps.couple.core.model.SessionEndReason
import com.shnapps.couple.core.model.SessionKind
import com.shnapps.couple.core.model.SessionState
import com.shnapps.couple.core.navigation.Route
import com.shnapps.couple.core.ui.namesReady
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
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
    /** Typed into the "who's playing" step, before it is remembered. */
    val firstName: String = "",
    val secondName: String = "",
    /** They said not now: this game carries on without names. */
    val skippedNames: Boolean = false,
    val session: NightSession? = null,
    val run: GameRun? = null,
    val paused: Boolean = false,
    val busy: Boolean = false,
    val error: GameError? = null,
    val keepOut: KeepOutOffer? = null,
    val closeAnswer: CloseCheckIn? = null,
    val closed: Boolean = false,
    val missing: Boolean = false,
    /** A phone each, rather than one passed between them (§12). */
    val twoPhones: Boolean = false,
    /** The listener is down: what is on screen is the last thing the server said (§12.3). */
    val reconnecting: Boolean = false,
    /** The partner's phone has not been heard from for a while (§12.3). */
    val partnerAway: Boolean = false,
) {
    val roundCount: Int get() = started?.released?.rounds?.size ?: session?.chapterCount ?: 0

    /**
     * This phone has seen the round's reveal and the other has not: on two phones the round
     * moves on only once both have (§12.1).
     */
    val waitingOnPartner: Boolean
        get() = twoPhones && session?.state == SessionState.REVEAL && session.seen && !session.partnerSeen

    /** 1-based, for "Round 2 of 8". */
    val roundNumber: Int get() = (session?.currentChapter ?: -1) + 1

    /**
     * Asked once, at the first hand-off, and never again (D-121).
     *
     * Only when the phone is passed: on two phones each side is genuinely "you", which is a real
     * name for that screen, so there is nothing to ask and nobody to ask it of.
     */
    val askingNames: Boolean
        get() = !players.named && !skippedNames && !twoPhones &&
            (stage as? GameStage.Playing)?.moment is GameMoment.HandOff

    val namesReady: Boolean get() = namesReady(firstName, secondName)

    companion object {
        val DEFAULT_PLAYERS = GamePlayers.UNNAMED
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
    private val clock: Clock,
) : ViewModel() {

    // Type-safe routes keep each argument under its property's name (Route.Game).
    private val experienceId: String = checkNotNull(savedStateHandle.get<String>(Route.Game::experienceId.name)) {
        "Route.Game needs an experienceId"
    }

    /** How the couple chose to play: a phone each, or one passed between them. */
    private val twoPhones: Boolean = savedStateHandle.get<Boolean>(Route.Game::twoPhones.name) ?: false

    private val local = MutableStateFlow(GameUiState(twoPhones = twoPhones))
    private val sessionId = MutableStateFlow<String?>(null)
    private var completedLogged = false

    /** The round this phone has already sent its answer for. */
    private var sentRound: Int? = null

    /** The round whose reveal this phone has already taken in. */
    private var receivedRound: Int? = null

    /** A passed-over round is waiting to be moved on. */
    private var passing = false

    /** The session, followed by its own id: the couple's pointer clears the moment it ends. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val link: Flow<SessionLink> =
        sessionId.flatMapLatest { id -> if (id == null) flowOf(SessionLink(null)) else games.observeSession(id) }

    private val session: Flow<NightSession?> = link.map { it.session }

    val uiState: StateFlow<GameUiState> = combine(local, link, sessionId) { state, link, id ->
        val session = link.session
        // How it is being played is the couple's choice until there is a session, and the
        // session's own after that: the phone that did not start it was never asked (§12).
        val current = state.copy(session = session, twoPhones = session?.kind?.isTwoPhones ?: state.twoPhones)
        current.copy(
            stage = GameStages.stageOf(current, awaitingSession = id != null && session == null && !link.reconnecting),
            paused = session?.state == SessionState.PAUSED,
            reconnecting = link.reconnecting,
            partnerAway = awayFor(session),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), GameUiState(twoPhones = twoPhones))

    /** A partner whose phone has said nothing for PARTNER_AWAY_MS is shown as dropped out (§12.3). */
    private fun awayFor(session: NightSession?): Boolean {
        if (session == null || session.kind != SessionKind.TWO_DEVICE || session.state.isOver) return false
        val seen = session.partnerLastSeenMillis ?: return false
        return clock.nowMillis() - seen > PARTNER_AWAY_MS
    }

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
        // "I'm still here", for as long as there is a session to be in (§12.3).
        viewModelScope.launch {
            combine(sessionId, session.map { it?.state?.isOver == true }) { id, over -> id.takeIf { !over } }
                .distinctUntilChanged()
                .flatMapLatest { id -> if (id == null) emptyFlow() else games.keepPresent(id) }
                .collect { }
        }
        // What the other phone answered, once the server has revealed it (§12.2).
        viewModelScope.launch {
            sessionId.filterNotNull().flatMapLatest { id ->
                local.map { it.run?.round }.distinctUntilChanged().filterNotNull().flatMapLatest { round ->
                    games.observeReveal(id, round).map { round to it }
                }
            }.collect { (round, answers) -> onReveal(round, answers) }
        }
    }

    /** The server's reveal for [round]: each phone's answer, put where this round expects it. */
    private fun onReveal(round: Int, answers: Map<Int, String?>?) {
        if (answers == null || receivedRound == round) return
        val run = local.value.run ?: return
        val seat = run.phone ?: return
        receivedRound = round
        // Its own answer comes back too: to a phone restarted mid-round, this is the only copy
        // of it there is (§12.3, D-074).
        val mine = run.answerToSend()
        val told = answers
            .filterKeys { who -> who != seat || mine == null }
            .mapNotNull { (who, encoded) -> encoded?.let(::decodeAnswer)?.let { who to it } }
            .fold(run) { current, (who, answer) -> current.receive(who, answer) }
        // A phone with nothing to do this round stays where it is: the answer that just arrived is
        // the thing it was waiting to be shown (§12).
        val stays = told.watchingOnly()
        setRun(if (told.moment is GameMoment.Waiting && !stays) told.proceed() else told)
    }

    /**
     * The phone that only watched a round has no way of its own to leave it (§12).
     *
     * The round ended because the other partner finished it, not because anything happened here —
     * so this phone follows the session rather than its own interpreter, which is still sitting on
     * the turn it was watching.
     */
    private fun onWatcherFollows(started: StartedGame, session: NightSession) {
        val watching = local.value.run ?: return
        if (!session.state.inChapter || session.currentChapter <= watching.round) return
        if (!watching.watchingOnly()) return
        sentRound = null
        receivedRound = null
        setRun(startRun(started, session, fromRound = session.currentChapter))
    }

    /** A phone that restarted mid-game picks the game up at the round the server has. */
    private fun onSessionMoved(started: StartedGame?, session: NightSession) {
        if (started == null) return
        val state = local.value
        // On two phones both sides hold the game; on one, only the phone it is played on.
        val plays = session.kind == SessionKind.TWO_DEVICE || session.isHost
        if (plays && session.state.inChapter && state.run == null) {
            val run = startRun(started, session, fromRound = session.currentChapter)
            // Restarted after it had answered: it waits for the reveal, and never answers twice.
            val answered = session.submitted
            if (answered) sentRound = session.currentChapter
            setRun(if (answered) run.awaitingReveal() else run)
        }
        if (plays) onWatcherFollows(started, session)
        if (session.state == SessionState.COMPLETED && session.isHost && !completedLogged) {
            completedLogged = true
            analytics.log(AnalyticsEvent.GameCompleted(started.released.game.id))
        }
    }

    // ---- before the first round ---------------------------------------------------------------

    fun open() = act {
        games.openSession(experienceId, twoPhones).map { id -> sessionId.value = id }
    }

    fun consent() = act { games.consent(requireSession()) }

    fun decline() = act { games.decline(requireSession()) }

    /** The first round: the server moves the session on, and says what is now ruled out. */
    fun play() = act {
        val started = local.value.started ?: return@act Outcome.Failure(AppError.NotFound())
        games.begin(requireSession()).map { blocked ->
            analytics.log(AnalyticsEvent.GameStarted(started.released.game.id))
            setRun(startRun(started, uiState.value.session, fromRound = 0).withBlocked(blocked, fromRound = 0))
        }
    }

    /** The game in hand, as this phone plays it: its own seat when there is a phone each (§12). */
    private fun startRun(started: StartedGame, session: NightSession?, fromRound: Int): GameRun {
        val phone = if (session?.kind == SessionKind.TWO_DEVICE) (if (session.isHost) 0 else 1) else null
        val players = if (phone == null) local.value.players else labels(phone)
        // With a phone each there are no names to pass around: each side is simply "you" (§12).
        if (phone != null) local.update { it.copy(players = players) }
        return GameRun.start(started.released, players, fromRound = fromRound, phone = phone)
    }

    /** On two phones, each side is "you" and the other is simply the partner: no names travel. */
    private fun labels(phone: Int): GamePlayers =
        if (phone == 0) GamePlayers(YOU, YOUR_PARTNER) else GamePlayers(YOUR_PARTNER, YOU)

    // ---- within a round: the interpreter's -----------------------------------------------------

    fun setFirstName(name: String) = local.update { it.copy(firstName = name) }

    fun setSecondName(name: String) = local.update { it.copy(secondName = name) }

    /**
     * Remembered on this phone and nowhere else, and the game carries on with them.
     *
     * The run in hand is mid-round, so it carries across rather than restarting: the very
     * hand-off that asked is the first one to use the names.
     */
    fun confirmNames() {
        val state = uiState.value
        if (!state.namesReady) return
        val named = GamePlayers(state.firstName.trim(), state.secondName.trim())
        local.update { it.copy(players = named, run = it.run?.withPlayers(named)) }
        viewModelScope.launch { games.rememberPlayers(named) }
    }

    /** Not now. Nothing needs a name, and nothing will ask again this game. */
    fun skipNames() = local.update { it.copy(skippedNames = true) }

    /** Past a hand-off or a gathering. */
    fun proceed() = edit { it.proceed() }

    fun submit(answer: Answer) {
        val before = local.value.run
        edit { it.submit(answer) ?: it }
        val after = local.value.run ?: return
        if (before != null && after.phone != null) sendAnswer(after)
        // Seen by this phone: the round is over for both only when each has seen it (§12.1).
        val sawReveal = (before?.moment as? GameMoment.Turn)?.primitive is RoundPrimitive.Reveal
        if (sawReveal && after.phone != null && uiState.value.session?.seen != true) {
            val round = before.round
            act { games.markSeen(requireSession(), round) }
        }
    }

    /**
     * This phone's answer for the round, sent once (§12.2).
     *
     * **As soon as there is one**, rather than when the round ends. In a game of turns the partner
     * is watching rather than answering, and the server reveals the round the moment the one it
     * asked has answered — so sending the choice at the end of the round would show them the dare
     * only after it had been done, which is no better than not showing it at all.
     *
     * A phone with nothing to send still says so once it has finished, because the chapter is
     * waiting on it.
     */
    private fun sendAnswer(run: GameRun) {
        if (sentRound == run.round) return
        val answer = run.answerToSend()
        val done = run.moment is GameMoment.Waiting || run.moment is GameMoment.RoundOver
        if (answer == null && !done) return
        val round = run.round
        sentRound = round
        act { games.submit(requireSession(), round, answer?.encode()) }
    }

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
        // On two phones a round that asked both of them is over only once both have seen it; the
        // server says when. A round that asked one of them is over when they are done — nobody is
        // waiting to see anything they have not already been watching.
        val asksBoth = run.phone?.let { run.asksSeat(1 - it) } ?: false
        if (asksBoth && uiState.value.session?.state == SessionState.REVEAL) return
        sentRound = null
        receivedRound = null
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

        /** Silence from the other phone for ninety seconds shows as dropped out (§12.3). */
        const val PARTNER_AWAY_MS = 90_000L

        const val YOU = "You"
        const val YOUR_PARTNER = "Your partner"
    }
}

private fun AppError.toGameError(): GameError = when {
    this is AppError.Network -> GameError.OFFLINE
    // The game moved on without this phone — the other one paused or stopped it. The session
    // listener already shows where it is.
    this is AppError.Validation && code == "illegal-transition" -> GameError.MOVED_ON
    else -> GameError.OTHER
}
