package com.shnapps.couple.feature.night

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.designsystem.theme.AfterhoursSurface
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.model.ChapterReaction
import com.shnapps.couple.core.model.CloseCheckIn
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.Mood
import com.shnapps.couple.core.model.NightSession
import com.shnapps.couple.core.model.NightSettings
import com.shnapps.couple.core.model.PlannedNight
import com.shnapps.couple.core.model.SessionEndReason
import com.shnapps.couple.core.model.SessionState
import com.shnapps.couple.core.testing.TestNights
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The builder and the player, screen by screen (BUILD_PROMPT.md §14.5, §14.6) — and the
 * promises they keep on screen: a sealed chapter holds nothing readable, the partner's phone
 * never shows a chapter, and STOP is one tap that is never covered.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h840dp-xhdpi")
class NightSnapshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val night: PlannedNight = TestNights.gentle.let { gentle ->
        gentle.copy(
            chapters = gentle.chapters.mapIndexed { index, chapter ->
                chapter.copy(
                    item = chapter.item.copy(
                        title = listOf("One look", "Whisper it", "Slow dance")[index],
                        subtitle = listOf("Across the room", "Close enough to feel it", "The last song")[index],
                        body = listOf(
                            "From across the room, catch each other's eye and hold it. First to smile loses.",
                            "Lean in and say one thing you've been thinking about all evening. Just one.",
                            "Put on one song. Dance slowly, closer than you need to, until it ends.",
                        )[index],
                    ),
                )
            },
        )
    }

    private fun session(
        state: SessionState,
        chapter: Int = -1,
        isHost: Boolean = true,
        consented: Boolean = false,
        endReason: SessionEndReason? = null,
    ) = NightSession(
        id = "s",
        experienceId = night.experienceId,
        state = state,
        pausedFrom = if (state == SessionState.PAUSED) SessionState.CHAPTER_ACTIVE else null,
        currentChapter = chapter,
        chapterCount = night.chapters.size,
        consentRequired = state == SessionState.WAITING_FOR_PARTNER,
        consented = consented,
        partnerConsented = false,
        isHost = isHost,
        endReason = endReason,
    )

    private val atChapterOne get() = session(SessionState.CHAPTER_ACTIVE, 1)

    private fun state(stage: NightStage, session: NightSession? = null, paused: Boolean = false) =
        NightUiState(stage = stage, night = night, session = session, paused = paused)

    // ---- the builder --------------------------------------------------------------------------

    @Test fun builder() = captureBuilder(
        "build-night",
        BuildNightUiState(
            settings = NightSettings(
                durationMin = 45,
                moods = setOf(Mood.NAUGHTY, Mood.MYSTERIOUS),
                intensity = Intensity.NAUGHTY,
            ),
            sharedLevel = Intensity.WILD,
        ),
    )

    @Test fun builderAroundAMatch() = captureBuilder(
        "build-night-focus",
        BuildNightUiState(
            settings = NightSettings(focus = "sensory_massage"),
            sharedLevel = Intensity.NAUGHTY,
            focusPrompt = "Massage",
        ),
    )

    @Test fun builderBuilding() = captureBuilder("build-night-building", BuildNightUiState(building = true))

    @Test fun builderNothingFits() = captureBuilder(
        "build-night-error",
        BuildNightUiState(error = BuildError.NOT_ENOUGH_CONTENT),
    )

    // ---- the player ----------------------------------------------------------------------------

    @Test fun shape() = capture("night-shape", state(NightStage.Shape))

    @Test fun consentHost() = capture(
        "night-consent-host",
        state(NightStage.Consent, session(SessionState.WAITING_FOR_PARTNER)).withBold(),
    )

    @Test fun consentWaiting() = capture(
        "night-consent-waiting",
        state(NightStage.Consent, session(SessionState.WAITING_FOR_PARTNER, consented = true)).withBold(),
    )

    @Test fun consentPartner() = capture(
        "night-consent-partner",
        state(NightStage.Consent, session(SessionState.WAITING_FOR_PARTNER, isHost = false)).withBold(),
    )

    @Test fun countdown() {
        composeRule.mainClock.autoAdvance = false
        capture("night-countdown", state(NightStage.Countdown, session(SessionState.READY)))
    }

    @Test fun checkIn() = capture("night-check-in", state(NightStage.CheckIn(1), atChapterOne))

    @Test fun sealed() = capture("night-sealed", state(NightStage.Chapter(1, open = false), atChapterOne))

    @Test fun open() = capture("night-open", state(NightStage.Chapter(1, open = true), atChapterOne))

    @Test fun keepOut() = capture(
        "night-keep-out",
        state(NightStage.Chapter(1, open = true), session(SessionState.CHAPTER_ACTIVE, 1))
            .copy(keepOut = KeepOutOffer(listOf("mood_charged" to "Charged"))),
    )

    @Test fun between() {
        composeRule.mainClock.autoAdvance = false
        capture("night-between", state(NightStage.Between(2), session(SessionState.CHAPTER_ACTIVE, 1)))
    }

    @Test fun paused() = capture(
        "night-paused",
        state(NightStage.Chapter(1, open = true), session(SessionState.PAUSED, 1), paused = true),
    )

    @Test fun elsewhere() = capture(
        "night-elsewhere",
        state(NightStage.Elsewhere, session(SessionState.CHAPTER_ACTIVE, 1, isHost = false)),
    )

    @Test fun stopped() = capture("night-stopped", state(NightStage.Ended(SessionEndReason.STOPPED)))

    @Test fun howAreWe() = capture("night-how-are-we", state(NightStage.HowAreWe))

    @Test fun howAreWeAnswered() = capture(
        "night-how-are-we-answered",
        state(NightStage.HowAreWe).copy(closeAnswer = CloseCheckIn.A_BIT_MUCH),
    )

    @Test fun feedback() = capture(
        "night-feedback",
        state(NightStage.Feedback).copy(reactions = mapOf(0 to ChapterReaction.LOVED)),
    )

    @Test fun done() = capture("night-done", state(NightStage.Done))

    @Test fun activeNightCard() {
        composeRule.setContent {
            Themed {
                ActiveNightContent(session(SessionState.WAITING_FOR_PARTNER, isHost = false), onOpen = { _, _ -> })
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/night-active-card.png")
    }

    // ---- promises kept on screen ----------------------------------------------------------------

    @Test
    fun `a sealed chapter keeps its content out of the tree entirely`() {
        show(state(NightStage.Chapter(1, open = false), session(SessionState.CHAPTER_ACTIVE, 1)))
        composeRule.onNodeWithText("Whisper it").assertDoesNotExist()
        composeRule.onNodeWithText(night.chapters[1].item.body).assertDoesNotExist()
    }

    @Test
    fun `the partner's phone never shows a chapter`() {
        show(state(NightStage.Elsewhere, session(SessionState.CHAPTER_ACTIVE, 1, isHost = false)))
        for (chapter in night.chapters) composeRule.onNodeWithText(chapter.item.title).assertDoesNotExist()
    }

    @Test
    fun `stop is one tap, and asks nothing`() {
        var stops = 0
        show(state(NightStage.Chapter(1, open = true), atChapterOne), NightActions(onStop = { stops++ }))
        composeRule.onNodeWithText("Stop").performClick()
        assertThat(stops).isEqualTo(1)
        composeRule.onNodeWithText("Are you sure", substring = true).assertDoesNotExist()
    }

    @Test
    fun `nothing under a pause can be touched`() {
        var done = 0
        show(
            state(NightStage.Chapter(1, open = true), session(SessionState.PAUSED, 1), paused = true),
            NightActions(onDone = { done++ }),
        )
        composeRule.onNodeWithText("Done").assertDoesNotExist()
        assertThat(done).isEqualTo(0)
    }

    @Test
    fun `a pause never covers the stop`() {
        show(state(NightStage.Chapter(1, open = true), session(SessionState.PAUSED, 1), paused = true))
        composeRule.onNodeWithText("Paused.").assertIsDisplayed()
        composeRule.onNodeWithText("Stop").assertIsDisplayed()
        composeRule.onNodeWithText("Resume").assertIsDisplayed()
    }

    private fun NightUiState.withBold(): NightUiState = copy(night = TestNights.bold)

    private fun show(state: NightUiState, actions: NightActions = NightActions()) {
        composeRule.setContent { Themed { NightContent(state = state, actions = actions) } }
    }

    private fun capture(name: String, state: NightUiState) {
        show(state)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun captureBuilder(name: String, state: BuildNightUiState) {
        composeRule.setContent { Themed { BuildNightContent(state = state, actions = BuildNightActions()) } }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        AfterhoursTheme { AfterhoursSurface { content() } }
    }
}
