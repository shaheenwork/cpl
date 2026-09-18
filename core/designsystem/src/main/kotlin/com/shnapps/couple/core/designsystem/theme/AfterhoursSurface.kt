package com.shnapps.couple.core.designsystem.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The root surface every screen is drawn on: the design system's ground, full-bleed.
 *
 * Used by `MainActivity` **and** by every screenshot test, so tests render exactly what
 * the app renders. Before this existed, the tests drew screens bare, the default white
 * window showed through, and cream-on-white headlines looked broken in a way the real app
 * never was — while the real launch-flash bug hid behind the same missing background.
 */
@Composable
fun AfterhoursSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        content = content,
    )
}
