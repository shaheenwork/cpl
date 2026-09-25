package com.shnapps.couple.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shnapps.couple.core.designsystem.component.CinematicCard
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.ui.BackRow
import com.shnapps.couple.core.ui.ErrorBanner
import com.shnapps.couple.core.ui.ScreenColumn
import com.shnapps.couple.core.ui.ScreenHeading
import com.shnapps.couple.core.ui.SecureScreen
import java.io.File

/**
 * Settings (BUILD_PROMPT.md §14.9).
 *
 * Most of it is doors: the private things — answers, limits, the app lock — each already have a
 * screen, and this is where they are found. What lives here is the account itself: what it holds
 * about you, how to take it away from this couple, and how to end it entirely.
 */
@Composable
fun SettingsScreen(
    routes: SettingsRoutes,
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    SecureScreen()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val exportsDirectory = File(LocalContext.current.cacheDir, "exports")
    // In an effect, not in composition: leaving is something that happens once, not something
    // that happens again on every frame the state is still "gone".
    LaunchedEffect(state.deleted) {
        if (state.deleted) onSignedOut()
    }
    SettingsContent(
        state = state,
        routes = routes,
        actions = SettingsActions(
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
    val onPreferences: () -> Unit = {},
    val onBoundaries: () -> Unit = {},
    val onNotifications: () -> Unit = {},
    val onAppLock: () -> Unit = {},
    val onMemories: () -> Unit = {},
    val onPremium: () -> Unit = {},
    val onDeleteAccount: () -> Unit = {},
    val onSignOut: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/** What this screen does on its own. */
internal data class SettingsActions(
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
    Column(modifier = modifier) {
        ScreenColumn {
            BackRow(label = "Back", onBack = routes.onBack)
            ScreenHeading(
                eyebrow = "Settings",
                headline = "Yours, and the two of you.",
                body = state.email?.let { "Signed in as $it." },
            )

            Group("You") {
                Row("Your private curiosities", "Only ever yours.", routes.onPreferences)
                Row("Your limits", "What you are up for, and what you are not.", routes.onBoundaries)
                Row("Notifications", "What this phone may say out loud.", routes.onNotifications)
                Row("App lock", "A PIN or your fingerprint, on this phone.", routes.onAppLock)
            }

            Group("The two of you") {
                Row("Our memories", "The nights you kept.", routes.onMemories)
                Row(
                    title = if (state.entitlement.premium) "Premium" else "Afterhours premium",
                    detail = if (state.entitlement.premium) "You're in, both of you." else "What's included.",
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
                Row("Delete my account", "This one cannot be undone.", routes.onDeleteAccount)
                Row("Sign out", "On this phone.", routes.onSignOut)
            }

            state.export?.let { Exported(it, actions.onDismissExport) }
            state.error?.let { ErrorBanner(message = it.message(), onDismiss = actions.onDismissError) }
        }
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

private fun SettingsError.message(): String = when (this) {
    SettingsError.OFFLINE -> "You're offline. That one needs the network."
    SettingsError.OTHER -> "That didn't work. Try again."
}
