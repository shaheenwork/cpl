package com.shnapps.couple.feature.chat

import com.shnapps.couple.core.common.ApplicationScope
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.ui.voice.NotePlayer
import com.shnapps.couple.core.ui.voice.VoiceLevels
import com.shnapps.couple.core.ui.voice.VoiceRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** A recording in progress: how long so far, and how loud right now. */
data class Recording(val seconds: Int, val level: Float)

/** A note playing: which one, how far through it, and how far in seconds. */
data class Playing(val id: String, val fraction: Float, val seconds: Int)

/** A finished recording, ready to send: the file, its length, and the shape it will be drawn as. */
data class VoiceTake(val file: File, val seconds: Int, val levels: List<Int>)

/**
 * The microphone and the speaker, as the chat uses them.
 *
 * It owns its own ticking, and that is the point. A ViewModel that polled the recorder in a
 * `while` loop of its own would hang `advanceUntilIdle()` for ever — the beat belongs to the
 * collaborator, never to the screen (D-076). A test substitutes this whole thing and ticks by
 * hand, or not at all.
 */
interface ChatVoice {
    val recording: StateFlow<Recording?>
    val playing: StateFlow<Playing?>

    /** Starts recording. False when the microphone refused — there is nothing to show for it. */
    fun start(): Boolean

    /** Stops, and hands back what was recorded. Null when nothing usable came of it. */
    fun finish(): VoiceTake?

    /** Throws it away: they changed their mind, or the chat closed. */
    fun cancel()

    /** Plays one note, stopping any other. False when it would not play. */
    fun play(id: String, file: File): Boolean

    fun stopPlaying()
}

@Singleton
class DefaultChatVoice @Inject constructor(
    private val recorder: VoiceRecorder,
    private val player: NotePlayer,
    @ApplicationScope private val scope: CoroutineScope,
) : ChatVoice {

    private val recordingState = MutableStateFlow<Recording?>(null)
    override val recording: StateFlow<Recording?> = recordingState.asStateFlow()

    private val playingState = MutableStateFlow<Playing?>(null)
    override val playing: StateFlow<Playing?> = playingState.asStateFlow()

    /** Every amplitude read while recording. Reduced to the waveform's bars when it stops. */
    private val samples = mutableListOf<Int>()
    private var ticker: Job? = null

    override fun start(): Boolean {
        if (recorder.start() !is Outcome.Success) return false
        samples.clear()
        recordingState.value = Recording(seconds = 0, level = 0f)
        ticker?.cancel()
        ticker = scope.launch {
            var elapsed = 0L
            while (isActive) {
                delay(POLL_MS)
                elapsed += POLL_MS
                val amplitude = recorder.amplitude()
                samples += amplitude
                recordingState.value = Recording(
                    seconds = (elapsed / MILLIS).toInt(),
                    level = VoiceLevels.meter(amplitude),
                )
            }
        }
        return true
    }

    override fun finish(): VoiceTake? {
        val seconds = recordingState.value?.seconds ?: 0
        val taken = samples.toList()
        stopTicking()
        val file = (recorder.stop() as? Outcome.Success)?.value ?: return null
        return VoiceTake(file = file, seconds = seconds, levels = VoiceLevels.of(taken))
    }

    override fun cancel() {
        stopTicking()
        recorder.cancel()
    }

    override fun play(id: String, file: File): Boolean {
        if (player.play(id, file) !is Outcome.Success) return false
        playingState.value = Playing(id = id, fraction = 0f, seconds = 0)
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive && player.playing == id) {
                val (elapsed, total) = player.progress()
                playingState.value = Playing(
                    id = id,
                    fraction = if (total > 0) (elapsed.toFloat() / total).coerceIn(0f, 1f) else 0f,
                    seconds = elapsed / MILLIS.toInt(),
                )
                delay(POLL_MS)
            }
            // The note ended by itself: the player stopped, so the bar has to stop too.
            if (isActive) playingState.value = null
        }
        return true
    }

    override fun stopPlaying() {
        ticker?.cancel()
        ticker = null
        player.stop()
        playingState.value = null
    }

    private fun stopTicking() {
        ticker?.cancel()
        ticker = null
        recordingState.value = null
        samples.clear()
    }

    private companion object {
        /** Ten reads a second: enough for a meter that looks alive and a waveform with detail. */
        const val POLL_MS = 100L
        const val MILLIS = 1_000L
    }
}
