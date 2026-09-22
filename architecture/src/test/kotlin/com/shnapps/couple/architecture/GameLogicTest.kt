package com.shnapps.couple.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test
import java.io.File

/**
 * No duplicated game logic (BUILD_PROMPT.md §11, Phase 11 exit): games are declarative
 * compositions of primitives, played by one interpreter. These rules keep it that way —
 * a new game is new content, never a new screen or a new branch somewhere.
 *
 * - Nothing in the app names a game: no code path can treat one game differently.
 * - Only the interpreter (:core:engine) and the parser that reads games (:core:data) look at a
 *   game's steps; screens only ever see the primitives the interpreter hands them.
 * - Each primitive has exactly one renderer, and nothing else in the games feature renders.
 * - Reveals are judged and scores kept in the interpreter alone.
 * - Every primitive is declared by the interpreter, next to the contract it answers to.
 */
class GameLogicTest {

    @Test
    fun `no code names a game - a game is content, not code`() {
        val ids = gameIds()
        assertTrue(ids.size >= MIN_GAMES) { "expected the shipped games in $GAMES_FILE, found $ids" }
        productionFiles().assertFalse(testName = "a game named in code") { file ->
            ids.any { id -> "\"$id\"" in file.text }
        }
    }

    @Test
    fun `only the interpreter and the game parser read a game's steps`() {
        productionFiles()
            .filterNot { file -> STEP_READERS.any { file.unixPath.contains(it) } }
            .assertFalse(testName = "a game's steps read outside the interpreter") { file ->
                file.imports.any { it.name in STEP_TYPES }
            }
    }

    @Test
    fun `every primitive has exactly one renderer, and nothing else renders`() {
        val primitives = Konsist.scopeFromProject()
            .interfaces()
            .filter { it.name == "RoundPrimitive" }
            .flatMap { it.classes() }
            .map { it.name }
            .toSet()
        assertTrue(primitives.size >= MIN_PRIMITIVES) { "expected RoundPrimitive's kinds, found $primitives" }

        val renderers = Konsist.scopeFromProject()
            .functions()
            .filter { "/feature/games/src/main/" in unix(it.path) && it.name.endsWith(RENDERER) }
            .map { it.name }
        val expected = primitives.map { it + RENDERER } + DISPATCH
        assertTrue(renderers.sorted() == expected.sorted()) {
            "renderers ${renderers.sorted()} are not one per primitive ${expected.sorted()}"
        }
    }

    @Test
    fun `reveals are judged and scores kept only by the interpreter`() {
        productionFiles()
            .filterNot { "/core/engine/" in it.unixPath }
            .assertFalse(testName = "a verdict or a score made outside the interpreter") { file ->
                VERDICTS.containsMatchIn(file.text)
            }
    }

    @Test
    fun `every primitive is declared by the interpreter`() {
        Konsist.scopeFromProject()
            .classes()
            .filter { klass -> klass.parents().any { it.name == "RoundPrimitive" } }
            .assertTrue(testName = "RoundPrimitive outside :core:engine") { "/core/engine/src/main/" in unix(it.path) }
    }

    @Test
    fun `the interpreter only starts from what the server released`() {
        val released = Konsist.scopeFromProject().classes().filter { it.name == "ReleasedGame" }
        released.assertTrue(testName = "ReleasedGame constructor must be internal") { declaration ->
            declaration.primaryConstructor?.hasInternalModifier == true
        }
        val run = Konsist.scopeFromProject().classes().filter { it.name == "GameRun" }
        run.assertTrue(testName = "GameRun constructor must be private") { declaration ->
            declaration.primaryConstructor?.hasPrivateModifier == true
        }
        assertTrue(released.size == 1 && run.size == 1) { "expected one ReleasedGame and one GameRun" }
    }

    private companion object {
        // Test tasks run with the module directory (architecture/) as the working directory.
        const val GAMES_FILE = "../content/games.json"
        const val MIN_GAMES = 8
        const val MIN_PRIMITIVES = 8
        const val RENDERER = "Renderer"

        /** The one function that matches a primitive to its renderer. */
        const val DISPATCH = "PrimitiveRenderer"

        /** A game's definition: what only the interpreter and the parser may read. */
        val STEP_TYPES = listOf(
            "GameStep",
            "StepAudience",
            "StepOptions",
            "StepText",
            "StepSeconds",
            "GamePrimitive",
            "TurnOrder",
        )
            .map { "com.shnapps.couple.core.model.$it" }
            .toSet()

        val STEP_READERS = listOf(
            "/core/engine/src/main/",
            "/core/model/src/main/",
            "/core/data/src/main/kotlin/com/shnapps/couple/core/data/content/GameParsing.kt",
            // Fixtures describe small games for tests, as content does for the app.
            "/core/testing/src/main/",
        )

        /** A verdict or a score being made, rather than read. */
        val VERDICTS = Regex("""Verdict\.(Matched|Point)\(|\bScore\(|\bRoundResult\(""")

        val TEST_SOURCE_SETS = listOf("/src/test/", "/src/androidTest/", "/src/testDebug/")

        val KoFileDeclaration.unixPath: String get() = unix(path)

        /** Konsist reports native paths: on Windows, backslash-separated. */
        fun unix(path: String): String = path.replace('\\', '/')

        fun productionFiles(): List<KoFileDeclaration> = Konsist.scopeFromProject().files
            .filterNot { file -> TEST_SOURCE_SETS.any { it in file.unixPath } }

        /** The shipped games' ids, read the plain way: the file is simple and fixed in shape. */
        fun gameIds(): Set<String> = Regex(""""id"\s*:\s*"([A-Z][A-Z_]+)"""")
            .findAll(File(GAMES_FILE).readText())
            .map { it.groupValues[1] }
            .toSet()

        fun assertTrue(condition: Boolean, message: () -> String) {
            if (!condition) throw AssertionError(message())
        }
    }
}
