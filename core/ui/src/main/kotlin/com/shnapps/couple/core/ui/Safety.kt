package com.shnapps.couple.core.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.icon.AfterhoursIcons
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme

/*
 * The safe word, and what follows it (the brief's consent section).
 *
 * Stop itself is `StopPill` on the `SafetyRail` (`:core:designsystem`), on every screen that plays
 * anything (D-143). Not the same thing as "Too far", and the difference matters. Too far is about
 * one card — it hides that card and, if asked, everything like it, and the evening carries on. Stop
 * ends the evening. A couple who need it need it now, so it is one tap from anywhere, it never asks
 * a question first, and it does not care what was mid-flight.
 *
 * What follows a stop is deliberately calm: no accent, no bloom, nothing that reads as a failure.
 * Two ways on, and one of them is simply ending.
 *
 * There is no Extreme gate here any more. Choosing Extreme is the consent: a level is a ceiling a
 * couple set together, and asking them again on top of it was a second question nobody had asked
 * for (D-144). A paired session still asks both phones for "I'm in" before it starts — that is the
 * server's, and it is untouched.
 */

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
        meta = "Breathe. Check in with each other.",
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
        leadingIcon = AfterhoursIcons.Down,
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
 * "Too far": one question, two answers, and nobody is told (§3.1).
 *
 * The same words on the deck and in a game, because it is the same statement — this one, or
 * everything like it. "Never" writes the card's own themes to this phone, which is how a limit is
 * learned in the moment instead of in a form. It never leaves the phone and the partner never sees
 * it. Keeping the card is the third way out and the quiet one: changing your mind costs nothing.
 */
@Composable
fun ColumnScope.RefusalChoices(onRefuse: (Boolean) -> Unit, onKeep: () -> Unit) {
    Text(
        text = "Skip this kind of thing?",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        text = "Nobody is told. Not tonight, not ever — your partner never sees this.",
        style = MaterialTheme.typography.bodyMedium,
        color = AfterhoursTheme.colors.textMuted,
    )
    GlowButton(
        text = "Just this one",
        onClick = { onRefuse(false) },
        style = GlowButtonStyle.Secondary,
        modifier = Modifier.fillMaxWidth(),
    )
    GlowButton(
        text = "Never show me these again",
        onClick = { onRefuse(true) },
        style = GlowButtonStyle.Secondary,
        modifier = Modifier.fillMaxWidth(),
    )
    GlowButton(
        text = "Keep this one",
        onClick = onKeep,
        style = GlowButtonStyle.Quiet,
        modifier = Modifier.fillMaxWidth(),
    )
}
