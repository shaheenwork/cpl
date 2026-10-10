package com.shnapps.couple.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.shnapps.couple.core.designsystem.icon.AfterhoursIcons
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme

/**
 * The controls that keep an evening safe, in one place on every screen that plays anything.
 *
 * Stop on the left, always. On the right whatever else that screen can refuse or hold — Too far,
 * Pause — and then the chat, when there is one (`BarExtra`). At the bottom of the screen, where a
 * thumb already is: the deck used to put Stop in the top corner and the two-phone game put it at
 * the bottom, so the one control that must never be hunted for lived in two places (D-143).
 *
 * Stop is drawn in the error colour and never in the tier's: it must not read as decoration, and
 * it must not read as the screen's primary action either.
 */
@Composable
fun SafetyRail(
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val spacing = AfterhoursTheme.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.md, vertical = spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StopPill(onStop = onStop)
        Spacer(modifier = Modifier.weight(1f))
        trailing()
        BarExtra()
    }
}

/** The safe word. One tap, no question (M7). */
@Composable
fun StopPill(onStop: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = AfterhoursTheme.spacing
    val signal = MaterialTheme.colorScheme.error
    val shape = MaterialTheme.shapes.extraLarge
    Row(
        modifier = modifier
            .heightIn(min = spacing.touchTarget)
            .clip(shape)
            .background(signal.copy(alpha = STOP_FILL), shape)
            .border(width = spacing.hairline, color = signal.copy(alpha = STOP_EDGE), shape = shape)
            .clickable(role = Role.Button, onClick = onStop)
            .semantics { contentDescription = "Stop. Ends everything, asks nothing." }
            .padding(start = spacing.item, end = spacing.md),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = AfterhoursIcons.SafeWord,
            contentDescription = null,
            tint = signal,
            modifier = Modifier.size(ICON),
        )
        Text(text = "Stop", style = MaterialTheme.typography.labelLarge, color = signal)
    }
}

/** A quiet control on the rail: Too far, Pause. Never the accent; never small. */
@Composable
fun RailAction(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = AfterhoursTheme.spacing
    val ink = AfterhoursTheme.colors.textMuted
    Row(
        modifier = modifier
            .heightIn(min = spacing.touchTarget)
            .clip(MaterialTheme.shapes.extraLarge)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = spacing.item),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = ink, modifier = Modifier.size(ICON))
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = ink)
    }
}

private val ICON = AfterhoursIcons.SafeWord.defaultWidth * RAIL_ICON_SCALE
private const val RAIL_ICON_SCALE = 0.84f
private const val STOP_FILL = 0.08f
private const val STOP_EDGE = 0.38f
