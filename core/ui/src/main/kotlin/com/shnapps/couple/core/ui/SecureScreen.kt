package com.shnapps.couple.core.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * Blocks screenshots, screen recording and the recent-apps thumbnail for as long as it is
 * in composition (BUILD_PROMPT.md §3.4).
 *
 * Belongs on every screen that shows private content: experiences, preferences,
 * boundaries, memories, media, and anything mid-session. Call it once near the top of the
 * screen's composable:
 *
 * ```
 * @Composable
 * fun PreferenceDiscoveryScreen() {
 *     SecureScreen()
 *     ...
 * }
 * ```
 *
 * The flag is cleared on dispose, so navigating to a non-sensitive screen restores normal
 * behaviour rather than leaving the whole app unscreenshottable.
 *
 * This is a privacy affordance, not a security boundary. It stops a casual capture and
 * keeps private content out of the app switcher; it does not defend against a determined
 * attacker with the device. App lock is the mitigation for that threat.
 */
@Composable
fun SecureScreen(enabled: Boolean = true) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current

    DisposableEffect(context, enabled, isPreview) {
        // Previews and Robolectric render without a real Activity window.
        val window = if (isPreview) null else context.findActivity()?.window

        if (enabled) {
            window?.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        }

        onDispose {
            if (enabled) {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
