package com.shnapps.couple.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IntensityTest {
    @Test
    fun `effective intensity takes the lowest ceiling`() {
        assertThat(Intensity.effective(Intensity.WILD, Intensity.NAUGHTY, Intensity.BOLD))
            .isEqualTo(Intensity.NAUGHTY)
    }

    @Test
    fun `effective intensity never escalates above the request`() {
        assertThat(Intensity.effective(Intensity.SOFT, Intensity.WILD)).isEqualTo(Intensity.SOFT)
    }

    @Test
    fun `bold and wild require both-party consent`() {
        assertThat(Intensity.entries.filter { it.requiresBothPartyConsent })
            .containsExactly(Intensity.BOLD, Intensity.WILD)
    }
}
