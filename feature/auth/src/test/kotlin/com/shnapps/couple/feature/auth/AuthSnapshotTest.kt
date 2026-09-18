package com.shnapps.couple.feature.auth

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
class AuthSnapshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun signIn() = capture("sign-in") {
        AuthContent(
            state = AuthUiState(email = "you@example.com", password = "a-good-password"),
            onEmailChange = {}, onPasswordChange = {}, onToggleMode = {},
            onSubmit = {}, onForgotPassword = {}, onDismissReset = {},
        )
    }

    @Test
    fun signUp() = capture("sign-up") {
        AuthContent(
            state = AuthUiState(mode = AuthMode.SignUp),
            onEmailChange = {}, onPasswordChange = {}, onToggleMode = {},
            onSubmit = {}, onForgotPassword = {}, onDismissReset = {},
        )
    }

    @Test
    fun signInFailed() = capture("sign-in-error") {
        AuthContent(
            state = AuthUiState(
                email = "you@example.com",
                password = "a-good-password",
                errorMessage = "That email and password don't match.",
            ),
            onEmailChange = {}, onPasswordChange = {}, onToggleMode = {},
            onSubmit = {}, onForgotPassword = {}, onDismissReset = {},
        )
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { AfterhoursTheme { AfterhoursSurface { content() } } }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
