package com.shnapps.couple.core.ui.voice

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.model.MediaItem
import org.junit.Test

/**
 * The waveform that travels with a voice note.
 *
 * Always the same number of bars, whatever the note's length, because the other phone draws it
 * from this list and neither of them decodes the audio to check (D-083 leaves the bytes
 * unreadable without a round trip; the shape has to arrive some other way).
 */
class VoiceLevelsTest {

    @Test
    fun `however many samples, always the drawn number of bars`() {
        listOf(1, 5, 40, 41, 1_200).forEach { count ->
            val levels = VoiceLevels.of(List(count) { 10_000 })

            assertThat(levels).hasSize(MediaItem.WAVEFORM_BARS)
        }
    }

    @Test
    fun `no samples is no waveform, rather than a flat one pretending to be a note`() {
        assertThat(VoiceLevels.of(emptyList())).isEmpty()
    }

    @Test
    fun `the loudest moment reaches the top, so a quiet note is still legible`() {
        val levels = VoiceLevels.of(List(40) { index -> index * 100 })

        assertThat(levels.max()).isEqualTo(VoiceLevels.MAX)
        assertThat(levels.min()).isAtLeast(0)
    }

    @Test
    fun `a bucket takes its loudest sample, not its average`() {
        // Forty bars over eighty samples: two per bucket, one of them silent. A mean would halve
        // every bar; a voice is mostly silence between syllables and averaging flattens it away.
        val samples = List(80) { index -> if (index % 2 == 0) 20_000 else 0 }

        val levels = VoiceLevels.of(samples)

        assertThat(levels.all { it == VoiceLevels.MAX }).isTrue()
    }

    @Test
    fun `fewer samples than bars spreads across them rather than leaving gaps`() {
        val levels = VoiceLevels.of(List(5) { 20_000 })

        assertThat(levels).hasSize(MediaItem.WAVEFORM_BARS)
        assertThat(levels.none { it == 0 }).isTrue()
    }

    @Test
    fun `silence is silence, and does not divide by its own zero`() {
        val levels = VoiceLevels.of(List(20) { 0 })

        assertThat(levels).hasSize(MediaItem.WAVEFORM_BARS)
        assertThat(levels.all { it == 0 }).isTrue()
    }

    @Test
    fun `the meter is rooted, so a voice at a normal level actually moves it`() {
        val quarter = VoiceLevels.meter(VoiceRecorder.MAX_AMPLITUDE / 4)

        // A linear meter would read 0.25 and barely budge; this reads about half.
        assertThat(quarter).isGreaterThan(0.45f)
        assertThat(quarter).isLessThan(0.55f)
        assertThat(VoiceLevels.meter(0)).isEqualTo(0f)
        assertThat(VoiceLevels.meter(Int.MAX_VALUE)).isEqualTo(1f)
    }
}
