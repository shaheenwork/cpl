package com.shnapps.couple.feature.instant

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.designsystem.theme.AfterhoursSurface
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.engine.InstantCard
import com.shnapps.couple.core.model.Intensity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The accessibility audit, run against the screens the rebuild added (BUILD_PROMPT.md §20).
 *
 * `AccessibilityTest` in `:core:designsystem` holds the components; these are the two screens a
 * person meets before anything else, and they were written after that suite existed. The same three
 * rules apply: type scales to 200% without losing anything, nothing you are meant to touch is
 * smaller than 48 dp, and everything interactive says what it is.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h840dp-xhdpi")
class InstantAccessibilityTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the first screen holds at 200 percent`() {
        capture("a11y-instant-start-200", LARGEST) {
            StartContent(
                state = StartUiState(tier = Intensity.HOT),
                onTier = {},
                onConfirm = {},
                onNotOldEnough = {},
            )
        }

        // The two things this screen exists to ask are still there at the largest type.
        composeRule.onNodeWithText("We're both 18 or over — start").assertIsDisplayed()
        composeRule.onNodeWithText("Change it any time, mid-game, with one thumb.").assertIsDisplayed()
    }

    @Test
    fun `the card holds at 200 percent`() {
        capture("a11y-instant-card-200", LARGEST) { play() }

        composeRule.onNodeWithText("Done").assertIsDisplayed()
        composeRule.onNodeWithText("Too far").assertIsDisplayed()
    }

    @Test
    fun `nothing you are meant to touch is smaller than 48 dp`() {
        // The player alone, not stacked under another screen: it fills the window now, so a
        // second screen above it would squeeze its own content to nothing and measure zero.
        compose(NORMAL) { play() }

        val clickable = composeRule.onAllNodes(isClickable).fetchSemanticsNodes()
        assertThat(clickable).isNotEmpty()
        for (index in clickable.indices) {
            composeRule.onAllNodes(isClickable)[index].assertHeightIsAtLeast(TOUCH_TARGET.dp)
            composeRule.onAllNodes(isClickable)[index].assertWidthIsAtLeast(TOUCH_TARGET.dp)
        }
    }

    @Test
    fun `everything interactive says what it is`() {
        compose(NORMAL) { play() }

        for (node in composeRule.onAllNodes(isClickable).fetchSemanticsNodes()) {
            val text = node.config.getOrNull(SemanticsProperties.Text)
                ?.joinToString { annotated -> annotated.text }
                .orEmpty()
            val description = node.config.getOrNull(SemanticsProperties.ContentDescription)
                ?.joinToString()
                .orEmpty()
            assertThat(text + description).isNotEmpty()
        }
    }

    @Composable
    private fun play() {
        InstantPlayContent(
            // Turned over, so the audit sees the card a couple actually reads.
            state = InstantPlayUiState(
                card = card,
                tier = Intensity.HOT,
                loading = false,
                position = 2,
                revealedId = card.id,
            ),
            actions = DeckActions(),
        )
    }

    private fun compose(fontScale: Float, content: @Composable () -> Unit) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = fontScale),
            ) {
                AfterhoursTheme { AfterhoursSurface { content() } }
            }
        }
    }

    private fun capture(name: String, fontScale: Float, content: @Composable () -> Unit) {
        compose(fontScale, content)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private companion object {
        /** Android's own maximum, and the number §20 names. */
        const val LARGEST = 2.0f
        const val NORMAL = 1.0f
        const val TOUCH_TARGET = 48

        val isClickable = SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)

        val card = InstantCard(
            id = "tease_opposite_corners",
            title = "Opposite Corners",
            body = "Sit at opposite sides of the room and talk only by message.",
            minutes = 5,
            intensity = Intensity.SPICY,
            pack = "TEASE",
            aftercare = false,
            themes = setOf("teasing_anticipation"),
        )
    }
}
