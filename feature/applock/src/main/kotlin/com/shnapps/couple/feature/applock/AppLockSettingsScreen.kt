package com.shnapps.couple.feature.applock

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.shnapps.couple.core.designsystem.icon.AfterhoursIcons
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.model.AppLockMode
import com.shnapps.couple.core.security.BiometricAuthenticator
import com.shnapps.couple.core.ui.AfterhoursSheet
import com.shnapps.couple.core.ui.ScreenScaffold
import com.shnapps.couple.core.ui.SecureScreen
import com.shnapps.couple.core.ui.SettingsGroup
import com.shnapps.couple.core.ui.SettingsRow
import com.shnapps.couple.core.ui.SettingsSwitchRow
import com.shnapps.couple.core.ui.SettingsTone
import com.shnapps.couple.core.ui.StatusCard

/**
 * The app lock, from Settings: what it is doing now, and how to change it (D-146).
 *
 * [onSetUp] opens the setup flow, for turning the lock on and for choosing another PIN. Setup was
 * the only lock screen there was, which is why there was no way to turn the lock off again.
 */
@Composable
fun AppLockSettingsScreen(
    onBack: () -> Unit,
    onSetUp: () -> Unit,
    biometricAuthenticator: BiometricAuthenticator,
    modifier: Modifier = Modifier,
    viewModel: AppLockSettingsViewModel = hiltViewModel(),
) {
    SecureScreen()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current as? FragmentActivity
    val sensor = activity?.let { biometricAuthenticator.canAuthenticate(it) } ?: false

    LaunchedEffect(state.openSetup) {
        if (state.openSetup) {
            viewModel.onSetupOpened()
            onSetUp()
        }
    }

    AppLockSettingsContent(
        state = state,
        sensor = sensor,
        actions = AppLockSettingsActions(
            onBack = onBack,
            onSetUp = onSetUp,
            onAsk = viewModel::ask,
            onCancel = viewModel::cancel,
            onPin = viewModel::onPin,
            onSubmit = viewModel::submit,
            onBiometric = viewModel::setBiometric,
        ),
        modifier = modifier,
    )
}

internal data class AppLockSettingsActions(
    val onBack: () -> Unit = {},
    val onSetUp: () -> Unit = {},
    val onAsk: (LockGate) -> Unit = {},
    val onCancel: () -> Unit = {},
    val onPin: (String) -> Unit = {},
    val onSubmit: () -> Unit = {},
    val onBiometric: (Boolean) -> Unit = {},
)

@Composable
internal fun AppLockSettingsContent(
    state: AppLockSettingsUiState,
    sensor: Boolean,
    actions: AppLockSettingsActions,
    modifier: Modifier = Modifier,
) {
    // Kept for the sheet's exit: `asking` is null the moment it is dismissed, and the sheet is still
    // sliding away saying something. It must not change its words on the way out.
    var gate by remember { mutableStateOf(LockGate.TURN_OFF) }
    state.asking?.let { gate = it }

    Box(modifier = modifier.fillMaxSize()) {
        ScreenScaffold(title = "App lock", onBack = actions.onBack) {
            when (state.mode) {
                // Not read yet: nothing drawn is better than the wrong state drawn.
                null -> Unit
                AppLockMode.OFF -> LockOff(onTurnOn = actions.onSetUp)
                else -> LockOn(state.mode, sensor, actions)
            }
        }
        AfterhoursSheet(
            visible = state.asking != null,
            onDismiss = actions.onCancel,
            title = if (gate == LockGate.TURN_OFF) "Turn off app lock" else "Change PIN",
        ) {
            PinGate(gate = gate, state = state, actions = actions)
        }
    }
}

@Composable
private fun ColumnScope.LockOff(onTurnOn: () -> Unit) {
    StatusCard(
        icon = AfterhoursIcons.LockOpen,
        title = "App lock is off",
        body = "Anyone who opens this phone can open the app.",
    ) {
        GlowButton(
            text = "Turn on app lock",
            onClick = onTurnOn,
            leadingIcon = AfterhoursIcons.Lock,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Text(
        text = "A PIN you choose, on this phone only. It is not sent anywhere, and there is no way to " +
            "reset it without it — so pick one you will remember.",
        style = MaterialTheme.typography.bodyMedium,
        color = AfterhoursTheme.colors.textMuted,
    )
}

@Composable
private fun ColumnScope.LockOn(mode: AppLockMode, sensor: Boolean, actions: AppLockSettingsActions) {
    StatusCard(
        icon = AfterhoursIcons.Lock,
        title = "App lock is on",
        body = if (mode == AppLockMode.BIOMETRIC) {
            "Opens with your fingerprint, or your PIN."
        } else {
            "Opens with your PIN."
        },
        lit = true,
    )

    SettingsGroup(title = "How it opens") {
        if (sensor) {
            SettingsSwitchRow(
                icon = AfterhoursIcons.Fingerprint,
                title = "Fingerprint",
                detail = "Quicker. Your PIN still works whenever the sensor doesn't.",
                checked = mode == AppLockMode.BIOMETRIC,
                onCheckedChange = actions.onBiometric,
            )
        }
        SettingsRow(
            icon = AfterhoursIcons.Keyhole,
            title = "Change PIN",
            detail = "Asks for your current PIN first.",
            onClick = { actions.onAsk(LockGate.CHANGE_PIN) },
        )
    }

    SettingsGroup {
        SettingsRow(
            icon = AfterhoursIcons.LockOpen,
            title = "Turn off app lock",
            detail = "Asks for your PIN first. Anyone who opens this phone can then open the app.",
            tone = SettingsTone.Danger,
            onClick = { actions.onAsk(LockGate.TURN_OFF) },
        )
    }

    Text(
        text = "It locks again when you've been away for a minute.",
        style = MaterialTheme.typography.bodySmall,
        color = AfterhoursTheme.colors.textFaint,
    )
}

/** The PIN, asked for the change that would weaken the lock. */
@Composable
private fun ColumnScope.PinGate(gate: LockGate, state: AppLockSettingsUiState, actions: AppLockSettingsActions) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(state.asking) {
        if (state.asking != null) focus.requestFocus()
    }

    Text(
        text = if (gate == LockGate.TURN_OFF) "Turn off app lock?" else "Change your PIN",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        text = if (gate == LockGate.TURN_OFF) {
            "Enter your PIN to turn it off. Anyone who opens this phone can then open the app."
        } else {
            "Enter your current PIN, then choose a new one."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = AfterhoursTheme.colors.textMuted,
    )
    OutlinedTextField(
        value = state.pin,
        onValueChange = actions.onPin,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focus)
            .semantics { contentDescription = "Your current PIN" },
        label = { Text("PIN") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { actions.onSubmit() }),
        isError = state.error != null,
    )
    state.error?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { contentDescription = it },
        )
    }
    GlowButton(
        text = if (gate == LockGate.TURN_OFF) "Turn off" else "Continue",
        onClick = actions.onSubmit,
        enabled = state.canSubmit,
        modifier = Modifier.fillMaxWidth(),
    )
    GlowButton(
        text = "Keep it as it is",
        onClick = actions.onCancel,
        style = GlowButtonStyle.Quiet,
        modifier = Modifier.fillMaxWidth(),
    )
}
