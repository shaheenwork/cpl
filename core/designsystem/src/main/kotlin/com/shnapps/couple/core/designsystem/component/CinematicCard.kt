package com.shnapps.couple.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme

/**
 * A raised panel: the base surface for something the product presents as a group.
 *
 * It used to be the hero as well, which is why the card a couple actually reads looked
 * like a list row. The hero is `AfterhoursCard` now (DESIGN_PROMPT.md §5.1); this is
 * everything else — a panel, a grouped block, a summary.
 *
 * Depth comes from `Modifier.raised`: fill, a rim light brighter along the top edge, and
 * weight underneath from `floating` up. Not from a border of uniform hairline, which is
 * what made every surface in the app read as the same object.
 */
@Composable
fun CinematicCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.large,
    glowing: Boolean = false,
    contentDescription: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = AfterhoursTheme.spacing
    val tier = AfterhoursTheme.tier
    // Bound to a local so the semantics setter below is not shadowed by the parameter.
    val description = contentDescription

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (glowing) {
                    Modifier.bloom(color = tier.rim, cornerRadius = spacing.lg, spread = spacing.xl)
                } else {
                    Modifier
                },
            )
            .raised(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = shape,
                rimTop = if (glowing) tier.rim.copy(alpha = RIM_LIT) else AfterhoursTheme.colors.rimTop,
            )
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(
                if (description != null) {
                    Modifier.semantics { this.contentDescription = description }
                } else {
                    Modifier
                },
            )
            .padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.tight),
        content = content,
    )
}

/** How bright a lit panel's top edge goes. Enough to read as light, not as a stroke. */
private const val RIM_LIT = 0.30f
