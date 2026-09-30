package com.shnapps.couple.feature.discovery

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.analytics.AnalyticsEvent
import com.shnapps.couple.core.analytics.AnalyticsLogger
import com.shnapps.couple.core.model.MatchLevel
import com.shnapps.couple.core.model.MutualMatch
import com.shnapps.couple.core.testing.FakeMutualRepository
import com.shnapps.couple.core.testing.FakeTaxonomyRepository
import com.shnapps.couple.core.testing.MainDispatcherRule
import com.shnapps.couple.core.testing.TestTaxonomy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** Mutual discovery from one partner's phone (BUILD_PROMPT.md §14.7). */
@OptIn(ExperimentalCoroutinesApi::class)
class MutualDiscoveryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private var mutual = FakeMutualRepository()
    private val logged = mutableListOf<AnalyticsEvent>()

    private fun match(itemId: String, level: MatchLevel = MatchLevel.BOTH_YES, at: Long = 1L, seen: Boolean = false) =
        MutualMatch(itemId, level, revealedAtEpochMillis = at, seen = seen)

    private fun TestScope.viewModel(): MutualDiscoveryViewModel =
        MutualDiscoveryViewModel(FakeTaxonomyRepository(), mutual, AnalyticsLogger { logged += it }).also { vm ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
            advanceUntilIdle()
        }

    private fun TestScope.act(action: () -> Unit) {
        action()
        advanceUntilIdle()
    }

    private val MutualDiscoveryViewModel.state get() = uiState.value

    @Test
    fun `asks for anything already due the moment it opens`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel()
        assertThat(mutual.releaseCalls).isEqualTo(1)
    }

    @Test
    fun `offers the oldest new match first, concealed`() = runTest(mainDispatcherRule.testDispatcher) {
        // The repository delivers oldest first.
        mutual = FakeMutualRepository(
            listOf(match(TestTaxonomy.romantic.id, at = 1), match(TestTaxonomy.flirty.id, at = 2)),
        )
        val vm = viewModel()

        assertThat(vm.state.current?.itemId).isEqualTo(TestTaxonomy.romantic.id)
        assertThat(vm.state.step).isEqualTo(RevealStep.Concealed)
        assertThat(vm.state.moreAfter).isEqualTo(1)
    }

    @Test
    fun `reveals one at a time, and marks each seen only once it has been revealed`() =
        runTest(mainDispatcherRule.testDispatcher) {
            mutual = FakeMutualRepository(listOf(match(TestTaxonomy.romantic.id)))
            val vm = viewModel()

            act { vm.reveal() }
            assertThat(vm.state.step).isEqualTo(RevealStep.Revealing)
            assertThat(mutual.seenCalls).isEmpty()

            act { vm.onRevealed() }
            assertThat(vm.state.step).isEqualTo(RevealStep.Revealed)
            assertThat(mutual.seenCalls).containsExactly(TestTaxonomy.romantic.id)
        }

    @Test
    fun `a revealed card stays on screen until the user moves on`() = runTest(mainDispatcherRule.testDispatcher) {
        mutual = FakeMutualRepository(listOf(match(TestTaxonomy.romantic.id)))
        val vm = viewModel()
        act { vm.reveal() }
        act { vm.onRevealed() }

        // The server now says it is seen; the moment must not be cut short by that.
        assertThat(mutual.matches.value.single().seen).isTrue()
        assertThat(vm.state.current?.itemId).isEqualTo(TestTaxonomy.romantic.id)

        act { vm.next() }
        assertThat(vm.state.current).isNull()
        assertThat(vm.state.shared.map { it.itemId }).containsExactly(TestTaxonomy.romantic.id)
    }

    @Test
    fun `moves through every new match, then shows everything shared, newest first`() =
        runTest(mainDispatcherRule.testDispatcher) {
            mutual = FakeMutualRepository(
                listOf(
                    match(TestTaxonomy.playful.id, at = 1, seen = true),
                    match(TestTaxonomy.romantic.id, at = 2),
                    match(TestTaxonomy.flirty.id, at = 3),
                ),
            )
            val vm = viewModel()
            assertThat(vm.state.shared.map { it.itemId }).containsExactly(TestTaxonomy.playful.id)

            repeat(2) {
                act { vm.reveal() }
                act { vm.onRevealed() }
                act { vm.next() }
            }

            assertThat(vm.state.current).isNull()
            assertThat(vm.state.shared.map { it.itemId })
                .containsExactly(TestTaxonomy.flirty.id, TestTaxonomy.romantic.id, TestTaxonomy.playful.id)
                .inOrder()
        }

    @Test
    fun `a match that arrives while the screen is open joins the queue`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        assertThat(vm.state.current).isNull()

        mutual.reveal(match(TestTaxonomy.intense.id, level = MatchLevel.BOTH_SECRET))
        advanceUntilIdle()

        assertThat(vm.state.current?.itemId).isEqualTo(TestTaxonomy.intense.id)
        assertThat(vm.state.isSecret).isTrue()
    }

    @Test
    fun `a withdrawn match leaves the screen, even mid-reveal`() = runTest(mainDispatcherRule.testDispatcher) {
        mutual = FakeMutualRepository(listOf(match(TestTaxonomy.romantic.id)))
        val vm = viewModel()
        act { vm.reveal() }

        mutual.withdraw(TestTaxonomy.romantic.id)
        advanceUntilIdle()

        assertThat(vm.state.current).isNull()
        assertThat(vm.state.shared).isEmpty()
    }

    @Test
    fun `skips a match the taxonomy no longer knows`() = runTest(mainDispatcherRule.testDispatcher) {
        mutual = FakeMutualRepository(listOf(match("retired_item"), match(TestTaxonomy.romantic.id, at = 2)))
        val vm = viewModel()

        assertThat(vm.state.current?.itemId).isEqualTo(TestTaxonomy.romantic.id)
        assertThat(vm.state.moreAfter).isEqualTo(0)
    }

    @Test
    fun `logs how many were new, once, as a count and nothing else`() = runTest(mainDispatcherRule.testDispatcher) {
        mutual = FakeMutualRepository(listOf(match(TestTaxonomy.romantic.id), match(TestTaxonomy.flirty.id, at = 2)))
        val vm = viewModel()
        repeat(2) {
            act { vm.reveal() }
            act { vm.onRevealed() }
            act { vm.next() }
        }

        assertThat(logged).containsExactly(AnalyticsEvent.MutualInterestFound(count = 2))
        logged.flatMap { it.params.values }.forEach { assertThat(it).isInstanceOf(Int::class.java) }
    }

    @Test
    fun `nothing new means nothing to reveal and nothing logged`() = runTest(mainDispatcherRule.testDispatcher) {
        mutual = FakeMutualRepository(listOf(match(TestTaxonomy.romantic.id, seen = true)))
        val vm = viewModel()
        act { vm.reveal() }

        assertThat(vm.state.current).isNull()
        assertThat(logged).isEmpty()
    }
}
