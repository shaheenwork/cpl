package com.shnapps.couple.feature.preferences

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.analytics.AnalyticsEvent
import com.shnapps.couple.core.analytics.AnalyticsLogger
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.PreferenceAnswer
import com.shnapps.couple.core.model.PreferenceValue
import com.shnapps.couple.core.model.UserProfile
import com.shnapps.couple.core.testing.FakeAuthRepository
import com.shnapps.couple.core.testing.FakePreferenceRepository
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

/**
 * Private discovery from the answering user's side (BUILD_PROMPT.md §9.5, §14.7).
 *
 * The fixture taxonomy (TestTaxonomy) has six items. At the default FLIRTY level four are
 * dealt, gentlest first: romantic and playful (level 1), then flirty and taking the lead
 * (level 2).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PreferenceDiscoveryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository().apply {
        user.value = FakeAuthRepository.DEFAULT_USER
        profile.value = UserProfile(uid = FakeAuthRepository.DEFAULT_USER.uid, contentLevel = Intensity.FLIRTY)
    }
    private var preferences = FakePreferenceRepository()
    private val logged = mutableListOf<AnalyticsEvent>()

    private fun TestScope.viewModel(): PreferenceDiscoveryViewModel =
        PreferenceDiscoveryViewModel(
            taxonomyRepository = FakeTaxonomyRepository(),
            preferences = preferences,
            auth = auth,
            analytics = AnalyticsLogger { logged += it },
        ).also { vm ->
            // uiState is WhileSubscribed, as on a real screen; keep a subscriber alive.
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
            advanceUntilIdle()
        }

    /**
     * Acts, then lets the ViewModel settle. Main is a StandardTestDispatcher (see
     * MainDispatcherRule), so state only moves when the test says so.
     */
    private fun TestScope.act(action: () -> Unit) {
        action()
        advanceUntilIdle()
    }

    private val PreferenceDiscoveryViewModel.state get() = uiState.value
    private val PreferenceDiscoveryViewModel.cardId get() = uiState.value.card?.item?.id

    private fun yes() = PreferenceAnswer(PreferenceValue.YES)

    @Test
    fun `opens on the intro, counting what the user's level would deal`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val vm = viewModel()

            assertThat(vm.state.stage).isEqualTo(DiscoveryStage.Intro)
            assertThat(vm.state.contentLevel).isEqualTo(Intensity.FLIRTY)
            assertThat(vm.state.remainingCount).isEqualTo(4)
            assertThat(vm.state.answeredCount).isEqualTo(0)
        }

    @Test
    fun `deals the gentlest cards first, in authored order within a level`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val vm = viewModel()
            act { vm.start() }

            val dealt = mutableListOf<String?>()
            repeat(4) {
                dealt += vm.cardId
                act { vm.skip() }
            }

            assertThat(dealt).containsExactly(
                TestTaxonomy.romantic.id,
                TestTaxonomy.playful.id,
                TestTaxonomy.flirty.id,
                TestTaxonomy.takeTheLead.id,
            ).inOrder()
        }

    @Test
    fun `never deals a card above the user's own level`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        act { vm.start() }

        assertThat(vm.state.deckSize).isEqualTo(4)
        val dealt = buildList {
            repeat(vm.state.deckSize) {
                add(vm.state.card!!.item)
                act { vm.skip() }
            }
        }
        assertThat(dealt.map { it.intensityFloor.level }.max()).isAtMost(Intensity.FLIRTY.level)
    }

    @Test
    fun `leaves answered cards out of the deck`() = runTest(mainDispatcherRule.testDispatcher) {
        preferences = FakePreferenceRepository(mapOf(TestTaxonomy.romantic.id to yes()))
        val vm = viewModel()

        assertThat(vm.state.remainingCount).isEqualTo(3)
        act { vm.start() }
        assertThat(vm.cardId).isEqualTo(TestTaxonomy.playful.id)
    }

    @Test
    fun `answering saves the answer and moves to the next card`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        act { vm.start() }
        act { vm.answer(yes()) }

        assertThat(preferences.writes).containsExactly(TestTaxonomy.romantic.id to yes())
        assertThat(vm.state.position).isEqualTo(2)
        assertThat(vm.cardId).isEqualTo(TestTaxonomy.playful.id)
    }

    @Test
    fun `secretly curious is saved as a secret curiosity`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        act { vm.start() }
        act { vm.answer(PreferenceAnswer.SECRETLY_CURIOUS) }

        val (_, saved) = preferences.writes.single()
        assertThat(saved.value).isEqualTo(PreferenceValue.CURIOUS)
        assertThat(saved.secret).isTrue()
    }

    @Test
    fun `going back shows the previous card with the answer already given`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val vm = viewModel()
            act { vm.start() }
            act { vm.answer(yes()) }
            act { vm.back() }

            assertThat(vm.state.position).isEqualTo(1)
            assertThat(vm.cardId).isEqualTo(TestTaxonomy.romantic.id)
            assertThat(vm.state.card?.answer).isEqualTo(yes())
        }

    @Test
    fun `giving the same answer again does not write it again`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        act { vm.start() }
        act { vm.answer(yes()) }
        act { vm.back() }
        act { vm.answer(yes()) }

        assertThat(preferences.writes).hasSize(1)
        assertThat(vm.state.position).isEqualTo(2)
    }

    @Test
    fun `changing an answer on the way back replaces it`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        act { vm.start() }
        act { vm.answer(yes()) }
        act { vm.back() }
        act { vm.answer(PreferenceAnswer(PreferenceValue.NOT_FOR_ME)) }

        assertThat(preferences.answers.value[TestTaxonomy.romantic.id])
            .isEqualTo(PreferenceAnswer(PreferenceValue.NOT_FOR_ME))
    }

    @Test
    fun `back from the first card returns to the intro`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        act { vm.start() }
        act { vm.back() }

        assertThat(vm.state.stage).isEqualTo(DiscoveryStage.Intro)
    }

    @Test
    fun `a skipped card is not saved, and is dealt again next time`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        act { vm.start() }
        act { vm.skip() }
        act { vm.finishLater() }

        assertThat(preferences.writes).isEmpty()
        assertThat(vm.state.remainingCount).isEqualTo(4)
        act { vm.start() }
        assertThat(vm.cardId).isEqualTo(TestTaxonomy.romantic.id)
    }

    @Test
    fun `finishing the deck logs how many were answered, and nothing else`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val vm = viewModel()
            act { vm.start() }
            act { vm.answer(yes()) }
            act { vm.skip() }
            act { vm.answer(PreferenceAnswer.SECRETLY_CURIOUS) }
            act { vm.answer(PreferenceAnswer(PreferenceValue.NEVER)) }

            assertThat(vm.state.stage).isEqualTo(DiscoveryStage.Done)
            assertThat(vm.state.answeredThisSession).isEqualTo(3)
            assertThat(logged).containsExactly(
                AnalyticsEvent.PreferencesStarted,
                AnalyticsEvent.PreferencesCompleted(answeredCount = 3),
            ).inOrder()
            // Counts only: no item id and no answer value may ever reach analytics (§17.3).
            logged.flatMap { it.params.values }.forEach { value -> assertThat(value).isInstanceOf(Int::class.java) }
        }

    @Test
    fun `finishing later keeps what was answered and does not claim completion`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val vm = viewModel()
            act { vm.start() }
            act { vm.answer(yes()) }
            act { vm.finishLater() }

            assertThat(vm.state.stage).isEqualTo(DiscoveryStage.Done)
            assertThat(vm.state.remainingCount).isEqualTo(3)
            assertThat(logged).containsExactly(AnalyticsEvent.PreferencesStarted)
        }

    @Test
    fun `raising the level deals the bolder cards too`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        act { vm.setContentLevel(Intensity.BOLD) }

        assertThat(vm.state.contentLevel).isEqualTo(Intensity.BOLD)
        assertThat(vm.state.remainingCount).isEqualTo(TestTaxonomy.taxonomy.items.size)
    }

    @Test
    fun `a refused save says so, and the message can be dismissed`() = runTest(mainDispatcherRule.testDispatcher) {
        preferences.setResult = Outcome.Failure(AppError.PermissionDenied())
        val vm = viewModel()
        act { vm.start() }
        act { vm.answer(yes()) }

        assertThat(vm.state.errorMessage).isNotNull()
        act { vm.dismissError() }
        assertThat(vm.state.errorMessage).isNull()
    }

    @Test
    fun `starting with nothing left to answer does nothing`() = runTest(mainDispatcherRule.testDispatcher) {
        preferences = FakePreferenceRepository(
            listOf(TestTaxonomy.romantic, TestTaxonomy.playful, TestTaxonomy.flirty, TestTaxonomy.takeTheLead)
                .associate { it.id to yes() },
        )
        val vm = viewModel()
        act { vm.start() }

        assertThat(vm.state.stage).isEqualTo(DiscoveryStage.Intro)
        assertThat(vm.state.remainingCount).isEqualTo(0)
        assertThat(logged).isEmpty()
    }

    @Test
    fun `the review lists every answer by category, in taxonomy order`() =
        runTest(mainDispatcherRule.testDispatcher) {
            preferences = FakePreferenceRepository(
                mapOf(
                    TestTaxonomy.takeTheLead.id to PreferenceAnswer(PreferenceValue.MAYBE),
                    TestTaxonomy.intense.id to PreferenceAnswer(PreferenceValue.NEVER),
                    TestTaxonomy.romantic.id to yes(),
                ),
            )
            val vm = viewModel()
            act { vm.openReview() }

            val review = vm.state.review
            assertThat(review.map { it.categoryTitle }).containsExactly("Mood", "Power & dynamics").inOrder()
            assertThat(review[0].entries.map { it.item.id })
                .containsExactly(TestTaxonomy.romantic.id, TestTaxonomy.intense.id).inOrder()
            // Answered above the current level still belongs to the user, so it is listed.
            assertThat(review[0].entries[1].answer).isEqualTo(PreferenceAnswer(PreferenceValue.NEVER))
        }

    @Test
    fun `editing from the review changes the answer and returns to the list`() =
        runTest(mainDispatcherRule.testDispatcher) {
            preferences = FakePreferenceRepository(mapOf(TestTaxonomy.romantic.id to yes()))
            val vm = viewModel()
            act { vm.openReview() }
            act { vm.edit(TestTaxonomy.romantic.id) }

            assertThat(vm.state.stage).isEqualTo(DiscoveryStage.Editing)
            assertThat(vm.state.card?.answer).isEqualTo(yes())

            act { vm.answer(PreferenceAnswer.SECRETLY_CURIOUS) }

            assertThat(vm.state.stage).isEqualTo(DiscoveryStage.Review)
            assertThat(preferences.answers.value[TestTaxonomy.romantic.id]).isEqualTo(PreferenceAnswer.SECRETLY_CURIOUS)
        }

    @Test
    fun `removing an answer forgets it entirely`() = runTest(mainDispatcherRule.testDispatcher) {
        preferences = FakePreferenceRepository(mapOf(TestTaxonomy.romantic.id to yes()))
        val vm = viewModel()
        act { vm.openReview() }
        act { vm.edit(TestTaxonomy.romantic.id) }
        act { vm.clearAnswer() }

        assertThat(preferences.clears).containsExactly(TestTaxonomy.romantic.id)
        assertThat(vm.state.stage).isEqualTo(DiscoveryStage.Review)
        assertThat(vm.state.answeredCount).isEqualTo(0)
        // Unanswered again, so it is dealt again.
        assertThat(vm.state.remainingCount).isEqualTo(4)
    }

    @Test
    fun `closing the review returns to where it was opened`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        act { vm.openReview() }
        act { vm.back() }
        assertThat(vm.state.stage).isEqualTo(DiscoveryStage.Intro)

        act { vm.start() }
        act { vm.finishLater() }
        act { vm.openReview() }
        act { vm.back() }
        assertThat(vm.state.stage).isEqualTo(DiscoveryStage.Done)
    }

    @Test
    fun `answers to items no longer in the taxonomy are not counted`() = runTest(mainDispatcherRule.testDispatcher) {
        preferences = FakePreferenceRepository(mapOf("retired_item" to yes(), TestTaxonomy.romantic.id to yes()))
        val vm = viewModel()

        assertThat(vm.state.answeredCount).isEqualTo(1)
    }

    @Test
    fun `back is handled inside discovery only where there is somewhere to step back to`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val vm = viewModel()
            assertThat(vm.state.handlesBack).isFalse()

            act { vm.start() }
            assertThat(vm.state.handlesBack).isTrue()

            act { vm.finishLater() }
            assertThat(vm.state.handlesBack).isFalse()
        }
}
