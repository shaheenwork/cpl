package com.shnapps.couple.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme

/**
 * **Stop is always one tap away** (BUILD_PROMPT.md §3.1).
 *
 * This bar belongs on every experience and session screen, in every state. It is never
 * hidden behind a menu, a scroll position or a confirmation chain. What it encodes, all
 * of it non-negotiable:
 *
 *  - STOP ends the session immediately. There is no "are you sure?" gauntlet.
 *  - The copy stays warm and neutral. Stopping is never framed as failure, and the
 *    partner is never told whose idea it was.
 *  - STOP uses the error colour, which the palette keeps deliberately distinct from the
 *    burgundy accent so it can never be mistaken for decoration.
 *  - Both controls meet the 48dp target (§20), and neither is ever animated out of reach.
 *
 * The caller owns what STOP does; this component only guarantees it is always reachable.
 */
@Composable
fun StopPauseBar(
    onPauseToggle: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    paused: Boolean = false,
) {
    val spacing = AfterhoursTheme.spacing

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, MaterialTheme.colorScheme.background),
                ),
            )
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onPauseToggle,
            modifier = Modifier.heightIn(min = spacing.touchTarget),
        ) {
            Icon(
                imageVector = if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                contentDescription = null,
                tint = AfterhoursTheme.colors.textMuted,
            )
            Text(
                text = if (paused) "Resume" else "Pause",
                style = MaterialTheme.typography.labelLarge,
                color = AfterhoursTheme.colors.textMuted,
                modifier = Modifier.padding(start = spacing.xs),
            )
        }

        TextButton(
            onClick = onStop,
            modifier = Modifier.heightIn(min = spacing.touchTarget),
        ) {
            Icon(
                imageVector = Icons.Filled.Stop,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                text = "Stop",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = spacing.xs),
            )
        }
    }
}
