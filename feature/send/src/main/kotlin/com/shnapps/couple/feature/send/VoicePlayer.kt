package com.shnapps.couple.feature.send

import android.media.MediaPlayer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import java.io.File

/**
 * Playing a voice note from a file on this phone (BUILD_PROMPT.md §13). Never from a URL: the
 * bytes were fetched with the user's own credentials, and the player is released the moment the
 * screen goes.
 */
@Composable
internal fun VoicePlayer(file: File, modifier: Modifier = Modifier) {
    // Screenshot tests render this without a media stack; they show the control, not the sound.
    val inspecting = LocalInspectionMode.current
    var playing by remember(file) { mutableStateOf(false) }
    val player = remember(file) {
        if (inspecting) {
            null
        } else {
            runCatching {
                MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    prepare()
                }
            }.getOrNull()
        }
    }

    DisposableEffect(player) {
        player?.setOnCompletionListener { playing = false }
        onDispose {
            player?.setOnCompletionListener(null)
            player?.release()
        }
    }

    GlowButton(
        text = if (playing) "Pause" else "Play it",
        onClick = {
            val media = player ?: return@GlowButton
            if (media.isPlaying) {
                media.pause()
                playing = false
            } else {
                media.start()
                playing = true
            }
        },
        leadingEmoji = "🎧",
        modifier = modifier.fillMaxWidth(),
    )
    if (player == null && !inspecting) {
        Text(
            text = "This one can't be played on this phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = AfterhoursTheme.colors.textMuted,
        )
    }
}
