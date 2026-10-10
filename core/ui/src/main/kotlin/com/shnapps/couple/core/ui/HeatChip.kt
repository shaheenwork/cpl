package com.shnapps.couple.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shnapps.couple.core.designsystem.component.HeatFlames
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.model.Intensity

/**
 * Tonight's heat, folded into a chip that opens the picker (D-143).
 *
 * The picker used to sit under the card on every playing screen, so a third of the screen was a
 * control the couple had set a minute before. It is one tap away now, in the bar — the same control,
 * out of the way of the card.
 */
@Composable
fun HeatChip(tier: Intensity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = AfterhoursTheme.tier
    Chip(
        onClick = onClick,
        description = "Heat: level ${tier.level} of 5, ${tier.label}. Change it",
        modifier = modifier,
        lit = true,
    ) {
        HeatFlames(count = tier.level, color = accent.accent)
        Text(text = tier.label, style = MaterialTheme.typography.labelLarge, color = accent.onDark, maxLines = 1)
    }
}

/** A lane, folded the same way: the pack being dealt from, or "Everything". */
@Composable
fun LaneChip(label: String?, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val text = label ?: "Everything"
    Chip(onClick = onClick, description = "Dealing from: $text. Change it", modifier = modifier, lit = false) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AfterhoursTheme.colors.textMuted,
            modifier = Modifier.size(CHIP_ICON),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = AfterhoursTheme.colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 36 dp to look at, 48 dp to hit: the touch target is the row's, padded to the minimum. */
@Composable
private fun Chip(
    onClick: () -> Unit,
    description: String,
    lit: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val spacing = AfterhoursTheme.spacing
    val tier = AfterhoursTheme.tier
    val shape = MaterialTheme.shapes.extraLarge
    val fill = if (lit) tier.accent.copy(alpha = LIT_FILL) else AfterhoursTheme.colors.edgeFaint
    val edge = if (lit) tier.rim.copy(alpha = LIT_EDGE) else AfterhoursTheme.colors.rimTop
    Row(
        modifier = modifier
            .heightIn(min = spacing.touchTarget)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(vertical = spacing.xs + spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .clip(shape)
                .background(fill, shape)
                .border(spacing.hairline, edge, shape)
                .padding(horizontal = spacing.item, vertical = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) { content() }
    }
}

private val CHIP_ICON = 16.dp
private const val LIT_FILL = 0.12f
private const val LIT_EDGE = 0.4f
