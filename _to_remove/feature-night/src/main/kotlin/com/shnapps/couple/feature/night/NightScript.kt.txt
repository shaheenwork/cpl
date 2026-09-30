package com.shnapps.couple.feature.night

import com.shnapps.couple.core.model.ChapterKind
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.Leadership
import com.shnapps.couple.core.model.PlannedNight

/** Who takes the lead in a chapter (§14.5 "Who leads"). "You" is whoever holds this phone. */
enum class Lead { YOU, PARTNER }

/**
 * The pure rules of a night on screen: who leads each chapter, and before which chapters the
 * host checks in. Deterministic from the night's seed, so a restarted player says the same.
 */
internal object NightScript {

    /** Me · You · Switch · Random, chapter by chapter. Random is fixed by the night's seed. */
    fun leadFor(leadership: Leadership, index: Int, seed: Long): Lead = when (leadership) {
        Leadership.ME -> Lead.YOU
        Leadership.YOU -> Lead.PARTNER
        Leadership.SWITCH -> if (index % 2 == 0) Lead.YOU else Lead.PARTNER
        Leadership.RANDOM -> if ((mix(seed, index) and 1L) == 0L) Lead.YOU else Lead.PARTNER
    }

    /**
     * Whether the host checks in before chapter [index] (§3.1, §3.2). Always before an item the
     * host asked to be asked about first — and, so that a check-in never tells anyone watching
     * the screen that a limit exists (DECISIONS.md D-060), also before the step up into BOLD
     * or beyond, and before roughly one in four other chapters, chosen by the seed.
     */
    fun checkInDue(night: PlannedNight, index: Int): Boolean {
        val chapter = night.chapters.getOrNull(index) ?: return false
        if (chapter.item.id in night.askFirstItems) return true
        if (index == 0) return false
        val previous = night.chapters[index - 1].intensity
        val stepsUp = chapter.intensity.requiresBothPartyConsent && !previous.requiresBothPartyConsent
        return stepsUp || mix(night.seed, index + DECOY_SALT) % DECOY_ODDS == 0L
    }

    /** A small, well-spread hash of (seed, index): SplitMix64's finaliser. */
    @Suppress("MagicNumber")
    private fun mix(seed: Long, index: Int): Long {
        var z = seed + index * -0x61c8864680b583ebL
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return (z xor (z ushr 31)) ushr 1
    }

    private const val DECOY_ODDS = 4L
    private const val DECOY_SALT = 1_000
}

/** How a chapter kind reads on screen (§10.4). */
internal val ChapterKind.label: String
    get() = when (this) {
        ChapterKind.WARM_UP -> "Warm up"
        ChapterKind.CURIOSITY -> "Curiosity"
        ChapterKind.TEASE -> "Tease"
        ChapterKind.CHALLENGE -> "Challenge"
        ChapterKind.CONTROL -> "Control"
        ChapterKind.CONFESSION -> "Confession"
        ChapterKind.ROLEPLAY -> "Roleplay"
        ChapterKind.WILDCARD -> "Wildcard"
        ChapterKind.YOUR_CHOICE -> "Your choice"
        ChapterKind.FINALE -> "Finale"
    }

/** The level's name as the ladder says it (Appendix A). */
internal val Intensity.label: String get() = name

internal fun Lead.line(): String = when (this) {
    Lead.YOU -> "You lead this one."
    Lead.PARTNER -> "Your partner leads this one."
}
