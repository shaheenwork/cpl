package com.shnapps.couple.feature.night

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shnapps.couple.core.analytics.AnalyticsEvent
import com.shnapps.couple.core.analytics.AnalyticsLogger
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.night.NightRepository
import com.shnapps.couple.core.data.taxonomy.TaxonomyRepository
import com.shnapps.couple.core.model.BoundaryLevel
import com.shnapps.couple.core.model.ChapterReaction
import com.shnapps.couple.core.model.CloseCheckIn
import com.shnapps.couple.core.model.NightSession
import com.shnapps.couple.core.model.PlannedNight
import com.shnapps.couple.core.model.SessionEndReason
import com.shnapps.couple.core.model.SessionState
import com.shnapps.couple.core.model.Taxonomy
import com.shnapps.couple.core.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where the night is, on this phone (BUILD_PROMPT.md §14.6). */
sealed interface NightStage {
    data object Loading : NightStage

    /** "Tonight has 7 chapters" — the shape, never the contents. */
    data object Shape : NightStage

    /** BOLD or WILD: each partner's "I'm in", from their own phone (§3.1). */
    data object Consent : NightStage

    data object Countdown : NightStage

    /** A quick check before chapter [index] (§3.1, §3.2). */
    data class CheckIn(val index: Int) : NightStage

    /** Chapter [index], sealed until [open]. */
    data class Chapter(val index: Int, val open: Boolean) : NightStage

    /** "Something interesting is coming…" on the way to chapter [nextIndex]. */
    data class Between(val nextIndex: Int) : NightStage

    /** The partner's phone while the night plays on the other one (D-059). */
    data object Elsewhere : NightStage

    data class Ended(val reason: SessionEndReason) : NightStage

    /** "How are we doing?" — the check-in at the close (§3.1). */
    data object HowAreWe : NightStage

    data object Feedback : NightStage

    /** "NIGHT COMPLETE ❤️" (§14.8). */
    data object Done : NightStage

    data object Missing : NightStage
}

/** After "Not this one": the themes the refused item was about, to keep out if wanted. */
data class KeepOutOffer(val themes: List<Pair<String, String>>)

enum class NightError { OFFLINE, MOVED_ON, OTHER }

data class NightUiState(
    val stage: NightStage = NightStage.Loading,
    val night: PlannedNight? = null,
    val session: NightSession? = null,
    val paused: Boolean = false,
    val busy: Boolean = false,
    val error: NightError? = null,
    val keepOut: KeepOutOffer? = null,
    /** "Not this one" found nothing else that fits this chapter. */
    val nothingElseFits: Boolean = false,
    val closeAnswer: CloseCheckIn? = null,
    val reactions: Map<Int, ChapterReaction> = emptyMap(),
) {
    val chapterCount: Int get() = night?.chapters?.size ?: session?.chapterCount ?: 0

    /** 1-based, for "Chapter 2 of 6". */
    val chapterNumber: Int get() = (session?.currentChapter ?: -1) + 1

    /** The chapters that were actually played, for feedback. */
    val played: List<Int> get() = night?.chapters?.map { it.index }?.filter { it !in night.unplayable }.orEmpty()
}

/**
 * The experience player (BUILD_PROMPT.md §3.1, §14.6), on the host's phone and on the
 * partner's.
 *
 * The server's session is always the truth: this follows it, and every move — begin, each
 * chapter, pause, stop, "I'm in" — is a request the server checks. What happens between the
 * server's states is this phone's alone: the countdown, a check-in, the sealed card, the pause
 * before the next chapter. STOP is one tap from every one of them, with no confirmation.
 */
// Its public functions are the player's controls, one each; the rules live in PlayerStages
// and NightScript, where they are tested on their own.
@Suppress("TooManyFunctions")
@HiltViewModel
class NightViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val nights: NightRepository,
    private val taxonomy: TaxonomyRepository,
    private val analytics: AnalyticsLogger,
) : ViewModel() {

    // Type-safe routes keep each argument under its property's name (Route.Night).
    private val experienceId: String = checkNotNull(savedStateHandle.get<String>(Route.Night::experienceId.name)) {
        "Route.Night needs an experienceId"
    }

    private val local = MutableStateFlow(NightUiState())
    private val step = MutableStateFlow<PlayerStep>(PlayerStep.None)
    private val sessionId = MutableStateFlow<String?>(null)
    private var startedLogged = false
    private var completedLogged = false

    /**
     * The session, followed by its own id. Not through the couple's pointer to its night under
     * way: that clears the moment a night ends, and the end is exactly what must be seen.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val session: Flow<NightSession?> =
        sessionId.flatMapLatest { id -> if (id == null) flowOf(null) else nights.observeSession(id) }

    val uiState: StateFlow<NightUiState> = combine(local, session, sessionId, step) { state, session, id, step ->
        render(state.copy(session = session), awaitingSession = id != null && session == null, step)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), NightUiState())

    init {
        viewModelScope.launch {
            when (val loaded = nights.load(experienceId)) {
                is Outcome.Success -> local.update { it.copy(night = loaded.value) }
                is Outcome.Failure -> local.update { it.copy(stage = NightStage.Missing) }
            }
        }
        // This night's session, if it is the couple's night under way: opened on the other
        // phone, or on this one before the app was closed.
        viewModelScope.launch {
            nights.activeSession.collect { active ->
                if (active?.experienceId == experienceId && sessionId.value == null) sessionId.value = active.id
            }
        }
        viewModelScope.launch {
            session.filterNotNull().distinctUntilChangedBy { it.state to it.currentChapter }.collect(::onSessionMoved)
        }
    }

    private fun render(state: NightUiState, awaitingSession: Boolean, step: PlayerStep): NightUiState {
        val stage = if (state.stage == NightStage.Missing) {
            NightStage.Missing
        } else {
            PlayerStages.stageOf(state.night, state.session, awaitingSession, step)
        }
        return state.copy(stage = stage, paused = state.session?.state == SessionState.PAUSED)
    }

    private suspend fun onSessionMoved(session: NightSession) {
        val night = local.value.night ?: nights.load(experienceId).getOrNull() ?: return
        val live = session.state == SessionState.CHAPTER_ACTIVE && session.isHost
        if (live && session.currentChapter in night.unplayable) {
            // Nothing released can stand in for this chapter any more: pass over it unseen.
            finish(session.currentChapter)
        }
        if (session.state == SessionState.COMPLETED && session.isHost && !completedLogged) {
            completedLogged = true
            val settings = night.settings
            val chapters = night.chapters.size
            analytics.log(AnalyticsEvent.ExperienceCompleted(settings.mode.name, settings.durationMin, chapters))
        }
    }

    // ---- the night's shape, consent and start ------------------------------------------------

    fun begin() = act {
        when (val started = nights.start(experienceId)) {
            is Outcome.Success -> {
                sessionId.value = started.value
                Outcome.Success(Unit)
            }
            is Outcome.Failure -> started
        }
    }

    fun consent() = act { nights.consent(requireSession()) }

    fun decline() = act { nights.decline(requireSession()) }

    /** The countdown ran out: the first chapter. */
    fun onCountdownDone() = act {
        nights.begin(experienceId, requireSession()).map { night ->
            local.update { it.copy(night = night) }
            if (!startedLogged) {
                startedLogged = true
                val settings = night.settings
                val level = night.effectiveIntensity.level
                analytics.log(AnalyticsEvent.ExperienceStarted(settings.mode.name, settings.durationMin, level))
            }
        }
    }

    // ---- chapters -----------------------------------------------------------------------------

    fun keepGoing(index: Int) = step.update { PlayerStep.Open(index, open = false) }

    fun open(index: Int) = step.update { PlayerStep.Open(index, open = true) }

    /** The chapter is done. The next one waits behind a moment of anticipation. */
    fun done(index: Int) {
        step.value = PlayerStep.Between(index + 1)
        viewModelScope.launch { finish(index) }
    }

    private suspend fun finish(index: Int) {
        local.update { it.copy(busy = true, keepOut = null, nothingElseFits = false) }
        val result = nights.finishChapter(experienceId, requireSession(), index)
        local.update { state ->
            when (result) {
                is Outcome.Success -> state.copy(busy = false, night = result.value)
                is Outcome.Failure -> state.copy(busy = false, error = result.error.toNightError())
            }
        }
        if (result is Outcome.Failure) step.value = PlayerStep.None
    }

    /** "Something interesting is coming…" has had its moment. */
    fun onBetweenDone(nextIndex: Int) {
        val night = local.value.night ?: return
        step.value = if (NightScript.checkInDue(night, nextIndex)) {
            PlayerStep.CheckIn(nextIndex)
        } else {
            PlayerStep.Open(nextIndex, open = false)
        }
    }

    /**
     * "Not this one" (§3.1): swaps the chapter instantly for something else released for the
     * night, and offers to keep what it was about out for good or for tonight.
     */
    fun notThisOne(index: Int) {
        val refused = local.value.night?.chapters?.getOrNull(index)?.item ?: return
        analytics.log(AnalyticsEvent.ExperienceSkipped(index))
        val swapped = nights.swap(experienceId, index)
        if (swapped == null) {
            local.update { it.copy(nothingElseFits = true) }
            return
        }
        local.update { it.copy(night = swapped, nothingElseFits = false) }
        val next = swapped.chapters[index].item
        step.value = if (next.id in swapped.askFirstItems) {
            PlayerStep.CheckIn(index)
        } else {
            PlayerStep.Open(index, open = true)
        }
        viewModelScope.launch {
            val themes = themesOf(refused.excludedByBoundaries, taxonomy.taxonomy.first())
            if (themes.isNotEmpty()) local.update { it.copy(keepOut = KeepOutOffer(themes)) }
        }
    }

    fun keepOut(themeId: String, level: BoundaryLevel, index: Int) {
        local.update { it.copy(keepOut = null) }
        act {
            nights.keepOut(experienceId, themeId, level, index + 1)
                .map { night -> local.update { it.copy(night = night) } }
        }
    }

    fun dismissKeepOut() = local.update { it.copy(keepOut = null) }

    fun dismissNothingElse() = local.update { it.copy(nothingElseFits = false) }

    // ---- PAUSE and STOP: always one tap (§3.1) --------------------------------------------------

    fun togglePause() = act(always = true) {
        val id = requireSession()
        if (uiState.value.paused) nights.resume(id) else nights.pause(id)
    }

    /** Never waits for anything else in flight, and asks nothing first. */
    fun stop() = act(always = true) { nights.stop(requireSession()) }

    // ---- the close ------------------------------------------------------------------------------

    fun answerClose(answer: CloseCheckIn) = local.update { it.copy(closeAnswer = answer) }

    fun toFeedback() = step.update { PlayerStep.Feedback }

    fun react(index: Int, reaction: ChapterReaction) =
        local.update { it.copy(reactions = it.reactions + (index to reaction)) }

    /**
     * Sends each reaction given — each private to this partner (D-063) — then closes the night.
     * A reaction that could not be saved keeps the couple here, told so, rather than lost quietly.
     */
    fun saveFeedback() = act {
        val failed = local.value.reactions.map { (index, reaction) -> nights.react(experienceId, index, reaction) }
            .filterIsInstance<Outcome.Failure>()
            .firstOrNull()
        if (failed == null) {
            step.value = PlayerStep.Done
            Outcome.Success(Unit)
        } else {
            failed
        }
    }

    fun skipFeedback() = step.update { PlayerStep.Done }

    fun dismissError() = local.update { it.copy(error = null) }

    // ---- plumbing -------------------------------------------------------------------------------

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
                    is Outcome.Failure -> state.copy(busy = false, error = result.error.toNightError())
                }
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

private fun themesOf(themeIds: Set<String>, taxonomy: Taxonomy): List<Pair<String, String>> =
    themeIds.mapNotNull { id -> taxonomy.theme(id)?.let { id to it.title } }

private fun AppError.toNightError(): NightError = when {
    this is AppError.Network -> NightError.OFFLINE
    // The night moved on without this phone — the other one paused or stopped it. The
    // session listener already shows where it is.
    this is AppError.Validation && code == "illegal-transition" -> NightError.MOVED_ON
    else -> NightError.OTHER
}
