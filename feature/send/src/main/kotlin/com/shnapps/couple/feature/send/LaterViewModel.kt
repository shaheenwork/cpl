package com.shnapps.couple.feature.send

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Clock
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.surprise.SurpriseRepository
import com.shnapps.couple.core.model.Countdown
import com.shnapps.couple.core.model.OpenWhenTrigger
import com.shnapps.couple.core.model.Surprise
import com.shnapps.couple.core.model.SurpriseContent
import com.shnapps.couple.core.model.SurpriseState
import com.shnapps.couple.core.model.SurpriseType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** When something is meant to arrive. */
enum class LeaveWhen(val label: String) {
    TONIGHT("Tonight"),
    TOMORROW("Tomorrow"),
    NEXT_WEEK("Next week"),
    WHEN_THEY("Open when…"),
}

/** Why something could not be left or opened, in words the screen can say. */
enum class LaterError { OFFLINE, NOT_YET, NOTHING_WAITING, OTHER }

data class LaterUiState(
    /** Everything either of them has left, soonest first. */
    val surprises: List<Surprise> = emptyList(),
    val countdowns: List<Countdown> = emptyList(),
    val title: String = "",
    val text: String = "",
    val leaveWhen: LeaveWhen = LeaveWhen.TONIGHT,
    val trigger: OpenWhenTrigger = OpenWhenTrigger.MISS_ME,
    val busy: Boolean = false,
    /** Set once something has been left: the screen says so and clears. */
    val leftIt: Boolean = false,
    /** What is open right now, once it may be read. */
    val open: Pair<Surprise, SurpriseContent>? = null,
    val error: LaterError? = null,
) {
    val canLeave: Boolean get() = title.isNotBlank() && text.isNotBlank() && !busy

    /** Delivered and waiting to be opened by this phone's user. */
    val arrived: List<Surprise> get() = surprises.filter { it.waitingForMe }

    /** Kept for a moment, by them, for this phone's user to pull. */
    val moments: List<OpenWhenTrigger>
        get() = surprises
            .filter { !it.mine && it.type == SurpriseType.OPEN_WHEN && it.state == SurpriseState.SCHEDULED }
            .mapNotNull { it.trigger }
            .distinct()
}

/**
 * Things left for later (BUILD_PROMPT.md §5.7, §13): a surprise for a time, an "Open when…" for a
 * moment, and the countdowns they are both waiting through.
 *
 * Nothing here decides what may be read. The server delivers, and the rules refuse a payload that
 * has not been delivered — so this asks, and says plainly when the answer is "not yet".
 */
@HiltViewModel
class LaterViewModel @Inject constructor(
    private val surprises: SurpriseRepository,
    private val clock: Clock,
) : ViewModel() {

    private val local = MutableStateFlow(LaterUiState())

    val uiState: StateFlow<LaterUiState> =
        combine(local, surprises.surprises, surprises.countdowns) { state, items, countdowns ->
            state.copy(surprises = items, countdowns = countdowns)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LaterUiState())

    fun setTitle(title: String) = local.update { it.copy(title = title.take(MAX_TITLE)) }

    fun setText(text: String) = local.update { it.copy(text = text.take(MAX_TEXT)) }

    fun setWhen(value: LeaveWhen) = local.update { it.copy(leaveWhen = value) }

    fun setTrigger(trigger: OpenWhenTrigger) = local.update { it.copy(trigger = trigger) }

    /** Leaves it. A time turns into a moment on the clock; "Open when…" waits for a feeling. */
    fun leave() {
        val state = local.value
        if (!state.canLeave) return
        local.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = when (state.leaveWhen) {
                LeaveWhen.WHEN_THEY -> surprises.leave(
                    type = SurpriseType.OPEN_WHEN,
                    title = state.title.trim(),
                    text = state.text.trim(),
                    trigger = state.trigger,
                )
                else -> surprises.leave(
                    type = if (state.leaveWhen == LeaveWhen.NEXT_WEEK) SurpriseType.CAPSULE else SurpriseType.SCHEDULED,
                    title = state.title.trim(),
                    text = state.text.trim(),
                    scheduledForMillis = clock.nowMillis() + state.leaveWhen.aheadMillis(),
                )
            }
            local.update { current ->
                when (result) {
                    is Outcome.Success -> LaterUiState(leftIt = true)
                    is Outcome.Failure -> current.copy(busy = false, error = result.error.toLaterError())
                }
            }
        }
    }

    /** "Open when you can't sleep": asks the server for one kept for that moment, then opens it. */
    fun pull(trigger: OpenWhenTrigger) = act {
        when (val pulled = surprises.pull(trigger)) {
            is Outcome.Failure -> pulled
            is Outcome.Success -> open(pulled.value)
        }
    }

    fun openArrived(surprise: Surprise) = act { open(surprise.id) }

    private suspend fun open(surpriseId: String): Outcome<Unit> =
        when (val content = surprises.open(surpriseId)) {
            is Outcome.Failure -> content
            is Outcome.Success -> {
                // The list lives in the combined state; `local` only holds what this screen chose.
                val surprise = uiState.value.surprises.firstOrNull { it.id == surpriseId }
                if (surprise != null) local.update { it.copy(open = surprise to content.value) }
                Outcome.Success(Unit)
            }
        }

    fun close() = local.update { it.copy(open = null) }

    fun dismissLeft() = local.update { it.copy(leftIt = false) }

    fun dismissError() = local.update { it.copy(error = null) }

    fun addCountdown(title: String, daysAhead: Int) = act {
        surprises.addCountdown(title.trim(), clock.nowMillis() + daysAhead * DAY_MS)
    }

    fun removeCountdown(countdownId: String) = act { surprises.removeCountdown(countdownId) }

    private fun act(work: suspend () -> Outcome<*>) {
        if (local.value.busy) return
        local.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = work()
            local.update { state ->
                when (result) {
                    is Outcome.Success -> state.copy(busy = false)
                    is Outcome.Failure -> state.copy(busy = false, error = result.error.toLaterError())
                }
            }
        }
    }

    companion object {
        const val MAX_TITLE = 60
        const val MAX_TEXT = 2000
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        private const val HOUR_MS = 60 * 60 * 1000L

        /** "Tonight" is this evening; "next week" is seven days. */
        private const val HOURS_TO_TONIGHT = 6
        private const val DAYS_TO_NEXT_WEEK = 7

        /** Tonight is a few hours away; the rest are days. */
        private fun LeaveWhen.aheadMillis(): Long = when (this) {
            LeaveWhen.TONIGHT -> HOURS_TO_TONIGHT * HOUR_MS
            LeaveWhen.TOMORROW -> DAY_MS
            LeaveWhen.NEXT_WEEK -> DAYS_TO_NEXT_WEEK * DAY_MS
            LeaveWhen.WHEN_THEY -> 0L
        }
    }
}

private fun AppError.toLaterError(): LaterError = when {
    this is AppError.Network -> LaterError.OFFLINE
    this is AppError.NotFound -> LaterError.NOTHING_WAITING
    this is AppError.Validation && code == "not-yet" -> LaterError.NOT_YET
    this is AppError.PermissionDenied -> LaterError.NOT_YET
    else -> LaterError.OTHER
}
