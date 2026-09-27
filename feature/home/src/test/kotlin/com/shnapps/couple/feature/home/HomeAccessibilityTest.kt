package com.shnapps.couple.feature.home

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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.designsystem.theme.AfterhoursSurface
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.model.Game
import com.shnapps.couple.core.model.Heat
import com.shnapps.couple.core.model.Mode
import com.shnapps.couple.core.model.Scoring
import com.shnapps.couple.core.model.TurnOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The accessibility audit, run against the one home (BUILD_PROMPT.md §20, D-096).
 *
 * The three rules are the suite's: type scales to 200% without losing anything, nothing you are
 * meant to touch is smaller than 48 dp, and everything interactive says what it is. The last one
 * matters more here than anywhere, because a row on this screen is one target with three pieces of
 * text in it — a screen reader has to hear it as one thing.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h840dp-xhdpi")
class HomeAccessibilityTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `home holds at 200 percent`() {
        capture("a11y-home-200", LARGEST) { HomeContent(state(), HomeActions()) }

        composeRule.onNodeWithText("Start now").assertIsDisplayed()
        composeRule.onNodeWithText("Spin").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `nothing you are meant to touch is smaller than 48 dp`() {
        compose(NORMAL) { HomeContent(state(), HomeActions()) }

        val clickable = composeRule.onAllNodes(isClickable).fetchSemanticsNodes()
        assertThat(clickable).isNotEmpty()
        for (index in clickable.indices) {
            composeRule.onAllNodes(isClickable)[index].assertHeightIsAtLeast(TOUCH_TARGET.dp)
            composeRule.onAllNodes(isClickable)[index].assertWidthIsAtLeast(TOUCH_TARGET.dp)
        }
    }

    @Test
    fun `everything interactive says what it is`() {
        compose(NORMAL) { HomeContent(state(Company.PAIRED), HomeActions()) }

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

    private fun state(company: Company = Company.ALONE) =
        HomeUiState(company = company, games = games, heat = Heat.HOT)

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

        val games = listOf(
            Game(
                id = "SPIN",
                title = "Spin",
                tagline = "It decides. You just do it.",
                description = "",
                modes = setOf(Mode.TOGETHER),
                rounds = 6,
                turns = TurnOrder.ALTERNATE,
                scoring = Scoring.NONE,
                promptKinds = emptyList(),
                steps = emptyList(),
            ),
        )
    }
}
