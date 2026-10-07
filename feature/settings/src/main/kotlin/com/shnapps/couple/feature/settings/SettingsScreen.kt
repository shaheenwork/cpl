package com.shnapps.couple.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shnapps.couple.core.data.entitlement.LevelAccess
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.security.DiscreetIcon
import com.shnapps.couple.core.ui.ErrorBanner
import com.shnapps.couple.core.ui.LegalUrls
import com.shnapps.couple.core.ui.ScreenHeading
import com.shnapps.couple.core.ui.ScreenScaffold
import com.shnapps.couple.core.ui.SecureScreen
import com.shnapps.couple.core.ui.onDay

/**
 * Settings (BUILD_PROMPT.md §14.9).
 *
 * Most of it is doors: the private things — answers, limits, the app lock — each already have a
 * screen, and this is where they are found. There is no account in v2, so nothing here signs in,
 * signs out or deletes one: what lives here is this phone, and the pair it is half of.
 */
@Composable
fun SettingsScreen(
    routes: SettingsRoutes,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    SecureScreen()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsContent(
        state = state,
        routes = routes,
        actions = SettingsActions(
            onIcon = viewModel::setIcon,
            onUnpair = viewModel::unpair,
            onDismissError = viewModel::dismissError,
        ),
        modifier = modifier,
    )
}

/** Where each row goes. The screen knows the words; the host knows the routes. */
data class SettingsRoutes(
    val onBoundaries: () -> Unit = {},
    val onNotifications: () -> Unit = {},
    val onAppLock: () -> Unit = {},
    val onPremium: () -> Unit = {},
    /** The only setup in the app, and the only row here that touches the other phone (V2). */
    val onPairing: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/**
 * Which icon the launcher shows (M8).
 *
 * Three across, with what each one looks like under the name, because "Notes" on its own does not
 * tell anybody what they will be tapping tomorrow.
 */
@Composable
private fun DiscreetIconRow(selected: DiscreetIcon, onIcon: (DiscreetIcon) -> Unit) {
    val spacing = AfterhoursTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(
            text = "HOW IT LOOKS ON YOUR HOME SCREEN",
            style = MaterialTheme.typography.labelSmall,
            color = AfterhoursTheme.colors.textFaint,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            modifier = Modifier.selectableGroup(),
        ) {
            DiscreetIcon.entries.forEach { icon ->
                IconTile(icon = icon, selected = icon == selected, onIcon = onIcon, modifier = Modifier.weight(1f))
            }
        }
        Text(
            text = selected.describes,
            style = MaterialTheme.typography.bodySmall,
            color = AfterhoursTheme.colors.textMuted,
        )
    }
}

/**
 * One of the three names this app can wear on a home screen (M8).
 *
 * A tile rather than a button, because "Afterhours" does not fit across a third of a phone at a
 * button's type size — it wrapped mid-word, which is the kind of thing a golden is for.
 */
@Composable
private fun IconTile(
    icon: DiscreetIcon,
    selected: Boolean,
    onIcon: (DiscreetIcon) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = AfterhoursTheme.spacing
    Box(
        modifier = modifier
            .heightIn(min = spacing.touchTarget)
            .clip(MaterialTheme.shapes.small)
            .background(if (selected) AfterhoursTheme.tier.container else MaterialTheme.colorScheme.surfaceContainerLow)
            .selectable(selected = selected, onClick = { onIcon(icon) }, role = Role.RadioButton)
            .padding(horizontal = spacing.xs, vertical = spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = icon.label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            textAlign = TextAlign.Center,
            color = if (selected) AfterhoursTheme.tier.onContainer else AfterhoursTheme.colors.textMuted,
        )
    }
}

/** What this screen does on its own. */
internal data class SettingsActions(
    val onIcon: (DiscreetIcon) -> Unit = {},
    val onUnpair: () -> Unit = {},
    val onDismissError: () -> Unit = {},
)

@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    routes: SettingsRoutes,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(
        modifier = modifier,
        title = "Settings",
        onBack = routes.onBack,
    ) {
        ScreenHeading(
            eyebrow = "Settings",
            headline = "Yours, and the two of you.",
            // Not "signed in as": there is nobody to be signed in as. What this phone is, is
            // either on its own or half of a pair (V2 rule 1).
            body = if (state.paired) "Paired with their phone." else "Not paired. Together mode needs nothing.",
        )

        Group("You") {
            Row("Your limits", "What you are up for, and what you are not.", routes.onBoundaries)
            Row("Notifications", "What this phone may say out loud.", routes.onNotifications)
            Row("App lock", "A PIN or your fingerprint, on this phone.", routes.onAppLock)
        }

        Group("On this phone") {
            // What the home screen shows. Not a lock and not a disguise: a name and an icon that
            // do not announce what the app is, for a phone somebody else might pick up (M8).
            DiscreetIconRow(selected = state.icon, onIcon = actions.onIcon)
            Row(
                title = "Panic",
                detail = "Two taps of back locks the app. Set a PIN above and it asks for it.",
                onClick = routes.onAppLock,
            )
        }

        Group("The two of you") {
            Row(
                title = if (state.paired) "Paired" else "Not paired",
                detail = if (state.paired) {
                    "These two phones are a pair until one of you unpairs."
                } else {
                    "Together mode needs nobody. Pair only to play apart."
                },
                onClick = routes.onPairing,
            )
            if (state.paired) {
                // The one destructive thing in the app, and the sentence says the whole of it.
                Row(
                    title = "Unpair and wipe",
                    detail = "Clears the pair on both phones and everything you shared. " +
                        "Your own limits stay on this phone.",
                    onClick = actions.onUnpair,
                )
            }
        }

        Group("Packs") {
            // What they own, said as a fact rather than as an offer. A couple who have bought
            // everything are never sold anything here again (V2 rule 5).
            Row(
                title = "What you own",
                detail = packsDetail(state.access),
                onClick = routes.onPremium,
            )
        }

        // Only when they exist: a row here that opens nothing would be worse than no row.
        if (LegalUrls.published) {
            Group("The small print") {
                val context = LocalContext.current
                if (LegalUrls.PRIVACY_POLICY.isNotBlank()) {
                    Row("Privacy policy", "What this app holds, and what it never shares.") {
                        context.open(LegalUrls.PRIVACY_POLICY)
                    }
                }
                if (LegalUrls.TERMS.isNotBlank()) {
                    Row("Terms of use", "The agreement between us.") { context.open(LegalUrls.TERMS) }
                }
            }
        }

        Group("About") {
            // A version, and nothing that looks like an account — because there is not one.
            Row(title = "Afterhours", detail = "Version ${versionName(LocalContext.current)}") {}
        }

        state.error?.let { ErrorBanner(message = it.message(), onDismiss = actions.onDismissError) }
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Text(text = title.uppercase(), style = EyebrowTextStyle, color = AfterhoursTheme.colors.textFaint)
    Column(verticalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.xs)) { content() }
}

@Composable
private fun Row(title: String, detail: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AfterhoursTheme.spacing.touchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = AfterhoursTheme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.xxs),
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(text = detail, style = MaterialTheme.typography.bodyMedium, color = AfterhoursTheme.colors.textMuted)
    }
}

private fun Context.open(url: String) {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

private fun SettingsError.message(): String = when (this) {
    SettingsError.OFFLINE -> "You're offline. That one needs the network."
    SettingsError.OTHER -> "That didn't work. Try again."
}

/** What settings says about the packs: owned, free for now, or what there is to have. */
private fun packsDetail(access: LevelAccess): String = when {
    access.isOwned(Intensity.EXTREME) -> "Everything's yours."
    access.freeForNow -> access.freeUntilMillis
        ?.let { "Everything unlocked until ${onDay(it)}." }
        ?: "Everything unlocked for now."
    access.owned.isNotEmpty() -> "${LevelAccess.ceilingOf(access.owned).label} and below. Restore, or go further."
    else -> "Mild and Flirty are free. Spicy, Hot and Extreme are packs."
}

/** This build's version, as the system records it. Nothing to maintain, and never wrong. */
private fun versionName(context: Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull().orEmpty().ifBlank { "—" }
