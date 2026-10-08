package com.shnapps.couple.feature.instant

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.shnapps.couple.core.designsystem.theme.AfterhoursSurface
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.engine.NightLength
import com.shnapps.couple.core.model.Intensity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The one question Game Night asks before an evening exists.
 *
 * The four lengths used to sit in the dock, eight dp above the button that acts on them, which
 * read as a single five-headed control. A choice goes on the screen with the question it answers;
 * the dock holds the one thing that starts the evening, and nothing else.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h840dp-xhdpi")
class GameNightSnapshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { AfterhoursTheme { AfterhoursSurface { content() } } }
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun howLongHaveYouGot() {
        val state = GameNightUiState(length = NightLength.DEFAULT, tier = Intensity.SPICY)

        capture("game-night-length") { Screen(state) }

        composeRule.onNodeWithText("How long have you got?").assertIsDisplayed()
        composeRule.onNodeWithText("THE EVENING").assertIsDisplayed()
        // One action in the dock, and it is the only thing down there.
        composeRule.onNodeWithText("Start the evening").assertIsDisplayed()
    }

    @Test
    fun theresNotEnoughToPlanOne() {
        val state = GameNightUiState(nothingToPlan = true, tier = Intensity.EXTREME)

        capture("game-night-nothing") { Screen(state) }

        composeRule.onNodeWithText("Not enough at this tier for a whole evening.").assertIsDisplayed()
    }

    @Composable
    private fun Screen(state: GameNightUiState) = GameNightContent(
        state = state,
        onLength = {},
        onPlan = {},
        onNext = {},
        onAgain = {},
        onBack = {},
    )

    private companion object {
        const val SETTLE_MS = 1_000L
    }
}
