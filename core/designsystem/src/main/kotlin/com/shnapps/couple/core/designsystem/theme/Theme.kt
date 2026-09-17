package com.shnapps.couple.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * PLACEHOLDER. Phase 2 replaces this with the real design system: deep near-black
 * grounds, burgundy/plum accents, warm cream type, editorial display face, motion
 * tokens and the component inventory (BUILD_PROMPT.md §15).
 *
 * Dark-first on purpose, and dark-only for now — there is deliberately no `darkTheme`
 * parameter yet, because the product is meant to read as a luxury after-hours lounge
 * rather than as stock Material 3 in either polarity.
 */
@Composable
fun AfterhoursTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(),
        content = content,
    )
}
