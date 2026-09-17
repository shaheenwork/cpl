package com.shnapps.couple.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.designsystem.theme.decorativeTween
import com.shnapps.couple.core.model.Intensity

/**
 * The 1-to-5 intensity selector (BUILD_PROMPT.md §14.5, Appendix A).
 *
 * Three things this encodes rather than merely displays:
 *
 *  - **Never silently escalate** (§3.1). This is a plain discrete control; nothing moves
 *    it except the user's finger.
 *  - **Levels 4 and 5 need both partners to agree** (§3.1). The dial marks that boundary
 *    visibly, so the escalation is legible *before* it is chosen rather than sprung as a
 *    dialog afterwards.
 *  - **Never colour alone** (§20). Every level carries its name, and the selected level is
 *    announced in full to TalkBack.
 *
 * [maxSelectable] is the effective ceiling — `min(contentLevel A, contentLevel B)`.
 * Anything above it is shown but not choosable, because hiding it would make the ceiling
 * invisible and confusing rather than honest.
 */
@Composable
fun IntensityDial(
    selected: Intensity,
    onSelect: (Intensity) -> Unit,
    modifier: Modifier = Modifier,
    maxSelectable: Intensity = Intensity.WILD,
) {
    val colors = AfterhoursTheme.colors
    val spacing = AfterhoursTheme.spacing

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Intensity.entries.forEach { level ->
                val enabled = level.level <= maxSelectable.level
                val isSelected = level == selected

                val barColor by animateColorAsState(
                    targetValue = when {
                        !enabled -> colors.edgeFaint
                        isSelected -> MaterialTheme.colorScheme.primary
                        level.level <= selected.level -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                        else -> MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    animationSpec = decorativeTween(
                        durationMillis = AfterhoursTheme.motion.quick,
                        reduceMotion = AfterhoursTheme.reduceMotion,
                    ),
                    label = "intensityBar",
                )

                val description = buildString {
                    append("Level ${level.level}, ${level.name.lowercase().replaceFirstChar { it.uppercase() }}")
                    if (level.requiresBothPartyConsent) append(", both partners must agree")
                    if (!enabled) append(", not available at your shared content level")
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .selectable(
                            selected = isSelected,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = { onSelect(level) },
                        )
                        .heightIn(min = spacing.touchTarget)
                        .semantics { contentDescription = description },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isSelected) 10.dp else 6.dp),
                        shape = RoundedCornerShape(spacing.xs),
                        color = barColor,
                    ) {}
                    Text(
                        text = level.level.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (enabled) colors.textMuted else colors.edgeFaint,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selected.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (selected.requiresBothPartyConsent) {
                Text(
                    text = "BOTH OF YOU CONFIRM",
                    style = EyebrowTextStyle,
                    color = colors.brass,
                    modifier = Modifier.padding(start = spacing.sm),
                )
            }
        }
    }
}
