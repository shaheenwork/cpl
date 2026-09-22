package com.shnapps.couple.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

/**
 * The client side of the boundary chokepoint (BUILD_PROMPT.md §3.2, §5.5, §21.1).
 *
 * The server decides what a couple may see; the device only ever assembles or remixes within
 * the pool the server released for a night. These rules keep it that way: no screen or
 * feature can reach the full catalogue, and the engine's pool can only be built from a
 * server's night.
 */
class ContentChokepointTest {

    @Test
    fun `only the content layer and the debug inspector can see the whole catalogue`() {
        productionFiles()
            .filter { file -> file.imports.any { it.name == CONTENT_REPOSITORY } }
            .assertTrue(testName = "catalogue reachable outside the content layer") { file ->
                CATALOGUE_READERS.any { file.unixPath.endsWith(it) }
            }
    }

    @Test
    fun `the engine assembles only with a pool built from the server's night`() {
        val pool = Konsist.scopeFromProject().classes().filter { it.name == "PermittedPool" }
        pool.assertTrue(testName = "PermittedPool constructor must be internal") { declaration ->
            declaration.primaryConstructor?.hasInternalModifier == true
        }
        assertTrue(pool.size == 1) { "expected exactly one PermittedPool, found ${pool.size}" }
    }

    @Test
    fun `the engine's planner and assembler are not reachable from app code`() {
        Konsist.scopeFromProject()
            .objects()
            .filter { it.name == "Assembler" || it.name == "Planner" }
            .assertTrue(testName = "engine internals must stay internal") { it.hasInternalModifier }
    }

    private companion object {
        const val CONTENT_REPOSITORY = "com.shnapps.couple.core.data.content.ContentRepository"

        /**
         * Everything that may import the content repository. The content layer's own package
         * never needs to; the taxonomy reads its taxonomy; Hilt binds it; the night repository
         * resolves only the ids a server's night released, through PermittedPool.fromServer
         * (DECISIONS.md D-048); the debug inspector lists it on purpose (D-042); the fake stands
         * in for it in tests.
         */
        val CATALOGUE_READERS = listOf(
            "core/data/src/main/kotlin/com/shnapps/couple/core/data/di/DataModule.kt",
            "core/data/src/main/kotlin/com/shnapps/couple/core/data/night/NightRepository.kt",
            "core/data/src/main/kotlin/com/shnapps/couple/core/data/taxonomy/TaxonomyRepository.kt",
            "app/src/debug/kotlin/com/shnapps/couple/debug/ContentInspector.kt",
            "core/testing/src/main/kotlin/com/shnapps/couple/core/testing/FakeContentRepository.kt",
        )

        val KoFileDeclaration.unixPath: String get() = path.replace('\\', '/')

        val TEST_SOURCE_SETS = listOf("/src/test/", "/src/androidTest/", "/src/testDebug/")

        fun productionFiles(): List<KoFileDeclaration> = Konsist.scopeFromProject().files
            .filterNot { file -> TEST_SOURCE_SETS.any { it in file.unixPath } }

        fun assertTrue(condition: Boolean, message: () -> String) {
            if (!condition) throw AssertionError(message())
        }
    }
}
