package com.shnapps.couple.core.data.content

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.database.content.ContentDeltaEntity
import com.shnapps.couple.core.database.content.ContentPromptEntity
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.PromptKind
import com.shnapps.couple.core.model.StepOptions
import com.shnapps.couple.core.model.StepSeconds
import com.shnapps.couple.core.model.StepText
import org.junit.Test

/**
 * Games and prompts read the way items are (BUILD_PROMPT.md §9.2, §11): a game this version
 * cannot play in full is dropped whole, a prompt without its boundaries is dropped, and a
 * bundle from before games existed still installs.
 */
class GameParsingTest {

    private fun game(steps: String, extra: String = "") =
        """{$extra"id": "TEST_GAME", "title": "Test", "tagline": "A tagline.", "description": "A description.",
           "modes": ["TOGETHER"], "rounds": 3, "turns": "ALTERNATE", "scoring": "POINTS",
           "promptKinds": [], "steps": [$steps]}"""

    private val playable = """
        {"primitive": "CHOICE", "who": "ACTIVE", "private": true, "text": "PROMPT", "options": "PROMPT"},
        {"primitive": "PREDICTION", "who": "OTHER", "private": true, "text": "What did they choose?",
         "options": "PROMPT", "of": [0]},
        {"primitive": "TIMER", "who": "TOGETHER", "seconds": "PROMPT"},
        {"primitive": "VOTE", "who": "TOGETHER", "text": "Who won?", "options": ["ME", "YOU", "TIE"]},
        {"primitive": "REVEAL", "who": "TOGETHER", "of": [0, 1], "outcome": "PREDICTED"}
    """

    private fun prompt(
        id: String = "test_one",
        extra: String = "",
        exclusions: String = """["mood_gentle"]""",
        kind: String = "null",
    ) =
        """{$extra"id": "$id", "version": 1, "game": "TEST_GAME", "kind": $kind, "text": "A prompt to play.",
           "options": ["One", "Two"], "seconds": 60, "category": "communication", "tags": ["game"], "intensity": 2,
           "modes": ["TOGETHER"], "moods": ["romantic"], "requiredMutualPreferences": [],
           "boostedByPreferences": [], "excludedByBoundaries": $exclusions, "noveltyWeight": 0.5,
           "repeatCooldownDays": 30, "status": "published", "locale": "en",
           "createdAt": "2026-09-22T00:00:00.000Z", "updatedAt": "2026-09-22T00:00:00.000Z"}"""

    @Test
    fun `reads a game's steps onto the domain`() {
        val game = ContentBundleParser.parseGames("[${game(playable)}]").single()

        assertThat(game.steps).hasSize(5)
        assertThat(game.steps[0].isPrivate).isTrue()
        assertThat(game.steps[0].text).isEqualTo(StepText.Prompt)
        assertThat(game.steps[1].text).isEqualTo(StepText.Literal("What did they choose?"))
        assertThat(game.steps[1].of).containsExactly(0)
        assertThat(game.steps[2].seconds).isEqualTo(StepSeconds.FromPrompt)
        assertThat(game.steps[3].options).isEqualTo(StepOptions.Fixed(listOf("ME", "YOU", "TIE")))
    }

    @Test
    fun `skips fields from a newer app, but drops a game it cannot play in full`() {
        assertThat(ContentBundleParser.parseGames("[${game(playable, extra = """"hologram": true,""")}]")).hasSize(1)

        val unknownPrimitive = game("""{"primitive": "ROULETTE", "who": "ACTIVE", "text": "PROMPT"}""")
        val unknownAudience = game("""{"primitive": "QUESTION", "who": "EVERYONE", "text": "PROMPT"}""")
        val forward = game("""{"primitive": "REVEAL", "who": "TOGETHER", "of": [1], "outcome": "MATCH"}""")
        val unknownOption =
            game("""{"primitive": "VOTE", "who": "EACH", "text": "PROMPT", "options": ["ME", "THEM"]}""")
        val unknownOutcome = game("""{"primitive": "REVEAL", "who": "TOGETHER", "of": [], "outcome": "COIN_FLIP"}""")
        for (broken in listOf(unknownPrimitive, unknownAudience, forward, unknownOption, unknownOutcome)) {
            assertThat(ContentBundleParser.parseGames("[$broken, ${game(playable)}]")).hasSize(1)
        }
    }

    @Test
    fun `maps a prompt onto the domain`() {
        val prompt = ContentBundleParser.parsePrompt(prompt(kind = "\"DARE\""))!!
        assertThat(prompt.intensity).isEqualTo(Intensity.FLIRTY)
        assertThat(prompt.kind).isEqualTo(PromptKind.DARE)
        assertThat(prompt.options).containsExactly("One", "Two").inOrder()
        assertThat(prompt.seconds).isEqualTo(60)
    }

    @Test
    fun `drops a prompt without its boundaries, or of a kind it does not know`() {
        assertThat(ContentBundleParser.parsePrompt(prompt(exclusions = "[]"))).isNull()
        val unbounded = prompt().replace(""""excludedByBoundaries": ["mood_gentle"],""", "")
        assertThat(ContentBundleParser.parsePrompt(unbounded)).isNull()
        assertThat(ContentBundleParser.parsePrompt(prompt(kind = "\"DOUBLE_DARE\""))).isNull()
    }

    @Test
    fun `a bundle from before games existed installs, with none`() {
        val parsed = ContentBundleParser.parse(ContentFixtures.bundle(1, listOf(ContentFixtures.item("flirt_one"))))
        assertThat(parsed.prompts).isEmpty()
        assertThat(ContentBundleParser.parseGames(parsed.gamesJson)).isEmpty()
    }

    @Test
    fun `a delta disables a prompt, and an item's delta never becomes one`() {
        val rows = listOf(
            ContentPromptEntity("test_one", 1, "TEST_GAME", "published", prompt("test_one")),
            ContentPromptEntity("test_two", 1, "TEST_GAME", "published", prompt("test_two")),
        )
        val deltas = listOf(
            ContentDeltaEntity("test_one", "disabled", 1, null, 10L),
            ContentDeltaEntity("flirt_added", "published", 1, ContentFixtures.item("flirt_added"), 10L),
            ContentDeltaEntity("test_added", "published", 1, prompt("test_added"), 10L),
        )
        assertThat(applyPromptDeltas(rows, deltas).map { it.id }).containsExactly("test_two", "test_added")
        assertThat(applyDeltas(emptyList(), deltas).map { it.id }).containsExactly("flirt_added")
    }
}
