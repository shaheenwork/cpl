package com.shnapps.couple.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class PreferenceAnswerTest {

    @Test
    fun `secretly curious is a secret curiosity`() {
        assertThat(PreferenceAnswer.SECRETLY_CURIOUS.value).isEqualTo(PreferenceValue.CURIOUS)
        assertThat(PreferenceAnswer.SECRETLY_CURIOUS.secret).isTrue()
    }

    @Test
    fun `nothing but curious can be secret`() {
        PreferenceValue.entries.filter { it != PreferenceValue.CURIOUS }.forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { PreferenceAnswer(value, secret = true) }
        }
    }

    @Test
    fun `offers every value once, plus secretly curious`() {
        val options = PreferenceAnswer.OPTIONS

        assertThat(options).containsNoDuplicates()
        assertThat(options.map { it.value }.toSet()).containsExactlyElementsIn(PreferenceValue.entries)
        assertThat(options.filter { it.secret }).containsExactly(PreferenceAnswer.SECRETLY_CURIOUS)
    }
}
