package com.shnapps.couple.feature.pairing

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

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h840dp-xhdpi")
class PairingSnapshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test fun choose() = capture("pairing-choose", PairingUiState())

    @Test fun invite() = capture(
        "pairing-invite",
        // No expiry, so the screenshot does not depend on the wall clock.
        PairingUiState(step = PairingStep.Invite(code = "482913", expiresAtEpochMillis = null)),
    )

    @Test fun confirmPartner() = capture(
        "pairing-confirm",
        PairingUiState(step = PairingStep.ConfirmPartner(listOf("🌙", "🍷", "🔥"))),
    )

    @Test fun enterCode() = capture(
        "pairing-enter",
        PairingUiState(step = PairingStep.EnterCode, codeInput = "4829"),
    )

    @Test fun waiting() = capture(
        "pairing-waiting",
        PairingUiState(step = PairingStep.Waiting(listOf("🌙", "🍷", "🔥"))),
    )

    private fun capture(name: String, state: PairingUiState) {
        composeRule.setContent {
            AfterhoursTheme {
                AfterhoursSurface { Content(state) }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Composable
    private fun Content(state: PairingUiState) = PairingContent(
        state = state,
        onInvite = {}, onHaveCode = {}, onCodeChange = {}, onSubmitCode = {},
        onApprove = {}, onDecline = {}, onCancel = {}, onBack = {}, onShare = {},
    )
}
