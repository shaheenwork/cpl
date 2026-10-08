package com.shnapps.couple.feature.chat

import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/**
 * The microphone and the speaker, without either.
 *
 * It does not tick. That is the point: the real one owns a polling job so a ViewModel does not
 * have to, and a ViewModel that ticked on its own would hang `advanceUntilIdle()` for ever
 * (D-076). A test moves the clock by setting the state.
 */
internal class FakeChatVoice : ChatVoice {

    override val recording = MutableStateFlow<Recording?>(null)
    override val playing = MutableStateFlow<Playing?>(null)

    /** What the microphone will do when asked. False is a refusal. */
    var available = true

    /** What `finish` hands back. Null is a recording that came to nothing. */
    var take: VoiceTake? = VoiceTake(File("note.m4a"), seconds = 7, levels = List(40) { 50 })

    var starts = 0
        private set
    var cancels = 0
        private set
    val played = mutableListOf<String>()
    var stops = 0
        private set

    override fun start(): Boolean {
        if (!available) return false
        starts += 1
        recording.value = Recording(seconds = 0, level = 0f)
        return true
    }

    override fun finish(): VoiceTake? {
        recording.value = null
        return take
    }

    override fun cancel() {
        cancels += 1
        recording.value = null
    }

    override fun play(id: String, file: File): Boolean {
        played += id
        playing.value = Playing(id = id, fraction = 0f, seconds = 0)
        return true
    }

    override fun stopPlaying() {
        stops += 1
        playing.value = null
    }
}
