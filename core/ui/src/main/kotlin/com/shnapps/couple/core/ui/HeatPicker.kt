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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.shnapps.couple.core.designsystem.theme.AfterhoursEasing
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.decorativeTween
import com.shnapps.couple.core.designsystem.theme.heatColors
import com.shnapps.couple.core.model.Heat

/**
 * How far tonight goes: the only setting the app asks for (DESIGN_PROMPT.md §5.4).
 *
 * It used to be three primary buttons side by side, each with its own halo. At the alpha
 * those halos were drawn with, three of them overlapped into one continuous smear across
 * the bottom of the screen — which is in the committed goldens, and is the clearest single
 * example of a light model that was not thought through.
 *
 * It is one track with three segments now. **Nothing in it blooms**, because a lit thing in
 * a row of siblings is a smear by construction. The selected segment takes its own heat's
 * container and accent, so the control shows what each choice would do rather than only
 * which one is on.
 *
 * Each names a ceiling rather than a promise: picking Wild rules nothing out in advance, it
 * does not mean everything will be.
 */
@Composable
fun HeatPicker(selected: Heat, onHeat: (Heat) -> Unit, modifier: Modifier = Modifier) {
    val spacing = AfterhoursTheme.spacing
    // Three across is right at ordinary type and wrong at 200%, where "Playful" breaks in
    // half — which is what the 200% golden showed. Past half again, they stack (§20).
    val stacked = LocalDensity.current.fontScale >= STACK_ABOVE_FONT_SCALE

    val track = modifier
        .fillMaxWidth()
        .clip(MaterialTheme.shapes.extraSmall)
        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
        .padding(spacing.xs)
        .selectableGroup()

    if (stacked) {
        Column(modifier = track, verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Heat.entries.forEach { heat -> Segment(heat, selected, onHeat, Modifier.fillMaxWidth()) }
        }
    } else {
        Row(modifier = track, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Heat.entries.forEach { heat -> Segment(heat, selected, onHeat, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun Segment(heat: Heat, selected: Heat, onHeat: (Heat) -> Unit, modifier: Modifier) {
    val spacing = AfterhoursTheme.spacing
    val reduceMotion = AfterhoursTheme.reduceMotion
    val isSelected = heat == selected
    // Its own heat's colours, not the screen's: the control shows what each choice would do.
    val colors = heatColors(heat)

    val fill by animateColorAsState(
        targetValue = if (isSelected) colors.container else Color.Transparent,
        animationSpec = decorativeTween(AfterhoursTheme.motion.standard, reduceMotion, AfterhoursEasing.Standard),
        label = "heatFill",
    )
    val label by animateColorAsState(
        targetValue = if (isSelected) colors.onContainer else AfterhoursTheme.colors.textFaint,
        animationSpec = decorativeTween(AfterhoursTheme.motion.standard, reduceMotion, AfterhoursEasing.Standard),
        label = "heatLabel",
    )

    Column(
        modifier = modifier
            .heightIn(min = spacing.touchTarget)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(fill)
            .selectable(
                selected = isSelected,
                onClick = { onHeat(heat) },
                role = androidx.compose.ui.semantics.Role.RadioButton,
            )
            .semantics { contentDescription = heat.label }
            .padding(vertical = spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.xxs, Alignment.CenterVertically),
    ) {
        Text(text = heat.dots, style = MaterialTheme.typography.labelSmall, color = label)
        Text(text = heat.label, style = MaterialTheme.typography.labelLarge, color = label)
    }
}

/** Half again above ordinary type: past this, three segments across stop fitting their words. */
private const val STACK_ABOVE_FONT_SCALE = 1.5f
