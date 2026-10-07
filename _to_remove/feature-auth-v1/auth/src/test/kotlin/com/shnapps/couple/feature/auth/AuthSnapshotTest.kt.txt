package com.shnapps.couple.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.shnapps.couple.core.designsystem.theme.AfterhoursSurface
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.model.AccountOffer
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
            onSubmit = {}, onForgotPassword = {}, onDismissReset = {}, onBack = {},
        )
    }

    @Test
    fun signUp() = capture("sign-up") {
        AuthContent(
            state = AuthUiState(mode = AuthMode.SignUp),
            onEmailChange = {}, onPasswordChange = {}, onToggleMode = {},
            onSubmit = {}, onForgotPassword = {}, onDismissReset = {}, onBack = {},
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
            onSubmit = {}, onForgotPassword = {}, onDismissReset = {}, onBack = {},
        )
    }

    @Test
    fun `arriving from a row that needs an account is told what it buys`() {
        capture("sign-up-apart") {
            AuthContent(
                state = AuthUiState(mode = AuthMode.SignUp, offer = AccountOffer.APART),
                onEmailChange = {}, onPasswordChange = {}, onToggleMode = {},
                onSubmit = {}, onForgotPassword = {}, onDismissReset = {}, onBack = {},
            )
        }

        composeRule.onNodeWithText("This one needs the two of you.").assertIsDisplayed()
        // Never a wall: whatever they came for, they can go back to playing without it (§6, U6).
        composeRule.onNodeWithText("Not now, keep playing").assertIsDisplayed()
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { AfterhoursTheme { AfterhoursSurface { content() } } }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
