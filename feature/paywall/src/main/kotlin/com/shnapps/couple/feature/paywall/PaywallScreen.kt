package com.shnapps.couple.feature.paywall

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shnapps.couple.core.designsystem.component.CinematicCard
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.icon.AfterhoursIcons
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.ui.BackRow
import com.shnapps.couple.core.ui.ErrorBanner
import com.shnapps.couple.core.ui.ScreenColumn
import com.shnapps.couple.core.ui.ScreenHeading
import com.shnapps.couple.core.ui.SecureScreen

/**
 * What premium includes (BUILD_PROMPT.md §19).
 *
 * It says what is in it, what it costs, and what happens if they say no — and nothing else. No
 * countdown, no "limited offer", no close button hidden in a corner: the way out is the same
 * "Not now" as on every other screen, and everything they already have stays theirs.
 */
@Composable
fun PaywallScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PaywallViewModel = hiltViewModel(),
) {
    SecureScreen()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // LocalActivity, not a cast: a Context is not always an Activity, and the purchase flow
    // needs a real one to launch into.
    val activity = LocalActivity.current
    PaywallContent(
        state = state,
        actions = PaywallActions(
            onSubscribe = { activity?.let(viewModel::subscribe) },
            onRestore = viewModel::restore,
            onDismissError = viewModel::dismissError,
            onBack = onBack,
        ),
        modifier = modifier,
    )
}

/** Everything this screen can do, gathered so the content stays easy to preview. */
internal data class PaywallActions(
    val onSubscribe: () -> Unit = {},
    val onRestore: () -> Unit = {},
    val onDismissError: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/** What premium is, in the order it matters. Each line is a thing they can do, not a feeling. */
private val INCLUDED = listOf(
    AfterhoursIcons.Moon to "As many evenings as you want — planned, a surprise, or fifteen minutes.",
    AfterhoursIcons.Eye to "A mystery: you find out as you go.",
    AfterhoursIcons.Calendar to "A few days of this — one, three, seven or fourteen, an evening each.",
    AfterhoursIcons.Gift to "Leave as many surprises waiting as you like.",
    AfterhoursIcons.Heart to "Everything free includes, unchanged.",
)

@Composable
internal fun PaywallContent(
    state: PaywallUiState,
    actions: PaywallActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        ScreenColumn {
            BackRow(label = "Not now", onBack = actions.onBack)
            ScreenHeading(
                eyebrow = "Afterhours premium",
                headline = state.gate?.headline ?: "More of it.",
                body = state.gate?.detail
                    ?: "One subscription covers both of you. Everything you have already stays yours.",
            )
            CinematicCard(modifier = Modifier.fillMaxWidth()) {
                Text(text = "WHAT'S INCLUDED", style = EyebrowTextStyle, color = AfterhoursTheme.colors.brass)
                Column(verticalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm)) {
                    for ((icon, line) in INCLUDED) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = AfterhoursTheme.colors.brass,
                                modifier = Modifier.size(INCLUDED_ICON),
                            )
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
            if (state.alreadyPremium) {
                AlreadyIn(state)
            } else {
                Buy(state, actions)
            }
            state.error?.let { ErrorBanner(message = it.message(), onDismiss = actions.onDismissError) }
        }
    }
}

@Composable
private fun Buy(state: PaywallUiState, actions: PaywallActions) {
    Text(
        text = state.offer?.let { "${it.price} a ${it.period.readablePeriod()}, cancel any time." }
            ?: "Cancel any time.",
        style = MaterialTheme.typography.bodyMedium,
        color = AfterhoursTheme.colors.textMuted,
    )
    GlowButton(
        text = if (state.busy) "One moment…" else "Go premium",
        onClick = actions.onSubscribe,
        enabled = !state.busy,
        leadingIcon = AfterhoursIcons.Spark,
        modifier = Modifier.fillMaxWidth(),
    )
    GlowButton(
        text = "I already subscribed",
        onClick = actions.onRestore,
        style = GlowButtonStyle.Quiet,
        enabled = !state.busy,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AlreadyIn(state: PaywallUiState) {
    CinematicCard(modifier = Modifier.fillMaxWidth(), glowing = true) {
        Text(text = "YOU'RE IN", style = EyebrowTextStyle, color = AfterhoursTheme.colors.brass)
        Text(
            text = if (state.entitlement.endingSoon) {
                "Premium, and set to end. Everything stays on until it does."
            } else {
                "Premium, for both of you."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Play says P1M; people say month. */
private fun String.readablePeriod(): String = when (this) {
    "P1W" -> "week"
    "P1M" -> "month"
    "P3M" -> "quarter"
    "P1Y" -> "year"
    else -> ifBlank { "month" }
}

private fun PaywallError.message(): String = when (this) {
    PaywallError.OFFLINE -> "You're offline. That one needs the network."
    PaywallError.UNAVAILABLE -> "Purchases aren't available on this device."
    PaywallError.NOTHING_TO_RESTORE -> "Nothing to restore on this account."
    PaywallError.OTHER -> "That didn't work. Nothing was charged."
}

/** An icon beside a line of copy: one step down from a button's. */
private val INCLUDED_ICON = 18.dp
