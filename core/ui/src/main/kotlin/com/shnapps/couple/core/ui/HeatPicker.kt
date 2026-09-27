package com.shnapps.couple.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.model.Heat

/**
 * How far tonight goes: the only setting the app asks for, and it is three buttons.
 *
 * Each names a ceiling rather than a promise: picking Wild rules nothing out in advance, it does not
 * mean everything will be. It lives in :core:ui because first launch and home both ask it, and one
 * feature may not reach into another (§4.2).
 */
@Composable
fun HeatPicker(selected: Heat, onHeat: (Heat) -> Unit, modifier: Modifier = Modifier) {
    // Three across is right at ordinary type and wrong at 200%, where "Playful" breaks in half —
    // which is what the 200% golden showed. Past half again, they stack (§20).
    val stacked = LocalDensity.current.fontScale >= STACK_ABOVE_FONT_SCALE

    if (stacked) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.xs),
        ) {
            Heat.entries.forEach { heat -> HeatButton(heat, selected, onHeat, Modifier.fillMaxWidth()) }
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.xs),
        ) {
            Heat.entries.forEach { heat -> HeatButton(heat, selected, onHeat, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun HeatButton(heat: Heat, selected: Heat, onHeat: (Heat) -> Unit, modifier: Modifier) {
    GlowButton(
        text = "${heat.dots}  ${heat.label}",
        onClick = { onHeat(heat) },
        style = if (heat == selected) GlowButtonStyle.Primary else GlowButtonStyle.Secondary,
        modifier = modifier.heightIn(min = AfterhoursTheme.spacing.touchTarget),
    )
}

/** Half again above ordinary type: past this, three buttons across stop fitting their own words. */
private const val STACK_ABOVE_FONT_SCALE = 1.5f
