package com.shnapps.couple.core.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.shnapps.couple.core.designsystem.theme.AfterhoursEasing
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle

/**
 * A sealed thing waiting for its moment: a mystery chapter, a scheduled surprise, a future
 * capsule, a day of a multi-day arc (BUILD_PROMPT.md §15.2 — "locked card → countdown →
 * unlock", and the vault treatment for scheduled surprises).
 *
 * The brass accent is reserved for exactly this: locks, vaults and sealed envelopes.
 *
 * As with [RevealCard], locked means the payload is genuinely absent from the tree, not
 * merely covered (§5.7). The caller passes only what may be shown *before* unlocking.
 */
@Composable
fun LockedCard(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    subtitle: String? = null,
    unlocked: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = AfterhoursTheme.colors
    val spacing = AfterhoursTheme.spacing
    val reduceMotion = AfterhoursTheme.reduceMotion

    // The lock breathes very slowly while sealed, so a waiting card still feels alive.
    val transition = rememberInfiniteTransition(label = "lockedCard")
    val shimmer by transition.animateFloat(
        initialValue = if (reduceMotion || unlocked) 1f else 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = AfterhoursTheme.motion.ambient,
                easing = AfterhoursEasing.Breathing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "lockShimmer",
    )

    val stateDescription = if (unlocked) "Unlocked" else "Locked, not available yet"

    CinematicCard(
        modifier = modifier.semantics { contentDescription = "$title. $stateDescription" },
        onClick = onClick,
        glowing = unlocked,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (unlocked) Icons.Filled.LockOpen else Icons.Filled.Lock,
                contentDescription = null,
                tint = colors.brass.copy(alpha = shimmer),
            )
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                if (eyebrow != null) {
                    Text(
                        text = eyebrow.uppercase(),
                        style = EyebrowTextStyle,
                        color = colors.brass,
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                    )
                }
            }
        }
    }
}
