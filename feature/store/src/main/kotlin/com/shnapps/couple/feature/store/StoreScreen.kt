package com.shnapps.couple.feature.store

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shnapps.couple.core.designsystem.component.CinematicCard
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.component.HeatFlames
import com.shnapps.couple.core.designsystem.icon.AfterhoursIcons
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.designsystem.theme.ProvideTier
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.ui.ErrorBanner
import com.shnapps.couple.core.ui.FreeRunBanner
import com.shnapps.couple.core.ui.ScreenHeading
import com.shnapps.couple.core.ui.ScreenScaffold
import com.shnapps.couple.core.ui.SecureScreen

/**
 * The packs, and what they cost (V2 rule 5).
 *
 * Three cards, a price on each, and a sentence about what is inside. Nothing counts down, nothing
 * is crossed out, nothing is "most popular", and the way out is the same back the rest of the app
 * has. The one thing it goes out of its way to say is what a couple keeps whatever they do: Mild
 * and Flirty, the limits profile, and the safe word, free for ever and with no account.
 */
@Composable
fun StoreScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StoreViewModel = hiltViewModel(),
) {
    SecureScreen()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // LocalActivity, not a cast: a Context is not always an Activity, and the purchase flow
    // needs a real one to launch into.
    val activity = LocalActivity.current
    StoreContent(
        state = state,
        actions = StoreActions(
            onBuy = { level -> activity?.let { viewModel.buy(it, level) } },
            onRestore = viewModel::restore,
            onDismissError = viewModel::dismissError,
            onBack = onBack,
        ),
        modifier = modifier,
    )
}

/** Everything this screen can do, gathered so the content stays easy to preview. */
internal data class StoreActions(
    val onBuy: (Intensity) -> Unit = {},
    val onRestore: () -> Unit = {},
    val onDismissError: () -> Unit = {},
    val onBack: () -> Unit = {},
)

@Composable
internal fun StoreContent(
    state: StoreUiState,
    actions: StoreActions,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(
        modifier = modifier,
        title = "Packs",
        onBack = actions.onBack,
        backLabel = "Not now",
    ) {
        ScreenHeading(
            eyebrow = "Afterhours",
            headline = state.asked?.let { "${it.label}, and everything under it." } ?: "Three packs.",
            body = "Mild and Flirty are free for ever, with no account. Each pack above them opens " +
                "the levels below it as well, once, and never expires.",
        )

        state.access.freeUntilMillis?.let { FreeRunBanner(untilMillis = it) }

        for (pack in state.packs) {
            PackCard(
                pack = pack,
                asked = pack.level == state.asked,
                busy = state.busy,
                freeForNow = state.access.freeForNow,
                onBuy = { actions.onBuy(pack.level) },
            )
        }

        GlowButton(
            text = "Restore purchases",
            onClick = actions.onRestore,
            style = GlowButtonStyle.Quiet,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.restoredNothing) {
            Text(
                text = "Nothing to restore on this account.",
                style = MaterialTheme.typography.bodySmall,
                color = AfterhoursTheme.colors.textMuted,
            )
        }
        state.error?.let { ErrorBanner(message = it.message(), onDismiss = actions.onDismissError) }
    }
}

@Composable
private fun PackCard(
    pack: StoreUiState.Pack,
    asked: Boolean,
    busy: Boolean,
    freeForNow: Boolean,
    onBuy: () -> Unit,
) {
    // Each pack in its own heat, so what a couple is buying is the colour it will light the room.
    // Lit only for the pack they came here for: three glowing cards is a smear, and a store that
    // leans on one of them is the "most popular" badge the brief rules out.
    ProvideTier(pack.level) { PackCardBody(pack, asked, busy, freeForNow, onBuy) }
}

@Composable
private fun PackCardBody(
    pack: StoreUiState.Pack,
    asked: Boolean,
    busy: Boolean,
    freeForNow: Boolean,
    onBuy: () -> Unit,
) {
    CinematicCard(modifier = Modifier.fillMaxWidth(), glowing = asked) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = pack.level.label.uppercase(),
                style = EyebrowTextStyle,
                color = AfterhoursTheme.tier.onDark,
                modifier = Modifier.weight(1f),
            )
            HeatFlames(count = pack.level.ordinal + 1)
        }
        Text(
            text = contents(pack.level),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        when {
            pack.owned -> Text(
                text = "Yours.",
                style = MaterialTheme.typography.bodyMedium,
                color = AfterhoursTheme.colors.brass,
            )
            freeForNow -> Text(
                text = "Unlocked for now. ${pack.price ?: ""}".trim(),
                style = MaterialTheme.typography.bodyMedium,
                color = AfterhoursTheme.colors.textMuted,
            )
            else -> GlowButton(
                text = pack.price?.let { "Unlock — $it" } ?: "Unlock",
                onClick = onBuy,
                enabled = !busy,
                leadingIcon = AfterhoursIcons.Spark,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** What each pack is, in one sentence that names the levels it opens. */
private fun contents(level: Intensity): String = when (level) {
    Intensity.SPICY -> "Every Spicy card, in all seven games."
    Intensity.HOT -> "Every Hot card, and Spicy with it."
    Intensity.EXTREME -> "Everything: Extreme, Hot and Spicy. The whole catalogue."
    else -> "Free, always."
}

private fun StoreError.message(): String = when (this) {
    StoreError.OFFLINE -> "You're offline. Buying needs the network."
    StoreError.UNAVAILABLE -> "Purchases aren't available on this device."
    StoreError.NOTHING_TO_RESTORE -> "Nothing to restore on this account."
    StoreError.OTHER -> "That didn't work. Nothing was charged."
}
