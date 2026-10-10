package com.shnapps.couple.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.shnapps.couple.core.model.Intensity
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
        assertContrast("onBackground on background", Palette.Blush, Palette.Base, AA_NORMAL)
        assertContrast("onSurface on surface", Palette.Blush, Palette.Base, AA_NORMAL)
        assertContrast(
            "onSurfaceVariant on surfaceVariant",
            Palette.BlushMuted,
            Palette.Velvet1,
            AA_NORMAL,
        )
        // Cream muted has to stay legible on every step of the surface stack, since cards
        // sit on cards.
        assertContrast("muted on surfaceHigh", Palette.BlushMuted, Palette.Velvet2, AA_NORMAL)
        assertContrast("muted on surfaceHighest", Palette.BlushMuted, Palette.Velvet3, AA_NORMAL)
    }

    @Test
    fun `faint text stays readable on every ground`() {
        // textFaint carries hints, timestamps and helper copy. It is allowed to recede,
        // but it is still body text, so it still owes 4.5:1.
        assertContrast("faint on background", Palette.BlushFaint, Palette.Base, AA_NORMAL)
        assertContrast("faint on surfaceHigh", Palette.BlushFaint, Palette.Velvet2, AA_NORMAL)
        assertContrast("faint on surfaceHighest", Palette.BlushFaint, Palette.Velvet3, AA_NORMAL)
    }

    @Test
    fun `accent pairs meet AA`() {
        assertContrast("onPrimary on primary", Palette.Base, Palette.Ember, AA_NORMAL)
        assertContrast("onPrimaryContainer on primaryContainer", Palette.Blush, Palette.Wine, AA_NORMAL)
        assertContrast("onSecondary on secondary", Palette.Base, Palette.Flame, AA_NORMAL)
        assertContrast("onSecondaryContainer on secondaryContainer", Palette.Blush, Palette.WineDeep, AA_NORMAL)
        assertContrast("onTertiary on tertiary", Palette.Base, Palette.Gold, AA_NORMAL)
        assertContrast(
            "onTertiaryContainer on tertiaryContainer",
            Palette.OnGoldContainer,
            Palette.GoldContainer,
            AA_NORMAL,
        )
    }

    @Test
    fun `stop and error signalling meets AA`() {
        // STOP must be unmistakable from every session state (§3.1), so its contrast is
        // not negotiable.
        assertContrast("onError on error", Palette.Base, Palette.Signal, AA_NORMAL)
        assertContrast(
            "onErrorContainer on errorContainer",
            Palette.OnSignalContainer,
            Palette.SignalContainer,
            AA_NORMAL,
        )
        assertContrast("error on background", Palette.Signal, Palette.Base, AA_NORMAL)
    }

    @Test
    fun `accents are distinguishable against the grounds they sit on`() {
        assertContrast("primary on background", Palette.Ember, Palette.Base, AA_NORMAL)
        assertContrast("secondary on background", Palette.Flame, Palette.Base, AA_NORMAL)
        assertContrast("gold on the card", Palette.Gold, Palette.Velvet2, AA_NORMAL)
        assertContrast("brass on raised", Palette.Gold, Palette.Velvet1, AA_LARGE)
    }

    @Test
    fun `outlines meet the non-text threshold`() {
        assertContrast("outline on background", Palette.Outline, Palette.Base, AA_LARGE)
        assertContrast("outline on the card", Palette.Outline, Palette.Velvet2, AA_LARGE)
    }

    @Test
    fun `background is never pure black`() {
        // Pure #000 reads as a switched-off screen rather than as a dim room (§15.1).
        assertThat(Palette.Base).isNotEqualTo(Color(0xFF000000))
        assertThat(Palette.Base.luminance()).isGreaterThan(0.0)
    }

    /**
     * The grounds have to be far enough apart to be *seen* apart (DESIGN_PROMPT.md §4.2).
     *
     * There used to be five, spanning under five percent of luminance in total, which meant
     * the app had one surface colour and nothing could be told from anything else. Each of the
     * four velvets is a clear step up, which is what a person can read on an OLED at low
     * brightness with grain over it — and why the prototype's velvets were lifted (D-145).
     */
    @Test
    fun `the grounds are far enough apart to be seen apart`() {
        val stack = listOf(Palette.Base, Palette.Velvet1, Palette.Velvet2, Palette.Velvet3)
        stack.map { it.luminance() }.zipWithNext().forEach { (lower, higher) ->
            assertWithMessage("each ground must be a visible step above the last")
                .that(higher - lower)
                .isAtLeast(MIN_GROUND_STEP)
        }
    }

    /**
     * Every tier, on every ground it is drawn on (§4.4).
     *
     * A heat owns the accent on a card, the label on a filled button and the text inside its
     * own container. Extreme is the one that had to be designed rather than picked: its red is
     * deep enough that dark text fails on it, which is why its filled state takes blush, and it
     * was lifted until the fill could be found on the room at all.
     */
    @Test
    fun `every tier is legible on every ground it is drawn on`() {
        Intensity.entries.forEach { tier ->
            val colors = tierColors(tier)
            assertContrast("${tier.label} text on card", colors.onDark, Palette.Velvet2, AA_NORMAL)
            assertContrast("${tier.label} text on raised", colors.onDark, Palette.Velvet1, AA_NORMAL)
            assertContrast("${tier.label} label on its fill", colors.onAccent, colors.accent, AA_NORMAL)
            assertContrast("${tier.label} in its container", colors.onContainer, colors.container, AA_NORMAL)
            assertContrast("${tier.label} fill on the scene", colors.accent, Palette.Base, AA_LARGE)
        }
    }

    /** The scanner's two colours, which do not follow the theme and still have to be read. */
    @Test
    fun `a pairing code is readable by a camera`() {
        assertContrast("code modules on the plate", Palette.Base, Palette.Blush, AA_NORMAL)
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

        /** The smallest luminance step a person can see on this stack. */
        const val MIN_GROUND_STEP = 0.003

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
