package com.shnapps.couple.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shnapps.couple.core.designsystem.component.CinematicCard
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.model.QuietHours
import com.shnapps.couple.core.ui.BackRow
import com.shnapps.couple.core.ui.ErrorBanner
import com.shnapps.couple.core.ui.ScreenColumn
import com.shnapps.couple.core.ui.ScreenHeading
import com.shnapps.couple.core.ui.SecureScreen

/**
 * What this phone is allowed to say out loud (BUILD_PROMPT.md §16.2).
 *
 * The screen is careful about its own promise: a notification never carries anything either of
 * them wrote, whichever way these switches are set. What they choose is whether it may name the
 * kind of thing that happened, and when it may arrive at all.
 */
@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationSettingsViewModel = hiltViewModel(),
) {
    SecureScreen()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    NotificationSettingsContent(
        state = state,
        actions = NotificationSettingsActions(
            onEnabled = viewModel::setEnabled,
            onShowContent = viewModel::setShowContent,
            onQuietHours = viewModel::setQuietHours,
            onDismissError = viewModel::dismissError,
            onBack = onBack,
        ),
        modifier = modifier,
    )
}

/** Everything this screen can do, gathered so the content stays easy to preview. */
internal data class NotificationSettingsActions(
    val onEnabled: (Boolean) -> Unit = {},
    val onShowContent: (Boolean) -> Unit = {},
    val onQuietHours: (Boolean) -> Unit = {},
    val onDismissError: () -> Unit = {},
    val onBack: () -> Unit = {},
)

@Composable
internal fun NotificationSettingsContent(
    state: NotificationSettingsUiState,
    actions: NotificationSettingsActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        ScreenColumn {
            BackRow(label = "Back", onBack = actions.onBack)
            ScreenHeading(
                eyebrow = "Notifications",
                headline = "What your phone may say.",
                body = "A notification never carries anything either of you wrote — not a title, " +
                    "not a word of it. What arrives is that something happened; the rest is here, " +
                    "behind your lock.",
            )
            SettingRow(
                title = "Let us tell you",
                detail = "Off means nothing at all, from anything.",
                checked = state.settings.enabled,
                enabled = !state.busy,
                onChange = actions.onEnabled,
            )
            SettingRow(
                title = "Say what kind of thing it was",
                detail = if (state.settings.showContent) {
                    "\"They left you something 👀\""
                } else {
                    "\"Something's waiting for you.\" — and nothing more."
                },
                checked = state.settings.showContent,
                enabled = state.settings.enabled && !state.busy,
                onChange = actions.onShowContent,
            )
            SettingRow(
                title = "Quiet hours",
                detail = state.settings.quietHours?.let { "Nothing between ${it.from} and ${it.to}." }
                    ?: "Nothing between ${QuietHours.NIGHT.from} and ${QuietHours.NIGHT.to}, when on.",
                checked = state.quietHoursOn,
                enabled = state.settings.enabled && !state.busy,
                onChange = actions.onQuietHours,
            )
            CinematicCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "WHAT WE SEND",
                    style = EyebrowTextStyle,
                    color = AfterhoursTheme.colors.brass,
                )
                Text(
                    text = "A notification is an id and a kind — nothing readable. Whatever it is " +
                        "about is fetched by this app, signed in as you, once you open it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AfterhoursTheme.colors.textMuted,
                )
            }
            if (state.failed) {
                ErrorBanner(message = "That didn't save. Try again.", onDismiss = actions.onDismissError)
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    detail: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(LABEL_WIDTH),
            verticalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.xxs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = AfterhoursTheme.colors.textMuted,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** The label takes most of the row; the switch takes the rest. */
private const val LABEL_WIDTH = 0.75f
