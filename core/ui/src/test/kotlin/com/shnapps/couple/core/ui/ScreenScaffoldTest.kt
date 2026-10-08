package com.shnapps.couple.core.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shnapps.couple.core.designsystem.component.LocalBarExtra
import com.shnapps.couple.core.designsystem.theme.AfterhoursSurface
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The bar, and the one control the whole app carries.
 *
 * The chat is drawn as a layer over every screen (D-138), so it has nowhere of its own to be. It
 * floated in the top-right corner for a while, which is exactly where home already had its
 * settings button — and settings simply disappeared underneath it. The layer offers the control
 * now; the bar places it. This is the test that says both are still there.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w392dp-h840dp-xhdpi")
class ScreenScaffoldTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `a screen's own control and the app's both sit in the bar`() {
        composeRule.setContent {
            Framed {
                CompositionLocalProvider(LocalBarExtra provides { Marker("Chat") }) {
                    ScreenScaffold(title = "Home", trailing = { Marker("Settings") }) {
                        Text("whatever the screen is")
                    }
                }
            }
        }

        composeRule.onNodeWithContentDescription("Chat").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Settings").assertIsDisplayed()
    }

    @Test
    fun `with nothing to offer, a screen's bar is unchanged`() {
        composeRule.setContent {
            Framed {
                ScreenScaffold(title = "Home", trailing = { Marker("Settings") }) {
                    Text("whatever the screen is")
                }
            }
        }

        composeRule.onNodeWithContentDescription("Settings").assertIsDisplayed()
    }

    @Test
    fun `a screen with no control of its own still carries the app's`() {
        composeRule.setContent {
            Framed {
                CompositionLocalProvider(LocalBarExtra provides { Marker("Chat") }) {
                    ScreenScaffold(title = "A game") { Text("a round") }
                }
            }
        }

        composeRule.onNodeWithContentDescription("Chat").assertIsDisplayed()
    }

    @Composable
    private fun Framed(content: @Composable () -> Unit) {
        AfterhoursTheme { AfterhoursSurface { content() } }
    }

    /** A stand-in for a real control: this is about where things go, not what they look like. */
    @Composable
    private fun Marker(name: String) {
        Text(
            text = "·",
            modifier = Modifier
                .size(AfterhoursTheme.spacing.touchTarget)
                .semantics { contentDescription = name },
        )
    }
}
