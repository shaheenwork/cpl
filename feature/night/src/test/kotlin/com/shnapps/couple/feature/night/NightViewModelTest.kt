package com.shnapps.couple.feature.night

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.analytics.AnalyticsEvent
import com.shnapps.couple.core.analytics.AnalyticsLogger
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.engine.RemixModifier
import com.shnapps.couple.core.model.BoundaryLevel
import com.shnapps.couple.core.model.ChapterReaction
import com.shnapps.couple.core.model.CloseCheckIn
import com.shnapps.couple.core.model.NightSession
import com.shnapps.couple.core.model.PlannedNight
import com.shnapps.couple.core.model.SessionEndReason
import com.shnapps.couple.core.model.SessionState
import com.shnapps.couple.core.testing.FakeNightRepository
import com.shnapps.couple.core.testing.FakeTaxonomyRepository
import com.shnapps.couple.core.testing.MainDispatcherRule
import com.shnapps.couple.core.testing.TestNights
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * The experience player on both phones (BUILD_PROMPT.md §3.1, §14.6), against a backend that
 * moves sessions the way the server does: a whole night, consent from each phone, PAUSE and
 * STOP from either, and "Not this one".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NightViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun test(body: suspend TestScope.() -> Unit) = runTest(mainDispatcherRule.testDispatcher) { body() }

    private val logged = mutableListOf<AnalyticsEvent>()

    /** A night that never checks in unprompted, so chapters open straight onto their cards. */
    private val quiet: PlannedNight = TestNights.gentle.let { night ->
        val seed = (1L..1000L).first { seed ->
            night.chapters.indices.none { NightScript.checkInDue(night.copy(seed = seed), it) }
        }
        night.copy(seed = seed)
    }

    private fun TestScope.player(nights: FakeNightRepository, experienceId: String = nights.night.experienceId) =
        NightViewModel(
            savedStateHandle = SavedStateHandle(mapOf("experienceId" to experienceId)),
            nights = nights,
            taxonomy = FakeTaxonomyRepository(),
            analytics = AnalyticsLogger { logged += it },
        ).also { vm ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
            advanceUntilIdle()
        }

    private fun TestScope.act(action: () -> Unit) {
        action()
        advanceUntilIdle()
    }

    private val NightViewModel.stage get() = uiState.value.stage

    /** From the shape to the first chapter's sealed card. */
    private fun TestScope.toFirstChapter(vm: NightViewModel) {
        act(vm::begin)
        assertThat(vm.stage).isEqualTo(NightStage.Countdown)
        act(vm::onCountdownDone)
    }

    @Test
    fun `plays a whole night on the host's phone, one sealed chapter at a time`() = test {
        val nights = FakeNightRepository(quiet)
        val vm = player(nights)
        assertThat(vm.stage).isEqualTo(NightStage.Shape)

        toFirstChapter(vm)
        for (index in quiet.chapters.indices) {
            assertThat(vm.stage).isEqualTo(NightStage.Chapter(index, open = false))
            act { vm.open(index) }
            assertThat(vm.stage).isEqualTo(NightStage.Chapter(index, open = true))
            act { vm.done(index) }
            if (index < quiet.chapters.lastIndex) {
                assertThat(vm.stage).isEqualTo(NightStage.Between(index + 1))
                act { vm.onBetweenDone(index + 1) }
            }
        }
        assertThat(vm.stage).isEqualTo(NightStage.HowAreWe)
        assertThat(nights.calls).containsExactly("load", "start", "begin", "finish:0", "finish:1", "finish:2").inOrder()
        assertThat(logged.map { it.name }).containsExactly("experience_started", "experience_completed").inOrder()
    }

    @Test
    fun `closes with a check-in, then private feedback, then done`() = test {
        val nights = FakeNightRepository(quiet)
        val vm = player(nights)
        toFirstChapter(vm)
        quiet.chapters.indices.forEach { index -> act { vm.done(index) } }
        assertThat(vm.stage).isEqualTo(NightStage.HowAreWe)

        act { vm.answerClose(CloseCheckIn.A_BIT_MUCH) }
        assertThat(vm.uiState.value.closeAnswer).isEqualTo(CloseCheckIn.A_BIT_MUCH)
        act(vm::toFeedback)
        assertThat(vm.stage).isEqualTo(NightStage.Feedback)
        act { vm.react(0, ChapterReaction.LOVED) }
        act { vm.react(2, ChapterReaction.NOT_FOR_US) }
        act(vm::saveFeedback)
        assertThat(vm.stage).isEqualTo(NightStage.Done)
        assertThat(nights.reactions).containsExactly(0 to ChapterReaction.LOVED, 2 to ChapterReaction.NOT_FOR_US)
    }

    @Test
    fun `a reaction that could not be saved keeps the couple on it, told so`() = test {
        val nights = FakeNightRepository(quiet)
        val vm = player(nights)
        toFirstChapter(vm)
        quiet.chapters.indices.forEach { index -> act { vm.done(index) } }
        act(vm::toFeedback)
        act { vm.react(1, ChapterReaction.FINE) }
        nights.failNext = AppError.Network()
        act(vm::saveFeedback)
        assertThat(vm.stage).isEqualTo(NightStage.Feedback)
        assertThat(vm.uiState.value.error).isEqualTo(NightError.OFFLINE)

        act(vm::saveFeedback)
        assertThat(vm.stage).isEqualTo(NightStage.Done)
        assertThat(nights.reactions).containsExactly(1 to ChapterReaction.FINE)
    }

    @Test
    fun `a BOLD night waits for both partners to say they're in, each on their own phone`() = test {
        val nights = FakeNightRepository(TestNights.bold)
        val vm = player(nights)
        act(vm::begin)
        assertThat(vm.stage).isEqualTo(NightStage.Consent)
        assertThat(vm.uiState.value.session?.consented).isFalse()

        act(vm::consent)
        assertThat(vm.stage).isEqualTo(NightStage.Consent)
        assertThat(vm.uiState.value.session?.consented).isTrue()
        assertThat(nights.calls).doesNotContain("begin")

        act(nights::partnerConsents)
        assertThat(vm.stage).isEqualTo(NightStage.Countdown)
    }

    @Test
    fun `not tonight ends a night waiting for consent, warmly`() = test {
        val nights = FakeNightRepository(TestNights.bold)
        val vm = player(nights)
        act(vm::begin)
        act(vm::decline)
        assertThat(vm.stage).isEqualTo(NightStage.Ended(SessionEndReason.DECLINED))
    }

    @Test
    fun `stop is one tap from every live stage, and asks nothing`() = test {
        for (reach in 0..3) {
            val nights = FakeNightRepository(quiet)
            val vm = player(nights)
            toFirstChapter(vm)
            if (reach >= 1) act { vm.open(0) }
            if (reach >= 2) act { vm.done(0) }
            if (reach >= 3) act(vm::togglePause)
            act(vm::stop)
            assertThat(vm.stage).isEqualTo(NightStage.Ended(SessionEndReason.STOPPED))
            assertThat(nights.calls.last()).isEqualTo("stop")
        }
    }

    @Test
    fun `stop goes through even while something else is on its way`() = test {
        val nights = FakeNightRepository(quiet)
        val vm = player(nights)
        toFirstChapter(vm)
        val onItsWay = CompletableDeferred<Unit>()
        nights.holdFinish = onItsWay
        act { vm.done(0) }
        assertThat(vm.uiState.value.busy).isTrue()

        act(vm::stop)
        assertThat(nights.calls.last()).isEqualTo("stop")
        assertThat(vm.stage).isEqualTo(NightStage.Ended(SessionEndReason.STOPPED))
        onItsWay.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `either phone can pause, and the night resumes where it was`() = test {
        val nights = FakeNightRepository(quiet)
        val vm = player(nights)
        toFirstChapter(vm)
        act { vm.open(0) }
        act(nights::partnerPauses)
        assertThat(vm.uiState.value.paused).isTrue()
        assertThat(vm.stage).isEqualTo(NightStage.Chapter(0, open = true))
        act(vm::togglePause)
        assertThat(vm.uiState.value.paused).isFalse()
        assertThat(nights.calls.last()).isEqualTo("resume")
    }

    @Test
    fun `a night stopped on the other phone ends here too`() = test {
        val nights = FakeNightRepository(quiet)
        val vm = player(nights)
        toFirstChapter(vm)
        act(nights::partnerStops)
        assertThat(vm.stage).isEqualTo(NightStage.Ended(SessionEndReason.STOPPED))
    }

    @Test
    fun `not this one swaps the chapter at once, and offers to keep its theme out`() = test {
        val refused = quiet.chapters[1].item.copy(excludedByBoundaries = setOf("mood_charged"))
        val night = quiet.copy(chapters = quiet.chapters.map { if (it.index == 1) it.copy(item = refused) else it })
        val replacement = night.chapters[1].copy(item = refused.copy(id = "tease_other", title = "Something else"))
        val swapped = night.copy(chapters = night.chapters.map { if (it.index == 1) replacement else it })
        val nights = FakeNightRepository(night).apply { swapWith = { index -> swapped.takeIf { index == 1 } } }
        val vm = player(nights)
        toFirstChapter(vm)
        act { vm.done(0) }
        act { vm.onBetweenDone(1) }
        act { vm.open(1) }

        act { vm.notThisOne(1) }
        assertThat(vm.stage).isEqualTo(NightStage.Chapter(1, open = true))
        assertThat(vm.uiState.value.night?.chapters?.get(1)?.item?.id).isEqualTo("tease_other")
        assertThat(vm.uiState.value.keepOut?.themes).containsExactly("mood_charged" to "Charged")
        assertThat(logged.map { it.name }).contains("experience_skipped")

        act { vm.keepOut("mood_charged", BoundaryLevel.NEVER, 1) }
        assertThat(nights.calls).contains("keepOut:mood_charged:NEVER")
        assertThat(vm.uiState.value.keepOut).isNull()
    }

    @Test
    fun `make it more rebuilds the night from what was released, and says when it cannot`() = test {
        val remixed = quiet.copy(chapters = quiet.chapters.reversed().mapIndexed { index, chapter ->
            chapter.copy(index = index)
        })
        val nights = FakeNightRepository(quiet).apply { remixWith = { remixed } }
        val vm = player(nights)
        assertThat(vm.stage).isEqualTo(NightStage.Shape)

        act { vm.makeIt(RemixModifier.MORE_INTENSE) }

        assertThat(nights.calls).contains("makeIt:MORE_INTENSE")
        assertThat(vm.uiState.value.night?.chapters?.first()?.item?.id).isEqualTo(remixed.chapters.first().item.id)
        assertThat(vm.uiState.value.remixFailed).isFalse()

        // Nothing released can make that night: said plainly, and the night stays as it was.
        nights.remixWith = { null }
        act { vm.makeIt(RemixModifier.SHORTER) }
        assertThat(vm.uiState.value.remixFailed).isTrue()
        assertThat(vm.uiState.value.night?.chapters?.first()?.item?.id).isEqualTo(remixed.chapters.first().item.id)
        act(vm::dismissRemixFailed)
        assertThat(vm.uiState.value.remixFailed).isFalse()
    }

    @Test
    fun `says so when nothing else released fits, and lets the couple skip it`() = test {
        val nights = FakeNightRepository(quiet)
        val vm = player(nights)
        toFirstChapter(vm)
        act { vm.open(0) }
        act { vm.notThisOne(0) }
        assertThat(vm.uiState.value.nothingElseFits).isTrue()
        act { vm.done(0) }
        assertThat(nights.calls.last()).isEqualTo("finish:0")
    }

    @Test
    fun `asks the host first before an item they asked to be asked about`() = test {
        val night = quiet.copy(askFirstItems = setOf(quiet.chapters[0].item.id))
        val vm = player(FakeNightRepository(night))
        toFirstChapter(vm)
        assertThat(vm.stage).isEqualTo(NightStage.CheckIn(0))
        act { vm.keepGoing(0) }
        assertThat(vm.stage).isEqualTo(NightStage.Chapter(0, open = false))
    }

    @Test
    fun `the partner's phone follows the night without showing it`() = test {
        val nights = FakeNightRepository(quiet)
        nights.session.value = guestSession(SessionState.CHAPTER_ACTIVE, chapter = 1)
        val vm = player(nights)
        assertThat(vm.stage).isEqualTo(NightStage.Elsewhere)
        assertThat(vm.uiState.value.chapterNumber).isEqualTo(2)
        act(vm::stop)
        assertThat(vm.stage).isEqualTo(NightStage.Ended(SessionEndReason.STOPPED))
    }

    @Test
    fun `the partner's phone asks for their own consent`() = test {
        val nights = FakeNightRepository(TestNights.bold)
        val waiting = guestSession(SessionState.WAITING_FOR_PARTNER, experienceId = TestNights.bold.experienceId)
        nights.session.value = waiting
        val vm = player(nights)
        assertThat(vm.stage).isEqualTo(NightStage.Consent)
        act(vm::consent)
        assertThat(nights.calls).contains("consent")
    }

    @Test
    fun `a night that cannot be found says so, rather than spinning`() = test {
        val nights = FakeNightRepository().apply { loadResult = Outcome.Failure(AppError.NotFound()) }
        assertThat(player(nights).stage).isEqualTo(NightStage.Missing)
    }

    @Test
    fun `an unreachable server is said plainly, and the night waits`() = test {
        val nights = FakeNightRepository(quiet)
        val vm = player(nights)
        nights.failNext = AppError.Network()
        act(vm::begin)
        assertThat(vm.uiState.value.error).isEqualTo(NightError.OFFLINE)
        assertThat(vm.stage).isEqualTo(NightStage.Shape)
    }

    private fun guestSession(
        state: SessionState,
        chapter: Int = -1,
        experienceId: String = quiet.experienceId,
    ) = NightSession(
        id = FakeNightRepository.SESSION_ID,
        experienceId = experienceId,
        state = state,
        pausedFrom = null,
        currentChapter = chapter,
        chapterCount = 3,
        consentRequired = state == SessionState.WAITING_FOR_PARTNER,
        consented = false,
        partnerConsented = true,
        isHost = false,
        endReason = null,
    )
}
