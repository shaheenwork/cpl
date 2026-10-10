package com.shnapps.couple.core.designsystem.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier

/**
 * The root surface every screen is drawn on: the design system's ground, full-bleed.
 *
 * It is where the scene is composed (DESIGN_PROMPT.md §4.1): the key light, the two wine
 * bounces, the fall-off, the silk and the grain, under everything, once. A screen never draws its
 * own background; it says what heat to light the room in with [LightTheRoom] (D-145).
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
    val light = rememberSceneLight()
    CompositionLocalProvider(LocalSceneLight provides light) {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(modifier = Modifier.fillMaxSize().scene()) { content() }
        }
    }
}
