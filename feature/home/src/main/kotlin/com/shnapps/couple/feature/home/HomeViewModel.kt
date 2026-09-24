package com.shnapps.couple.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shnapps.couple.core.analytics.AnalyticsEvent
import com.shnapps.couple.core.analytics.AnalyticsLogger
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.billing.gate
import com.shnapps.couple.core.data.media.MediaRepository
import com.shnapps.couple.core.data.night.NightRepository
import com.shnapps.couple.core.datastore.AppPreferencesStore
import com.shnapps.couple.core.model.Gate
import com.shnapps.couple.core.model.MediaItem
import com.shnapps.couple.core.model.Mode
import com.shnapps.couple.core.model.NightPlan
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

data class HomeUiState(
    /** The same room, or apart: one switch drives the whole surface (§14.2). */
    val mode: Mode = Mode.TOGETHER,
    /** The couple's one experience under way, from either phone, or null. */
    val active: NightSession? = null,
    /** A surprise is being made: the app is choosing, and the tap has landed. */
    val making: Boolean = false,
    /** Set once a surprise exists: the screen opens it and calls [HomeViewModel.onShown]. */
    val startedExperienceId: String? = null,
    /** Something the partner planned, waiting to be found (§2.5 #2). */
    val waitingForMe: NightPlan? = null,
    /** Something this phone's user planned for them, and whether they have opened it. */
    val plannedByMe: NightPlan? = null,
    /** A voice note or a photo they sent that nobody has opened (§13). */
    val unopenedMedia: MediaItem? = null,
    val error: HomeError? = null,
    /** A limit they reached: the screen offers what premium includes, and nothing is lost (§19). */
    val gate: Gate? = null,
) {
    /** Nothing else can start while the couple already has something under way (D-059). */
    val canStart: Boolean get() = active == null && !making
}

/**
 * Home (BUILD_PROMPT.md §14.2): "Tonight could get interesting."
 *
 * The one UX law (§2.4) is that nobody should be made to think "which question should we
 * pick?", so the two taps that need no thought are here: a surprise, which the server shapes
 * from what only it can see, and a Mystery Night, which does the same and keeps its shape to
 * itself (D-077). Everything else leads to a screen that asks at most a couple of things.
 *
 * The surface is one switch — the same room, or apart — kept on the phone, because it is about
 * where this phone is rather than about the couple.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val nights: NightRepository,
    private val preferences: AppPreferencesStore,
    private val analytics: AnalyticsLogger,
    media: MediaRepository,
) : ViewModel() {

    private val local = MutableStateFlow(HomeUiState())

    val uiState: StateFlow<HomeUiState> = combine(
        local,
        nights.activeSession,
        preferences.preferences.map { it.mode },
        nights.plans,
        media.inbox,
    ) { state, active, mode, plans, inbox ->
        state.copy(
            active = active,
            mode = mode,
            // Theirs first: a night waiting to be found is the best thing on this screen.
            waitingForMe = plans.firstOrNull { !it.mine },
            plannedByMe = plans.firstOrNull { it.mine },
            unopenedMedia = inbox.firstOrNull { it.unopened },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState())

    fun setMode(mode: Mode) {
        viewModelScope.launch { preferences.setMode(mode) }
    }

    /** 🎲 Surprise us, and 🌙 Mystery Night: one tap, and the app decides (§14.2). */
    fun surprise(mystery: Boolean = false) {
        if (!uiState.value.canStart) return
        local.update { it.copy(making = true, error = null) }
        viewModelScope.launch {
            when (val result = nights.surprise(mystery)) {
                is Outcome.Success -> {
                    // One event for both: which of the two taps it was is not worth recording.
                    analytics.log(AnalyticsEvent.SurpriseCreated)
                    local.update { it.copy(making = false, startedExperienceId = result.value.experienceId) }
                }
                is Outcome.Failure -> local.update {
                    val gate = result.error.gate()
                    it.copy(
                        making = false,
                        gate = gate,
                        error = if (gate == null) result.error.toHomeError() else null,
                    )
                }
            }
        }
    }

    /**
     * "Something is waiting for you tonight 👀" (§13): opening it tells the partner it landed —
     * never what happened next — and hands the night to the player.
     */
    fun openWaiting() {
        val plan = uiState.value.waitingForMe ?: return
        if (!uiState.value.canStart) return
        local.update { it.copy(making = true, error = null) }
        viewModelScope.launch {
            when (val result = nights.openPlan(plan.experienceId)) {
                is Outcome.Success -> local.update {
                    it.copy(making = false, startedExperienceId = plan.experienceId)
                }
                is Outcome.Failure -> local.update {
                    val gate = result.error.gate()
                    it.copy(
                        making = false,
                        gate = gate,
                        error = if (gate == null) result.error.toHomeError() else null,
                    )
                }
            }
        }
    }

    /** The screen has opened what was made: the next tap starts again from nothing. */
    fun onShown() = local.update { it.copy(startedExperienceId = null) }

    fun dismissError() = local.update { it.copy(error = null) }

    fun dismissGate() = local.update { it.copy(gate = null) }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        /** The couple's limits leave too little for a night at all. */
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
