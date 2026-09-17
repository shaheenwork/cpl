package com.shnapps.couple.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * WCAG contrast verification for the palette (BUILD_PROMPT.md §20, and the Phase 2 exit
 * criterion "contrast verified").
 *
 * Cream on plum is the risky pairing the brief calls out, so this computes every
 * foreground/background pair the theme actually uses rather than trusting the eye. If a
 * token fails here, the fix is to change the token — not to lower the threshold.
 *
 * Thresholds are the WCAG 2.1 minimums:
 *  - 4.5:1 for normal body text (AA)
 *  - 3.0:1 for large text and for non-text UI such as outlines and icons
 */
class ContrastTest {

    @Test
    fun `body text pairs meet AA`() {
        assertContrast("onBackground on background", Palette.Cream, Palette.Ink, AA_NORMAL)
        assertContrast("onSurface on surface", Palette.Cream, Palette.Ink, AA_NORMAL)
        assertContrast(
            "onSurfaceVariant on surfaceVariant",
            Palette.CreamMuted,
            Palette.InkElevated,
            AA_NORMAL,
        )
        // Cream muted has to stay legible on every step of the surface stack, since cards
        // sit on cards.
        assertContrast("muted on surfaceHighest", Palette.CreamMuted, Palette.InkHighest, AA_NORMAL)
    }

    @Test
    fun `faint text stays readable on every ground`() {
        // textFaint carries hints, timestamps and helper copy. It is allowed to recede,
        // but it is still body text, so it still owes 4.5:1.
        assertContrast("faint on background", Palette.CreamFaint, Palette.Ink, AA_NORMAL)
        assertContrast("faint on surfaceHigh", Palette.CreamFaint, Palette.InkHigh, AA_NORMAL)
        assertContrast("faint on surfaceHighest", Palette.CreamFaint, Palette.InkHighest, AA_NORMAL)
    }

    @Test
    fun `accent pairs meet AA`() {
        assertContrast("onPrimary on primary", Palette.Ink, Palette.Burgundy, AA_NORMAL)
        assertContrast(
            "onPrimaryContainer on primaryContainer",
            Palette.OnBurgundyContainer,
            Palette.BurgundyContainer,
            AA_NORMAL,
        )
        assertContrast("onSecondary on secondary", Palette.Ink, Palette.Plum, AA_NORMAL)
        assertContrast(
            "onSecondaryContainer on secondaryContainer",
            Palette.OnPlumContainer,
            Palette.PlumContainer,
            AA_NORMAL,
        )
        assertContrast("onTertiary on tertiary", Palette.Ink, Palette.Brass, AA_NORMAL)
        assertContrast(
            "onTertiaryContainer on tertiaryContainer",
            Palette.OnBrassContainer,
            Palette.BrassContainer,
            AA_NORMAL,
        )
    }

    @Test
    fun `stop and error signalling meets AA`() {
        // STOP must be unmistakable from every session state (§3.1), so its contrast is
        // not negotiable.
        assertContrast("onError on error", Palette.Ink, Palette.Signal, AA_NORMAL)
        assertContrast(
            "onErrorContainer on errorContainer",
            Palette.OnSignalContainer,
            Palette.SignalContainer,
            AA_NORMAL,
        )
        assertContrast("error on background", Palette.Signal, Palette.Ink, AA_NORMAL)
    }

    @Test
    fun `accents are distinguishable against the grounds they sit on`() {
        assertContrast("primary on background", Palette.Burgundy, Palette.Ink, AA_NORMAL)
        assertContrast("secondary on background", Palette.Plum, Palette.Ink, AA_NORMAL)
        assertContrast("brass on surfaceContainer", Palette.Brass, Palette.InkElevated, AA_LARGE)
    }

    @Test
    fun `outlines meet the non-text threshold`() {
        assertContrast("outline on background", Palette.Outline, Palette.Ink, AA_LARGE)
        assertContrast("outline on surfaceHigh", Palette.Outline, Palette.InkHigh, AA_LARGE)
    }

    @Test
    fun `background is never pure black`() {
        // Pure #000 reads as cheap OLED rather than as a dim room (§15.1).
        assertThat(Palette.Ink).isNotEqualTo(Color(0xFF000000))
        assertThat(Palette.Ink.luminance()).isGreaterThan(0.0)
    }

    @Test
    fun `the surface stack steps upward so elevation is visible`() {
        val stack = listOf(
            Palette.Ink,
            Palette.InkRaised,
            Palette.InkElevated,
            Palette.InkHigh,
            Palette.InkHighest,
        )
        val luminances = stack.map { it.luminance() }
        luminances.zipWithNext().forEach { (lower, higher) ->
            assertThat(higher).isGreaterThan(lower)
        }
    }

    private fun assertContrast(label: String, foreground: Color, background: Color, min: Double) {
        val ratio = contrastRatio(foreground, background)
        // Truth's assertWithMessage only understands %s, so the numbers are formatted here.
        assertWithMessage("$label measured ${"%.2f".format(ratio)}:1, needs $min:1")
            .that(ratio)
            .isAtLeast(min)
    }

    private companion object {
        const val AA_NORMAL = 4.5
        const val AA_LARGE = 3.0

        /** WCAG 2.1 relative luminance. */
        fun Color.luminance(): Double {
            fun channel(value: Float): Double {
                val c = value.toDouble()
                return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)
        }

        fun contrastRatio(a: Color, b: Color): Double {
            val la = a.luminance()
            val lb = b.luminance()
            return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
        }
    }
}
