package com.shnapps.couple.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.decorativeTween

/**
 * Recording and playing a voice note (BUILD_PROMPT.md §35).
 *
 * Voice is one of the strongest long-distance mechanics in the product, so the control is
 * deliberately plain: a big obvious button, a visible elapsed time, and a delete that is
 * always reachable.
 *
 * The level meter is a single animated bar rather than a scrolling waveform. A waveform
 * would be prettier and would also mean retaining and drawing audio samples, which is
 * more of the user's most sensitive content held in memory than the decoration is worth.
 */
@Composable
fun VoiceRecorderBar(
    state: VoiceState,
    elapsedLabel: String,
    onRecord: () -> Unit,
    onStop: () -> Unit,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    level: Float = 0f,
) {
    val colors = AfterhoursTheme.colors
    val spacing = AfterhoursTheme.spacing

    val animatedLevel by animateFloatAsState(
        targetValue = if (state == VoiceState.Recording) level.coerceIn(0f, 1f) else 0f,
        animationSpec = decorativeTween(
            durationMillis = AfterhoursTheme.motion.quick,
            reduceMotion = AfterhoursTheme.reduceMotion,
        ),
        label = "voiceLevel",
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = spacing.sm)
                .heightIn(min = spacing.touchTarget),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (state) {
                VoiceState.Idle -> IconButton(
                    onClick = onRecord,
                    modifier = Modifier
                        .size(spacing.touchTarget)
                        .semantics { contentDescription = "Record a voice message" },
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }

                VoiceState.Recording -> IconButton(
                    onClick = onStop,
                    modifier = Modifier
                        .size(spacing.touchTarget)
                        .semantics { contentDescription = "Stop recording" },
                ) {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                }

                VoiceState.Recorded, VoiceState.Playing -> IconButton(
                    onClick = if (state == VoiceState.Playing) onStop else onPlay,
                    modifier = Modifier
                        .size(spacing.touchTarget)
                        .semantics {
                            contentDescription =
                                if (state == VoiceState.Playing) "Stop playback" else "Play back"
                        },
                ) {
                    Icon(
                        imageVector = if (state == VoiceState.Playing) {
                            Icons.Filled.Stop
                        } else {
                            Icons.Filled.PlayArrow
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp + (12.dp * animatedLevel)),
                shape = RoundedCornerShape(spacing.xs),
                color = if (state == VoiceState.Recording) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
            ) {}

            Text(
                text = elapsedLabel,
                style = MaterialTheme.typography.labelLarge,
                color = colors.textMuted,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )

            if (state == VoiceState.Recorded || state == VoiceState.Playing) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(spacing.touchTarget)
                        .semantics { contentDescription = "Delete this recording" },
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        tint = colors.textMuted,
                    )
                }
            }
        }
    }
}

/** A small round dot used to mark an active recording in lists. */
@Composable
internal fun RecordingDot(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(8.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.error,
    ) {}
}
