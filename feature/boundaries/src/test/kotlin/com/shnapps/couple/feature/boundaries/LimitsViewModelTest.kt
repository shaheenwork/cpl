package com.shnapps.couple.feature.boundaries

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.limits.LimitsRepository
import com.shnapps.couple.core.model.LimitAnswer
import com.shnapps.couple.core.model.LimitTag
import com.shnapps.couple.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * The limits profile, as a screen (M3).
 *
 * The assertions are about what is *not* required: nothing is answered by default, nothing has to
 * be answered at all, and a write that fails does not take the answer away — the phone's copy is
 * authoritative until there is an account, so the row is right even when the account is not.
 */
class LimitsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val limits = FakeLimits()

    @Test
    fun `every tag has a row, and none of them starts answered`() = runTest {
        val viewModel = viewModel()

        assertThat(viewModel.uiState.value.rows.map { it.tag }).isEqualTo(LimitTag.entries)
        assertThat(viewModel.uiState.value.rows.map { it.answer }.toSet()).containsExactly(null)
        assertThat(viewModel.uiState.value.answered).isEqualTo(0)
        assertThat(viewModel.uiState.value.loading).isFalse()
    }

    @Test
    fun `answering one writes it and leaves the rest alone`() = runTest {
        val viewModel = viewModel()

        viewModel.answer(LimitTag.POWER_PLAY, LimitAnswer.NO)
        advanceUntilIdle()

        assertThat(limits.written).containsExactly(LimitTag.POWER_PLAY to LimitAnswer.NO)
        assertThat(viewModel.uiState.value.answered).isEqualTo(1)
        assertThat(viewModel.uiState.value.rows.first { it.tag == LimitTag.POWER_PLAY }.answer)
            .isEqualTo(LimitAnswer.NO)
    }

    @Test
    fun `a failed write keeps the answer and says what happened`() = runTest {
        limits.result = Outcome.Failure(AppError.Network())
        val viewModel = viewModel()

        viewModel.answer(LimitTag.SENSORY, LimitAnswer.MAYBE)
        advanceUntilIdle()

        // The phone's copy is what governs play until there is an account, so the row stands.
        assertThat(viewModel.uiState.value.rows.first { it.tag == LimitTag.SENSORY }.answer)
            .isEqualTo(LimitAnswer.MAYBE)
        assertThat(viewModel.uiState.value.error).isInstanceOf(AppError.Network::class.java)

        viewModel.dismissError()
        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun `an answer set elsewhere shows up here`() = runTest {
        val viewModel = viewModel()

        limits.state.value = mapOf(LimitTag.FANTASY to LimitAnswer.NO)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.rows.first { it.tag == LimitTag.FANTASY }.answer)
            .isEqualTo(LimitAnswer.NO)
    }

    private fun viewModel(): LimitsViewModel {
        val viewModel = LimitsViewModel(limits)
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
        return viewModel
    }

    /** A real object with real state, not a mock: the fakes rule (DECISIONS.md D-013). */
    private class FakeLimits : LimitsRepository {
        val state = MutableStateFlow<Map<LimitTag, LimitAnswer>>(emptyMap())
        val written = mutableListOf<Pair<LimitTag, LimitAnswer>>()
        var result: Outcome<Unit> = Outcome.Success(Unit)

        override val limits: Flow<Map<LimitTag, LimitAnswer>> = state

        override val refusedThemes: Flow<Set<String>> = state.map { answers ->
            answers.filterValues { it == LimitAnswer.NO }.keys.flatMapTo(mutableSetOf()) { it.themes }
        }

        override suspend fun set(tag: LimitTag, answer: LimitAnswer): Outcome<Unit> {
            written += tag to answer
            if (result is Outcome.Success) state.value = state.value + (tag to answer)
            return result
        }
    }
}
