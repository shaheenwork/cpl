package com.shnapps.couple.debug

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.content.ContentSyncResult
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.Mode
import com.shnapps.couple.core.testing.FakeContentRepository
import com.shnapps.couple.core.testing.MainDispatcherRule
import com.shnapps.couple.core.testing.TestContent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class ContentInspectorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeContentRepository()

    @Test
    fun `lists everything on the device until a filter is chosen`() = runTest {
        val viewModel = ContentInspectorViewModel(repository)

        viewModel.uiState.test {
            val loaded = awaitState { !it.loading }
            assertThat(loaded.items).isEqualTo(TestContent.items)
            assertThat(loaded.totalItems).isEqualTo(3)
            assertThat(loaded.packs.map { it.id }).containsExactly("FLIRT", "AFTER_DARK")
        }
    }

    @Test
    fun `filters by pack, mode and intensity, and a second tap clears a filter`() = runTest {
        val viewModel = ContentInspectorViewModel(repository)

        viewModel.uiState.test {
            awaitState { !it.loading }

            viewModel.selectPack("FLIRT")
            assertThat(awaitState { it.pack == "FLIRT" }.items)
                .containsExactly(TestContent.softTogether, TestContent.flirtyApart)

            viewModel.selectMode(Mode.APART)
            assertThat(awaitState { it.mode == Mode.APART }.items).containsExactly(TestContent.flirtyApart)

            viewModel.selectPack("FLIRT")
            viewModel.selectIntensity(Intensity.BOLD)
            val filtered = awaitState { it.intensity == Intensity.BOLD && it.pack == null }
            assertThat(filtered.items).containsExactly(TestContent.boldBoth)
        }
    }

    @Test
    fun `sync now reports what happened`() = runTest {
        val viewModel = ContentInspectorViewModel(repository)
        repository.syncResult = Outcome.Success(ContentSyncResult(installedVersion = 2, deltasReceived = 1))

        viewModel.uiState.test {
            awaitState { !it.loading }
            viewModel.syncNow()
            assertThat(awaitState { it.syncResult != null }.syncResult).isEqualTo("installed 2, 1 delta(s)")

            repository.syncResult = Outcome.Failure(AppError.Network())
            viewModel.syncNow()
            assertThat(awaitState { it.syncResult?.startsWith("failed") == true }.syncResult)
                .isEqualTo("failed: Network")
        }
        assertThat(repository.syncCalls).isEqualTo(2)
    }

    /** The next state matching [predicate]; the StateFlow may skip the ones in between. */
    private suspend fun ReceiveTurbine<ContentInspectorUiState>.awaitState(
        predicate: (ContentInspectorUiState) -> Boolean,
    ): ContentInspectorUiState {
        while (true) {
            val state = awaitItem()
            if (predicate(state)) return state
        }
    }
}
