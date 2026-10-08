package com.shnapps.couple.core.ui.voice

import android.media.MediaPlayer
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Playing a voice note back, for a screen that draws its own control.
 *
 * `VoicePlayer` in this package is the other shape of the same idea: a button, for a round that
 * needs one and nothing more. This one has no UI at all, because a conversation draws a bar with a
 * waveform in it and needs to know where in the note it is.
 *
 * One at a time, on purpose: there is one pair of ears, and two notes over each other is nobody's
 * intention. Starting a second one stops the first.
 *
 * It reports where it is rather than driving a progress bar itself — a player that owned a
 * coroutine would be one no test could step through. Whatever uses it ticks.
 */
interface NotePlayer {
    /** The note playing now, or null. */
    val playing: String?

    /** How far in, and how long altogether, in milliseconds. Zeroes when nothing is playing. */
    fun progress(): Pair<Int, Int>

    /** Starts [file], tagged [id] so a screen can tell which bubble is playing. */
    fun play(id: String, file: File): Outcome<Unit>

    /** Stops whatever is playing. Safe when nothing is. */
    fun stop()
}

@Singleton
class AndroidNotePlayer @Inject constructor() : NotePlayer {

    private var player: MediaPlayer? = null
    private var current: String? = null

    override val playing: String? get() = current

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override fun progress(): Pair<Int, Int> {
        val active = player ?: return 0 to 0
        return try {
            active.currentPosition to active.duration
        } catch (error: Throwable) {
            // Being torn down mid-read. Zeroes stop the bar, which is the right answer.
            0 to 0
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun play(id: String, file: File): Outcome<Unit> {
        stop()
        return try {
            val started = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                // The note ends itself: nothing has to notice and then stop it.
                setOnCompletionListener { stop() }
                prepare()
                start()
            }
            player = started
            current = id
            Outcome.Success(Unit)
        } catch (error: Throwable) {
            stop()
            Outcome.Failure(AppError.Unknown(error))
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override fun stop() {
        try {
            player?.release()
        } catch (error: Throwable) {
            // Already gone, or never prepared. There is nothing left to stop.
        }
        player = null
        current = null
    }
}
