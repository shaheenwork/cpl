package com.shnapps.couple.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.shnapps.couple.core.designsystem.theme.AfterhoursSurface
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the first-run screens (BUILD_PROMPT.md §14.1, §3.1).
 *
 * The app cannot yet be run on a device here (HUMAN_SETUP.md §1.3), so these are how the
 * screens are actually seen and reviewed.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h840dp-xhdpi")
class OnboardingSnapshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun ageGate() = capture("age-gate") {
        AgeGateContent(state = AgeGateUiState(), onConfirm = {}, onDecline = {})
    }

    @Test
    fun ageGateWriteFailed() = capture("age-gate-error") {
        AgeGateContent(
            state = AgeGateUiState(errorMessage = "No connection. Check your signal and try again."),
            onConfirm = {},
            onDecline = {},
        )
    }

    @Test
    fun welcome() = capture("welcome") {
        WelcomeScreen(onFinished = {})
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { AfterhoursTheme { AfterhoursSurface { content() } } }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
