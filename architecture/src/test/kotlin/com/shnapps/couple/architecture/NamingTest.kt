package com.shnapps.couple.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

/**
 * :architecture is a pure JVM module, so parents are matched by name rather than by
 * KClass — androidx types are deliberately not on this classpath.
 */
class NamingTest {

    @Test
    fun `view models are named ViewModel`() {
        val viewModels = Konsist.scopeFromProject()
            .classes()
            .filter { klass -> klass.parents().any { it.name == "ViewModel" } }

        if (viewModels.isEmpty()) return // Rule activates from Phase 3, when the first ViewModel lands.

        viewModels.assertTrue(testName = "ViewModel suffix") { it.name.endsWith("ViewModel") }
    }

    @Test
    fun `ui state holders are immutable data classes`() {
        val states = Konsist.scopeFromProject()
            .classes()
            .filter { it.name.endsWith("UiState") }

        if (states.isEmpty()) return // Rule activates from Phase 3.

        states.assertTrue(testName = "UiState is an immutable data class") { klass ->
            klass.hasDataModifier && klass.properties().none { it.isVar }
        }
    }
}
