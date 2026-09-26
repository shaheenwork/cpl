package com.shnapps.couple.core.engine.game

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.model.ContentStatus
import com.shnapps.couple.core.model.Game
import com.shnapps.couple.core.model.GamePrimitive
import com.shnapps.couple.core.model.GamePrompt
import com.shnapps.couple.core.model.GameStep
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.Mode
import com.shnapps.couple.core.model.PromptKind
import com.shnapps.couple.core.model.Scoring
import com.shnapps.couple.core.model.StepAudience
import com.shnapps.couple.core.model.StepText
import com.shnapps.couple.core.model.TurnOrder
import org.junit.Test

/**
 * Starting a game on a phone with no account (REDESIGN_PROMPT.md §4.1, D-100).
 *
 * The same reasoning as `InstantDeck`, and the same thing worth proving: what bounds a local game
 * is the heat on this phone and the refusals made on it, and neither can be exceeded. Everything
 * else here — the climb, the kinds, the alternates — is about the game being playable at all.
 */
class LocalGameTest {

    @Test
    fun `nothing above the ceiling this phone set`() {
        val released = ReleasedGame.forHeat(simple, prompts, ceiling = 2)

        val intensities = released.rounds.flatMap { it.prompts }.map { it.intensity.level }
        assertThat(intensities).isNotEmpty()
        assertThat(intensities.max()).isAtMost(2)
        assertThat(released.alternates.map { it.intensity.level }.maxOrNull() ?: 1).isAtMost(2)
    }

    @Test
    fun `a refused kind of thing is not in the game either`() {
        val released = ReleasedGame.forHeat(simple, prompts, ceiling = 5, refusedThemes = setOf("power_yielding"))

        val themes = (released.rounds.flatMap { it.prompts } + released.alternates)
            .flatMap { it.excludedByBoundaries }
        assertThat(themes).doesNotContain("power_yielding")
    }

    @Test
    fun `prompts of another game never appear`() {
        val released = ReleasedGame.forHeat(simple, prompts + strangers, ceiling = 5)

        val games = (released.rounds.flatMap { it.prompts } + released.alternates).map { it.game }
        assertThat(games.toSet()).containsExactly(simple.id)
    }

    @Test
    fun `a round is never played twice`() {
        val released = ReleasedGame.forHeat(simple, prompts, ceiling = 5)

        val ids = released.rounds.flatMap { it.prompts }.map { it.id }
        assertThat(ids).containsNoDuplicates()
        assertThat(released.alternates.map { it.id }).containsNoneIn(ids)
    }

    @Test
    fun `the evening climbs towards the ceiling instead of starting at it`() {
        val released = ReleasedGame.forHeat(simple, prompts, ceiling = 5)
        val levels = released.rounds.mapNotNull { round -> round.prompts.maxOfOrNull { it.intensity.level } }

        assertThat(levels.first()).isLessThan(levels.last())
        assertThat(levels.last()).isAtMost(5)
    }

    @Test
    fun `the same game deals the same way twice, and differently with another seed`() {
        val once = ReleasedGame.forHeat(simple, prompts, ceiling = 5, seed = 7)
        val again = ReleasedGame.forHeat(simple, prompts, ceiling = 5, seed = 7)
        val other = ReleasedGame.forHeat(simple, prompts, ceiling = 5, seed = 99)

        fun ids(game: ReleasedGame) = game.rounds.flatMap { it.prompts }.map { it.id }
        assertThat(ids(again)).isEqualTo(ids(once))
        assertThat(ids(other)).isNotEqualTo(ids(once))
    }

    @Test
    fun `a game of truths and dares gets one of each, every round`() {
        val released = ReleasedGame.forHeat(kinded, kindedPrompts, ceiling = 5)

        val playable = released.rounds.filter { it.playable }
        assertThat(playable).isNotEmpty()
        playable.forEach { round ->
            assertThat(round.prompts.map { it.kind }).containsExactly(PromptKind.TRUTH, PromptKind.DARE).inOrder()
        }
    }

    @Test
    fun `a round nothing can fill is left unplayable rather than filled with anything`() {
        // Two prompts, six rounds: the rest cannot be dealt, and the player passes over them.
        val released = ReleasedGame.forHeat(simple, prompts.take(2), ceiling = 5)

        assertThat(released.rounds.count { it.playable }).isEqualTo(2)
        assertThat(released.rounds.count { !it.playable }).isEqualTo(simple.rounds - 2)
    }

    @Test
    fun `a game the phone has nothing for is empty rather than wrong`() {
        val released = ReleasedGame.forHeat(simple, emptyList(), ceiling = 5)

        assertThat(released.rounds.none { it.playable }).isTrue()
        assertThat(released.alternates).isEmpty()
    }

    private companion object {
        fun game(id: String, kinds: List<PromptKind>) = Game(
            id = id,
            title = id,
            tagline = "",
            description = "",
            modes = setOf(Mode.TOGETHER),
            rounds = 6,
            turns = TurnOrder.ALTERNATE,
            scoring = Scoring.NONE,
            promptKinds = kinds,
            steps = listOf(
                GameStep(
                    primitive = GamePrimitive.CHALLENGE,
                    who = StepAudience.ACTIVE,
                    isPrivate = false,
                    text = StepText.Prompt,
                    options = null,
                    answer = null,
                    of = emptyList(),
                    outcome = null,
                    seconds = null,
                    kindFrom = null,
                ),
            ),
        )

        fun prompt(
            id: String,
            game: String,
            level: Int,
            kind: PromptKind? = null,
            themes: Set<String> = setOf("mood_gentle"),
        ) = GamePrompt(
            id = id,
            version = 1,
            game = game,
            kind = kind,
            text = "The prompt $id.",
            options = emptyList(),
            seconds = null,
            category = "communication",
            tags = setOf("game"),
            intensity = Intensity.ofLevel(level),
            modes = setOf(Mode.TOGETHER),
            moods = emptySet(),
            requiredMutualPreferences = emptySet(),
            boostedByPreferences = emptySet(),
            excludedByBoundaries = themes,
            status = ContentStatus.PUBLISHED,
        )

        val simple = game("LOCAL_SIMPLE", emptyList())
        val kinded = game("LOCAL_KINDED", listOf(PromptKind.TRUTH, PromptKind.DARE))

        /** Four at every level, one of them a kind of thing that can be refused. */
        val prompts = (1..5).flatMap { level ->
            listOf(
                prompt("simple_${level}_a", simple.id, level),
                prompt("simple_${level}_b", simple.id, level),
                prompt("simple_${level}_c", simple.id, level),
                prompt("simple_${level}_d", simple.id, level, themes = setOf("power_yielding")),
            )
        }

        val kindedPrompts = (1..5).flatMap { level ->
            listOf(
                prompt("kinded_t_${level}_a", kinded.id, level, PromptKind.TRUTH),
                prompt("kinded_t_${level}_b", kinded.id, level, PromptKind.TRUTH),
                prompt("kinded_d_${level}_a", kinded.id, level, PromptKind.DARE),
                prompt("kinded_d_${level}_b", kinded.id, level, PromptKind.DARE),
            )
        }

        val strangers = listOf(prompt("other_1", "SOME_OTHER_GAME", 1))
    }
}
