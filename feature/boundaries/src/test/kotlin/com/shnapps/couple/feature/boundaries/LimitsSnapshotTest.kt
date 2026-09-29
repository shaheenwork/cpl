package com.shnapps.couple.feature.boundaries

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.shnapps.couple.core.designsystem.theme.AfterhoursSurface
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.model.LimitAnswer
import com.shnapps.couple.core.model.LimitTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The limits profile, drawn: eight rows, three answers, nothing required (M3). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h840dp-xhdpi")
class LimitsSnapshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `nothing answered says so, rather than looking like eight noes`() {
        capture("limits-empty") { content(LimitsUiState(loading = false)) }

        composeRule.onNodeWithText("What are you up for?").assertIsDisplayed()
        composeRule.onNodeWithText("Kissing").assertIsDisplayed()
        // Eight rows is longer than a phone, so the later ones exist rather than show. That is the
        // screen behaving: it scrolls, and nothing below the fold is required to be answered.
        composeRule.onNodeWithText("Power play").assertExists()
        composeRule.onNodeWithText(
            "Nothing set. Leave it that way if you like — how far tonight goes is the tier.",
        ).assertExists()
    }

    @Test
    fun `answers show on their own rows, and the count is honest`() {
        val state = LimitsUiState(
            rows = LimitTag.entries.map { tag ->
                LimitRow(
                    tag = tag,
                    answer = when (tag) {
                        LimitTag.POWER_PLAY -> LimitAnswer.NO
                        LimitTag.TEASING -> LimitAnswer.YES
                        LimitTag.SENSORY -> LimitAnswer.MAYBE
                        else -> null
                    },
                )
            },
            loading = false,
        )
        capture("limits-answered") { content(state) }

        composeRule.onNodeWithText("3 of 8 answered. You can change any of them any time.").assertExists()
        // Each answer is reachable by name, because five dots and three words are not a label.
        composeRule.onNodeWithContentDescription("Power play: No").assertExists()
        composeRule.onNodeWithContentDescription("Teasing: Yes").assertExists()
    }

    @Composable
    private fun content(state: LimitsUiState) {
        LimitsContent(state = state, onAnswer = { _, _ -> }, onDismissError = {}, onBack = {})
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { AfterhoursTheme { AfterhoursSurface { content() } } }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
