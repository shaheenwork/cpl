package com.shnapps.couple.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shnapps.couple.core.designsystem.icon.AfterhoursIcons

/**
 * **Stop is always one tap away** (BUILD_PROMPT.md §3.1).
 *
 * This bar belongs on every experience and session screen, in every state. It is never
 * hidden behind a menu, a scroll position or a confirmation chain. What it encodes, all
 * of it non-negotiable:
 *
 *  - STOP ends the session immediately. There is no "are you sure?" gauntlet.
 *  - The copy stays warm and neutral. Stopping is never framed as failure, and the
 *    partner is never told whose idea it was.
 *  - STOP uses the error colour, which the palette keeps deliberately distinct from every
 *    tier's accent so it can never be mistaken for decoration.
 *  - Both controls meet the 48dp target (§20), and neither is ever animated out of reach.
 *
 * It is the safety rail with Pause on it: the same rail every playing screen has, so Stop is in
 * the same place in a game played a phone each as it is on the deck (D-143).
 */
@Composable
fun StopPauseBar(
    onPauseToggle: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    paused: Boolean = false,
) {
    SafetyRail(onStop = onStop, modifier = modifier) {
        RailAction(
            label = if (paused) "Resume" else "Pause",
            icon = if (paused) AfterhoursIcons.Play else AfterhoursIcons.Pause,
            onClick = onPauseToggle,
        )
    }
}
