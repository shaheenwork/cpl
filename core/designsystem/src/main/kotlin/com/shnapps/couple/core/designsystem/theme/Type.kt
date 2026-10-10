package com.shnapps.couple.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.shnapps.couple.core.designsystem.R

/**
 * Editorial typography (BUILD_PROMPT.md §15.1): a high-contrast display serif for
 * headings against a clean sans for everything else.
 *
 * Both families are variable fonts, so weights come from `FontVariation.Settings` rather
 * than from separate files. That needs API 26, which is the project's minSdk.
 */
@OptIn(ExperimentalTextApi::class)
private fun variableFont(resId: Int, weight: FontWeight) = Font(
    resId = resId,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** Playfair Display. Headings and numerals only — never body copy. */
val DisplayFontFamily = FontFamily(
    variableFont(R.font.playfair_display_variable, FontWeight.Normal),
    variableFont(R.font.playfair_display_variable, FontWeight.Medium),
    variableFont(R.font.playfair_display_variable, FontWeight.SemiBold),
    variableFont(R.font.playfair_display_variable, FontWeight.Bold),
)

/** Inter. Body, labels and anything the user has to read quickly. */
val BodyFontFamily = FontFamily(
    variableFont(R.font.inter_variable, FontWeight.Normal),
    variableFont(R.font.inter_variable, FontWeight.Medium),
    variableFont(R.font.inter_variable, FontWeight.SemiBold),
)

// Trims the extra leading Compose adds above the first line and below the last, so large
// display text sits optically centred in a card instead of floating high.
private val TrimmedLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

val AfterhoursTypography = Typography(
    // --- Display: the serif voice. Used sparingly, and large. ---------------
    displayLarge = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 52.sp,
        lineHeight = 58.sp,
        letterSpacing = (-0.5).sp,
        lineHeightStyle = TrimmedLineHeight,
    ),
    displayMedium = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.25).sp,
        lineHeightStyle = TrimmedLineHeight,
    ),
    displaySmall = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        lineHeightStyle = TrimmedLineHeight,
    ),

    headlineLarge = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        lineHeightStyle = TrimmedLineHeight,
    ),
    headlineMedium = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        lineHeightStyle = TrimmedLineHeight,
    ),
    headlineSmall = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        lineHeightStyle = TrimmedLineHeight,
    ),

    // --- Title: the sans voice, carrying weight -----------------------------
    titleLarge = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),

    // --- Body ----------------------------------------------------------------
    bodyLarge = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.15.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.15.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp,
    ),

    // --- Label ---------------------------------------------------------------
    labelLarge = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.5.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.8.sp,
    ),
)

/**
 * Small-caps-ish eyebrow label: wide tracking, used above a card title or a section.
 * Not a Material slot, so it lives here.
 *
 * `case` asks the font for capital-height punctuation, so the middle dot in
 * "HOT · 5 MIN" sits on the cap line instead of on the x-height.
 */
val EyebrowTextStyle = TextStyle(
    fontFamily = BodyFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 2.2.sp,
    fontFeatureSettings = CAPS_PUNCTUATION,
)

/**
 * The tease: a line in the display face, in italic, said under its breath (D-145).
 *
 * For what a level means, an invitation on the back of a card, a caption that is meant to be
 * read in a voice. Never for an instruction — something a person has to *do* is set upright.
 */
val TeaseTextStyle = TextStyle(
    fontFamily = DisplayFontFamily,
    fontWeight = FontWeight.Normal,
    fontStyle = FontStyle.Italic,
    fontSize = 18.sp,
    lineHeight = 26.sp,
    letterSpacing = 0.sp,
)

/**
 * The hero card's body (DESIGN_PROMPT.md §4.5).
 *
 * `bodyLarge` is a list style — 17/26 with positive tracking — and it was being used for
 * the one thing in the app a couple actually reads. This is larger, leaded further, and
 * tracked at zero, and it is meant to be used with [CARD_MEASURE].
 */
val CardBodyTextStyle = TextStyle(
    fontFamily = BodyFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 19.sp,
    lineHeight = 30.sp,
    letterSpacing = 0.sp,
)

/**
 * Anything that counts: a timer, a round, a score, a pairing code.
 *
 * `tnum` fixes every digit to the same width. Without it a clock changes width as it
 * counts down, and the whole card shifts under the reader — the cheapest possible tell
 * that nobody looked at the screen for more than a second.
 */
val NumeralTextStyle = TextStyle(
    fontFamily = BodyFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 20.sp,
    lineHeight = 24.sp,
    letterSpacing = 0.4.sp,
    fontFeatureSettings = TABULAR_NUMERALS,
)

/** Tabular figures, for any style that has to hold its width while a number changes. */
const val TABULAR_NUMERALS = "tnum"

/** Capital-height punctuation, for text set in all caps. */
private const val CAPS_PUNCTUATION = "case"

/**
 * How wide the hero card's body is allowed to get, as a multiple of its font size.
 *
 * About 38–42 characters. Body copy that runs the full width of a phone at 19 sp is
 * roughly 52 characters a line, which is past the point where the eye starts losing the
 * line it was on.
 */
const val CARD_MEASURE = 21
