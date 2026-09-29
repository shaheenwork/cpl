package com.shnapps.couple.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IntensityTest {
    @Test
    fun `effective intensity takes the lowest ceiling`() {
        assertThat(Intensity.effective(Intensity.EXTREME, Intensity.SPICY, Intensity.HOT))
            .isEqualTo(Intensity.SPICY)
    }

    @Test
    fun `effective intensity never escalates above the request`() {
        assertThat(Intensity.effective(Intensity.MILD, Intensity.EXTREME)).isEqualTo(Intensity.MILD)
    }

    @Test
    fun `bold and wild require both-party consent`() {
        assertThat(Intensity.entries.filter { it.requiresBothPartyConsent })
            .containsExactly(Intensity.HOT, Intensity.EXTREME)
    }
}
