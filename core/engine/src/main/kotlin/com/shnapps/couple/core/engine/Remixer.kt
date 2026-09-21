package com.shnapps.couple.core.engine

import com.shnapps.couple.core.model.ChapterKind
import com.shnapps.couple.core.model.Mood
import com.shnapps.couple.core.model.Novelty

/** The "MAKE IT…" choices after a night is built (BUILD_PROMPT.md §14.6). */
enum class RemixModifier {
    MORE_INTENSE,
    MORE_MYSTERIOUS,
    MORE_TEASING,
    MORE_UNPREDICTABLE,
    MORE_ROMANTIC,
    MORE_ROLEPLAY,
    MORE_CONVERSATIONAL,
    SHORTER,
    SLOWER,
    MORE_ADVENTUROUS,
}

/** A night on the device: what was asked for, and its chapters. */
data class Night(
    val request: NightRequest,
    val chapters: List<Chapter>,
    val effectiveIntensity: Int,
)

/**
 * Local remixes (BUILD_PROMPT.md §10.1, §14.6): "Make it more…" and swapping one chapter —
 * instantly, offline, and only ever with the [PermittedPool] the server released, so a remix
 * can never reach content the couple's boundaries removed (§3.2). The ceiling stays the
 * night's: "more intense" climbs sooner, it never goes higher.
 *
 * Every result passes [Validator]. When the pool cannot make what was asked, the answer is
 * null, and the app asks the server for a fresh night instead.
 */
class Remixer(private val context: ClientContext = ClientContext()) {

    fun remix(night: Night, modifier: RemixModifier, pool: PermittedPool, seed: Long): Night? {
        val request = modified(night.request, modifier) ?: return null
        val startOffset = when (modifier) {
            RemixModifier.MORE_INTENSE -> INTENSE_START_OFFSET
            RemixModifier.SLOWER -> SLOWER_START_OFFSET
            else -> Planner.DEFAULT_START_OFFSET
        }
        val before = night.chapters.map { it.itemId }
        for (attempt in 0 until MAX_ATTEMPTS) {
            val chapters = attempt(request, pool, Rng.attemptSeed(seed, attempt), startOffset)
            if (chapters != null && chapters.map { it.itemId } != before) {
                return Night(request.copy(seed = seed), chapters, pool.effectiveIntensity)
            }
        }
        return null
    }

    /** One plan and assembly over the pool, or null if it does not make a valid night. */
    private fun attempt(request: NightRequest, pool: PermittedPool, seed: Long, startOffset: Int): List<Chapter>? {
        val effective = pool.effectiveIntensity
        val planned = Planner.plan(
            durationMin = request.durationMin,
            moods = request.moods,
            leadership = request.leadership,
            effective = effective,
            available = pool.items.flatMapTo(HashSet()) { it.chapterKinds },
            rng = Rng(seed),
            startOffset = startOffset,
        )
        val plan = (planned as? Planner.Result.Planned)?.plan ?: return null
        val scores = pool.items.associate { it.id to PublicScore.score(it, request, effective, context) }
        val input = Assembler.Input(
            pool.items, scores, plan, request.durationMin, effective, context.weights.repetitionPenalty,
        )
        val chapters = Assembler.assemble(input) ?: return null
        val byId = pool.items.associateBy { it.id }
        val valid = Validator.validate(chapters, request.durationMin, effective, request.mode, byId).isEmpty()
        return chapters.takeIf { valid }
    }

    /**
     * "Not this one" (BUILD_PROMPT.md §3.1): the best item from the pool for chapter [index],
     * never one of [avoid], keeping every rule of the night. A chapter's level can stay or drop,
     * never rise: the replacement answers to the chapter it replaces. The finale is the exception
     * and may reach the night's ceiling, as a fresh night's finale would.
     */
    fun swap(night: Night, index: Int, pool: PermittedPool, avoid: Set<String> = emptySet()): Night? {
        val byId = pool.items.associateBy { it.id }
        if (night.chapters.any { it.itemId !in byId }) return null
        val effective = pool.effectiveIntensity
        val scores = pool.items.associate { it.id to PublicScore.score(it, night.request, effective, context) }
        val plan = NightPlan(
            kinds = night.chapters.map { it.kind },
            targets = night.chapters.map { chapter ->
                if (chapter.kind == ChapterKind.FINALE) effective else chapter.intensity
            },
        )
        val input = Assembler.Input(
            pool.items, scores, plan, night.request.durationMin, effective, context.weights.repetitionPenalty,
        )
        val current = night.chapters.map { byId.getValue(it.itemId) }
        val used = night.chapters.mapTo(HashSet()) { it.itemId }
        val replacement = Assembler.bestReplacement(input, current, index, used + avoid) ?: return null

        val chapters = night.chapters.map { chapter ->
            if (chapter.index == index) {
                chapter.copy(itemId = replacement.id, intensity = replacement.intensity.level)
            } else {
                chapter
            }
        }
        val violations = Validator.validate(chapters, night.request.durationMin, effective, night.request.mode, byId)
        return if (violations.isEmpty()) night.copy(chapters = chapters) else null
    }

    private fun modified(request: NightRequest, modifier: RemixModifier): NightRequest? = when (modifier) {
        RemixModifier.MORE_INTENSE -> request.copy(moods = request.moods + Mood.BOLD)
        RemixModifier.MORE_MYSTERIOUS -> request.copy(moods = request.moods + Mood.MYSTERIOUS)
        RemixModifier.MORE_TEASING -> request.copy(moods = request.moods + Mood.NAUGHTY + Mood.MYSTERIOUS)
        RemixModifier.MORE_UNPREDICTABLE ->
            request.copy(moods = request.moods + Mood.UNPREDICTABLE, novelty = Novelty.SURPRISE_ME)
        RemixModifier.MORE_ROMANTIC -> request.copy(moods = request.moods + Mood.ROMANTIC)
        RemixModifier.MORE_ROLEPLAY -> request.copy(moods = request.moods + Mood.ROLEPLAY)
        RemixModifier.MORE_CONVERSATIONAL -> request.copy(moods = request.moods + Mood.TALK)
        RemixModifier.MORE_ADVENTUROUS ->
            request.copy(moods = request.moods + Mood.EXPERIMENTAL, novelty = Novelty.SURPRISE_ME)
        RemixModifier.SLOWER -> request
        RemixModifier.SHORTER ->
            STANDARD_DURATIONS.lastOrNull { it < request.durationMin }?.let { request.copy(durationMin = it) }
    }

    private companion object {
        const val MAX_ATTEMPTS = 5

        /** "More intense" starts one level below the ceiling; "slower", three. */
        const val INTENSE_START_OFFSET = 1
        const val SLOWER_START_OFFSET = 3

        /** The Build Our Night lengths (BUILD_PROMPT.md §14.5). "Shorter" steps down one. */
        val STANDARD_DURATIONS = listOf(15, 30, 45, 60, 90)
    }
}
