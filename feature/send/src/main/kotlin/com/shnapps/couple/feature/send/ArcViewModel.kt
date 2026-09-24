package com.shnapps.couple.feature.send

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Clock
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.billing.gate
import com.shnapps.couple.core.data.surprise.ArcRepository
import com.shnapps.couple.core.model.Arc
import com.shnapps.couple.core.model.ArcDay
import com.shnapps.couple.core.model.Gate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Why a day could not be opened, in words the screen can say. */
enum class ArcError { OFFLINE, NOT_YET, FINISH_YESTERDAY, OTHER }

data class ArcUiState(
    val arc: Arc? = null,
    val busy: Boolean = false,
    /** A day is being opened: its night is built while they wait. */
    val opening: Boolean = false,
    /** Set once a day has been opened: the screen goes to its night. */
    val openedExperienceId: String? = null,
    val error: ArcError? = null,
    /** A limit they reached: the screen offers what premium includes, and nothing is lost (§19). */
    val gate: Gate? = null,
    /** The lengths an arc can run for (§13). */
    val lengths: List<Int> = Arc.LENGTHS,
)

/**
 * A few days of this (BUILD_PROMPT.md §13). The phone shows the plan and asks for today's day;
 * the server decides whether it is that day's turn, and builds its night when it is.
 */
@HiltViewModel
class ArcViewModel @Inject constructor(
    private val arcs: ArcRepository,
    private val clock: Clock,
) : ViewModel() {

    private val local = MutableStateFlow(ArcUiState())

    val uiState: StateFlow<ArcUiState> = combine(local, arcs.arc) { state, arc ->
        state.copy(arc = arc)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ArcUiState())

    /** The day that is due now, if any: what the screen offers to open. */
    fun dayDue(): ArcDay? = uiState.value.arc?.dayDue(clock.nowMillis())

    fun start(lengthDays: Int) = act { arcs.start(lengthDays) }

    fun openDay(day: ArcDay) {
        val arc = uiState.value.arc ?: return
        if (local.value.busy) return
        local.update { it.copy(busy = true, opening = true, error = null) }
        viewModelScope.launch {
            val result = arcs.openDay(arc.id, day.index)
            local.update { state ->
                when (result) {
                    is Outcome.Success ->
                        state.copy(busy = false, opening = false, openedExperienceId = result.value)
                    is Outcome.Failure -> {
                        // A subscription that lapsed mid-arc: the day's night is a new night, so
                        // it is refused like any other — and said in the same words (§19).
                        val gate = result.error.gate()
                        state.copy(
                            busy = false,
                            opening = false,
                            gate = gate,
                            error = if (gate == null) result.error.toArcError() else null,
                        )
                    }
                }
            }
        }
    }

    fun end() {
        val arc = uiState.value.arc ?: return
        act { arcs.end(arc.id) }
    }

    /** The screen has gone to the night. */
    fun onShown() = local.update { it.copy(openedExperienceId = null) }

    fun dismissError() = local.update { it.copy(error = null) }

    fun dismissGate() = local.update { it.copy(gate = null) }

    private fun act(work: suspend () -> Outcome<*>) {
        if (local.value.busy) return
        local.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = work()
            local.update { state ->
                when (result) {
                    is Outcome.Success -> state.copy(busy = false)
                    is Outcome.Failure -> {
                        val gate = result.error.gate()
                        state.copy(
                            busy = false,
                            gate = gate,
                            error = if (gate == null) result.error.toArcError() else null,
                        )
                    }
                }
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

private fun AppError.toArcError(): ArcError = when {
    this is AppError.Network -> ArcError.OFFLINE
    this is AppError.Validation && code == "not-yet" -> ArcError.NOT_YET
    this is AppError.Validation && code == "finish-the-day-before" -> ArcError.FINISH_YESTERDAY
    else -> ArcError.OTHER
}
