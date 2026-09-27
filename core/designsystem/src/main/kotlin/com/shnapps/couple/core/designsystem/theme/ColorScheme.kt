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
    onPrimary = Palette.Scene,
    primaryContainer = Palette.BurgundyContainer,
    onPrimaryContainer = Palette.OnBurgundyContainer,

    secondary = Palette.Plum,
    onSecondary = Palette.Scene,
    secondaryContainer = Palette.PlumContainer,
    onSecondaryContainer = Palette.OnPlumContainer,

    tertiary = Palette.Brass,
    onTertiary = Palette.Scene,
    tertiaryContainer = Palette.BrassContainer,
    onTertiaryContainer = Palette.OnBrassContainer,

    background = Palette.Scene,
    onBackground = Palette.Cream,

    surface = Palette.Scene,
    onSurface = Palette.Cream,
    surfaceVariant = Palette.Raised,
    onSurfaceVariant = Palette.CreamMuted,

    // Three grounds, not five (DESIGN_PROMPT.md §4.2). Material has five slots; they map
    // onto the three a person can actually tell apart, so a component that reaches for
    // `surfaceContainer` gets the row ground and one that reaches for `High` gets the card.
    surfaceContainerLowest = Palette.Scene,
    surfaceContainerLow = Palette.Raised,
    surfaceContainer = Palette.Raised,
    surfaceContainerHigh = Palette.Card,
    surfaceContainerHighest = Palette.Card,

    inverseSurface = Palette.Cream,
    inverseOnSurface = Palette.Scene,
    inversePrimary = Palette.BurgundyDeep,

    outline = Palette.Outline,
    outlineVariant = Palette.OutlineFaint,

    error = Palette.Signal,
    onError = Palette.Scene,
    errorContainer = Palette.SignalContainer,
    onErrorContainer = Palette.OnSignalContainer,

    scrim = Palette.Scrim,
)
