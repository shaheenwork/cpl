package com.shnapps.couple.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.shnapps.couple.core.designsystem.theme.AfterhoursEasing
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.decorativeTween

/**
 * Where an evening is, as a shape (DESIGN_PROMPT.md §5.6).
 *
 * It was four words of 11 sp grey text — "2 of 6 this run" — floating between the card and the
 * buttons with nothing to anchor it. A person could read where they were; they could not *see*
 * it, and an evening that visibly moves along is most of what makes one feel like an evening
 * rather than a list of prompts.
 *
 * One segment per card: done behind, the current one in the heat's own accent, the rest waiting.
 * The whole rail carries one description, so a screen reader hears "Card 2 of 6" instead of six
 * anonymous boxes.
 */
@Composable
fun RunProgress(
    current: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    if (total <= 0) return
    val spacing = AfterhoursTheme.spacing
    val position = current.coerceIn(0, total - 1)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.gutter, vertical = spacing.sm)
            .semantics { contentDescription = "Card ${position + 1} of $total" },
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        repeat(total) { index ->
            Segment(
                state = when {
                    index < position -> SegmentState.DONE
                    index == position -> SegmentState.NOW
                    else -> SegmentState.TO_COME
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private enum class SegmentState { DONE, NOW, TO_COME }

@Composable
private fun Segment(state: SegmentState, modifier: Modifier) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val color by animateColorAsState(
        targetValue = when (state) {
            SegmentState.DONE -> onSurface.copy(alpha = DONE)
            SegmentState.NOW -> AfterhoursTheme.tier.accent
            SegmentState.TO_COME -> onSurface.copy(alpha = TO_COME)
        },
        animationSpec = decorativeTween(
            AfterhoursTheme.motion.standard,
            AfterhoursTheme.reduceMotion,
            AfterhoursEasing.Standard,
        ),
        label = "runSegment",
    )
    Box(
        modifier = modifier
            .height(RAIL)
            .background(color, MaterialTheme.shapes.extraSmall),
    )
}

private val RAIL = 3.dp

/** Behind you, and still to come. Both have to read as the same rail. */
private const val DONE = 0.35f
private const val TO_COME = 0.10f
