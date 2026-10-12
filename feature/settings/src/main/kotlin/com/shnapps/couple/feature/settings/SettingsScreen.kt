package com.shnapps.couple.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.icon.AfterhoursIcons
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.ui.AfterhoursSheet
import com.shnapps.couple.core.ui.ErrorBanner
import com.shnapps.couple.core.ui.LegalUrls
import com.shnapps.couple.core.ui.ScreenHeading
import com.shnapps.couple.core.ui.ScreenScaffold
import com.shnapps.couple.core.ui.SecureScreen
import com.shnapps.couple.core.ui.SettingsGroup
import com.shnapps.couple.core.ui.SettingsRow
import com.shnapps.couple.core.ui.StatusCard

/**
 * Settings (BUILD_PROMPT.md §14.9, D-146).
 *
 * Doors, mostly: the private things — limits, notifications, the app lock — each have a screen, and
 * this is where they are found. What lives here is this phone, and the pair it is half of. There is
 * no account in v2, so nothing here signs in, signs out or deletes one.
 *
 * It is drawn as objects rather than as text: one card for the pair, which is the thing that can
 * change under you, and then groups of rows with an icon each.
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
            onAskUnpair = viewModel::askUnpair,
            onKeepPair = viewModel::keepPair,
            onUnpair = viewModel::unpair,
            onDismissUnpaired = viewModel::dismissUnpaired,
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
    /** The only setup in the app, and the only row here that touches the other phone (V2). */
    val onPairing: () -> Unit = {},
    /** Debug builds only. Null in a product build, which then draws no "Developer" group at all. */
    val onGallery: (() -> Unit)? = null,
    val onInspector: (() -> Unit)? = null,
    val onBack: () -> Unit = {},
)

/** What this screen does on its own. */
internal data class SettingsActions(
    val onAskUnpair: () -> Unit = {},
    val onKeepPair: () -> Unit = {},
    val onUnpair: () -> Unit = {},
    val onDismissUnpaired: () -> Unit = {},
    val onDismissError: () -> Unit = {},
)

@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    routes: SettingsRoutes,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
) {
    val spacing = AfterhoursTheme.spacing
    Box(modifier = modifier.fillMaxSize()) {
        ScreenScaffold(title = "Settings", onBack = routes.onBack) {
            ScreenHeading(eyebrow = "Settings", headline = "Yours, and the two of you.")

            // At the top, where a failure of the thing they just tapped is seen — not at the foot of
            // a list that has scrolled it out of view.
            state.error?.let { ErrorBanner(message = it.message(), onDismiss = actions.onDismissError) }

            if (state.unpaired) UnpairedNotice(onDismiss = actions.onDismissUnpaired)

            PairingCard(
                state = state,
                onPair = {
                    // They are pairing again: what it said about the last pair has been read.
                    actions.onDismissUnpaired()
                    routes.onPairing()
                },
                onUnpair = actions.onAskUnpair,
            )

            SettingsGroup(title = "Yours") {
                SettingsRow(
                    icon = AfterhoursIcons.Limits,
                    title = "Your limits",
                    detail = "What you are up for, and what you are not.",
                    onClick = routes.onBoundaries,
                )
                SettingsRow(
                    icon = AfterhoursIcons.Bell,
                    title = "Notifications",
                    detail = "What this phone may say out loud.",
                    onClick = routes.onNotifications,
                )
                SettingsRow(
                    icon = AfterhoursIcons.Lock,
                    title = "App lock",
                    detail = "A PIN or your fingerprint, on this phone.",
                    // Said before it is opened: whether the lock is on is the one thing worth
                    // knowing about it from here.
                    value = if (state.lock.isEnabled) "On" else "Off",
                    onClick = routes.onAppLock,
                )
            }

            // Only when they exist: a row here that opens nothing would be worse than no row.
            if (LegalUrls.published) SmallPrint()

            if (routes.onGallery != null || routes.onInspector != null) {
                SettingsGroup(title = "Developer") {
                    routes.onGallery?.let {
                        SettingsRow(
                            icon = AfterhoursIcons.Tools,
                            title = "Component gallery",
                            detail = "Every component, as drawn. Debug builds only.",
                            onClick = it,
                        )
                    }
                    routes.onInspector?.let {
                        SettingsRow(
                            icon = AfterhoursIcons.Document,
                            title = "Content inspector",
                            detail = "Every card in the cache. Debug builds only.",
                            onClick = it,
                        )
                    }
                }
            }

            Text(
                text = "Afterhours · Version ${versionName(LocalContext.current)}",
                style = MaterialTheme.typography.bodySmall,
                color = AfterhoursTheme.colors.textFaint,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = spacing.sm),
            )
        }

        AfterhoursSheet(
            visible = state.confirmingUnpair,
            onDismiss = actions.onKeepPair,
            title = "Unpair",
        ) {
            UnpairQuestion(onKeep = actions.onKeepPair, onUnpair = actions.onUnpair)
        }
    }
}

/**
 * The pair, as the state it is in: paired, working on it, or not paired (D-146).
 *
 * The one card on the screen that can change under you, so it is the one drawn as an object. It is
 * lit while there is a pair, because that is the thing that is working; unpaired it is quiet.
 */
@Composable
private fun PairingCard(state: SettingsUiState, onPair: () -> Unit, onUnpair: () -> Unit) {
    when {
        state.busy -> StatusCard(
            icon = AfterhoursIcons.Phones,
            title = "Unpairing…",
            body = "Clearing the pair on both phones.",
        ) {
            // Nothing to tap while it is happening: a second tap is the thing that must not land.
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(vertical = AfterhoursTheme.spacing.xs)
                    .semantics { contentDescription = "Unpairing" },
                color = AfterhoursTheme.tier.accent,
                strokeWidth = 3.dp,
            )
        }

        state.paired -> StatusCard(
            icon = AfterhoursIcons.Phones,
            title = "Paired",
            body = "These two phones are a pair until one of you unpairs.",
            lit = true,
        ) {
            GlowButton(
                text = "Unpair…",
                onClick = onUnpair,
                style = GlowButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        else -> StatusCard(
            icon = AfterhoursIcons.Link,
            title = "Not paired",
            body = "Together mode needs nobody. Pair only to play apart.",
        ) {
            GlowButton(
                text = "Pair your phones",
                onClick = onPair,
                leadingIcon = AfterhoursIcons.Link,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** What an unpairing left, said once in words: the card below it changing is not enough. */
@Composable
private fun UnpairedNotice(onDismiss: () -> Unit) {
    StatusCard(
        icon = AfterhoursIcons.Check,
        title = "You're unpaired",
        body = "The pair, and everything you shared, is cleared on both phones. " +
            "Your own limits are still here.",
    ) {
        GlowButton(
            text = "Got it",
            onClick = onDismiss,
            style = GlowButtonStyle.Quiet,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The question before the only destructive thing in the app, and the sentence says the whole of it. */
@Composable
private fun ColumnScope.UnpairQuestion(onKeep: () -> Unit, onUnpair: () -> Unit) {
    Text(
        text = "Unpair from their phone?",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        text = "Clears the pair on both phones and everything you shared. " +
            "Your own limits stay on this phone.",
        style = MaterialTheme.typography.bodyLarge,
        color = AfterhoursTheme.colors.textMuted,
    )
    Text(
        text = "You can pair again later, with a new code.",
        style = MaterialTheme.typography.bodyMedium,
        color = AfterhoursTheme.colors.textFaint,
    )
    // The safe answer is the primary one: a thumb that lands here by habit keeps the pair.
    Column(verticalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm)) {
        GlowButton(text = "Keep the pair", onClick = onKeep, modifier = Modifier.fillMaxWidth())
        GlowButton(
            text = "Unpair and wipe",
            onClick = onUnpair,
            style = GlowButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SmallPrint() {
    val context = LocalContext.current
    SettingsGroup(title = "The small print") {
        if (LegalUrls.PRIVACY_POLICY.isNotBlank()) {
            SettingsRow(
                icon = AfterhoursIcons.Shield,
                title = "Privacy policy",
                detail = "What this app holds, and what it never shares.",
                onClick = { context.open(LegalUrls.PRIVACY_POLICY) },
            )
        }
        if (LegalUrls.TERMS.isNotBlank()) {
            SettingsRow(
                icon = AfterhoursIcons.Document,
                title = "Terms of use",
                detail = "The agreement between us.",
                onClick = { context.open(LegalUrls.TERMS) },
            )
        }
    }
}

private fun Context.open(url: String) {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

private fun SettingsError.message(): String = when (this) {
    SettingsError.OFFLINE -> "You're offline. Unpairing needs the network, so nothing has changed."
    SettingsError.OTHER -> "That didn't work, and nothing has changed. Try again."
}

/** This build's version, as the system records it. Nothing to maintain, and never wrong. */
private fun versionName(context: Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull().orEmpty().ifBlank { "—" }
