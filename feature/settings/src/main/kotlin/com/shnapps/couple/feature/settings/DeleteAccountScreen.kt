package com.shnapps.couple.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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

/**
 * Deleting an account (BUILD_PROMPT.md §14.9, D-093).
 *
 * It says what will go before it goes — including the part people do not expect, which is that the
 * couple goes too, with everything the two of them made. Nothing is deleted until the second tap,
 * and the second tap is behind the first: no single stray press on someone else's phone.
 */
@Composable
fun DeleteAccountScreen(
    onDeleted: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    SecureScreen()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.deleted) {
        if (state.deleted) onDeleted()
    }
    var sure by remember { mutableStateOf(false) }
    DeleteAccountContent(
        state = state,
        sure = sure,
        actions = DeleteAccountActions(
            onSure = { sure = true },
            onDelete = viewModel::deleteAccount,
            onDismissError = viewModel::dismissError,
            onBack = onBack,
        ),
        modifier = modifier,
    )
}

/** Everything this screen can do, gathered so the content stays easy to preview. */
internal data class DeleteAccountActions(
    val onSure: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onDismissError: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/** What goes, in the order people worry about it. */
private val GOES = listOf(
    "Your account, and the way back into it.",
    "Your private answers, your limits, and everything only you could see.",
    "The couple — and with it every night you played, every memory you kept, and everything " +
        "either of you sent the other.",
    "Anything waiting: surprises, countdowns, days of an arc.",
)

@Composable
internal fun DeleteAccountContent(
    state: SettingsUiState,
    sure: Boolean,
    actions: DeleteAccountActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        ScreenColumn {
            BackRow(label = "Back", onBack = actions.onBack)
            ScreenHeading(
                eyebrow = "Delete my account",
                headline = "This cannot be undone.",
                body = "There is no copy, no grace period and no way to ask for it back.",
            )
            CinematicCard(modifier = Modifier.fillMaxWidth()) {
                Text(text = "WHAT GOES", style = EyebrowTextStyle, color = AfterhoursTheme.colors.brass)
                Column(verticalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm)) {
                    for (line in GOES) {
                        Text(
                            text = "• $line",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            Text(
                text = if (state.paired) {
                    "Your partner keeps their own account, their own answers and their own limits — " +
                        "and nothing the two of you made together."
                } else {
                    "You are not in a couple, so there is nothing shared to take with it."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = AfterhoursTheme.colors.textMuted,
            )
            Text(
                text = "If you only want to leave the couple, unpair instead — that keeps your account.",
                style = MaterialTheme.typography.bodyMedium,
                color = AfterhoursTheme.colors.textMuted,
            )
            if (sure) {
                GlowButton(
                    text = if (state.busy) "Deleting…" else "Delete everything, permanently",
                    onClick = actions.onDelete,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                GlowButton(
                    text = "I understand what goes",
                    onClick = actions.onSure,
                    style = GlowButtonStyle.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            GlowButton(
                text = "Keep my account",
                onClick = actions.onBack,
                style = GlowButtonStyle.Quiet,
                modifier = Modifier.fillMaxWidth(),
            )
            state.error?.let {
                ErrorBanner(
                    message = "That didn't work, and nothing was deleted. Try again.",
                    onDismiss = actions.onDismissError,
                )
            }
        }
    }
}
