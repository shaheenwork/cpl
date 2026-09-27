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

    /**
     * The app makes no sound of its own (BUILD_PROMPT.md §20, REDESIGN_PROMPT.md §6).
     *
     * There is no "sounds off" setting because there is nothing to turn off: the only audio in the
     * app is a voice note, played because somebody pressed play on it. A phone that chimed at the
     * wrong moment is a worse failure here than in most apps, so this is a rule rather than a
     * habit — anything that can make a noise has to justify itself in this list.
     */
    @Test
    fun `nothing plays a sound except the voice notes someone asked for`() {
        productionFiles()
            .filter { file -> SOUND.containsMatchIn(file.text) }
            .assertTrue(testName = "a sound played outside the voice-note player") { file ->
                SOUND_MAKERS.any { file.unixPath.endsWith(it) }
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

    /**
     * The deck a phone builds for itself (D-100) is bounded the same way the night's pool is: it
     * can only be made by the factory that applies the heat and the refusals, never assembled by a
     * screen from a list of its own choosing.
     */
    @Test
    fun `an instant deck can only be built through its factory`() {
        val deck = Konsist.scopeFromProject().classes().filter { it.name == "InstantDeck" }
        deck.assertTrue(testName = "InstantDeck constructor must be private") { declaration ->
            declaration.primaryConstructor?.hasPrivateModifier == true
        }
        assertTrue(deck.size == 1) { "expected exactly one InstantDeck, found ${deck.size}" }
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

        /** Anything that can make the phone audible on its own. */
        val SOUND = Regex("MediaPlayer|SoundPool|RingtoneManager|ToneGenerator|MediaRecorder")

        /**
         * The only places allowed to: a voice note is recorded because somebody held the button,
         * and played because somebody pressed play (§13).
         */
        val SOUND_MAKERS = listOf(
            "core/ui/src/main/kotlin/com/shnapps/couple/core/ui/voice/VoicePlayer.kt",
            "core/ui/src/main/kotlin/com/shnapps/couple/core/ui/voice/VoiceRecorder.kt",
        )

        /**
         * Everything that may import the content repository. The content layer's own package
         * never needs to; the taxonomy reads its taxonomy; Hilt binds it; the night repository
         * resolves only the ids a server's night released, through PermittedPool.fromServer
         * (DECISIONS.md D-048), and the game repository only a server's game's, through
         * ReleasedGame.fromServer (D-070); the debug inspector lists it on purpose (D-042); the
         * fake stands in for it in tests.
         *
         * The instant repository is the one addition the rebuild makes, and it is a deliberate
         * one (D-100): a phone with no account has no partner whose limits could be leaked, so
         * it may build a deck from the shipped catalogue bounded by its own heat and refusals.
         * The rule below keeps that from widening — a deck can only be made through
         * InstantDeck.of, and narrowing it can only ever remove.
         */
        val CATALOGUE_READERS = listOf(
            "core/data/src/main/kotlin/com/shnapps/couple/core/data/di/DataModule.kt",
            "core/data/src/main/kotlin/com/shnapps/couple/core/data/night/NightRepository.kt",
            "core/data/src/main/kotlin/com/shnapps/couple/core/data/game/GameRepository.kt",
            "core/data/src/main/kotlin/com/shnapps/couple/core/data/taxonomy/TaxonomyRepository.kt",
            "core/data/src/main/kotlin/com/shnapps/couple/core/data/instant/InstantPlayRepository.kt",
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
