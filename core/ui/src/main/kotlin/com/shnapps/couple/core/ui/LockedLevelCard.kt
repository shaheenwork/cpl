package com.shnapps.couple.core.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shnapps.couple.core.designsystem.component.CinematicCard
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.icon.AfterhoursIcons
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.model.Intensity

/**
 * A level they tapped that they do not have yet (V2 rule 5).
 *
 * **Not a wall.** It appears where the tap happened, says what the level is and what it costs, and
 * the second button puts everything back as it was. There is no countdown on it, no crossed-out
 * price and no second ask — the brief rules all three out, and a couple deciding at eleven at night
 * deserves to be told the price once and left alone.
 *
 * What it says about the pack is the thing most stores leave out: buying this level opens the ones
 * below it too. That is not generosity, it is how the deck works — a ceiling with a hole in it
 * would skip rungs on the way up — and a couple should know it before they pay rather than after.
 */
@Composable
fun LockedLevelCard(
    level: Intensity,
    price: String?,
    onUnlock: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CinematicCard(modifier = modifier.fillMaxWidth()) {
        Text(text = level.label.uppercase(), style = EyebrowTextStyle, color = AfterhoursTheme.colors.brass)
        Text(
            text = "${level.label} is a pack.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = includedIn(level),
            style = MaterialTheme.typography.bodyMedium,
            color = AfterhoursTheme.colors.textMuted,
        )
        GlowButton(
            text = price?.let { "Unlock ${level.label} — $it" } ?: "Unlock ${level.label}",
            onClick = onUnlock,
            leadingIcon = AfterhoursIcons.Spark,
            modifier = Modifier.fillMaxWidth(),
        )
        GlowButton(
            text = "Not now",
            onClick = onDismiss,
            style = GlowButtonStyle.Quiet,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** What the pack opens, in the one sentence the store screen says at more length. */
private fun includedIn(level: Intensity): String {
    val below = Intensity.entries
        .filter { it.level in Intensity.SPICY.level until level.level }
        .joinToString(" and ") { it.label }
    val once = "Bought once, yours for good — and your partner's too, once you are paired. " +
        "Mild and Flirty stay free whatever you do."
    return when {
        below.isEmpty() -> "Every ${level.label} card, in all seven games. $once"
        else -> "Every ${level.label} card, in all seven games — and $below with it. $once"
    }
}
