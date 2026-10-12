package com.shnapps.couple.feature.applock

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
import com.shnapps.couple.core.model.AppLockMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The app lock as Settings shows it (D-146): on, off, and the PIN it asks before it lets go. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h840dp-xhdpi")
class AppLockSettingsSnapshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { AfterhoursTheme { AfterhoursSurface { content() } } }
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Composable
    private fun settings(state: AppLockSettingsUiState, sensor: Boolean = true) =
        AppLockSettingsContent(state, sensor, AppLockSettingsActions())

    @Test
    fun on() {
        capture("lock-settings-on") { settings(AppLockSettingsUiState(mode = AppLockMode.PIN)) }

        composeRule.onNodeWithText("App lock is on").assertIsDisplayed()
        // The way out is on the screen, and says what it will ask.
        composeRule.onNodeWithText("Turn off app lock").assertIsDisplayed()
        composeRule.onNodeWithText("Change PIN").assertIsDisplayed()
        composeRule.onNodeWithText("Fingerprint").assertIsDisplayed()
    }

    @Test
    fun withTheSensor() {
        capture("lock-settings-biometric") { settings(AppLockSettingsUiState(mode = AppLockMode.BIOMETRIC)) }

        composeRule.onNodeWithText("Opens with your fingerprint, or your PIN.").assertIsDisplayed()
    }

    @Test
    fun aPhoneWithNoSensorIsNotOfferedOne() {
        composeRule.setContent {
            AfterhoursTheme { AfterhoursSurface { settings(AppLockSettingsUiState(mode = AppLockMode.PIN), false) } }
        }

        assertThat(composeRule.onAllNodesWithText("Fingerprint").fetchSemanticsNodes()).isEmpty()
        composeRule.onNodeWithText("Change PIN").assertIsDisplayed()
    }

    @Test
    fun off() {
        capture("lock-settings-off") { settings(AppLockSettingsUiState(mode = AppLockMode.OFF)) }

        composeRule.onNodeWithText("App lock is off").assertIsDisplayed()
        composeRule.onNodeWithText("Turn on app lock").assertIsDisplayed()
        // Nothing to turn off, so nothing offering to.
        assertThat(composeRule.onAllNodesWithText("Turn off app lock").fetchSemanticsNodes()).isEmpty()
    }

    @Test
    fun beforeItIsKnown() {
        composeRule.setContent { AfterhoursTheme { AfterhoursSurface { settings(AppLockSettingsUiState()) } } }

        // Neither state is drawn: the wrong one would offer to turn on something already running.
        for (word in listOf("App lock is on", "App lock is off")) {
            assertThat(composeRule.onAllNodesWithText(word).fetchSemanticsNodes()).isEmpty()
        }
    }

    @Test
    fun theQuestionBeforeTurningItOff() {
        val state = AppLockSettingsUiState(mode = AppLockMode.PIN, asking = LockGate.TURN_OFF, pin = "24")
        capture("lock-settings-turn-off") { settings(state) }

        composeRule.onNodeWithText("Turn off app lock?").assertIsDisplayed()
        composeRule.onNodeWithText("Turn off").assertIsDisplayed()
        composeRule.onNodeWithText("Keep it as it is").assertIsDisplayed()
    }

    @Test
    fun aWrongPin() {
        val state = AppLockSettingsUiState(
            mode = AppLockMode.PIN,
            asking = LockGate.TURN_OFF,
            error = "That PIN didn't match.",
        )
        capture("lock-settings-wrong-pin") { settings(state) }

        composeRule.onNodeWithText("That PIN didn't match.").assertIsDisplayed()
    }

    private companion object {
        const val SETTLE_MS = 2_000L
    }
}
