package com.shnapps.couple.core.designsystem.theme

import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * The app's theme (BUILD_PROMPT.md §15).
 *
 * Material 3 is the substrate and is completely re-themed: deep near-black grounds,
 * burgundy and plum accents, warm cream editorial type. It must not read as stock M3.
 *
 * **Dark only.** There is no `darkTheme` parameter and the system light/dark setting is
 * ignored on purpose — this is a private room after dark, and a light mode would be a
 * different product. Half-supporting one would be worse than not offering it.
 *
 * Extras that Material has no slot for are reached through [AfterhoursTheme]:
 * ```
 * AfterhoursTheme.colors.glow
 * AfterhoursTheme.spacing.gutter
 * AfterhoursTheme.motion.reveal
 * ```
 */
@Composable
fun AfterhoursTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current

    val reduceMotion = remember(context, isPreview) {
        if (isPreview) false else context.animatorDurationScale() == 0f
    }

    CompositionLocalProvider(
        LocalAfterhoursColors provides afterhoursColors,
        LocalSpacing provides Spacing(),
        LocalElevations provides Elevations(),
        LocalMotion provides Motion(),
        LocalReduceMotion provides reduceMotion,
    ) {
        MaterialTheme(
            colorScheme = afterhoursColorScheme(),
            typography = AfterhoursTypography,
            shapes = AfterhoursShapes,
            content = content,
        )
    }
}

/** Accessors for the tokens Material 3 has no slot for. */
object AfterhoursTheme {
    val colors: AfterhoursColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAfterhoursColors.current

    val spacing: Spacing
        @Composable
        @ReadOnlyComposable
        get() = LocalSpacing.current

    val elevations: Elevations
        @Composable
        @ReadOnlyComposable
        get() = LocalElevations.current

    val motion: Motion
        @Composable
        @ReadOnlyComposable
        get() = LocalMotion.current

    val reduceMotion: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalReduceMotion.current
}

internal val LocalAfterhoursColors =
    androidx.compose.runtime.staticCompositionLocalOf { afterhoursColors }

/**
 * Reads the system animator scale. Zero means the user has switched animation off, either
 * in Accessibility or in Developer options.
 */
private fun android.content.Context.animatorDurationScale(): Float = runCatching {
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
}.getOrDefault(1f)
