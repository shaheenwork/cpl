package com.shnapps.couple.core.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shnapps.couple.core.designsystem.component.CinematicCard
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.icon.AfterhoursIcons
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.model.Gate

/**
 * A limit they have reached (BUILD_PROMPT.md §19), said the same way everywhere it happens.
 *
 * It appears where the thing was asked for, before anything was built — never over a night that
 * has started, which nothing in the app is allowed to interrupt. "Not now" leaves everything as
 * it was, and the screen says what is still theirs.
 */
@Composable
fun PremiumNudge(
    gate: Gate,
    onSeeWhatsIncluded: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CinematicCard(modifier = modifier.fillMaxWidth()) {
        Text(text = "PREMIUM", style = EyebrowTextStyle, color = AfterhoursTheme.colors.brass)
        Text(
            text = gate.headline,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = gate.detail,
            style = MaterialTheme.typography.bodyMedium,
            color = AfterhoursTheme.colors.textMuted,
        )
        GlowButton(
            text = "See what's included",
            onClick = onSeeWhatsIncluded,
            leadingIcon = AfterhoursIcons.Spark,
            modifier = Modifier.fillMaxWidth(),
        )
        GlowButton(
            text = "Not now",
            onClick = onDismiss,
            style = GlowButtonStyle.Quiet,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
