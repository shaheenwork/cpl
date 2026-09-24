package com.shnapps.couple.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.shnapps.couple.core.designsystem.theme.AfterhoursSurface
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.model.NotificationSettings
import com.shnapps.couple.core.model.QuietHours
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** What the phone may say out loud (BUILD_PROMPT.md §16.2), and how plainly the screen says it. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h840dp-xhdpi")
class SettingsSnapshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { AfterhoursTheme { AfterhoursSurface { content() } } }
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun previewsHiddenByDefault() {
        capture("notifications-default") {
            NotificationSettingsContent(NotificationSettingsUiState(), NotificationSettingsActions())
        }
        composeRule.onNodeWithText("\"Something's waiting for you.\" — and nothing more.").assertIsDisplayed()
        composeRule.onNodeWithText("Let us tell you").assertIsDisplayed()
        composeRule.onNodeWithText("Quiet hours").assertIsDisplayed()
    }

    @Test
    fun everythingOn() {
        val state = NotificationSettingsUiState(
            settings = NotificationSettings(enabled = true, showContent = true, quietHours = QuietHours.NIGHT),
        )
        capture("notifications-shown") { NotificationSettingsContent(state, NotificationSettingsActions()) }
        composeRule.onNodeWithText("\"They left you something 👀\"").assertIsDisplayed()
        composeRule.onNodeWithText("Nothing between 22:00 and 07:00.").assertIsDisplayed()
    }

    private companion object {
        const val SETTLE_MS = 2_000L
    }
}
