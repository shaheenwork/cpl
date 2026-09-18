package com.shnapps.couple.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shnapps.couple.core.designsystem.component.CinematicCard
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.ui.SecureScreen

/**
 * The 18+ gate (BUILD_PROMPT.md §3.1).
 *
 * Deliberately not a dismissible dialog and not a pre-ticked checkbox. Confirming is an
 * explicit action, declining is a real option with a real outcome, and the attestation is
 * stored with a timestamp so the record is auditable.
 *
 * A missing record always re-prompts. Having an account is never treated as evidence that
 * someone already confirmed.
 */
@Composable
fun AgeGateScreen(
    onConfirmed: () -> Unit,
    onDeclined: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AgeGateViewModel = hiltViewModel(),
) {
    SecureScreen()

    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.confirmed) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onConfirmed() }
    }

    AgeGateContent(
        state = state,
        onConfirm = viewModel::confirm,
        onDecline = onDeclined,
        modifier = modifier,
    )
}

@Composable
internal fun AgeGateContent(
    state: AgeGateUiState,
    onConfirm: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = AfterhoursTheme.spacing

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.gutter, vertical = spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        Text(
            text = "BEFORE WE START",
            style = EyebrowTextStyle,
            color = AfterhoursTheme.colors.brass,
        )

        Text(
            text = "This is an app for adults.",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Text(
            text = "Everything inside is made for two consenting adults in a relationship. " +
                "You can set your own limits at any time, and nothing ever happens that " +
                "you both haven't agreed to.",
            style = MaterialTheme.typography.bodyLarge,
            color = AfterhoursTheme.colors.textMuted,
        )

        CinematicCard {
            Text(
                text = "What you're confirming",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.padding(top = spacing.sm))
            Text(
                text = "You are 18 or over, and you are happy to see adult content.",
                style = MaterialTheme.typography.bodyMedium,
                color = AfterhoursTheme.colors.textMuted,
            )
        }

        if (state.errorMessage != null) {
            Text(
                text = state.errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { contentDescription = state.errorMessage },
            )
        }

        Spacer(Modifier.weight(1f))

        GlowButton(
            text = if (state.isSubmitting) "One moment…" else "I'm 18 or over",
            onClick = onConfirm,
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        )

        GlowButton(
            text = "I'm not",
            onClick = onDecline,
            style = GlowButtonStyle.Quiet,
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
