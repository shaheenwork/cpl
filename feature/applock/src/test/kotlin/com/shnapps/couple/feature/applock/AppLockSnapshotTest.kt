package com.shnapps.couple.feature.applock

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.shnapps.couple.core.designsystem.theme.AfterhoursSurface
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.model.AppLockMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h840dp-xhdpi")
class AppLockSnapshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun pinLock() = capture("lock-pin") {
        AppLockContent(
            state = AppLockUiState(mode = AppLockMode.PIN, showPinEntry = true, pin = "12"),
            onPinChange = {}, onSubmitPin = {}, onUsePin = {}, onRetryBiometric = {},
        )
    }

    @Test
    fun biometricLockedOut() = capture("lock-biometric-lockout") {
        AppLockContent(
            state = AppLockUiState(
                mode = AppLockMode.BIOMETRIC,
                showPinEntry = true,
                hasPinFallback = true,
                biometricLockedOut = true,
                errorMessage = "Too many tries. Use your PIN.",
            ),
            onPinChange = {}, onSubmitPin = {}, onUsePin = {}, onRetryBiometric = {},
        )
    }

    @Test
    fun setupEnterPin() = capture("lock-setup") {
        AppLockSetupContent(
            state = AppLockSetupUiState(pin = "24"),
            onDigits = {}, onContinue = {}, onEnableBiometric = {}, onSkipBiometric = {},
        )
    }

    @Test
    fun setupOfferBiometric() = capture("lock-setup-biometric") {
        AppLockSetupContent(
            state = AppLockSetupUiState(step = LockSetupStep.OfferBiometric),
            onDigits = {}, onContinue = {}, onEnableBiometric = {}, onSkipBiometric = {},
        )
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { AfterhoursTheme { AfterhoursSurface { content() } } }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
