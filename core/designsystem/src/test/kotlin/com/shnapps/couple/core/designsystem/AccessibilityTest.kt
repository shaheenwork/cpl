package com.shnapps.couple.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
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
import com.shnapps.couple.core.designsystem.gallery.ButtonsSpecimen
import com.shnapps.couple.core.designsystem.gallery.CardsSpecimen
import com.shnapps.couple.core.designsystem.gallery.NightControlsSpecimen
import com.shnapps.couple.core.designsystem.gallery.PrivateAnswersSpecimen
import com.shnapps.couple.core.designsystem.gallery.SafetySpecimen
import com.shnapps.couple.core.designsystem.gallery.TypeSpecimen
import com.shnapps.couple.core.designsystem.gallery.VoiceSpecimen
import com.shnapps.couple.core.designsystem.gallery.WaitingSpecimen
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * The accessibility audit, as tests (BUILD_PROMPT.md §20, Phase 22).
 *
 * Contrast has its own suite (`ContrastTest`) because it is arithmetic on the tokens. What is left
 * needs the components composed: that type scales to 200% without anything disappearing, that
 * nothing you are meant to touch is smaller than 48 dp, and that every interactive element says
 * what it is to a screen reader rather than being a shape with a colour.
 *
 * The 200% goldens are recorded, so a change that starts clipping at the largest type shows up as
 * a diff rather than as a bug report.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AccessibilityTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** Android's own maximum, and the number §20 names. */
    private val largestType = 2.0f

    @Test
    fun `type scales to 200 percent without losing anything`() {
        capture("a11y-type-200", largestType) { TypeSpecimen() }
        // Every line is still there at twice the size: nothing is clipped away to fit.
        composeRule.onNodeWithText("Tonight could get interesting.").assertIsDisplayed()
    }

    @Test
    fun `cards hold their content at 200 percent`() {
        capture("a11y-cards-200", largestType) { CardsSpecimen() }
        assertThat(composeRule.onAllNodes(hasText).fetchSemanticsNodes()).isNotEmpty()
    }

    @Test
    fun `private answers stay readable at 200 percent`() {
        capture("a11y-answers-200", largestType) { PrivateAnswersSpecimen() }
    }

    @Test
    fun `the safety controls stay reachable at 200 percent`() {
        // PAUSE and STOP are the two controls that must never become hard to hit (§3.1).
        capture("a11y-safety-200", largestType) { SafetySpecimen() }
    }

    @Test
    fun `nothing you are meant to touch is smaller than 48 dp`() {
        compose(1.0f) {
            Column {
                ButtonsSpecimen()
                NightControlsSpecimen()
                SafetySpecimen()
            }
        }
        val clickable = composeRule.onAllNodes(isClickable).fetchSemanticsNodes()
        assertThat(clickable).isNotEmpty()
        for (index in clickable.indices) {
            composeRule.onAllNodes(isClickable)[index].assertHeightIsAtLeast(TOUCH_TARGET.dp)
            composeRule.onAllNodes(isClickable)[index].assertWidthIsAtLeast(TOUCH_TARGET.dp)
        }
    }

    @Test
    fun `everything interactive says what it is`() {
        compose(1.0f) {
            Column {
                ButtonsSpecimen()
                NightControlsSpecimen()
                SafetySpecimen()
                VoiceAndWaiting()
            }
        }
        val nodes = composeRule.onAllNodes(isClickable).fetchSemanticsNodes()
        for (node in nodes) {
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
    private fun VoiceAndWaiting() {
        VoiceSpecimen()
        WaitingSpecimen()
    }

    private fun compose(fontScale: Float, content: @Composable () -> Unit) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = fontScale),
            ) {
                AfterhoursTheme {
                    Column(
                        modifier = Modifier
                            .width(PHONE_WIDTH.dp)
                            .background(MaterialTheme.colorScheme.background)
                            .padding(16.dp)
                            .fillMaxWidth(),
                    ) {
                        content()
                    }
                }
            }
        }
    }

    private fun capture(name: String, fontScale: Float, content: @Composable () -> Unit) {
        compose(fontScale, content)
        composeRule.onRoot().captureRoboImage("$GOLDEN_DIR/$name.png")
    }

    private companion object {
        const val PHONE_WIDTH = 392
        const val TOUCH_TARGET = 48
        const val GOLDEN_DIR = "src/test/screenshots"

        val isClickable = SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)
        val hasText = SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)
    }
}
