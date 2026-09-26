package com.shnapps.couple.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Being apart, as one thread (REDESIGN_PROMPT.md §5.4).
 *
 * The thread is a view over things that already had their own rules, so the tests that matter are
 * the ones about what it must not show: the inside of something undelivered, and anything that
 * makes a burned thing look like it is still there.
 */
class ThreadEntryTest {

    @Test
    fun `a surprise still scheduled says nothing about itself`() {
        val thread = threadOf(
            media = emptyList(),
            surprises = listOf(surprise("s1", mine = false, state = SurpriseState.SCHEDULED, title = "Read this at 9")),
            countdowns = emptyList(),
            nowMillis = NOW,
        )

        val left = thread.single() as ThreadEntry.Left
        assertThat(left.sealed).isTrue()
        assertThat(left.title).isNull()
    }

    @Test
    fun `the person who left it always knows what it is`() {
        val thread = threadOf(
            media = emptyList(),
            surprises = listOf(surprise("s1", mine = true, state = SurpriseState.SCHEDULED, title = "Read this at 9")),
            countdowns = emptyList(),
            nowMillis = NOW,
        )

        assertThat((thread.single() as ThreadEntry.Left).title).isEqualTo("Read this at 9")
    }

    @Test
    fun `once it is delivered, it can be read`() {
        val thread = threadOf(
            media = emptyList(),
            surprises = listOf(surprise("s1", mine = false, state = SurpriseState.DELIVERED, title = "For you")),
            countdowns = emptyList(),
            nowMillis = NOW,
        )

        val left = thread.single() as ThreadEntry.Left
        assertThat(left.title).isEqualTo("For you")
        assertThat(left.readyForMe).isTrue()
    }

    @Test
    fun `a voice note they have not opened is the one thing that waits`() {
        val thread = threadOf(
            media = listOf(
                media("m1", mine = false, viewedAt = null),
                media("m2", mine = false, viewedAt = NOW),
                media("m3", mine = true, viewedAt = null),
            ),
            surprises = emptyList(),
            countdowns = emptyList(),
            nowMillis = NOW,
        )

        val waiting = thread.filterIsInstance<ThreadEntry.Media>().filter { it.waiting }.map { it.id }
        assertThat(waiting).containsExactly("m1")
    }

    @Test
    fun `media that never left the phone is not in the thread`() {
        // Reserved but never confirmed: it exists as a record and has no place in a conversation.
        val thread = threadOf(
            media = listOf(media("pending", mine = true, viewedAt = null, sentAt = null)),
            surprises = emptyList(),
            countdowns = emptyList(),
            nowMillis = NOW,
        )

        assertThat(thread).isEmpty()
    }

    @Test
    fun `newest first, and a scheduled surprise sits at the moment it is due`() {
        val thread = threadOf(
            media = listOf(media("old", mine = true, viewedAt = null, sentAt = NOW - HOUR)),
            surprises = listOf(
                surprise("soon", mine = true, state = SurpriseState.SCHEDULED, title = "t", dueAt = NOW + HOUR),
            ),
            countdowns = listOf(Countdown("c1", "Our anniversary", NOW + DAY)),
            nowMillis = NOW,
        )

        assertThat(thread.map { it.id }).containsExactly("c1", "soon", "old").inOrder()
    }

    @Test
    fun `an open-when says which moment it is kept for, without saying what it is`() {
        val thread = threadOf(
            media = emptyList(),
            surprises = listOf(
                surprise(
                    "w1",
                    mine = false,
                    state = SurpriseState.SCHEDULED,
                    title = "Something",
                    trigger = OpenWhenTrigger.CANT_SLEEP,
                ),
            ),
            countdowns = emptyList(),
            nowMillis = NOW,
        )

        val left = thread.single() as ThreadEntry.Left
        assertThat(left.forMoment).isEqualTo("You can't sleep")
        assertThat(left.title).isNull()
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val HOUR = 3_600_000L
        const val DAY = 86_400_000L

        fun media(
            id: String,
            mine: Boolean,
            viewedAt: Long?,
            sentAt: Long? = NOW,
            burn: Boolean = true,
        ) = MediaItem(
            id = id,
            kind = MediaKind.VOICE,
            mine = mine,
            note = null,
            burnAfterView = burn,
            sentAtMillis = sentAt,
            viewedAtMillis = viewedAt,
            expiresAtMillis = null,
        )

        fun surprise(
            id: String,
            mine: Boolean,
            state: SurpriseState,
            title: String,
            dueAt: Long? = NOW,
            trigger: OpenWhenTrigger? = null,
        ) = Surprise(
            id = id,
            type = if (trigger == null) SurpriseType.SCHEDULED else SurpriseType.OPEN_WHEN,
            title = title,
            mine = mine,
            state = state,
            scheduledForMillis = dueAt,
            trigger = trigger,
            hasMedia = false,
        )
    }
}
