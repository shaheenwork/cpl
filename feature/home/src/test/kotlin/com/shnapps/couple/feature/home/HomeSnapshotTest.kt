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
import com.shnapps.couple.core.model.Mode
import com.shnapps.couple.core.model.NightSession
import com.shnapps.couple.core.model.SessionState
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
    fun apartSaysWhatItIs() {
        capture("home-apart") { HomeContent(HomeUiState(mode = Mode.APART), HomeActions()) }
        composeRule.onNodeWithText("Apart is being built.").assertIsDisplayed()
        // Nothing on this side pretends to work yet.
        assertThat(composeRule.onAllNodesWithText("Surprise us").fetchSemanticsNodes()).isEmpty()
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
