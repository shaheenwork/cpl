package com.shnapps.couple.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shnapps.couple.core.analytics.AnalyticsEvent
import com.shnapps.couple.core.analytics.AnalyticsLogger
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Clock
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.auth.AuthRepository
import com.shnapps.couple.core.data.billing.gate
import com.shnapps.couple.core.data.game.GameRepository
import com.shnapps.couple.core.data.instant.InstantPlayRepository
import com.shnapps.couple.core.data.media.MediaRepository
import com.shnapps.couple.core.data.night.NightRepository
import com.shnapps.couple.core.data.surprise.SurpriseRepository
import com.shnapps.couple.core.model.ExperienceType
import com.shnapps.couple.core.model.Game
import com.shnapps.couple.core.model.Gate
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.MediaItem
import com.shnapps.couple.core.model.Mode
import com.shnapps.couple.core.model.NightPlan
import com.shnapps.couple.core.model.NightSession
import com.shnapps.couple.core.model.ThreadEntry
import com.shnapps.couple.core.model.threadOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Why the app could not make something out of one tap. */
enum class HomeError { OFFLINE, NOT_ENOUGH_CONTENT, BUSY, OTHER }

/** What home has opened, once a tap has produced something to open. */
sealed interface Opened {
    /** A game dealt on this phone: nothing was asked of any server. */
    data class Dealt(val gameId: String) : Opened

    /** An evening or a game the server built, and the session both phones follow. */
    data class Session(val experienceId: String, val type: ExperienceType) : Opened
}

/** Who is using the app. The same home for all three — more of it works, further down. */
enum class Company {
    /** No account: everything this phone can do on its own, which is most of it. */
    ALONE,

    /** An account, but nobody joined to it yet. */
    SIGNED_IN,

    /** Both of them. */
    PAIRED,
    ;

    val signedIn: Boolean get() = this != ALONE
    val paired: Boolean get() = this == PAIRED
}

data class HomeUiState(
    /** Who is using it: what is enabled, never which screen they get (REDESIGN_PROMPT.md §6). */
    val company: Company = Company.ALONE,
    /** Every game the app ships. The list is the same for everyone. */
    val games: List<Game> = emptyList(),
    /** How far tonight goes: the one setting home has. */
    val tier: Intensity = Intensity.DEFAULT,
    /** Together in a room, or apart on two phones (M6). Kept, so the app opens where it was. */
    val mode: Mode = Mode.TOGETHER,
    /** The couple's one experience under way, from either phone, or null. */
    val active: NightSession? = null,
    /** Something is being built: the app is deciding, and the tap has landed. */
    val making: Boolean = false,
    /** Set once there is something to play; the screen opens it and calls [HomeViewModel.onShown]. */
    val opened: Opened? = null,
    /** Something the partner planned, waiting to be found (§2.5 #2). */
    val waitingForMe: NightPlan? = null,
    /** Something this phone's user planned for them, and whether they have opened it. */
    val plannedByMe: NightPlan? = null,
    /** A voice note or a photo they sent that nobody has opened (§13). */
    val unopenedMedia: MediaItem? = null,
    /**
     * Being apart, as one thread (REDESIGN_PROMPT.md §5.4): what they left, what was sent, what is
     * still sealed. Ordered newest first, and saying nothing about anything undelivered.
     */
    val thread: List<ThreadEntry> = emptyList(),
    val error: HomeError? = null,
    /** A limit they reached: the screen offers what premium includes, and nothing is lost (§19). */
    val gate: Gate? = null,
) {
    /** Nothing else can start while the couple already has something under way (D-059). */
    val canStart: Boolean get() = active == null && !making

    /** What is actually waiting for this person in the thread: the only thing worth a nudge. */
    val threadWaiting: Int
        get() = thread.count { entry ->
            (entry is ThreadEntry.Media && entry.waiting) || (entry is ThreadEntry.Left && entry.readyForMe)
        }
}

/**
 * Home — the only one (REDESIGN_PROMPT.md §6).
 *
 * There used to be two: a plain one for a phone with no account, and a wall of buttons for a couple
 * who had signed in. Signing in therefore replaced the app, which is the reason nobody could learn
 * it. This is one screen for all three audiences: the same heading, the same one way to start, the
 * same list of games, in the same order. What an account adds is that more of it works.
 *
 * Every flow read here returns nothing at all when there is no account behind it, so the signed-out
 * home costs no reads and shows no empty scaffolding.
 */
@Suppress("LongParameterList") // One home now: what used to be two screens' worth of sources.
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val nights: NightRepository,
    private val instant: InstantPlayRepository,
    private val gameStarts: GameRepository,
    private val analytics: AnalyticsLogger,
    private val clock: Clock,
    auth: AuthRepository,
    media: MediaRepository,
    surprises: SurpriseRepository,
) : ViewModel() {

    private val local = MutableStateFlow(HomeUiState())

    init {
        // The mode this phone was last left in, so the app opens where it was (M6).
        viewModelScope.launch {
            instant.mode.collect { mode -> local.update { it.copy(mode = mode) } }
        }
    }

    /** Signed in at all, and joined to someone: the two facts that enable things. */
    private val company = combine(auth.authState, auth.currentProfile) { user, profile ->
        when {
            user == null -> Company.ALONE
            profile?.coupleId == null -> Company.SIGNED_IN
            else -> Company.PAIRED
        }
    }

    /** What this phone can play on its own: the list, and how far it goes. */
    private val ownDeck = combine(instant.games, instant.tier, ::Pair)

    /**
     * What being apart looks like, in one flow: the inbox, and the thread built from it.
     *
     * Composed here because a combine takes five, and because the inbox is wanted twice — once for
     * the one unopened thing the home nudges about, and once as part of the thread.
     */
    private val apart = combine(
        media.inbox,
        surprises.surprises,
        surprises.countdowns,
    ) { inbox, left, counting ->
        inbox to threadOf(inbox, left, counting, clock.nowMillis())
    }

    /** What the couple has going, and what one of them left for the other. */
    private val theirs = combine(nights.activeSession, nights.plans) { active, plans -> active to plans }

    val uiState: StateFlow<HomeUiState> = combine(
        local,
        company,
        ownDeck,
        apart,
        theirs,
    ) { state, company, (games, tier), (inbox, thread), (active, plans) ->
        state.copy(
            company = company,
            games = games,
            tier = tier,
            active = active,
            // Theirs first: an evening waiting to be found is the best thing on this screen.
            waitingForMe = plans.firstOrNull { !it.mine },
            plannedByMe = plans.firstOrNull { it.mine },
            unopenedMedia = inbox.firstOrNull { it.unopened },
            thread = thread,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState())

    fun setTier(tier: Intensity) {
        local.update { it.copy(tier = tier) }
        viewModelScope.launch { instant.setTier(tier) }
    }

    /**
     * Together in a room, or apart on two phones (M6).
     *
     * Kept on the phone rather than held on the screen, so the app opens where it was left: a
     * couple who are apart this week are apart tomorrow too, and re-choosing it every launch is
     * the friction the brief spends its first section on.
     */
    fun setMode(mode: Mode) {
        local.update { it.copy(mode = mode) }
        viewModelScope.launch { instant.setMode(mode) }
    }

    /**
     * A game, from the one list.
     *
     * A paired couple's game is the server's: it chooses the rounds behind both partners'
     * boundaries, and both phones follow the session it opens (§3.2, D-070). A phone on its own
     * deals the game itself, from the shipped content and inside its own heat (D-100). One tap,
     * either way — the difference is not something a person should have to know about.
     */
    fun play(gameId: String) {
        if (!uiState.value.company.paired) {
            local.update { it.copy(opened = Opened.Dealt(gameId), error = null) }
            return
        }
        if (!uiState.value.canStart) return
        val ceiling = uiState.value.tier
        local.update { it.copy(making = true, error = null) }
        viewModelScope.launch {
            finish(gameStarts.start(gameId, ceiling).map { it.experienceId }, ExperienceType.GAME)
        }
    }

    /** Surprise us, and a mystery: one tap, and the app decides (§14.2). */
    fun surprise(mystery: Boolean = false) {
        if (!uiState.value.canStart) return
        local.update { it.copy(making = true, error = null) }
        viewModelScope.launch {
            val result = nights.surprise(mystery)
            // One event for both: which of the two taps it was is not worth recording.
            if (result is Outcome.Success) analytics.log(AnalyticsEvent.SurpriseCreated)
            finish(result.map { it.experienceId }, ExperienceType.NIGHT)
        }
    }

    /**
     * Something waiting for them (§13): opening it tells the partner it landed — never what
     * happened next — and hands the evening to the player.
     */
    fun openWaiting() {
        val plan = uiState.value.waitingForMe ?: return
        if (!uiState.value.canStart) return
        local.update { it.copy(making = true, error = null) }
        viewModelScope.launch {
            finish(nights.openPlan(plan.experienceId).map { plan.experienceId }, ExperienceType.NIGHT)
        }
    }

    /** The screen has opened what was made: the next tap starts again from nothing. */
    fun onShown() = local.update { it.copy(opened = null) }

    fun dismissError() = local.update { it.copy(error = null) }

    fun dismissGate() = local.update { it.copy(gate = null) }

    /** One ending for every tap that asks the server for something (§19). */
    private fun finish(result: Outcome<String>, type: ExperienceType) = local.update { state ->
        when (result) {
            is Outcome.Success -> state.copy(making = false, opened = Opened.Session(result.value, type))
            is Outcome.Failure -> {
                val gate = result.error.gate()
                state.copy(
                    making = false,
                    gate = gate,
                    error = if (gate == null) result.error.toHomeError() else null,
                )
            }
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        /** The couple's limits leave too little for an evening at all. */
        const val NOT_ENOUGH_CONTENT = "not-enough-content"

        /** They already have something under way: there is one at a time (D-059). */
        const val SESSION_IN_PROGRESS = "session-in-progress"
    }
}

private fun AppError.toHomeError(): HomeError = when {
    this is AppError.Network -> HomeError.OFFLINE
    this is AppError.Validation && code == HomeViewModel.NOT_ENOUGH_CONTENT -> HomeError.NOT_ENOUGH_CONTENT
    this is AppError.Validation && code == HomeViewModel.SESSION_IN_PROGRESS -> HomeError.BUSY
    else -> HomeError.OTHER
}
