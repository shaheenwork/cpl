package com.shnapps.couple.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.Test

/**
 * Enforces the module graph from BUILD_PROMPT.md §4.2.
 *
 * Konsist scans the whole repository, so these rules hold no matter which module a file
 * lives in.
 *
 * Containment rules apply to **production** sources only. Tests must be able to import
 * the thing they are testing — the emulator smoke test in :app has to touch Firebase
 * directly in order to prove the app really reaches the emulator.
 */
class ModuleGraphTest {

    @Test
    fun `feature modules do not depend on other feature modules`() {
        productionFiles()
            .filter { "/feature/" in it.unixPath }
            .assertFalse(testName = "feature to feature dependency") { file ->
                val ownFeature = FEATURE_PATH.find(file.unixPath)?.groupValues?.get(1)
                file.imports.any { import ->
                    val imported = FEATURE_PACKAGE.find(import.name)?.groupValues?.get(1)
                    imported != null && imported != ownFeature
                }
            }
    }

    @Test
    fun `only core firebase imports firebase`() {
        productionFiles()
            .filterNot { "/core/firebase/" in it.unixPath }
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
                "Firebase" in function.text || "Firestore" in function.text
            }
    }

    @Test
    fun `analytics is logged only through the typed facade`() {
        productionFiles()
            .filterNot { "/core/analytics/" in it.unixPath || "/core/firebase/" in it.unixPath }
            .assertFalse(testName = "direct FirebaseAnalytics usage") { file ->
                file.imports.any { "FirebaseAnalytics" in it.name }
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

        /**
         * Konsist reports native paths, so on Windows these are backslash-separated.
         * Every path predicate here goes through this, otherwise the rules silently
         * match nothing and pass for the wrong reason.
         */
        val KoFileDeclaration.unixPath: String get() = path.replace('\\', '/')

        fun productionFiles(): List<KoFileDeclaration> = scope().files
            .filterNot { "/src/test/" in it.unixPath || "/src/androidTest/" in it.unixPath }

        fun scope(): KoScope = Konsist.scopeFromProject()
    }
}
