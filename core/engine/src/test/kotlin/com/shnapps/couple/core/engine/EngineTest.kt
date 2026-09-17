package com.shnapps.couple.core.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Proves the pure-JVM test path works before the real engine lands in Phase 9. */
class EngineTest {
    @Test
    fun `engine version is set`() {
        assertThat(Engine.VERSION).isAtLeast(1)
    }
}
