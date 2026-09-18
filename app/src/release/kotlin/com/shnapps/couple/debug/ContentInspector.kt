package com.shnapps.couple.debug

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * Release builds have no content inspector (DECISIONS.md D-042): content only ever reaches a
 * user through the engine, behind their boundaries (BUILD_PROMPT.md §3.2). The destination
 * exists so the navigation graph is the same in every build; nothing links to it, and
 * anything that somehow lands here is sent straight back.
 */
const val CONTENT_INSPECTOR_AVAILABLE = false

@Composable
fun ContentInspectorScreen(onBack: () -> Unit) {
    LaunchedEffect(Unit) { onBack() }
}
