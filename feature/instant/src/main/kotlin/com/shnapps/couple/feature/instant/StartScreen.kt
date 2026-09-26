package com.shnapps.couple.feature.instant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.model.Heat
import com.shnapps.couple.core.ui.ScreenColumn
import com.shnapps.couple.core.ui.ScreenHeading
import com.shnapps.couple.core.ui.SecureScreen

/**
 * First launch, whole (REDESIGN_PROMPT.md §5.1).
 *
 * What used to be an age gate, a welcome, a sign-up, an email, twenty preference cards and a limits
 * screen is two taps: yes we are adults, and this is how tonight feels. Everything else is asked for
 * later, by whatever needs it.
 */
@Composable
fun StartScreen(
    onStarted: () -> Unit,
    onNotOldEnough: () -> Unit,
    onPairInstead: () -> Unit,
    viewModel: StartViewModel = hiltViewModel(),
) {
    SecureScreen()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.started) {
        if (state.started) onStarted()
    }

    StartContent(
        state = state,
        onHeat = viewModel::setHeat,
        onConfirm = viewModel::confirmAge,
        onNotOldEnough = onNotOldEnough,
        onPairInstead = onPairInstead,
    )
}

@Composable
internal fun StartContent(
    state: StartUiState,
    onHeat: (Heat) -> Unit,
    onConfirm: () -> Unit,
    onNotOldEnough: () -> Unit,
    onPairInstead: () -> Unit,
) {
    ScreenColumn {
        ScreenHeading(
            eyebrow = "Afterhours",
            headline = "Two questions, then we start.",
            body = "No account. No setup. Nothing leaves this phone until you ask it to.",
        )

        Text(
            text = "HOW FAR TONIGHT?",
            style = EyebrowTextStyle,
            color = AfterhoursTheme.colors.textFaint,
        )
        HeatPicker(selected = state.heat, onHeat = onHeat)
        Text(
            text = "Change it any time, mid-game, with one thumb.",
            style = MaterialTheme.typography.bodyMedium,
            color = AfterhoursTheme.colors.textMuted,
        )

        GlowButton(
            text = "We're both 18 or over — start",
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth(),
        )
        GlowButton(
            text = "My partner isn't here",
            onClick = onPairInstead,
            style = GlowButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
        GlowButton(
            text = "I'm not 18",
            onClick = onNotOldEnough,
            style = GlowButtonStyle.Quiet,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The only setting this app asks for, and it is three buttons.
 *
 * Each names a ceiling rather than a promise: picking Wild rules nothing out in advance, it does not
 * mean everything will be.
 */
@Composable
internal fun HeatPicker(selected: Heat, onHeat: (Heat) -> Unit) {
    // Three across is right at ordinary type and wrong at 200%, where "Playful" breaks in half —
    // which is what the 200% golden showed. Past half again, they stack (§20).
    val stacked = LocalDensity.current.fontScale >= STACK_ABOVE_FONT_SCALE

    if (stacked) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.xs),
        ) {
            Heat.entries.forEach { heat -> HeatButton(heat, selected, onHeat, Modifier.fillMaxWidth()) }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
