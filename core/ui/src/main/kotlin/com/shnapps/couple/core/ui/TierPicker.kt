package com.shnapps.couple.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.shnapps.couple.core.designsystem.theme.AfterhoursEasing
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.decorativeTween
import com.shnapps.couple.core.designsystem.theme.tierColors
import com.shnapps.couple.core.model.Intensity

/**
 * How far tonight goes: the only setting the app asks for (DESIGN_PROMPT.md §5.4).
 *
 * It used to be three primary buttons side by side, each with its own halo. At the alpha those
 * halos were drawn with, three of them overlapped into one continuous smear across the bottom of
 * the screen. It became one track with three segments, and now it is one track with **five**,
 * because the tier a card is written at and the tier a couple picks are finally the same thing.
 *
 * Five labels do not fit across a phone — "Extreme" alone needs more room than a fifth of the
 * screen — so the segments carry their dots and the chosen tier is named once, above the track.
 * That reads as what it is, a ramp, rather than as five buttons; and the name is still on screen,
 * which is the part that matters when somebody is deciding at eleven at night.
 *
 * It does **not** stack at large type, which the three-segment version did. Five stacked rows are
 * taller than the screen they are on: at 200% they pushed the age gate's own button off the
 * bottom, which `InstantAccessibilityTest` caught. A row of dots is the one layout whose height
 * does not grow with the type, so the row stays and only the name above it scales.
 *
 * **Nothing in it blooms**, because a lit thing in a row of siblings is a smear by construction.
 * The selected segment takes its own tier's container and accent, so the control shows what the
 * choice would do rather than only which one is on.
 *
 * Each names a ceiling rather than a promise: picking Extreme rules nothing out in advance, it
 * does not mean everything will be.
 */
@Composable
fun TierPicker(selected: Intensity, onTier: (Intensity) -> Unit, modifier: Modifier = Modifier) {
    val spacing = AfterhoursTheme.spacing

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Text(
            text = selected.label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = AfterhoursTheme.tier.onDark,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraSmall)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(spacing.xs)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Intensity.entries.forEach { tier ->
                Segment(tier, selected, onTier, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Segment(
    tier: Intensity,
    selected: Intensity,
    onTier: (Intensity) -> Unit,
    modifier: Modifier,
) {
    val spacing = AfterhoursTheme.spacing
    val reduceMotion = AfterhoursTheme.reduceMotion
    val isSelected = tier == selected
    // Its own tier's colours, not the screen's: the control shows what each choice would do.
    val colors = tierColors(tier)

    val fill by animateColorAsState(
        targetValue = if (isSelected) colors.container else Color.Transparent,
        animationSpec = decorativeTween(AfterhoursTheme.motion.standard, reduceMotion, AfterhoursEasing.Standard),
        label = "tierFill",
    )
    val label by animateColorAsState(
        targetValue = if (isSelected) colors.onContainer else AfterhoursTheme.colors.textFaint,
        animationSpec = decorativeTween(AfterhoursTheme.motion.standard, reduceMotion, AfterhoursEasing.Standard),
        label = "tierLabel",
    )

    Column(
        modifier = modifier
            .heightIn(min = spacing.touchTarget)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(fill)
            .selectable(selected = isSelected, onClick = { onTier(tier) }, role = Role.RadioButton)
            // Named for a screen reader whether or not the word is drawn, because the dots alone
            // are "five circles" to anything that is not looking at them.
            .semantics { contentDescription = tier.label }
            .padding(vertical = spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.xxs, Alignment.CenterVertically),
    ) {
        Text(text = tier.dots, style = MaterialTheme.typography.labelSmall, color = label)
    }
}
