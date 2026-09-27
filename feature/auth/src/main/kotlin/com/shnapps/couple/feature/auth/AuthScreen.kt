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
import com.shnapps.couple.core.model.AccountOffer
import com.shnapps.couple.core.ui.SecureScreen

/**
 * Sign in and sign up (BUILD_PROMPT.md §7).
 *
 * One screen with a mode toggle rather than two, because the fields are identical and the
 * split only ever produces "wrong screen" friction.
 *
 * Nobody is sent here on the way in. They arrive from a row on home that needs an account, and the
 * screen says which one it was and what the account buys — then offers to go back without it (§6).
 */
@Composable
fun AuthScreen(
    onSignedIn: () -> Unit,
    onBack: () -> Unit,
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
        onBack = onBack,
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
    onBack: () -> Unit,
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
            text = state.offer?.eyebrow() ?: if (isSignUp) "MAKE IT YOURS" else "WELCOME BACK",
            style = EyebrowTextStyle,
            color = AfterhoursTheme.colors.brass,
        )
        Text(
            text = state.offer?.headline()
                ?: if (isSignUp) "Create your account." else "Welcome back to your private world.",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        // Asked for where it buys something, saying what it buys — never on the way in (§6).
        state.offer?.let { offer ->
            Text(
                text = offer.body(),
                style = MaterialTheme.typography.bodyLarge,
                color = AfterhoursTheme.colors.textMuted,
            )
        }

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

        // Nothing here is a wall: whatever they came for, they can go back to playing without it.
        GlowButton(
            text = "Not now, keep playing",
            onClick = onBack,
            style = GlowButtonStyle.Quiet,
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** What an account buys, said where somebody reached for it (§6). The words are the screen's. */
private fun AccountOffer.eyebrow(): String = when (this) {
    AccountOffer.APART -> "WHEN YOU'RE APART"
    AccountOffer.MEMORIES -> "KEEPING IT"
    AccountOffer.EVENING -> "A WHOLE EVENING"
}

private fun AccountOffer.headline(): String = when (this) {
    AccountOffer.APART -> "This one needs the two of you."
    AccountOffer.MEMORIES -> "Keeping this needs an account."
    AccountOffer.EVENING -> "We build this one for you."
}

private fun AccountOffer.body(): String = when (this) {
    AccountOffer.APART ->
        "Send them something, leave something for later, count down to a date. An account, then " +
            "one code between you — and how far you've said you'll go comes with you."
    AccountOffer.MEMORIES ->
        "The evenings you keep, in your own words, and finding each other again on a new phone. " +
            "How far you've said you'll go comes with you."
    AccountOffer.EVENING ->
        "A surprise, a mystery, fifteen minutes, or a whole evening planned — built on the server, " +
            "behind both of your limits, which is why it needs to know who you both are."
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
