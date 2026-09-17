package com.shnapps.couple.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme

/**
 * Material 3 is the substrate, fully re-themed (BUILD_PROMPT.md §15.1).
 *
 * Dark-only on purpose. A light scheme is not offered: the product is a private room
 * after dark, and a light mode would be a different product. `AfterhoursTheme` therefore
 * ignores the system light/dark setting rather than half-supporting it.
 */
internal fun afterhoursColorScheme(): ColorScheme = darkColorScheme(
    primary = Palette.Burgundy,
    onPrimary = Palette.Ink,
    primaryContainer = Palette.BurgundyContainer,
    onPrimaryContainer = Palette.OnBurgundyContainer,

    secondary = Palette.Plum,
    onSecondary = Palette.Ink,
    secondaryContainer = Palette.PlumContainer,
    onSecondaryContainer = Palette.OnPlumContainer,

    tertiary = Palette.Brass,
    onTertiary = Palette.Ink,
    tertiaryContainer = Palette.BrassContainer,
    onTertiaryContainer = Palette.OnBrassContainer,

    background = Palette.Ink,
    onBackground = Palette.Cream,

    surface = Palette.Ink,
    onSurface = Palette.Cream,
    surfaceVariant = Palette.InkElevated,
    onSurfaceVariant = Palette.CreamMuted,

    surfaceContainerLowest = Palette.Ink,
    surfaceContainerLow = Palette.InkRaised,
    surfaceContainer = Palette.InkElevated,
    surfaceContainerHigh = Palette.InkHigh,
    surfaceContainerHighest = Palette.InkHighest,

    inverseSurface = Palette.Cream,
    inverseOnSurface = Palette.Ink,
    inversePrimary = Palette.BurgundyDeep,

    outline = Palette.Outline,
    outlineVariant = Palette.OutlineFaint,

    error = Palette.Signal,
    onError = Palette.Ink,
    errorContainer = Palette.SignalContainer,
    onErrorContainer = Palette.OnSignalContainer,

    scrim = Palette.Scrim,
)
