package com.shnapps.couple.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.auth.AuthRepository
import com.shnapps.couple.core.data.billing.BillingRepository
import com.shnapps.couple.core.data.entitlement.LevelAccess
import com.shnapps.couple.core.data.game.GameRepository
import com.shnapps.couple.core.data.instant.InstantPlayRepository
import com.shnapps.couple.core.data.night.NightRepository
import com.shnapps.couple.core.model.ExperienceType
import com.shnapps.couple.core.model.Game
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.Mode
import com.shnapps.couple.core.model.NightSession
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

    /**
     * An evening or a game the server built, and the session both phones follow.
     *
     * [twoPhones] is how it is being played: a phone each, or one between them. It is decided
     * here, when the game is started, because that is the only place that knows both the mode
     * this phone is in and whether the game can be played that way at all (§12).
     */
    data class Session(
        val experienceId: String,
        val type: ExperienceType,
        val twoPhones: Boolean = false,
    ) : Opened
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
    val error: HomeError? = null,
    /** How far this phone may go: what the heat dial draws locks on (V2 rule 5). */
    val access: LevelAccess = LevelAccess.FREE,
    /** A locked level they tapped here. Nothing changes; the store is one tap on. */
    val lockedLevel: Intensity? = null,
    /** What that pack costs, once the store has said. Null means no price, never a guess. */
    val lockedPrice: String? = null,
) {
    /** Nothing else can start while the couple already has something under way (D-059). */
    val canStart: Boolean get() = active == null && !making

    /**
     * Apart, with nobody paired: there is nothing to play yet (D-146).
     *
     * Apart means a phone each, and a phone each needs the other phone. It used to say so in a line
     * and carry on, which let a couple choose Apart and then play on one phone — the opposite of
     * what they had picked. Now it is a state of its own: the games are not offered, and the one
     * thing on offer is the pairing that would make them playable.
     */
    val needsPairing: Boolean get() = mode == Mode.APART && !company.paired

    /**
     * Cards and Game Night are played on one phone, so Apart does not offer them (D-150).
     *
     * They are not games and have no phone-each version: they deal from the whole catalogue on the
     * phone in your hand. Offered under a toggle that says "a phone each" they were two ways to
     * quietly play alone, which is the thing Apart exists to not be.
     */
    val offersOnePhoneModes: Boolean get() = mode == Mode.TOGETHER
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
    billing: BillingRepository,
    auth: AuthRepository,
) : ViewModel() {

    private val local = MutableStateFlow(HomeUiState())

    init {
        // The mode this phone was last left in, so the app opens where it was (M6).
        viewModelScope.launch {
            instant.mode.collect { mode -> local.update { it.copy(mode = mode) } }
        }
        // What this phone has bought, told to the couple (D-149), so one of them paying opens the
        // levels for both. Home is the one screen that is always there, and the sharing runs for as
        // long as it is.
        viewModelScope.launch { billing.shareOwnership() }
        // And what the store holds, asked for here and not only in the store (D-152): a reinstalled
        // phone, or a payment that has cleared since, should not need anybody to open a screen.
        viewModelScope.launch { billing.keepRestored() }
    }

    /** Signed in at all, and joined to someone: the two facts that enable things. */
    private val company = combine(auth.authState, auth.currentProfile) { user, profile ->
        when {
            user == null -> Company.ALONE
            profile?.coupleId == null -> Company.SIGNED_IN
            else -> Company.PAIRED
        }
    }

    /** What this phone can play on its own: the list, how far it goes, and how far it may. */
    private val ownDeck = combine(instant.games, instant.tier, instant.access, ::Triple)

    val uiState: StateFlow<HomeUiState> = combine(
        local,
        company,
        ownDeck,
        nights.activeSession,
    ) { state, company, (games, tier, access), active ->
        state.copy(
            company = company,
            games = games,
            tier = tier,
            access = access,
            active = active,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState())

    fun setTier(tier: Intensity) {
        local.update { it.copy(tier = tier) }
        viewModelScope.launch { instant.setTier(tier) }
    }

    /**
     * They tapped a level they have not bought (V2 rule 5).
     *
     * Nothing changes here either: the heat stays where it was, and the card that appears says
     * what the level is and what it costs. The store is a tap on from there, never automatic.
     */
    fun tapLocked(level: Intensity) {
        local.update { it.copy(lockedLevel = level, lockedPrice = null) }
        viewModelScope.launch {
            val price = instant.packPrices()[level]
            local.update { if (it.lockedLevel == level) it.copy(lockedPrice = price) else it }
        }
    }

    fun dismissLocked() = local.update { it.copy(lockedLevel = null, lockedPrice = null) }

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
        val state = uiState.value
        // Not offered while it is true, so this is the second lock on the same door.
        if (state.needsPairing) return
        if (!state.company.paired) {
            local.update { it.copy(opened = Opened.Dealt(gameId), error = null) }
            return
        }
        if (!state.canStart) return
        // A phone each, when they are apart and the game can be played that way. A game that asks
        // one of them per round and nothing of the other would leave a round waiting for an answer
        // that never comes, so it stays on one phone however the toggle is set.
        val apart = state.mode == Mode.APART && state.games.firstOrNull { it.id == gameId }?.playableApart == true
        val ceiling = state.tier
        local.update { it.copy(making = true, error = null) }
        viewModelScope.launch {
            finish(gameStarts.start(gameId, ceiling).map { it.experienceId }, ExperienceType.GAME, apart)
        }
    }

    /** The screen has opened what was made: the next tap starts again from nothing. */
    fun onShown() = local.update { it.copy(opened = null) }

    fun dismissError() = local.update { it.copy(error = null) }

    /**
     * One ending for every tap that asks the server for something.
     *
     * No gate is mapped here any more: v2's limits are levels, and a level is refused by the deck
     * before anything is asked for (V2 rule 5). Nothing home can start is behind a server gate —
     * starting a game never calls one — so a refusal here is an error, not a price.
     */
    private fun finish(
        result: Outcome<String>,
        type: ExperienceType,
        twoPhones: Boolean = false,
    ) = local.update { state ->
        when (result) {
            is Outcome.Success ->
                state.copy(making = false, opened = Opened.Session(result.value, type, twoPhones))
            is Outcome.Failure -> state.copy(making = false, error = result.error.toHomeError())
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
