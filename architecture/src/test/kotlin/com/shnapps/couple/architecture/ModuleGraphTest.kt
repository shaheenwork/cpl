package com.shnapps.couple.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.Test

/**
 * Enforces the module graph from BUILD_PROMPT.md §4.2.
 *
 * Konsist scans the whole repository, so these rules hold no matter which module a file
 * lives in.
 */
class ModuleGraphTest {

    @Test
    fun `feature modules do not depend on other feature modules`() {
        Konsist.scopeFromProject()
            .files
            .filter { it.path.contains("/feature/") }
            .assertFalse(testName = "feature to feature dependency") { file ->
                val ownFeature = FEATURE_PATH.find(file.path)?.groupValues?.get(1)
                file.imports.any { import ->
                    val imported = FEATURE_PACKAGE.find(import.name)?.groupValues?.get(1)
                    imported != null && imported != ownFeature
                }
            }
    }

    @Test
    fun `only core firebase imports firebase`() {
        Konsist.scopeFromProject()
            .files
            .filterNot { it.path.contains("/core/firebase/") }
            .assertFalse(testName = "firebase import outside :core:firebase") { file ->
                file.imports.any { it.name.startsWith("com.google.firebase") }
            }
    }

    @Test
    fun `composables never touch firebase`() {
        Konsist.scopeFromProject()
            .functions()
            .filter { it.hasAnnotationWithName("Composable", "androidx.compose.runtime.Composable") }
            .assertFalse(testName = "firebase call inside a composable") { function ->
                function.text.contains("Firebase") || function.text.contains("Firestore")
            }
    }

    @Test
    fun `analytics is logged only through the typed facade`() {
        Konsist.scopeFromProject()
            .files
            .filterNot { it.path.contains("/core/analytics/") || it.path.contains("/core/firebase/") }
            .assertFalse(testName = "direct FirebaseAnalytics usage") { file ->
                file.imports.any { it.name.contains("FirebaseAnalytics") }
            }
    }

    @Test
    fun `no LiveData anywhere - UI state is exposed as StateFlow`() {
        Konsist.scopeFromProject()
            .files
            .assertFalse(testName = "LiveData usage") { file ->
                file.imports.any { it.name.startsWith("androidx.lifecycle.LiveData") } ||
                    file.imports.any { it.name.startsWith("androidx.lifecycle.MutableLiveData") }
            }
    }

    @Test
    fun `no GlobalScope`() {
        Konsist.scopeFromProject()
            .files
            .assertFalse(testName = "GlobalScope usage") { file ->
                file.imports.any { it.name == "kotlinx.coroutines.GlobalScope" }
            }
    }

    private companion object {
        val FEATURE_PATH = Regex("/feature/([^/]+)/")
        val FEATURE_PACKAGE = Regex("""com\.shnapps\.couple\.feature\.([^.]+)""")
    }
}
