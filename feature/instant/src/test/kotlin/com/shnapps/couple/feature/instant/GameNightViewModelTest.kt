package com.shnapps.couple.feature.instant

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.testing.FakeInstantPlayRepository
import com.shnapps.couple.core.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * The safe word in a guided evening (M7, D-143).
 *
 * Game Night had no Stop: it was the one screen that played cards with nothing on it to end the
 * evening in one tap. What is worth asserting is that it now stops without asking anything, and
 * that turning it down really is Mild rather than the old plan carrying on under a new label.
 */
class GameNightViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val instant = FakeInstantPlayRepository()

    private fun viewModel() = GameNightViewModel(instant)

    @Test
    fun `the safe word stops the evening and asks nothing`() = runTest {
        instant.tier.value = Intensity.HOT
        val viewModel = viewModel()
        viewModel.plan()
        advanceUntilIdle()

        viewModel.stop()

        assertThat(viewModel.uiState.value.stopped).isTrue()
    }

    @Test
    fun `turning it down plans the evening again, at Mild`() = runTest {
        instant.tier.value = Intensity.HOT
        val viewModel = viewModel()
        viewModel.plan()
        advanceUntilIdle()
        viewModel.stop()

        viewModel.carryOnGently()
        advanceUntilIdle()

        // A plan's climb was settled for the old tier; carrying it on at a new label would not be
        // turning anything down.
        assertThat(viewModel.uiState.value.stopped).isFalse()
        assertThat(instant.tier.value).isEqualTo(Intensity.MILD)
        assertThat(viewModel.uiState.value.position).isEqualTo(0)
    }
}
