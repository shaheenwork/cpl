package com.shnapps.couple.core.data.entitlement

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.common.FakeClock
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The free period is one value in Firebase, and the console takes it as text (D-151).
 *
 * So what is worth holding is every way somebody might type it, the two edges of the day it names,
 * and — the one that matters most — that a mistyped value cannot be the reason everybody's levels
 * switch off.
 */
class LaunchOfferTest {

    private val london = ZoneId.of("Europe/London")
    private val tokyo = ZoneId.of("Asia/Tokyo")

    private fun millis(local: String, zone: ZoneId): Long =
        LocalDateTime.parse(local).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `a date is the whole of that day, on the phone's own clock`() {
        val until = FreeUntil.parse("2026-11-06", london)!!

        // The last millisecond of the sixth, in London — not midnight, not UTC.
        assertThat(until).isEqualTo(millis("2026-11-06T23:59:59.999", london))
        // The same date in Tokyo ends nine hours earlier in absolute time.
        assertThat(FreeUntil.parse("2026-11-06", tokyo)!!).isLessThan(until)
    }

    @Test
    fun `a moment is exactly that moment, wherever the phone is`() {
        val expected = LocalDateTime.parse("2026-11-06T18:00:00").toInstant(ZoneOffset.UTC).toEpochMilli()

        assertThat(FreeUntil.parse("2026-11-06T18:00:00Z", tokyo)).isEqualTo(expected)
        assertThat(FreeUntil.parse("2026-11-06T19:00:00+01:00", london)).isEqualTo(expected)
    }

    @Test
    fun `a moment with no zone is on the phone's own clock`() {
        assertThat(FreeUntil.parse("2026-11-06T18:00:00", london))
            .isEqualTo(millis("2026-11-06T18:00:00", london))
    }

    @Test
    fun `a number is a moment, in milliseconds or in seconds`() {
        assertThat(FreeUntil.parse("1794009599999", london)).isEqualTo(1_794_009_599_999L)
        assertThat(FreeUntil.parse("1794009599", london)).isEqualTo(1_794_009_599_000L)
    }

    @Test
    fun `blank means there is no free period, which is how the offer is ended`() {
        assertThat(FreeUntil.parse("", london)).isNull()
        assertThat(FreeUntil.parse("   ", london)).isNull()
    }

    @Test
    fun `a value nobody can read is ignored, and the date the app ships with applies`() {
        val shipped = FreeUntil.parse(FreeUntil.DEFAULT, london)

        // A typo in a console must not be able to switch everybody's levels off.
        assertThat(FreeUntil.parse("6th November", london)).isEqualTo(shipped)
        assertThat(FreeUntil.parse("2026-13-45", london)).isEqualTo(shipped)
        assertThat(FreeUntil.parse("soon", london)).isEqualTo(shipped)
        assertThat(shipped).isNotNull()
    }

    @Test
    fun `it is free up to the last millisecond, and not after`() {
        val until = 1_794_009_599_999L
        val clock = FakeClock(until)
        val offer = RemoteLaunchOffer(config("1794009599999"), clock)

        assertThat(offer.isFree).isTrue()
        clock.now = until + 1
        assertThat(offer.isFree).isFalse()
        assertThat(offer.freeUntilMillis).isEqualTo(until)
        // Over, so nothing left to count.
        assertThat(offer.daysLeft).isNull()
    }

    @Test
    fun `moving the date in Firebase moves the end, with no release`() {
        val clock = FakeClock(1_793_000_000_000L)
        var raw = "1794009599999"
        val offer = RemoteLaunchOffer(object : LaunchConfig { override fun freeUntil() = raw }, clock)
        assertThat(offer.isFree).isTrue()

        raw = ""

        assertThat(offer.isFree).isFalse()
    }

    @Test
    fun `it says something at fourteen days and at three, and not otherwise`() {
        val until = 1_794_009_599_999L
        val day = 24L * 60 * 60 * 1000
        val offer = { remaining: Long -> RemoteLaunchOffer(config("$until"), FakeClock(until - remaining)) }

        assertThat(offer(14 * day + 1).shouldRemind).isTrue()
        assertThat(offer(3 * day + 1).shouldRemind).isTrue()
        assertThat(offer(10 * day).shouldRemind).isFalse()
        assertThat(offer(30 * day).shouldRemind).isFalse()
        assertThat(offer(14 * day + 1).daysLeft).isEqualTo(14)
    }

    private fun config(raw: String) = object : LaunchConfig {
        override fun freeUntil(): String = raw
    }
}
