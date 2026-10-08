package com.shnapps.couple.core.ui.voice

import com.shnapps.couple.core.model.MediaItem

/**
 * A recording's loudness, turned into something that can be drawn.
 *
 * A waveform has to look the same on both phones, and neither of them should have to decode an
 * audio file to find out what it looks like. So it is measured once, while the note is being
 * recorded, and travels with the record (D-083 says nothing about the bytes being drawable, and
 * they are not — there is no URL and no decoder here).
 *
 * The samples arrive at whatever rate the poll ran at: a two-second note gives twenty, a
 * two-minute one gives twelve hundred. Either way the answer is exactly
 * [MediaItem.WAVEFORM_BARS] bars, each 0..[MAX], because that is what the other phone will draw.
 */
object VoiceLevels {

    /** The tallest a bar can be. A percentage, so it survives any width it is drawn at. */
    const val MAX = 100

    /**
     * Reduces [samples] — raw `getMaxAmplitude` readings — to the bars a bubble draws.
     *
     * Each bucket takes the loudest sample in it, not the mean: a voice is mostly silence between
     * syllables, and averaging turns speech into a flat line. The whole thing is then scaled so
     * the loudest moment reaches the top, which is what makes a quiet note still legible.
     *
     * Fewer samples than bars is not an error — a note of half a second is a real note. Its
     * samples are spread across the bars rather than crammed into the first few.
     */
    fun of(samples: List<Int>): List<Int> {
        if (samples.isEmpty()) return emptyList()
        val bars = MediaItem.WAVEFORM_BARS
        val peaks = IntArray(bars)
        samples.forEachIndexed { index, sample ->
            val bucket = index * bars / samples.size
            val level = sample.coerceIn(0, VoiceRecorder.MAX_AMPLITUDE)
            if (level > peaks[bucket]) peaks[bucket] = level
        }
        // A bucket no sample landed in — more bars than samples — takes the one before it, so a
        // short note draws as a short wave rather than a wave with gaps punched through it.
        for (index in 1 until bars) {
            if (peaks[index] == 0) peaks[index] = peaks[index - 1]
        }
        val loudest = peaks.max()
        if (loudest == 0) return List(bars) { 0 }
        return peaks.map { peak -> peak * MAX / loudest }
    }

    /**
     * How loud it is right now, 0f..1f, for the meter in the composer.
     *
     * Rooted rather than linear: amplitude is a pressure reading and a voice at a conversational
     * level sits near the bottom of it, so a linear meter barely moves while somebody talks.
     */
    fun meter(amplitude: Int): Float {
        val clamped = amplitude.coerceIn(0, VoiceRecorder.MAX_AMPLITUDE)
        val share = clamped.toFloat() / VoiceRecorder.MAX_AMPLITUDE
        return kotlin.math.sqrt(share)
    }
}
