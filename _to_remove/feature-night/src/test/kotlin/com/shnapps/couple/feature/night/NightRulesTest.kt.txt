package com.shnapps.couple.feature.night

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.Leadership
import com.shnapps.couple.core.model.NightSession
import com.shnapps.couple.core.model.PlannedNight
import com.shnapps.couple.core.model.SessionEndReason
import com.shnapps.couple.core.model.SessionState
import com.shnapps.couple.core.testing.TestNights
import org.junit.Test

/**
 * The player's rules, without a screen (BUILD_PROMPT.md §3.1, §3.2, §14.6): who leads, when
 * the host checks in, and which stage each phone shows for every state of the server's session.
 */
class NightRulesTest {

    private val gentle = TestNights.gentle

    private fun session(
        state: SessionState,
        chapter: Int = -1,
        isHost: Boolean = true,
        pausedFrom: SessionState? = null,
        endReason: SessionEndReason? = null,
    ) = NightSession(
        id = "s",
        experienceId = gentle.experienceId,
        state = state,
        pausedFrom = pausedFrom,
        currentChapter = chapter,
        chapterCount = gentle.chapters.size,
        consentRequired = false,
        consented = false,
        partnerConsented = false,
        isHost = isHost,
        endReason = endReason,
    )

    private fun stage(session: NightSession?, step: PlayerStep = PlayerStep.None, night: PlannedNight = gentle) =
        PlayerStages.stageOf(night, session, awaitingSession = false, step = step)

    // ---- who leads ---------------------------------------------------------------------------

    @Test
    fun `me and you lead every chapter, switch alternates, random is fixed by the seed`() {
        assertThat((0..5).map { NightScript.leadFor(Leadership.ME, it, 1) }.toSet()).containsExactly(Lead.YOU)
        assertThat((0..5).map { NightScript.leadFor(Leadership.YOU, it, 1) }.toSet()).containsExactly(Lead.PARTNER)
        assertThat((0..3).map { NightScript.leadFor(Leadership.SWITCH, it, 1) })
            .containsExactly(Lead.YOU, Lead.PARTNER, Lead.YOU, Lead.PARTNER).inOrder()
        val random = (0..9).map { NightScript.leadFor(Leadership.RANDOM, it, 42) }
        assertThat(random).isEqualTo((0..9).map { NightScript.leadFor(Leadership.RANDOM, it, 42) })
        assertThat(random.toSet()).containsExactly(Lead.YOU, Lead.PARTNER)
    }

    // ---- check-ins -----------------------------------------------------------------------------

    @Test
    fun `always checks in before something the host asked to be asked about first`() {
        for (index in gentle.chapters.indices) {
            val night = gentle.copy(askFirstItems = setOf(gentle.chapters[index].item.id))
            assertThat(NightScript.checkInDue(night, index)).isTrue()
        }
    }

    @Test
    fun `checks in at other moments too, so a check-in never gives a limit away`() {
        // Across many nights with no ask-first at all, check-ins still happen — about one
        // chapter in four after the first — so seeing one says nothing about why.
        var due = 0
        var total = 0
        for (seed in 1L..400L) {
            val night = gentle.copy(seed = seed)
            for (index in 1 until night.chapters.size) {
                total++
                if (NightScript.checkInDue(night, index)) due++
            }
        }
        assertThat(due.toDouble() / total).isWithin(0.08).of(0.25)
        // Never before the first chapter without a reason: the night has only just begun.
        assertThat((1L..400L).none { NightScript.checkInDue(gentle.copy(seed = it), 0) }).isTrue()
    }

    @Test
    fun `checks in on the step up into BOLD`() {
        // NAUGHTY, then BOLD: the step that needed both partners' "I'm in".
        assertThat(TestNights.bold.chapters.map { it.intensity })
            .containsExactly(Intensity.NAUGHTY, Intensity.BOLD, Intensity.BOLD).inOrder()
        for (seed in 1L..20L) assertThat(NightScript.checkInDue(TestNights.bold.copy(seed = seed), 1)).isTrue()
    }

    @Test
    fun `the same night checks in at the same moments, however often it is opened`() {
        val first = gentle.chapters.indices.map { NightScript.checkInDue(gentle, it) }
        assertThat(gentle.chapters.indices.map { NightScript.checkInDue(gentle, it) }).isEqualTo(first)
    }

    // ---- stages ---------------------------------------------------------------------------------

    @Test
    fun `the shape comes first, then consent, countdown and chapters on the host's phone`() {
        assertThat(stage(null)).isEqualTo(NightStage.Shape)
        assertThat(stage(session(SessionState.WAITING_FOR_PARTNER))).isEqualTo(NightStage.Consent)
        assertThat(stage(session(SessionState.READY))).isEqualTo(NightStage.Countdown)
        val chapter = stage(session(SessionState.CHAPTER_ACTIVE, 1), night = gentle.copy(seed = sealedSeed(1)))
        assertThat(chapter).isEqualTo(NightStage.Chapter(1, open = false))
    }

    @Test
    fun `the partner's phone shows the night is elsewhere, never its chapters`() {
        assertThat(stage(session(SessionState.READY, isHost = false))).isEqualTo(NightStage.Elsewhere)
        for (chapter in gentle.chapters.indices) {
            val elsewhere = stage(session(SessionState.CHAPTER_ACTIVE, chapter, isHost = false))
            assertThat(elsewhere).isEqualTo(NightStage.Elsewhere)
        }
        assertThat(stage(session(SessionState.WAITING_FOR_PARTNER, isHost = false))).isEqualTo(NightStage.Consent)
    }

    @Test
    fun `a pause keeps the night where it was`() {
        val paused = session(SessionState.PAUSED, 1, pausedFrom = SessionState.CHAPTER_ACTIVE)
        val step = PlayerStep.Open(1, open = true)
        assertThat(stage(paused, step)).isEqualTo(NightStage.Chapter(1, open = true))
    }

    @Test
    fun `every ending ends warmly, with its reason and nobody's name`() {
        for (reason in listOf(SessionEndReason.STOPPED, SessionEndReason.DECLINED, SessionEndReason.EXPIRED)) {
            val ended = stage(session(SessionState.ABANDONED, 1, endReason = reason))
            assertThat(ended).isEqualTo(NightStage.Ended(reason))
        }
    }

    @Test
    fun `after the last chapter comes the check-in, then feedback, then done`() {
        val done = session(SessionState.COMPLETED, 2, endReason = SessionEndReason.COMPLETED)
        assertThat(stage(done)).isEqualTo(NightStage.HowAreWe)
        assertThat(stage(done, PlayerStep.Feedback)).isEqualTo(NightStage.Feedback)
        assertThat(stage(done, PlayerStep.Done)).isEqualTo(NightStage.Done)
        // The guest's phone closes the night too.
        assertThat(stage(done.copy(isHost = false))).isEqualTo(NightStage.HowAreWe)
    }

    @Test
    fun `waits between chapters until the server catches up, and never flashes the old one`() {
        val atOne = session(SessionState.CHAPTER_ACTIVE, 1)
        assertThat(stage(atOne, PlayerStep.Between(2))).isEqualTo(NightStage.Between(2))
        assertThat(stage(atOne, PlayerStep.Open(2, open = false))).isEqualTo(NightStage.Between(2))
    }

    @Test
    fun `passes over a chapter nothing released can replace, unseen`() {
        val night = gentle.copy(unplayable = setOf(1))
        assertThat(stage(session(SessionState.CHAPTER_ACTIVE, 1), night = night)).isEqualTo(NightStage.Between(2))
    }

    @Test
    fun `is loading until both the night and its session are known`() {
        val noNight = PlayerStages.stageOf(null, null, awaitingSession = false, step = PlayerStep.None)
        val noSessionYet = PlayerStages.stageOf(gentle, null, awaitingSession = true, step = PlayerStep.None)
        assertThat(noNight).isEqualTo(NightStage.Loading)
        assertThat(noSessionYet).isEqualTo(NightStage.Loading)
    }

    /** A seed whose night does not check in before chapter [index], for a plain chapter stage. */
    private fun sealedSeed(index: Int): Long =
        (1L..1000L).first { !NightScript.checkInDue(gentle.copy(seed = it), index) }
}
