package com.shnapps.couple.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
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
 * Small-caps-ish eyebrow label: wide tracking, used above a chapter title or section.
 * Not a Material slot, so it lives here.
 */
val EyebrowTextStyle = TextStyle(
    fontFamily = BodyFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    letterSpacing = 2.4.sp,
)
