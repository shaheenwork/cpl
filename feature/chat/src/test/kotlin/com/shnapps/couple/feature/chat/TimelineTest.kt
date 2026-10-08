package com.shnapps.couple.feature.chat

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.model.Message
import com.shnapps.couple.core.model.MessageKind
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The rows a conversation is drawn as.
 *
 * Grouping and day boundaries are the two things most easily got wrong and least visible in a
 * screenshot: a run of five messages that each carry their own timestamp looks like five
 * interruptions, and a day separator computed in UTC lands in the middle of the evening for
 * anybody east of London.
 */
class TimelineTest {

    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")
    private val today: LocalDate = LocalDate.of(2026, 10, 8)

    @Test
    fun `a run from one person carries one timestamp, on its last message`() {
        val entries = Timeline.of(
            messages = listOf(
                said("m1", mine = false, at = at(21, 0)),
                said("m2", mine = false, at = at(21, 1)),
                said("m3", mine = false, at = at(21, 2)),
            ),
            newFrom = null,
            zone = zone,
            today = today,
        )

        val bubbles = entries.filterIsInstance<Entry.Bubble>()
        assertThat(bubbles.map { it.first }).containsExactly(true, false, false).inOrder()
        assertThat(bubbles.map { it.last }).containsExactly(false, false, true).inOrder()
    }

    @Test
    fun `a gap longer than the run window starts a new run`() {
        val entries = Timeline.of(
            messages = listOf(
                said("m1", mine = true, at = at(21, 0)),
                said("m2", mine = true, at = at(21, 6)),
            ),
            newFrom = null,
            zone = zone,
            today = today,
        )

        val bubbles = entries.filterIsInstance<Entry.Bubble>()
        // Six minutes apart: two things said, not one thing said twice.
        assertThat(bubbles.map { it.first }).containsExactly(true, true).inOrder()
        assertThat(bubbles.map { it.last }).containsExactly(true, true).inOrder()
    }

    @Test
    fun `the other person always starts a new run, however close together`() {
        val entries = Timeline.of(
            messages = listOf(
                said("m1", mine = false, at = at(21, 0)),
                said("m2", mine = true, at = at(21, 0)),
            ),
            newFrom = null,
            zone = zone,
            today = today,
        )

        assertThat(entries.filterIsInstance<Entry.Bubble>().map { it.first })
            .containsExactly(true, true).inOrder()
    }

    @Test
    fun `days are separated in this phone's own zone, not in UTC`() {
        // 11pm and 1am local: two days here, one day in UTC, and the evening is what matters.
        val entries = Timeline.of(
            messages = listOf(
                said("m1", mine = false, at = at(23, 0, day = 7)),
                said("m2", mine = false, at = at(1, 0, day = 8)),
            ),
            newFrom = null,
            zone = zone,
            today = today,
        )

        val labels = entries.filterIsInstance<Entry.Day>().map { it.label }
        assertThat(labels).containsExactly("YESTERDAY", "TODAY").inOrder()
    }

    @Test
    fun `older than yesterday is named by its date`() {
        val entries = Timeline.of(
            messages = listOf(said("m1", mine = false, at = at(20, 0, day = 2))),
            newFrom = null,
            zone = zone,
            today = today,
        )

        assertThat(entries.filterIsInstance<Entry.Day>().single().label).isEqualTo("2 OCTOBER")
    }

    @Test
    fun `the unread mark goes above the message it names`() {
        val entries = Timeline.of(
            messages = listOf(
                said("m1", mine = true, at = at(21, 0)),
                said("m2", mine = false, at = at(21, 30)),
            ),
            newFrom = "m2",
            zone = zone,
            today = today,
        )

        val mark = entries.indexOfFirst { it is Entry.New }
        val named = entries.indexOfFirst { it is Entry.Bubble && it.message.id == "m2" }
        assertThat(mark).isEqualTo(named - 1)
    }

    @Test
    fun `nothing said is no rows at all, not an empty day`() {
        assertThat(Timeline.of(emptyList(), newFrom = null, zone = zone, today = today)).isEmpty()
    }

    @Test
    fun `one still on its way belongs to today, because that is where it will land`() {
        val entries = Timeline.of(
            messages = listOf(
                said("m1", mine = true, at = at(20, 0, day = 2)),
                said("m2", mine = true, at = 0L),
            ),
            newFrom = null,
            zone = zone,
            today = today,
        )

        assertThat(entries.filterIsInstance<Entry.Day>().map { it.label })
            .containsExactly("2 OCTOBER", "TODAY").inOrder()
    }

    private fun at(hour: Int, minute: Int, day: Int = 8): Long =
        LocalDateTime.of(2026, 10, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun said(id: String, mine: Boolean, at: Long) = Message(
        id = id,
        mine = mine,
        kind = MessageKind.TEXT,
        text = "something",
        mediaId = null,
        atMillis = at,
    )
}
