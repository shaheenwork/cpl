package com.shnapps.couple.feature.applock

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.model.AppLockMode
import com.shnapps.couple.core.security.BiometricAuthenticator
import com.shnapps.couple.core.ui.SecureScreen
import kotlinx.coroutines.launch

/**
 * The lock screen (BUILD_PROMPT.md §3.4, §57).
 *
 * Shows nothing about the couple, the app or what is inside it. Someone holding the phone
 * should learn nothing from this screen, which is why the copy is the product's voice
 * rather than a name or a photo.
 *
 * There is no "forgot PIN" escape hatch here on purpose. The lock is local, so a reset
 * path would just be a way around it; signing out and back in is the documented recovery
 * route, and it is deliberately more effort than remembering the PIN.
 */
@Composable
fun AppLockScreen(
    onUnlocked: () -> Unit,
    biometricAuthenticator: BiometricAuthenticator,
    modifier: Modifier = Modifier,
    viewModel: AppLockViewModel = hiltViewModel(),
) {
    SecureScreen()

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current as? FragmentActivity

    LaunchedEffect(state.unlocked) {
        if (state.unlocked) onUnlocked()
    }

    // Prompt once on arrival when biometrics are the configured mode.
    LaunchedEffect(state.mode, activity) {
        if (state.mode == AppLockMode.BIOMETRIC && activity != null) {
            viewModel.onBiometricResult(
                biometricAuthenticator.authenticate(
                    activity = activity,
                    title = "Welcome back",
                    subtitle = "Unlock your private world",
                    negativeButtonText = "Use PIN",
                ),
            )
        }
    }

    AppLockContent(
        state = state,
        onPinChange = viewModel::onPinChange,
        onSubmitPin = viewModel::submitPin,
        onUsePin = viewModel::showPinEntry,
        onRetryBiometric = {
            if (activity != null) {
                viewModel.onBiometricResult(
                    biometricAuthenticator.authenticate(
                        activity = activity,
                        title = "Welcome back",
                        subtitle = "Unlock your private world",
                        negativeButtonText = "Use PIN",
                    ),
                )
            }
        },
        modifier = modifier,
    )
}

@Composable
internal fun AppLockContent(
    state: AppLockUiState,
    onPinChange: (String) -> Unit,
    onSubmitPin: () -> Unit,
    onUsePin: () -> Unit,
    onRetryBiometric: suspend () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = AfterhoursTheme.spacing

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = spacing.gutter, vertical = spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        Text(
            text = "LOCKED",
            style = EyebrowTextStyle,
            color = AfterhoursTheme.colors.brass,
        )
        Text(
            text = "Welcome back to your private world.",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        if (state.showPinEntry) {
            OutlinedTextField(
                value = state.pin,
                onValueChange = onPinChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = spacing.md)
                    .semantics { contentDescription = "Enter your PIN" },
                label = { Text("PIN") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onSubmitPin() }),
                isError = state.errorMessage != null,
            )
        }

        if (state.errorMessage != null) {
            Text(
                text = state.errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { contentDescription = state.errorMessage },
            )
        }

        Spacer(Modifier.weight(1f))

        if (state.showPinEntry) {
            GlowButton(
                text = "Unlock",
                onClick = onSubmitPin,
                enabled = state.canSubmitPin,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (state.mode == AppLockMode.BIOMETRIC && !state.biometricLockedOut) {
            BiometricRetryButton(onRetryBiometric)
            if (!state.showPinEntry && state.hasPinFallback) {
                GlowButton(
                    text = "Use PIN instead",
                    onClick = onUsePin,
                    style = GlowButtonStyle.Quiet,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun BiometricRetryButton(onRetry: suspend () -> Unit) {
    val scope = rememberCoroutineScope()
    GlowButton(
        text = "Try again",
        onClick = { scope.launch { onRetry() } },
        style = GlowButtonStyle.Secondary,
        modifier = Modifier.fillMaxWidth(),
    )
}
