package com.shnapps.couple.core.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.icon.AfterhoursIcons
import com.shnapps.couple.core.model.Intensity

/**
 * The safe word: a stop on every screen that plays anything (the brief's consent section).
 *
 * Not the same thing as "Too far", and the difference matters. Too far is about one card — it hides
 * that card and, if asked, everything like it, and the evening carries on. This stops the evening.
 * A couple who need it need it now, so it is one tap from anywhere, it never asks a question first,
 * and it does not care what was mid-flight.
 *
 * What follows a stop is deliberately calm: no accent, no bloom, nothing that reads as a failure.
 * Two ways on, and one of them is simply ending — [PausedContent] draws it and the screens supply
 * the two lambdas.
 */
@Composable
fun SafeWordAction(onStop: () -> Unit) {
    BarAction(label = "Stop", icon = AfterhoursIcons.Shield, onClick = onStop)
}

/**
 * Where a stop lands.
 *
 * It says what has already happened — everything is stopped — rather than asking whether they meant
 * it. A confirmation here would be the app arguing with the one control it promised would not.
 */
@Composable
fun PausedContent() {
    AfterhoursCard(
        eyebrow = "Stopped",
        title = "Everything's stopped.",
        body = "Nothing is waiting on either of you. Take as long as you like — turn it right " +
            "down and carry on gently, or end here.",
        lit = false,
    )
}

/** The two ways on from a stop. Ending is a real answer and is not the quiet one. */
@Composable
fun ColumnScope.PausedDock(onGentle: () -> Unit, onEnd: () -> Unit) {
    GlowButton(
        text = "Turn it down to Mild",
        onClick = onGentle,
        style = GlowButtonStyle.Secondary,
        leadingIcon = AfterhoursIcons.Shield,
        modifier = Modifier.fillMaxWidth(),
    )
    GlowButton(
        text = "End here",
        onClick = onEnd,
        style = GlowButtonStyle.Secondary,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * The Extreme gate (the brief's consent section): two taps, every session.
 *
 * Asked once a session rather than once ever, because "we are both up for this tonight" is a
 * different statement from "we were up for it in March". Two taps rather than one because the
 * question is whether *both* of them want it, and a single tap is one person's answer.
 *
 * It gates the top tier only. Every tier below is governed by the limits profile and the tier
 * itself; adding a confirmation to each of them would make the control meaningless by repetition.
 */
@Composable
fun ExtremeGateContent(tier: Intensity) {
    AfterhoursCard(
        eyebrow = tier.label,
        title = "Both of you want this?",
        body = "Extreme is the furthest this goes. Nothing here is a dare and nothing is a " +
            "measure of anything — the stop is on every screen, and it works mid-anything.",
        lit = true,
    )
}

/** Confirming takes two taps. Turning it down takes one, which is the way round it should be. */
@Composable
fun ColumnScope.ExtremeGateDock(confirmed: Boolean, onConfirm: () -> Unit, onDown: () -> Unit) {
    GlowButton(
        text = if (confirmed) "Yes — both of us" else "We're both in",
        onClick = onConfirm,
        leadingIcon = AfterhoursIcons.Check,
        modifier = Modifier.fillMaxWidth(),
    )
    GlowButton(
        text = "Not tonight — turn it down",
        onClick = onDown,
        style = GlowButtonStyle.Quiet,
        modifier = Modifier.fillMaxWidth(),
    )
}
