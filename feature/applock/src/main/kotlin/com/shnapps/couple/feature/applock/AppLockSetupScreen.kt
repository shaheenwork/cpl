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
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.security.BiometricAuthenticator
import com.shnapps.couple.core.security.PinRules
import com.shnapps.couple.core.ui.SecureScreen

/** Setting up the app lock (BUILD_PROMPT.md §3.4, §57). Reached from Settings in Phase 20. */
@Composable
fun AppLockSetupScreen(
    onFinished: () -> Unit,
    biometricAuthenticator: BiometricAuthenticator,
    modifier: Modifier = Modifier,
    viewModel: AppLockSetupViewModel = hiltViewModel(),
) {
    SecureScreen()

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current as? FragmentActivity
    val biometricAvailable = activity?.let { biometricAuthenticator.canAuthenticate(it) } ?: false

    LaunchedEffect(state.step, biometricAvailable) {
        // No sensor, nothing to offer: skip straight past the biometric step.
        if (state.step == LockSetupStep.OfferBiometric && !biometricAvailable) viewModel.skipBiometric()
        if (state.step == LockSetupStep.Done) onFinished()
    }

    AppLockSetupContent(
        state = state,
        onDigits = viewModel::onDigits,
        onContinue = viewModel::onContinue,
        onEnableBiometric = viewModel::enableBiometric,
        onSkipBiometric = viewModel::skipBiometric,
        modifier = modifier,
    )
}

@Composable
internal fun AppLockSetupContent(
    state: AppLockSetupUiState,
    onDigits: (String) -> Unit,
    onContinue: () -> Unit,
    onEnableBiometric: () -> Unit,
    onSkipBiometric: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = AfterhoursTheme.spacing
    val (eyebrow, headline, body) = when (state.step) {
        LockSetupStep.EnterPin -> Triple(
            "APP LOCK",
            "Choose a PIN.",
            "${PinRules.MIN_LENGTH} to ${PinRules.MAX_LENGTH} digits. Different from your phone's " +
                "own code — whoever has your unlocked phone already knows that one.",
        )
        LockSetupStep.ConfirmPin -> Triple("APP LOCK", "Once more.", "Enter the same PIN again.")
        LockSetupStep.OfferBiometric, LockSetupStep.Done -> Triple(
            "APP LOCK",
            "Use your fingerprint too?",
            "Faster day to day. Your PIN still works whenever the sensor doesn't.",
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = spacing.gutter, vertical = spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Text(text = eyebrow, style = EyebrowTextStyle, color = AfterhoursTheme.colors.brass)
        Text(
            text = headline,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = AfterhoursTheme.colors.textMuted,
        )

        if (state.step == LockSetupStep.EnterPin || state.step == LockSetupStep.ConfirmPin) {
            val value = if (state.step == LockSetupStep.EnterPin) state.pin else state.confirmation
            OutlinedTextField(
                value = value,
                onValueChange = onDigits,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = spacing.sm)
                    .semantics {
                        contentDescription = if (state.step == LockSetupStep.EnterPin) {
                            "New PIN"
                        } else {
                            "Confirm PIN"
                        }
                    },
                label = { Text("PIN") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onContinue() }),
                isError = state.errorMessage != null,
            )
        }

        if (state.errorMessage != null) {
            Text(
                text = state.errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.weight(1f))

        if (state.step == LockSetupStep.OfferBiometric) {
            GlowButton(
                text = "Use fingerprint",
                onClick = onEnableBiometric,
                modifier = Modifier.fillMaxWidth(),
            )
            GlowButton(
                text = "PIN only",
                onClick = onSkipBiometric,
                style = GlowButtonStyle.Quiet,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            GlowButton(
                text = if (state.step == LockSetupStep.ConfirmPin) "Turn on app lock" else "Continue",
                onClick = onContinue,
                enabled = state.canContinue,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
