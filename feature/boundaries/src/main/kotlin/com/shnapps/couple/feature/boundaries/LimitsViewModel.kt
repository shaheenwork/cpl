package com.shnapps.couple.feature.boundaries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.limits.LimitsRepository
import com.shnapps.couple.core.model.LimitAnswer
import com.shnapps.couple.core.model.LimitTag
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One row: what is being asked, and what this person has said about it. */
data class LimitRow(val tag: LimitTag, val answer: LimitAnswer?)

/** Immutable screen state (BUILD_PROMPT.md §4.1; enforced by :architecture). */
data class LimitsUiState(
    val rows: List<LimitRow> = LimitTag.entries.map { LimitRow(it, answer = null) },
    val loading: Boolean = true,
    val error: AppError? = null,
) {
    /** How many of the eight have been answered: the only progress this screen shows. */
    val answered: Int get() = rows.count { it.answer != null }
}

/**
 * The limits profile: eight questions, three answers each (M3).
 *
 * It replaces a twenty-seven row list with a five-level slider and a private note per row. That
 * screen was right about the model and wrong about the ask — the themes are how the content is
 * authored and filtered, not how a person thinks about what they do and do not want, and
 * twenty-seven rows is a form nobody finishes. [LimitTag] is the translation, and it is a
 * partition, so no answer here quietly answers another question.
 *
 * **Nothing here is required.** The profile is offered after a first session and can be left
 * half-done for ever; an unanswered tag is absent rather than defaulted, and the tier is what
 * governs an evening in the meantime.
 *
 * **Nothing here is ever shown to the partner.** Answers are written to the user's own boundary
 * documents, which are owner-only for life (§5.1); the server intersects both partners' answers
 * into a filter no client can read (§3.2, D-032). Neither of them ever learns the other's.
 */
@HiltViewModel
class LimitsViewModel @Inject constructor(
    private val limits: LimitsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LimitsUiState())
    val uiState: StateFlow<LimitsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            limits.limits.collect { answers ->
                _uiState.update { state ->
                    state.copy(
                        rows = LimitTag.entries.map { LimitRow(it, answers[it]) },
                        loading = false,
                    )
                }
            }
        }
    }

    /**
     * Answers one tag.
     *
     * The row moves at once rather than waiting for the write, because the phone's copy is written
     * first and is authoritative until there is an account. A failure puts the message up without
     * taking the answer back: what they said is still recorded here.
     */
    fun answer(tag: LimitTag, answer: LimitAnswer) {
        _uiState.update { state ->
            state.copy(rows = state.rows.map { if (it.tag == tag) it.copy(answer = answer) else it })
        }
        viewModelScope.launch {
            when (val result = limits.set(tag, answer)) {
                is Outcome.Failure -> _uiState.update { it.copy(error = result.error) }
                is Outcome.Success -> _uiState.update { it.copy(error = null) }
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }
}
