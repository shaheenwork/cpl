package com.shnapps.couple.feature.store

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import com.shnapps.couple.core.designsystem.theme.tierColors
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.ui.ErrorBanner
import com.shnapps.couple.core.ui.FreeRunBanner
import com.shnapps.couple.core.ui.ScreenHeading
import com.shnapps.couple.core.ui.ScreenScaffold
import com.shnapps.couple.core.ui.SecureScreen
import com.shnapps.couple.core.ui.SettingsGroup
import com.shnapps.couple.core.ui.SettingsRow
import com.shnapps.couple.core.ui.onDay

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
            headline = state.asked?.let { "${it.label}, and everything under it." } ?: "Go as far as you like.",
            body = "Buy a pack once and it opens every level below it, and it is shared with your " +
                "partner. No subscription, ever.",
        )

        state.access.freeUntilMillis?.let { FreeRunBanner(untilMillis = it) }

        // What nobody ever pays for, said first and plainly: a store that leads with a price for the
        // gentle half is one nobody should trust with the rest.
        SettingsGroup {
            SettingsRow(
                icon = AfterhoursIcons.Check,
                title = "Mild and Flirty",
                detail = "Free for ever. No account, no purchase.",
                value = "Included",
            )
        }

        for (pack in state.packs) {
            PackCard(
                pack = pack,
                asked = pack.level == state.asked,
                busy = state.busy,
                freeUntilMillis = state.access.freeUntilMillis.takeIf { state.access.freeForNow },
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

        Text(
            text = "Billed once by Google Play. The limits profile, the safe word and the aftercare " +
                "cards are free, always.",
            style = MaterialTheme.typography.bodySmall,
            color = AfterhoursTheme.colors.textFaint,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PackCard(
    pack: StoreUiState.Pack,
    asked: Boolean,
    busy: Boolean,
    freeUntilMillis: Long?,
    onBuy: () -> Unit,
) {
    // Each pack in its own heat, so what a couple is buying is the colour it will light the room.
    // Lit only for the pack they came here for: three glowing cards is a smear, and a store that
    // leans on one of them is the "most popular" badge the brief rules out.
    ProvideTier(pack.level) { PackCardBody(pack, asked, busy, freeUntilMillis, onBuy) }
}

@Composable
private fun PackCardBody(
    pack: StoreUiState.Pack,
    asked: Boolean,
    busy: Boolean,
    freeUntilMillis: Long?,
    onBuy: () -> Unit,
) {
    val spacing = AfterhoursTheme.spacing
    CinematicCard(modifier = Modifier.fillMaxWidth(), glowing = asked) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text(text = "PACK", style = EyebrowTextStyle, color = AfterhoursTheme.colors.textFaint)
                Text(
                    text = pack.level.label,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            HeatFlames(count = pack.level.level, height = FLAMES)
        }
        Text(
            text = contents(pack.level),
            style = MaterialTheme.typography.bodyMedium,
            color = AfterhoursTheme.colors.textMuted,
        )
        Opens(upTo = pack.level)
        when {
            pack.owned -> Owned()
            freeUntilMillis != null -> Text(
                text = buildString {
                    append("Open until ${onDay(freeUntilMillis)}")
                    pack.price?.let { append(", then $it") }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = AfterhoursTheme.colors.textMuted,
            )
            else -> Purchase(price = pack.price, label = pack.level.label, busy = busy, onBuy = onBuy)
        }
    }
}

/** Which levels the pack opens: a chip each, in that level's own colour, so the ladder is visible. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Opens(upTo: Intensity) {
    val spacing = AfterhoursTheme.spacing
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "Opens " + Intensity.entries.filter { it.level <= upTo.level }
                    .joinToString(", ") { it.label }
            },
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        for (level in Intensity.entries.filter { it.level <= upTo.level }) {
            val colors = tierColors(level)
            Row(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(colors.accent.copy(alpha = CHIP_FILL))
                    .padding(horizontal = spacing.item, vertical = spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs + spacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(DOT).clip(CircleShape).background(colors.accent))
                Text(
                    text = level.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onDark,
                    maxLines = 1,
                )
            }
        }
    }
}

/** The price, once and large, and what kind of purchase it is, then the button. */
@Composable
private fun Purchase(price: String?, label: String, busy: Boolean, onBuy: () -> Unit) {
    val spacing = AfterhoursTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.item)) {
        price?.let {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = it,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "one-time, shared with your partner",
                    style = MaterialTheme.typography.bodySmall,
                    color = AfterhoursTheme.colors.textMuted,
                    modifier = Modifier.padding(bottom = spacing.xs),
                )
            }
        }
        GlowButton(
            text = "Unlock $label",
            onClick = onBuy,
            enabled = !busy,
            leadingIcon = AfterhoursIcons.Spark,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Owned() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = AfterhoursIcons.Check,
            contentDescription = null,
            tint = AfterhoursTheme.colors.brass,
            modifier = Modifier.size(OWNED_ICON),
        )
        Text(
            text = "Yours, for good.",
            style = MaterialTheme.typography.titleSmall,
            color = AfterhoursTheme.colors.brass,
        )
    }
}

/** What each pack is, in one sentence. */
private fun contents(level: Intensity): String = when (level) {
    Intensity.SPICY -> "Every Spicy card, in all seven games."
    Intensity.HOT -> "Every Hot card, in all seven games."
    Intensity.EXTREME -> "The furthest it goes. Everything, in all seven games."
    else -> "Free, always."
}

private val FLAMES = 18.dp
private val DOT = 8.dp
private val OWNED_ICON = 20.dp
private const val CHIP_FILL = 0.14f

private fun StoreError.message(): String = when (this) {
    StoreError.OFFLINE -> "You're offline. Buying needs the network."
    StoreError.UNAVAILABLE -> "Purchases aren't available on this device."
    StoreError.NOT_FOR_SALE -> "The store doesn't have this pack yet. Nothing was charged."
    StoreError.PENDING -> "Your payment is waiting to clear. The pack opens by itself once it does."
    StoreError.NOTHING_TO_RESTORE -> "Nothing to restore on this account."
    StoreError.OTHER -> "That didn't work. Nothing was charged."
}
