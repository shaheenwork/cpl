package com.shnapps.couple.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * The raw palette (BUILD_PROMPT.md §15.1).
 *
 * Deep near-black grounds — never pure #000, which reads as cheap OLED rather than as a
 * dim room — warmed with a trace of red so the whole surface stack feels lit by
 * candlelight rather than by a monitor. Accents are burgundy, plum and oxblood. Text is
 * warm cream, never white.
 *
 * Reference: a luxury after-hours lounge. Explicitly NOT pink gradients, hearts, cartoon
 * flames or anything from an adult novelty store.
 *
 * Every foreground/background pairing used by [afterhoursColorScheme] is checked against
 * WCAG AA by `ContrastTest`, so these values are verified rather than eyeballed.
 */
internal object Palette {

    // --- Grounds: near-black, warm-tinted, stepping up in elevation ---------
    val Ink = Color(0xFF0B0708)
    val InkRaised = Color(0xFF151013)
    val InkElevated = Color(0xFF1E171B)
    val InkHigh = Color(0xFF281F24)
    val InkHighest = Color(0xFF332830)

    // --- Burgundy: the primary accent ---------------------------------------
    val Burgundy = Color(0xFFC75C7A)
    val BurgundyDeep = Color(0xFF8E2A47)
    val BurgundyContainer = Color(0xFF4A1426)
    val OnBurgundyContainer = Color(0xFFFFD9E1)

    // --- Plum: the secondary accent -----------------------------------------
    val Plum = Color(0xFFC49BC4)
    val PlumDeep = Color(0xFF6E3A63)
    val PlumContainer = Color(0xFF3A1F36)
    val OnPlumContainer = Color(0xFFF2DAEF)

    // --- Brass: restrained metallic highlight, for locks, vaults, reveals ----
    val Brass = Color(0xFFD9B978)
    val BrassDeep = Color(0xFF8A6D32)
    val BrassContainer = Color(0xFF3A2E16)
    val OnBrassContainer = Color(0xFFF7E4BC)

    // --- Cream: typography ---------------------------------------------------
    val Cream = Color(0xFFF2E6DA)
    val CreamMuted = Color(0xFFCBBAAE)
    val CreamFaint = Color(0xFFA2938A)

    // --- Lines and edges -----------------------------------------------------
    val Outline = Color(0xFF7D6C74)
    val OutlineFaint = Color(0xFF3A2F35)

    // --- Signal --------------------------------------------------------------
    // Used for STOP and for destructive confirmations only. Deliberately distinct from
    // the burgundy accent so "end this now" can never be mistaken for decoration (§3.1).
    val Signal = Color(0xFFFFB4A8)
    val SignalDeep = Color(0xFF8C1D18)
    val SignalContainer = Color(0xFF5C1410)
    val OnSignalContainer = Color(0xFFFFDAD4)

    val Scrim = Color(0xFF000000)
}

/**
 * Semantic colours that Material 3's [androidx.compose.material3.ColorScheme] has no slot
 * for. Reached through `AfterhoursTheme.colors`.
 */
@androidx.compose.runtime.Immutable
data class AfterhoursColors(
    /** Soft bloom behind a revealed card. Low alpha by design — glow, not fill. */
    val glow: Color,
    /** Bloom used while a partner is choosing or a chapter is unlocking. */
    val glowSecondary: Color,
    /** Metallic highlight for locks, vaults and sealed envelopes. */
    val brass: Color,
    val brassContainer: Color,
    val onBrassContainer: Color,
    /** Text that must recede: hints, timestamps, helper copy. */
    val textFaint: Color,
    /** Text one step down from primary: subtitles, supporting copy. */
    val textMuted: Color,
    /** Hairlines and card edges that should barely register. */
    val edgeFaint: Color,
    /** The top and bottom of the ambient background wash. */
    val backdropTop: Color,
    val backdropBottom: Color,
)

internal val afterhoursColors = AfterhoursColors(
    glow = Palette.Burgundy.copy(alpha = 0.22f),
    glowSecondary = Palette.Plum.copy(alpha = 0.18f),
    brass = Palette.Brass,
    brassContainer = Palette.BrassContainer,
    onBrassContainer = Palette.OnBrassContainer,
    textFaint = Palette.CreamFaint,
    textMuted = Palette.CreamMuted,
    edgeFaint = Palette.OutlineFaint,
    backdropTop = Palette.InkRaised,
    backdropBottom = Palette.Ink,
)
