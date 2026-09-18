package com.shnapps.couple.core.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme

/** A dismissable problem report, announced politely to screen readers. */
@Composable
fun ErrorBanner(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = AfterhoursTheme.spacing
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(spacing.md)
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(start = spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            // Not a GlowButton: its quiet style is burgundy, which is too faint on the error
            // container to pass contrast (section 20).
            TextButton(onClick = onDismiss) {
                Text(
                    text = "OK",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}
