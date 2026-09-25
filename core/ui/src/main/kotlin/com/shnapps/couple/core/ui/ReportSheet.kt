package com.shnapps.couple.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shnapps.couple.core.designsystem.component.CinematicCard
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.model.ReportReason

/**
 * Reporting something (BUILD_PROMPT.md §3.5): four reasons, no text box, and one line about where
 * it goes.
 *
 * The same card wherever it appears — under a chapter, under a voice note — because the promise is
 * the same in both places: the other person is never told, and nothing about the report comes back
 * to either of them.
 */
@Composable
fun ReportSheet(
    title: String,
    onReport: (ReportReason) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sent: Boolean = false,
) {
    CinematicCard(modifier = modifier.fillMaxWidth()) {
        Text(text = "REPORT", style = EyebrowTextStyle, color = AfterhoursTheme.colors.brass)
        if (sent) {
            Text(
                text = "Thank you. Someone will look at it.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Your partner is not told, and never will be.",
                style = MaterialTheme.typography.bodyMedium,
                color = AfterhoursTheme.colors.textMuted,
            )
        } else {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Column(verticalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm)) {
                for (reason in ReportReason.entries) {
                    GlowButton(
                        text = reason.label,
                        onClick = { onReport(reason) },
                        style = GlowButtonStyle.Secondary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Text(
                text = "It goes to us, not to them. Your partner is never told.",
                style = MaterialTheme.typography.bodyMedium,
                color = AfterhoursTheme.colors.textMuted,
            )
        }
        GlowButton(
            text = if (sent) "Done" else "Never mind",
            onClick = onDismiss,
            style = GlowButtonStyle.Quiet,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
