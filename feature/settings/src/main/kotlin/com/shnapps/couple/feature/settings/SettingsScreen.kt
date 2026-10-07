package com.shnapps.couple.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shnapps.couple.core.data.entitlement.LevelAccess
import com.shnapps.couple.core.designsystem.component.CinematicCard
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
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
import java.io.File

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
    val exportsDirectory = File(LocalContext.current.cacheDir, "exports")
    SettingsContent(
        state = state,
        routes = routes,
        actions = SettingsActions(
            onIcon = viewModel::setIcon,
            onExport = { viewModel.export(exportsDirectory) },
            onUnpair = viewModel::unpair,
            onDismissError = viewModel::dismissError,
            onDismissExport = viewModel::dismissExport,
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
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            DiscreetIcon.entries.forEach { icon ->
                GlowButton(
                    text = icon.label,
                    onClick = { onIcon(icon) },
                    style = if (icon == selected) GlowButtonStyle.Secondary else GlowButtonStyle.Quiet,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(
            text = selected.describes,
            style = MaterialTheme.typography.bodySmall,
            color = AfterhoursTheme.colors.textMuted,
        )
    }
}

/** What this screen does on its own. */
internal data class SettingsActions(
    val onIcon: (DiscreetIcon) -> Unit = {},
    val onExport: () -> Unit = {},
    val onUnpair: () -> Unit = {},
    val onDismissError: () -> Unit = {},
    val onDismissExport: () -> Unit = {},
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
            // What they own, said as a fact rather than as an offer. A couple who have bought
            // everything are never sold anything here again (V2 rule 5).
            Row(
                title = "Packs",
                detail = packsDetail(state.access),
                onClick = routes.onPremium,
            )
            if (state.paired) {
                Row("Unpair", "End the couple. Your own answers and limits stay yours.", actions.onUnpair)
            }
        }

        Group("Your data") {
            Row(
                title = if (state.exporting) "Putting it together…" else "Export my data",
                detail = "Everything this app holds about you, as one file.",
                onClick = actions.onExport,
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

        state.export?.let { Exported(it, actions.onDismissExport) }
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

/** Where the file went, and how long the server's copy of it lasts. */
@Composable
private fun Exported(export: ExportSummary, onDismiss: () -> Unit) {
    CinematicCard(modifier = Modifier.fillMaxWidth(), glowing = true) {
        Text(text = "YOUR DATA", style = EyebrowTextStyle, color = AfterhoursTheme.colors.brass)
        Text(
            text = "${export.fileName} — ${export.bytes} bytes, on this phone.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Our copy is deleted after a week. Yours is yours.",
            style = MaterialTheme.typography.bodyMedium,
            color = AfterhoursTheme.colors.textMuted,
        )
        GlowButton(
            text = "Done",
            onClick = onDismiss,
            style = GlowButtonStyle.Quiet,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Opens a published page in whatever the phone uses for the web. Nothing is passed in the URL. */
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
