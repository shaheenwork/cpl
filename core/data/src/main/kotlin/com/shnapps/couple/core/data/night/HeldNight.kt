package com.shnapps.couple.core.data.night

import com.shnapps.couple.core.engine.Night
import com.shnapps.couple.core.engine.NightRequest
import com.shnapps.couple.core.engine.PermittedPool
import com.shnapps.couple.core.engine.RemixModifier
import com.shnapps.couple.core.engine.Remixer
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.NightChapter
import com.shnapps.couple.core.model.NightSettings
import com.shnapps.couple.core.model.PlannedNight

/**
 * A night held on the phone that plays it (BUILD_PROMPT.md §14.6): what the server released
 * — its chapters and its alternates, as a [PermittedPool] — and what the phone has done with
 * them since. Pure, so every rule here is tested without a backend.
 *
 * The phone only ever rearranges what it was given (DECISIONS.md D-048): "Not this one"
 * swaps a chapter for another released item that keeps every rule of the night, and a limit
 * set during the night takes the items it rules out of the rest of it (D-062).
 */
internal data class HeldNight(
    val experienceId: String,
    val settings: NightSettings,
    val pool: PermittedPool,
    val night: Night,
    /** What the server believes each chapter is, so a swap can be reported when it is played. */
    val serverIds: List<String>,
    val askFirstItems: Set<String>,
    val focusIncluded: Boolean,
    /** Items refused with "Not this one", or no longer permitted: never swapped in again. */
    val avoid: Set<String> = emptySet(),
    /** Chapters the couple's limits now rule out and nothing released can replace. */
    val unplayable: Set<Int> = emptySet(),
    /** A Mystery Night: nothing of its shape is shown (§14.2, D-077). */
    val mystery: Boolean = false,
) {
    private val byId = pool.items.associateBy { it.id }

    fun planned(): PlannedNight = PlannedNight(
        experienceId = experienceId,
        settings = settings,
        chapters = night.chapters.map { NightChapter(it.index, it.kind, byId.getValue(it.itemId)) },
        effectiveIntensity = Intensity.ofLevel(night.effectiveIntensity),
        askFirstItems = askFirstItems,
        focusIncluded = focusIncluded,
        seed = night.request.seed,
        unplayable = unplayable,
        mystery = mystery,
    )

    /**
     * "Make it more…" (§14.6): the same released pool, planned and assembled again the way
     * [modifier] asks. Null when the pool cannot make that night — the phone then asks the
     * server for a fresh one rather than pretending.
     */
    fun remixed(modifier: RemixModifier, remixer: Remixer, seed: Long): HeldNight? {
        val next = remixer.remix(night, modifier, pool, seed) ?: return null
        return copy(night = next, settings = settings.copy(durationMin = next.request.durationMin))
    }

    /** "Not this one" (§3.1) on chapter [index]: another released item, or null if none fits. */
    fun swapped(index: Int, remixer: Remixer): HeldNight? {
        val current = night.chapters.getOrNull(index) ?: return null
        val next = remixer.swap(night, index, pool, avoid + current.itemId) ?: return null
        return copy(night = next, avoid = avoid + current.itemId)
    }

    /**
     * [blocked] is what the couple's limits no longer permit. Every chapter from [fromIndex] on
     * that holds one is swapped for something released that is still allowed; one that cannot
     * be is marked unplayable, so the player passes over it without showing it.
     */
    fun withBlocked(blocked: Set<String>, fromIndex: Int, remixer: Remixer): HeldNight {
        if (blocked.isEmpty()) return this
        val nightIds = night.chapters.mapTo(HashSet()) { it.itemId }
        // The pool must still hold every chapter of the night to swap within it, so only
        // alternates leave it; blocked chapters are avoided instead.
        var held = copy(pool = pool.without(blocked - nightIds), avoid = avoid + blocked)
        for (chapter in night.chapters) {
            if (chapter.index < fromIndex || chapter.itemId !in blocked) continue
            val next = remixer.swap(held.night, chapter.index, held.pool, held.avoid)
            held = if (next != null) {
                held.copy(night = next)
            } else {
                held.copy(unplayable = held.unplayable + chapter.index)
            }
        }
        return held
    }

    /** The item chapter [index] was played as, if the server does not already know it. */
    fun unreportedSwap(index: Int): String? {
        val played = night.chapters.getOrNull(index)?.itemId ?: return null
        return played.takeIf { it != serverIds.getOrNull(index) }
    }

    fun reported(index: Int): HeldNight {
        val played = night.chapters.getOrNull(index)?.itemId ?: return this
        return copy(serverIds = serverIds.mapIndexed { at, id -> if (at == index) played else id })
    }

    /** Items about [themeId] among what this night could still show. */
    fun itemsAbout(themeId: String): Set<String> =
        pool.items.filter { themeId in it.excludedByBoundaries }.mapTo(HashSet()) { it.id }

    companion object {
        fun requestOf(settings: NightSettings, seed: Long): NightRequest = NightRequest(
            mode = settings.mode,
            durationMin = settings.durationMin,
            moods = settings.moods,
            intensity = settings.intensity.level,
            leadership = settings.leadership,
            novelty = settings.novelty,
            seed = seed,
            focus = settings.focus,
        )
    }
}
