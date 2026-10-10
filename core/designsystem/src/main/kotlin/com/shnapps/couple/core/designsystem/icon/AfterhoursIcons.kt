package com.shnapps.couple.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The icon set (DESIGN_PROMPT.md §4.9).
 *
 * The app used emoji — 🔥 🎲 🌙 💌 👀 📖 ⏱ — as its icons. They render differently on every
 * platform and every OS version, cannot be coloured, cannot be sized to a grid, cannot be
 * animated, and are the single clearest signal that a product has no designer.
 *
 * These are drawn to one specification and no other: **24 dp box, 1.5 dp stroke, round
 * caps, round joins, no fills.** A set that is uniformly plain beats a set that is
 * individually pretty, because the eye reads the inconsistency long before it reads the
 * draughtsmanship.
 *
 * Every icon takes its colour from the caller, so a heat change re-tints the whole app
 * (§4.4). None of them is ever the only label for anything — `contentDescription` still
 * carries the meaning (§20).
 */
object AfterhoursIcons {

    // --- navigation ----------------------------------------------------------
    val Back = icon("Back", "M15 18l-6-6 6-6")
    val Forward = icon("Forward", "M9 6l6 6-6 6")
    val Close = icon("Close", "M6 6l12 12", "M18 6L6 18")
    val Check = icon("Check", "M4 12l5 5L20 6")
    val Plus = icon("Plus", "M12 5v14", "M5 12h14")

    // --- an evening ----------------------------------------------------------
    val Flame = icon("Flame", "M12 3c3 3.5 5 6 5 9a5 5 0 01-10 0c0-1.6.7-3 1.8-4.3.4 1 1 1.7 1.7 2C10.6 7.4 11 5 12 3z")
    val Dice = icon(
        "Dice",
        "M4 4h16v16H4z",
        "M9 9h0.01", "M15 9h0.01", "M9 15h0.01", "M15 15h0.01",
    )
    val Spark = icon(
        "Spark",
        "M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9z",
        "M18 17l.8 2.2L21 20l-2.2.8L18 23l-.8-2.2L15 20l2.2-.8z",
    )
    val Moon = icon("Moon", "M20 14.6A8.1 8.1 0 0 1 9.4 4A8.6 8.6 0 1 0 20 14.6Z")
    val Clock = icon("Clock", "M3.5 12a8.5 8.5 0 0 1 17.0 0a8.5 8.5 0 0 1 -17.0 0", "M12 7.5V12l3 1.8")
    val Skip = icon("Skip", "M5 5l8 7-8 7z", "M18 5v14")

    /** Starting a voice note. Filled, unlike the rest of the set: at 20 dp an outline closes up. */
    val Play = icon("Play", "M7 4.5l12 7.5-12 7.5z")
    val Pause = icon("Pause", "M9.5 5v14", "M14.5 5v14")
    val Stop = icon("Stop", "M6 6h12v12H6z")

    // --- the two of you ------------------------------------------------------
    val People = icon(
        "People",
        "M5.8 8a3.2 3.2 0 0 1 6.4 0a3.2 3.2 0 0 1 -6.4 0",
        "M3.5 19.5a5.5 5.5 0 0111 0",
        "M16 5.6a3.2 3.2 0 010 4.8",
        "M17.5 14.6a5.5 5.5 0 013 4.9",
    )
    val Send = icon("Send", "M20 4L3.5 10.8l6.8 2.2 2.2 6.8z", "M20 4l-9.7 9.7")

    /** What the two of them say to each other: one bubble, with the tail of a second. */
    val Message = icon("Message", "M4 5h16v10H9l-4 4V5z")
    val Mic = icon(
        "Mic",
        "M12 3a2.5 2.5 0 0 1 2.5 2.5v5a2.5 2.5 0 0 1 -5 0v-5A2.5 2.5 0 0 1 12 3Z",
        "M6 11a6 6 0 0 0 12 0",
        "M12 17v4",
    )
    val Camera = icon("Camera", "M4 8h3l1.5-2h7L17 8h3v11H4z", "M9 13a3 3 0 0 1 6 0a3 3 0 0 1 -6 0")
    val Gift = icon(
        "Gift",
        "M4 9.5h16V20H4z",
        "M4 13h16",
        "M12 9.5V20",
        "M12 9.5S10.5 4 8 4a2.2 2.2 0 0 0 0 5.5Z",
        "M12 9.5S13.5 4 16 4a2.2 2.2 0 0 1 0 5.5Z",
    )
    val Calendar = icon("Calendar", "M4 5.5h16v15H4z", "M4 10h16", "M9 3.5v4", "M15 3.5v4")

    // --- what is yours -------------------------------------------------------
    val Heart = icon(
        "Heart",
        "M12 20.5S4.5 15.6 4.5 10.6A4.1 4.1 0 0 1 12 8.4A4.1 4.1 0 0 1 19.5 10.6C19.5 15.6 12 20.5 12 20.5Z",
    )
    val Bookmark = icon("Bookmark", "M7 4h10v16l-5-3.4L7 20z")
    val Star = icon("Star", "M12 3.5l2.6 5.6 6 .8-4.4 4.2 1.1 6-5.3-2.9-5.3 2.9 1.1-6L3.4 9.9l6-.8z")
    val Eye = icon(
        "Eye",
        "M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z",
        "M9.3 12a2.7 2.7 0 0 1 5.4 0a2.7 2.7 0 0 1 -5.4 0",
    )
    val Lock = icon("Lock", "M4.5 10.5h15V20h-15z", "M8 10.5V8a4 4 0 118 0v2.5")
    val Shield = icon("Shield", "M12 3l7 2.8v5.6c0 4.3-2.9 7.6-7 9.6-4.1-2-7-5.3-7-9.6V5.8z")

    // Sliders rather than a cog with rays: the ray version reads as a brightness control,
    // which is what the first draft of this set drew.
    val Settings = icon(
        "Settings",
        "M4 7h9", "M17 7h3",
        "M4 12h3", "M11 12h9",
        "M4 17h9", "M17 17h3",
        "M15 7a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
        "M7 12a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
        "M15 17a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
    )

    // --- after dark (design/redesign) ------------------------------------------
    /** The safe word: the shield with a stop bar through it, so Stop reads as Stop to anyone. */
    val SafeWord = icon(
        "SafeWord",
        "M12 3l7 2.8v5.6c0 4.3-2.9 7.6-7 9.6-4.1-2-7-5.3-7-9.6V5.8z",
        "M9 12h6",
    )

    /** Limits: a shield that has been answered. */
    val Limits = icon(
        "Limits",
        "M12 3l7 2.8v5.6c0 4.3-2.9 7.6-7 9.6-4.1-2-7-5.3-7-9.6V5.8z",
        "M9 12l2 2 4-4",
    )

    /** Discretion, and the brand: a keyhole. */
    val Keyhole = icon(
        "Keyhole",
        "M12 4.5a3.5 3.5 0 0 1 2 6.37V13.5l1.2 5.5H8.8l1.2-5.5v-2.63A3.5 3.5 0 0 1 12 4.5z",
    )

    /** Pairing. */
    val Link = icon(
        "Link",
        "M10 14a4 4 0 0 0 5.66 0l3-3a4 4 0 0 0-5.66-5.66l-1 1",
        "M14 10a4 4 0 0 0-5.66 0l-3 3a4 4 0 0 0 5.66 5.66l1-1",
    )

    /** Two phones, joined. */
    val Phones = icon(
        "Phones",
        "M3 6.5A1.5 1.5 0 0 1 4.5 5h3A1.5 1.5 0 0 1 9 6.5v11A1.5 1.5 0 0 1 7.5 19h-3A1.5 1.5 0 0 1 3 17.5z",
        "M15 6.5A1.5 1.5 0 0 1 16.5 5h3A1.5 1.5 0 0 1 21 6.5v11a1.5 1.5 0 0 1-1.5 1.5h-3a1.5 1.5 0 0 1-1.5-1.5z",
        "M9 12h6",
    )

    /** A level opening. */
    val LockOpen = icon(
        "LockOpen",
        "M4.5 10.5h15V20h-15z",
        "M8 10.5V8a4 4 0 0 1 7.7-1.6",
    )

    /** Game Night. */
    val Candle = icon(
        "Candle",
        "M9.5 10.5h5V20h-5z",
        "M12 10.5V8.6",
        "M12 3c1.3 1.6 2 2.6 2 3.5a2 2 0 0 1-4 0C10 5.6 10.7 4.6 12 3z",
    )

    /** Truth or Dare: two facets of one stone. */
    val Facets = icon(
        "Facets",
        "M9 4l5 8-5 8-5-8z",
        "M15 4l5 8-5 8-5-8z",
    )

    /** Fantasy Match: two rings, linked. */
    val Rings = icon(
        "Rings",
        "M3.5 12a4.5 4.5 0 1 0 9 0a4.5 4.5 0 1 0 -9 0",
        "M11.5 12a4.5 4.5 0 1 0 9 0a4.5 4.5 0 1 0 -9 0",
    )

    /** Win It / Owe It. */
    val Scales = icon(
        "Scales",
        "M12 4.5v15",
        "M8 19.5h8",
        "M5 7.5h14",
        "M5 7.5l-2.5 5a2.5 2.5 0 0 0 5 0z",
        "M19 7.5l-2.5 5a2.5 2.5 0 0 0 5 0z",
    )

    /** Would You Rather. */
    val Fork = icon(
        "Fork",
        "M12 20v-7",
        "M12 13L6.5 6.5",
        "M12 13l5.5-6.5",
        "M6.5 10V6.5H10",
        "M17.5 10V6.5H14",
    )

    /** Hot or Not: a flame that is asking. */
    val FlameAsk = icon(
        "FlameAsk",
        "M12 3c3 3.5 5 6 5 9a5 5 0 01-10 0c0-1.6.7-3 1.8-4.3.4 1 1 1.7 1.7 2C10.6 7.4 11 5 12 3z",
        "M10.9 12.1a1.2 1.2 0 1 1 1.7 1.1c-.4.2-.6.5-.6.9",
        "M12 16.1v.01",
    )

    /** Something sent: an envelope with a seal. */
    val Sealed = icon(
        "Sealed",
        "M3.5 6.5h17v11h-17z",
        "M3.5 6.5l8.5 6 8.5-6",
        "M12 15.6a1.4 1.4 0 1 0 0.01 0",
    )

    /** Aftercare: a heart, held. */
    val Aftercare = icon(
        "Aftercare",
        "M12 16.5s-4.2-2.7-4.2-5.5A2.3 2.3 0 0 1 12 9.8a2.3 2.3 0 0 1 4.2 1.2c0 2.8-4.2 5.5-4.2 5.5z",
        "M4 13.5c0 3.6 3.6 6.5 8 6.5s8-2.9 8-6.5",
    )

    /** A lane of the deck. */
    val Lanes = icon(
        "Lanes",
        "M5 6h14",
        "M5 12h14",
        "M5 18h9",
    )

    /** A card face down. */
    val CardBack = icon(
        "CardBack",
        "M6.5 3.5h11a1.5 1.5 0 0 1 1.5 1.5v14a1.5 1.5 0 0 1-1.5 1.5h-11A1.5 1.5 0 0 1 5 19V5a1.5 1.5 0 0 1 1.5-1.5z",
        "M12 8l3 4-3 4-3-4z",
    )

    /** Try again. */
    val Retry = icon(
        "Retry",
        "M19 12a7 7 0 1 1-2.05-4.95",
        "M19 4.5V8h-3.5",
    )

    /** Turn it down. */
    val Down = icon(
        "Down",
        "M12 4v12",
        "M7 11l5 5 5-5",
        "M6 20h12",
    )

    /** Turn it up. */
    val Up = icon(
        "Up",
        "M12 20V8",
        "M7 13l5-5 5 5",
        "M6 4h12",
    )

    /** Delete a digit. */
    val Backspace = icon(
        "Backspace",
        "M9 6h11v12H9l-5-6z",
        "M12.5 9.5l5 5",
        "M17.5 9.5l-5 5",
    )

    /** Unlock with a fingerprint. */
    val Fingerprint = icon(
        "Fingerprint",
        "M7.5 18.5c1-1.8 1.5-3.9 1.5-6.5a3 3 0 0 1 6 0c0 1.5-.1 2.9-.4 4.2",
        "M12 12c0 3.2-.6 5.8-1.8 8",
        "M5 15.5c.6-1.2 1-2.4 1-3.5a6 6 0 0 1 11.6-2.1",
        "M17.9 13.5c-.2 2.2-.7 4.2-1.5 6",
    )

    /** Send a link. */
    val Share = icon(
        "Share",
        "M12 4v11",
        "M8 8l4-4 4 4",
        "M5 13v6h14v-6",
    )

    /** A code to scan. */
    val Qr = icon(
        "Qr",
        "M4 4h6v6H4z",
        "M14 4h6v6h-6z",
        "M4 14h6v6H4z",
        "M14 14h2v2h-2z",
        "M18 18h2v2h-2z",
        "M14 18h2",
        "M18 14h2",
    )

    /** Every icon in the set, for the gallery and for the audit that holds them to one spec. */
    val all: List<Pair<String, ImageVector>> = listOf(
        "back" to Back, "forward" to Forward, "close" to Close, "check" to Check, "plus" to Plus,
        "flame" to Flame, "dice" to Dice, "spark" to Spark, "moon" to Moon, "clock" to Clock,
        "skip" to Skip, "pause" to Pause, "stop" to Stop,
        "people" to People, "send" to Send, "mic" to Mic, "camera" to Camera, "gift" to Gift,
        "calendar" to Calendar,
        "heart" to Heart, "bookmark" to Bookmark, "star" to Star, "eye" to Eye, "lock" to Lock,
        "shield" to Shield,
        "settings" to Settings,
        "safe word" to SafeWord, "limits" to Limits, "keyhole" to Keyhole, "link" to Link, "phones" to Phones,
        "lock open" to LockOpen, "candle" to Candle, "facets" to Facets, "rings" to Rings, "scales" to Scales,
        "fork" to Fork, "flame ask" to FlameAsk, "sealed" to Sealed, "aftercare" to Aftercare, "lanes" to Lanes,
        "card back" to CardBack, "retry" to Retry, "down" to Down, "up" to Up, "backspace" to Backspace,
        "fingerprint" to Fingerprint, "share" to Share, "qr" to Qr,
    )
}

/**
 * One icon, to the one specification.
 *
 * Built here rather than declared per-file so the stroke, the caps, the joins and the box
 * cannot drift apart between icons — which is the failure mode of every hand-assembled
 * icon set.
 */
private fun icon(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = BOX.dp,
        defaultHeight = BOX.dp,
        viewportWidth = BOX,
        viewportHeight = BOX,
    ).apply {
        paths.forEach { data ->
            addPath(
                pathData = PathParser().parsePathString(data).toNodes(),
                pathFillType = PathFillType.NonZero,
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

/** 24 dp box, 1.5 dp stroke. The whole specification. */
private const val BOX = 24f
private const val STROKE = 1.5f
