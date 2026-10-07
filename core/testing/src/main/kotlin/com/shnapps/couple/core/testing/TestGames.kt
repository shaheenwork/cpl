package com.shnapps.couple.core.testing

import com.shnapps.couple.core.model.ContentStatus
import com.shnapps.couple.core.model.Game
import com.shnapps.couple.core.model.GameDie
import com.shnapps.couple.core.model.GamePrimitive
import com.shnapps.couple.core.model.GamePrompt
import com.shnapps.couple.core.model.GameStep
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.Mode
import com.shnapps.couple.core.model.PromptKind
import com.shnapps.couple.core.model.RevealOutcome
import com.shnapps.couple.core.model.Scoring
import com.shnapps.couple.core.model.StepAudience
import com.shnapps.couple.core.model.StepOptions
import com.shnapps.couple.core.model.StepText
import com.shnapps.couple.core.model.TurnOrder

/**
 * Three small games for tests, shaped like the shipped ones: a shared choice with a matched
 * reveal, truth or dare with kinds, and a roll of three dice. The real definitions are read from
 * the bundle by :core:data's file test; these keep screen and view-model tests independent of
 * content.
 */
object TestGames {
    const val CHOICES = "TEST_CHOICES"
    const val TRUTH_OR_DARE = "TEST_TRUTH_OR_DARE"
    const val DICE = "TEST_DICE"

    val choices = Game(
        id = CHOICES,
        title = "Would you rather",
        tagline = "Two options. Choose in secret.",
        description = "Each of you chooses in private, then you see whether you matched.",
        modes = setOf(Mode.TOGETHER),
        rounds = 3,
        turns = TurnOrder.SHARED,
        scoring = Scoring.MATCHES,
        promptKinds = emptyList(),
        steps = listOf(
            step(
                GamePrimitive.CHOICE,
                StepAudience.EACH,
                isPrivate = true,
                text = StepText.Prompt,
                options = StepOptions.FromPrompt,
            ),
            step(GamePrimitive.REVEAL, StepAudience.TOGETHER, of = listOf(0), outcome = RevealOutcome.MATCH),
        ),
    )

    val truthOrDare = Game(
        id = TRUTH_OR_DARE,
        title = "Truth or dare",
        tagline = "The classic, at your level.",
        description = "Take turns: tell the truth, or take the dare.",
        modes = setOf(Mode.TOGETHER),
        rounds = 3,
        turns = TurnOrder.ALTERNATE,
        scoring = Scoring.NONE,
        promptKinds = listOf(PromptKind.TRUTH, PromptKind.DARE),
        steps = listOf(
            step(
                GamePrimitive.CHOICE,
                StepAudience.ACTIVE,
                text = StepText.Literal("Truth or dare?"),
                options = StepOptions.Fixed(listOf("TRUTH", "DARE")),
            ),
            // No reaction step: the shipped game has none either. "How was that?" after every
            // round was an extra tap for nothing, and the fixtures mirror the games.
            step(GamePrimitive.CHALLENGE, StepAudience.ACTIVE, text = StepText.Prompt, kindFrom = 0),
        ),
    )

    /** Three dice at once: fixed faces, the prompt's own, and a clock (D-130). */
    val dice = Game(
        id = DICE,
        title = "Heat dice",
        tagline = "Three dice. Do what they say.",
        description = "One says what, one says where, one says how long.",
        modes = setOf(Mode.TOGETHER),
        rounds = 3,
        turns = TurnOrder.ALTERNATE,
        scoring = Scoring.NONE,
        promptKinds = emptyList(),
        steps = listOf(
            step(
                GamePrimitive.DICE,
                StepAudience.ACTIVE,
                text = StepText.Prompt,
                dice = listOf(
                    GameDie("What", StepOptions.Fixed(listOf("Kiss", "Tease", "Whisper it", "Their choice"))),
                    GameDie("Where", StepOptions.FromPrompt),
                    GameDie(
                        label = "How long",
                        faces = StepOptions.Fixed(listOf("30 seconds", "1 minute", "Until they say stop")),
                        seconds = listOf(30, 60, 0),
                    ),
                ),
            ),
        ),
    )

    val games = listOf(choices, truthOrDare, dice)

    val choicePrompts = (1..5).map { n ->
        prompt("choices_$n", CHOICES, "Would you rather, number $n?", options = listOf("Stay in $n", "Go out $n"))
    }

    val truths = (1..5).map { n ->
        prompt("tod_t$n", TRUTH_OR_DARE, "A truth to tell, number $n.", kind = PromptKind.TRUTH)
    }
    val dares = (1..5).map { n ->
        prompt("tod_d$n", TRUTH_OR_DARE, "A dare to take, number $n.", kind = PromptKind.DARE)
    }

    val dicePrompts = (1..5).map { n ->
        prompt(
            id = "dice_$n",
            game = DICE,
            text = "The rule for round $n: do it slowly.",
            options = listOf("Their hands $n", "Their neck $n", "Wherever you like $n"),
        )
    }

    val prompts = choicePrompts + truths + dares + dicePrompts

    fun prompt(
        id: String,
        game: String,
        text: String,
        kind: PromptKind? = null,
        options: List<String> = emptyList(),
        intensity: Intensity = Intensity.MILD,
        themes: Set<String> = setOf("mood_gentle"),
    ) = GamePrompt(
        id = id,
        version = 1,
        game = game,
        kind = kind,
        text = text,
        options = options,
        seconds = null,
        category = "communication",
        tags = setOf("game"),
        intensity = intensity,
        modes = setOf(Mode.TOGETHER),
        moods = emptySet(),
        requiredMutualPreferences = emptySet(),
        boostedByPreferences = emptySet(),
        excludedByBoundaries = themes,
        status = ContentStatus.PUBLISHED,
    )

    @Suppress("LongParameterList") // A step, with only what the test sets named.
    private fun step(
        primitive: GamePrimitive,
        who: StepAudience,
        isPrivate: Boolean = false,
        text: StepText? = null,
        options: StepOptions? = null,
        of: List<Int> = emptyList(),
        outcome: RevealOutcome? = null,
        kindFrom: Int? = null,
        dice: List<GameDie>? = null,
    ) = GameStep(
        primitive = primitive,
        who = who,
        isPrivate = isPrivate,
        text = text,
        options = options,
        answer = null,
        of = of,
        outcome = outcome,
        seconds = null,
        kindFrom = kindFrom,
        dice = dice,
    )
}
