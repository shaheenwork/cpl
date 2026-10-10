package com.shnapps.couple.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme

/**
 * Material 3 is the substrate, fully re-themed (BUILD_PROMPT.md §15.1, D-145).
 *
 * Dark-only on purpose. A light scheme is not offered: the product is a private room after dark,
 * and a light mode would be a different product. `AfterhoursTheme` therefore ignores the system
 * light/dark setting rather than half-supporting it.
 */
internal fun afterhoursColorScheme(): ColorScheme = darkColorScheme(
    primary = Palette.Ember,
    onPrimary = Palette.Base,
    primaryContainer = Palette.Wine,
    onPrimaryContainer = Palette.Blush,

    secondary = Palette.Flame,
    onSecondary = Palette.Base,
    secondaryContainer = Palette.WineDeep,
    onSecondaryContainer = Palette.Blush,

    tertiary = Palette.Gold,
    onTertiary = Palette.Base,
    tertiaryContainer = Palette.GoldContainer,
    onTertiaryContainer = Palette.OnGoldContainer,

    background = Palette.Base,
    onBackground = Palette.Blush,

    surface = Palette.Base,
    onSurface = Palette.Blush,
    surfaceVariant = Palette.Velvet1,
    onSurfaceVariant = Palette.BlushMuted,

    // Four grounds over Material's five slots. A component that reaches for `surfaceContainer`
    // gets the row ground, `High` gets the card, and `Highest` is something set on a card.
    surfaceContainerLowest = Palette.Base,
    surfaceContainerLow = Palette.Velvet1,
    surfaceContainer = Palette.Velvet1,
    surfaceContainerHigh = Palette.Velvet2,
    surfaceContainerHighest = Palette.Velvet3,

    inverseSurface = Palette.Blush,
    inverseOnSurface = Palette.Base,
    inversePrimary = Palette.Wine,

    outline = Palette.Outline,
    outlineVariant = Palette.HairlineFaint,

    error = Palette.Signal,
    onError = Palette.Base,
    errorContainer = Palette.SignalContainer,
    onErrorContainer = Palette.OnSignalContainer,

    scrim = Palette.Scrim,
)
