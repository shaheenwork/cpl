package com.shnapps.couple.core.data.content

import com.shnapps.couple.core.model.AnswerKind
import com.shnapps.couple.core.model.ContentStatus
import com.shnapps.couple.core.model.Game
import com.shnapps.couple.core.model.GamePrimitive
import com.shnapps.couple.core.model.GamePrompt
import com.shnapps.couple.core.model.GameStep
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.MediaKind
import com.shnapps.couple.core.model.Mode
import com.shnapps.couple.core.model.Mood
import com.shnapps.couple.core.model.PromptKind
import com.shnapps.couple.core.model.RevealOutcome
import com.shnapps.couple.core.model.Scoring
import com.shnapps.couple.core.model.StepAudience
import com.shnapps.couple.core.model.StepOptions
import com.shnapps.couple.core.model.StepSeconds
import com.shnapps.couple.core.model.StepText
import com.shnapps.couple.core.model.TurnOrder
import com.shnapps.couple.core.model.VoteOption
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

// Games and their prompts as the bundle carries them (BUILD_PROMPT.md §11), read by the rules
// in ContentBundleParser: a game this version cannot play in full is dropped whole, never
// played in part, and a prompt that arrives without its boundaries is dropped (fail closed).

private const val FROM_PROMPT = "PROMPT"
private const val FROM_STATEMENTS = "STATEMENTS"

/** Option ids a fixed list may hold: who a vote names, or a prompt kind. */
private val FIXED_OPTIONS: Set<String> = VoteOption.entries.map { it.name }.toSet() + PromptKind.entries.map { it.name }

@Serializable
internal data class GameDto(
    val id: String,
    val title: String,
    val tagline: String = "",
    val description: String = "",
    val modes: List<String>,
    val rounds: Int,
    val turns: String,
    val scoring: String,
    val promptKinds: List<String> = emptyList(),
    val steps: List<GameStepDto>,
)

@Serializable
internal data class GameStepDto(
    val primitive: String,
    val who: String,
    @SerialName("private") val isPrivate: Boolean = false,
    val text: String? = null,
    val options: JsonElement? = null,
    val answer: String? = null,
    val of: List<Int> = emptyList(),
    val outcome: String? = null,
    val seconds: JsonElement? = null,
    val kindFrom: Int? = null,
    val media: String? = null,
)

/**
 * The exclusion and requirement lists have no defaults, as for items: a prompt without them
 * fails to decode and is dropped (§3.2).
 */
@Serializable
internal data class GamePromptDto(
    val id: String,
    val version: Int,
    val game: String,
    val kind: String? = null,
    val text: String,
    val options: List<String> = emptyList(),
    val seconds: Int? = null,
    val category: String,
    val tags: List<String> = emptyList(),
    val intensity: Int,
    val modes: List<String>,
    val moods: List<String> = emptyList(),
    val requiredMutualPreferences: List<String>,
    val boostedByPreferences: List<String> = emptyList(),
    val excludedByBoundaries: List<String>,
    val status: String,
)

private inline fun <reified T : Enum<T>> known(name: String?): T? =
    name?.let { value -> enumValues<T>().firstOrNull { it.name == value } }

@Suppress("ReturnCount") // One early return per field a game cannot be played without: fail closed on each.
internal fun GameDto.toGame(): Game? {
    val knownModes = modes.mapNotNull { known<Mode>(it) }.toSet()
    val kinds = promptKinds.map { known<PromptKind>(it) ?: return null }
    val parsedSteps = steps.mapIndexed { index, step -> step.toStep(index) ?: return null }
    val order = known<TurnOrder>(turns) ?: return null
    val score = known<Scoring>(scoring) ?: return null
    if (knownModes.isEmpty() || parsedSteps.isEmpty() || rounds < 1) return null
    return Game(
        id = id,
        title = title,
        tagline = tagline,
        description = description,
        modes = knownModes,
        rounds = rounds,
        turns = order,
        scoring = score,
        promptKinds = kinds,
        steps = parsedSteps,
    )
}

// One early return per field that can make a step unplayable: the game fails closed on each.
@Suppress("ReturnCount", "CyclomaticComplexMethod")
private fun GameStepDto.toStep(index: Int): GameStep? {
    val primitive = known<GamePrimitive>(primitive) ?: return null
    val audience = known<StepAudience>(who) ?: return null
    val answerKind = if (answer == null) null else known<AnswerKind>(answer) ?: return null
    val revealOutcome = if (outcome == null) null else known<RevealOutcome>(outcome) ?: return null
    // A reveal must say what it compares; one that does not cannot be scored.
    if (primitive == GamePrimitive.REVEAL && revealOutcome == null) return null
    // A step may only refer back: a forward reference cannot be played in order.
    if (of.any { it !in 0 until index } || (kindFrom != null && kindFrom !in 0 until index)) return null
    val mediaKind = if (media == null) null else known<MediaKind>(media) ?: return null
    // A step that records must say what it records; one that does not cannot be rendered.
    if (primitive == GamePrimitive.MEDIA && mediaKind == null) return null
    val stepOptions = options?.let { parseOptions(it, primitive) ?: return null }
    val stepSeconds = seconds?.let { parseSeconds(it) ?: return null }
    return GameStep(
        primitive = primitive,
        who = audience,
        isPrivate = isPrivate,
        text = text?.let { if (it == FROM_PROMPT) StepText.Prompt else StepText.Literal(it) },
        options = stepOptions,
        answer = answerKind,
        of = of,
        outcome = revealOutcome,
        seconds = stepSeconds,
        kindFrom = kindFrom,
        media = mediaKind,
    )
}

/**
 * Where a step's options come from.
 *
 * A fixed list is normally a closed vocabulary — who a vote names, which kind a choice picks —
 * because the interpreter reads meaning from those ids: `RoundReading.named` turns ME and YOU into
 * seats, and a vote given arbitrary options could not be resolved at all.
 *
 * A [GamePrimitive.DRAW] is the exception, and the reason is what a die is. Its faces are the
 * game's own and the same on every roll, so they are words rather than ids, and nothing reads
 * meaning from them — chance lands on one and the screen shows it. The validator allows free faces
 * on a draw and nowhere else; this is the same rule on the reading side.
 */
private fun parseOptions(element: JsonElement, primitive: GamePrimitive): StepOptions? = when {
    element is JsonPrimitive && element.isString && element.content == FROM_PROMPT -> StepOptions.FromPrompt
    element is JsonPrimitive && element.isString && element.content == FROM_STATEMENTS -> StepOptions.FromStatements
    element is JsonArray -> {
        val ids = element.map { (it as? JsonPrimitive)?.takeIf { value -> value.isString }?.content }
        val faces = ids.filterNotNull().filter { it.isNotBlank() }
        val allowed = if (primitive == GamePrimitive.DRAW) faces else faces.filter { it in FIXED_OPTIONS }
        if (ids.size >= 2 && allowed.size == ids.size) StepOptions.Fixed(allowed) else null
    }
    else -> null
}

private fun parseSeconds(element: JsonElement): StepSeconds? {
    val value = element as? JsonPrimitive ?: return null
    if (value.isString) return if (value.content == FROM_PROMPT) StepSeconds.FromPrompt else null
    return value.intOrNull?.takeIf { it > 0 }?.let { StepSeconds.Fixed(it) }
}

@Suppress("ReturnCount") // One early return per field a prompt cannot be played without: fail closed on each.
internal fun GamePromptDto.toGamePrompt(): GamePrompt? {
    val level = Intensity.entries.firstOrNull { it.level == intensity } ?: return null
    val lifecycle = ContentStatus.fromId(status) ?: return null
    val promptKind = if (kind == null) null else known<PromptKind>(kind) ?: return null
    val knownModes = modes.mapNotNull { known<Mode>(it) }.toSet()
    if (knownModes.isEmpty() || excludedByBoundaries.isEmpty()) return null
    return GamePrompt(
        id = id,
        version = version,
        game = game,
        kind = promptKind,
        text = text,
        options = options,
        seconds = seconds,
        category = category,
        tags = tags.toSet(),
        intensity = level,
        modes = knownModes,
        moods = moods.mapNotNull(Mood::fromId).toSet(),
        requiredMutualPreferences = requiredMutualPreferences.toSet(),
        boostedByPreferences = boostedByPreferences.toSet(),
        excludedByBoundaries = excludedByBoundaries.toSet(),
        status = lifecycle,
    )
}
