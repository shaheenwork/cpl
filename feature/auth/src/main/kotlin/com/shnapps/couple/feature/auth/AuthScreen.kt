package com.shnapps.couple.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.ui.SecureScreen

/**
 * Sign in and sign up (BUILD_PROMPT.md §7).
 *
 * One screen with a mode toggle rather than two, because the fields are identical and the
 * split only ever produces "wrong screen" friction.
 */
@Composable
fun AuthScreen(
    onSignedIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    SecureScreen()

    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.signedIn) {
        if (state.signedIn) onSignedIn()
    }

    AuthContent(
        state = state,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onToggleMode = viewModel::toggleMode,
        onSubmit = viewModel::submit,
        onForgotPassword = viewModel::sendPasswordReset,
        onDismissReset = viewModel::dismissResetConfirmation,
        modifier = modifier,
    )
}

@Composable
internal fun AuthContent(
    state: AuthUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onToggleMode: () -> Unit,
    onSubmit: () -> Unit,
    onForgotPassword: () -> Unit,
    onDismissReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = AfterhoursTheme.spacing
    val keyboard = LocalSoftwareKeyboardController.current
    var passwordVisible by remember { mutableStateOf(false) }

    val isSignUp = state.mode == AuthMode.SignUp

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.gutter, vertical = spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Text(
            text = if (isSignUp) "MAKE IT YOURS" else "WELCOME BACK",
            style = EyebrowTextStyle,
            color = AfterhoursTheme.colors.brass,
        )
        Text(
            text = if (isSignUp) "Create your account." else "Welcome back to your private world.",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.padding(top = spacing.sm))

        EmailField(
            value = state.email,
            onValueChange = onEmailChange,
            enabled = !state.isSubmitting,
        )

        PasswordField(
            value = state.password,
            onValueChange = onPasswordChange,
            enabled = !state.isSubmitting,
            isSignUp = isSignUp,
            visible = passwordVisible,
            onToggleVisibility = { passwordVisible = !passwordVisible },
            onSubmit = {
                keyboard?.hide()
                onSubmit()
            },
        )

        if (state.errorMessage != null) {
            Text(
                text = state.errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { contentDescription = state.errorMessage },
            )
        }

        if (state.resetEmailSent) {
            Text(
                text = "If there's an account with that email, a reset link is on its way.",
                style = MaterialTheme.typography.bodyMedium,
                color = AfterhoursTheme.colors.textMuted,
            )
            GlowButton(
                text = "Got it",
                onClick = onDismissReset,
                style = GlowButtonStyle.Quiet,
            )
        }

        Spacer(Modifier.weight(1f))

        GlowButton(
            text = when {
                state.isSubmitting -> "One moment…"
                isSignUp -> "Create account"
                else -> "Sign in"
            },
            onClick = {
                keyboard?.hide()
                onSubmit()
            },
            enabled = state.canSubmit,
            modifier = Modifier.fillMaxWidth(),
        )

        GlowButton(
            text = if (isSignUp) "I already have an account" else "Create an account",
            onClick = onToggleMode,
            style = GlowButtonStyle.Secondary,
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        )

        if (!isSignUp) {
            GlowButton(
                text = "Forgot your password?",
                onClick = onForgotPassword,
                style = GlowButtonStyle.Quiet,
                enabled = !state.isSubmitting && state.email.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun EmailField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentType = ContentType.EmailAddress },
        label = { Text("Email") },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
        ),
    )
}

/**
 * Split out of [AuthContent] because it carries most of the screen's branching: autofill
 * hint, visibility toggle, supporting text and IME action all differ between signing in
 * and signing up.
 */
@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    isSignUp: Boolean,
    visible: Boolean,
    onToggleVisibility: () -> Unit,
    onSubmit: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                // NewPassword tells the password manager to offer a generated one rather
                // than to autofill an existing entry.
                contentType = if (isSignUp) ContentType.NewPassword else ContentType.Password
            },
        label = { Text("Password") },
        singleLine = true,
        enabled = enabled,
        visualTransformation = if (visible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        supportingText = if (isSignUp) {
            { Text("At least ${AuthUiState.MIN_PASSWORD_LENGTH} characters.") }
        } else {
            null
        },
        trailingIcon = {
            IconButton(onClick = onToggleVisibility) {
                Icon(
                    imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (visible) "Hide password" else "Show password",
                )
            }
        },
    )
}
