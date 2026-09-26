package com.shnapps.couple.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.designsystem.theme.AfterhoursSurface
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.model.ExperienceType
import com.shnapps.couple.core.model.MediaKind
import com.shnapps.couple.core.model.Mode
import com.shnapps.couple.core.model.NightPlan
import com.shnapps.couple.core.model.NightSession
import com.shnapps.couple.core.model.SessionState
import com.shnapps.couple.core.model.SurpriseState
import com.shnapps.couple.core.model.ThreadEntry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Home, screen by screen (BUILD_PROMPT.md §14.2): it opens with "Tonight could get
 * interesting.", never a questionnaire; the two taps that need no thought are the first thing
 * on it; and Apart says what it is rather than offering controls that do nothing (§25).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h840dp-xhdpi")
class HomeSnapshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { AfterhoursTheme { AfterhoursSurface { content() } } }
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun togetherHome() {
        capture("home-together") { HomeContent(HomeUiState(), HomeActions()) }
        composeRule.onNodeWithText("Tonight could get interesting.").assertIsDisplayed()
        composeRule.onNodeWithText("Surprise us").assertIsDisplayed()
        composeRule.onNodeWithText("We have 15 minutes").assertIsDisplayed()
    }

    @Test
    fun apartIsItsOwnWayIn() {
        val state = HomeUiState(
            mode = Mode.APART,
            plannedByMe = NightPlan(experienceId = "for_them", mine = true, chapterCount = 4, opened = false),
        )
        capture("home-apart") { HomeContent(state, HomeActions()) }
        composeRule.onNodeWithText("Start tonight").assertIsDisplayed()
        composeRule.onNodeWithText("Plan a night they'll find later.").assertIsDisplayed()
        // The planner is told it landed, never what is in it (§2.5 #2).
        composeRule.onNodeWithText("You've planned something. They haven't found it yet.").assertIsDisplayed()
        assertThat(composeRule.onAllNodesWithText("Build tonight").fetchSemanticsNodes()).isEmpty()
    }

    @Test
    fun apartIsAThread() {
        // What they left, what was sent, what is still sealed (REDESIGN_PROMPT.md §5.4).
        val state = HomeUiState(
            mode = Mode.APART,
            thread = listOf(
                ThreadEntry.Media(
                    id = "m1",
                    atMillis = NOW,
                    mine = false,
                    kind = MediaKind.VOICE,
                    note = "listen when you're alone",
                    burnAfterView = true,
                    opened = false,
                ),
                ThreadEntry.Left(
                    id = "s1",
                    atMillis = NOW - 1,
                    mine = false,
                    title = null,
                    state = SurpriseState.SCHEDULED,
                    forMoment = null,
                ),
                ThreadEntry.Counting(id = "c1", atMillis = NOW + 1, title = "Friday"),
            ),
        )

        capture("home-apart-thread") { HomeContent(state, HomeActions()) }

        composeRule.onNodeWithText("A voice note, waiting. It goes when you open it.").assertIsDisplayed()
        // The one that matters: a surprise before its moment says only that it exists.
        composeRule.onNodeWithText("Something is coming.").assertIsDisplayed()
    }

    @Test
    fun somethingIsWaiting() {
        val state = HomeUiState(
            waitingForMe = NightPlan(experienceId = "planned", mine = false, chapterCount = 5, opened = false),
        )
        capture("home-waiting") { HomeContent(state, HomeActions()) }
        composeRule.onNodeWithText("They've planned something for you.").assertIsDisplayed()
        composeRule.onNodeWithText("5 chapters. That's all either of you knows until you open it.")
            .assertIsDisplayed()
    }

    @Test
    fun somethingUnderWay() {
        val state = HomeUiState(
            active = NightSession(
                id = "s",
                experienceId = "e",
                state = SessionState.CHAPTER_ACTIVE,
                pausedFrom = null,
                currentChapter = 1,
                chapterCount = 6,
                consentRequired = false,
                consented = false,
                partnerConsented = false,
                isHost = true,
                endReason = null,
                experienceType = ExperienceType.NIGHT,
            ),
        )
        capture("home-under-way") { HomeContent(state, HomeActions()) }
        composeRule.onNodeWithText("Your night is waiting. Pick up where you left off.").assertIsDisplayed()
    }

    private companion object {
        const val SETTLE_MS = 2_000L
    }
}

/** A fixed moment: a golden must not change with the clock. */
private const val NOW = 1_800_000_000_000L
