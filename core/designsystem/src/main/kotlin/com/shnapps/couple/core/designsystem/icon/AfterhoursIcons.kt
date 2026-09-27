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
    val Moon = icon("Moon", "M20 14.5A8 8 0 019.5 4 8.5 8.5 0 1020 14.5z")
    val Clock = icon("Clock", "M3.5 12a8.5 8.5 0 0 1 17.0 0a8.5 8.5 0 0 1 -17.0 0", "M12 7.5V12l3 1.8")
    val Skip = icon("Skip", "M5 5l8 7-8 7z", "M18 5v14")
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
    val Mic = icon(
        "Mic",
        "M12 3a2.5 2.5 0 012.5 2.5v5a2.5 2.5 0 01-5 0v-5A2.5 2.5 0 0112 3z",
        "M6 11a6 6 0 0012 0",
        "M12 17v4",
    )
    val Camera = icon("Camera", "M4 8h3l1.5-2h7L17 8h3v11H4z", "M9 13a3 3 0 0 1 6 0a3 3 0 0 1 -6 0")
    val Gift = icon(
        "Gift",
        "M4 9.5h16V20H4z",
        "M4 13h16",
        "M12 9.5V20",
        "M12 9.5S10.5 4 8 4a2 2 0 000 5.5z",
        "M12 9.5S13.5 4 16 4a2 2 0 010 5.5z",
    )
    val Calendar = icon("Calendar", "M4 5.5h16v15H4z", "M4 10h16", "M9 3.5v4", "M15 3.5v4")

    // --- what is yours -------------------------------------------------------
    val Heart = icon("Heart", "M12 20s-7-4.4-7-9.2A4 4 0 0112 8.6 4 4 0 0119 10.8C19 15.6 12 20 12 20z")
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
        "M15 7a2 2 0 104 0 2 2 0 10-4 0",
        "M7 12a2 2 0 104 0 2 2 0 10-4 0",
        "M15 17a2 2 0 104 0 2 2 0 10-4 0",
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
