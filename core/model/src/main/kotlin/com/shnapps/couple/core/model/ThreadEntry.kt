package com.shnapps.couple.core.model

/**
 * Being apart, as one thread (REDESIGN_PROMPT.md §5.4).
 *
 * The app already had all of this — voice notes, photos, scheduled surprises, "Open when…", the
 * countdowns — and showed it as four screens and a settings page. What two people apart actually
 * want is one list: what they left you, what you sent, what is still sealed.
 *
 * This is the whole of that: a pure function over what the repositories already give, so what the
 * thread hides is testable without a device. Two things it must never do:
 *
 * - **Say what is inside something undelivered.** A surprise before its moment is "something is
 *   coming" and nothing else — not its title, not its kind (D-085).
 * - **Pretend a burned thing is still there.** When it is gone it says gone, for both of them.
 */
sealed interface ThreadEntry {
    /** When it happened, or when it is due: the thread is ordered by this, newest first. */
    val atMillis: Long

    /** True when this phone's user is the one who sent or left it. */
    val mine: Boolean

    val id: String

    /** A voice note or a photo. */
    data class Media(
        override val id: String,
        override val atMillis: Long,
        override val mine: Boolean,
        val kind: MediaKind,
        val note: String?,
        val burnAfterView: Boolean,
        val opened: Boolean,
    ) : ThreadEntry {
        /** Sent to them, never opened: the only thing the thread ever nudges about. */
        val waiting: Boolean get() = !mine && !opened
    }

    /**
     * Something left for later.
     *
     * [title] is null until it may be read — which is the rule, not a courtesy: before delivery the
     * payload is its author's alone, and the thread is not a way around that.
     */
    data class Left(
        override val id: String,
        override val atMillis: Long,
        override val mine: Boolean,
        val title: String?,
        val state: SurpriseState,
        val forMoment: String?,
    ) : ThreadEntry {
        val sealed: Boolean get() = title == null
        val readyForMe: Boolean get() = !mine && state == SurpriseState.DELIVERED
    }

    /** Something they are both counting down to. Nothing private in it, by design. */
    data class Counting(
        override val id: String,
        override val atMillis: Long,
        val title: String,
    ) : ThreadEntry {
        override val mine: Boolean get() = false
    }
}

/**
 * The thread as it stands, newest first.
 *
 * A surprise still scheduled is ordered by the moment it is due, because that is the thing about
 * it that matters; everything else is ordered by when it happened.
 */
fun threadOf(
    media: List<MediaItem>,
    surprises: List<Surprise>,
    countdowns: List<Countdown>,
    nowMillis: Long,
): List<ThreadEntry> {
    val fromMedia = media.mapNotNull { item ->
        val at = item.sentAtMillis ?: return@mapNotNull null
        ThreadEntry.Media(
            id = item.id,
            atMillis = at,
            mine = item.mine,
            kind = item.kind,
            note = item.note,
            burnAfterView = item.burnAfterView,
            opened = item.viewedAtMillis != null,
        )
    }

    val fromSurprises = surprises.map { surprise ->
        // Its own author always knows what they left. Everyone else learns the title only once the
        // server has delivered it, and never before (D-085).
        val readable = surprise.mine || surprise.state != SurpriseState.SCHEDULED
        ThreadEntry.Left(
            id = surprise.id,
            atMillis = surprise.scheduledForMillis ?: nowMillis,
            mine = surprise.mine,
            title = surprise.title.takeIf { readable },
            state = surprise.state,
            forMoment = surprise.trigger?.label,
        )
    }

    val fromCountdowns = countdowns.map { ThreadEntry.Counting(it.id, it.targetAtMillis, it.title) }

    return (fromMedia + fromSurprises + fromCountdowns).sortedByDescending { it.atMillis }
}
