package com.shnapps.couple.core.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import java.util.WeakHashMap

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
 * The flag is held, not simply set: every `SecureScreen` in composition holds it, and it is
 * cleared only when the last one leaves. Navigating from one private screen to another, the
 * next screen arrives before the last one has gone — a plain set-then-clear let the leaving
 * screen switch protection off under the arriving one (DECISIONS.md D-073). Navigating to a
 * non-sensitive screen still restores normal behaviour rather than leaving the whole app
 * unscreenshottable.
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
        val window = if (isPreview || !enabled) null else context.findActivity()?.window
        window?.let(SecureHolds::hold)
        onDispose { window?.let(SecureHolds::release) }
    }
}

/**
 * How many private screens hold each window secure. Effects run on the main thread, so the
 * count needs no locking; windows are held weakly, so a destroyed activity is not kept alive.
 */
private object SecureHolds {
    private val holds = WeakHashMap<Window, Int>()

    fun hold(window: Window) {
        holds[window] = (holds[window] ?: 0) + 1
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    }

    fun release(window: Window) {
        val left = (holds[window] ?: 1) - 1
        if (left > 0) {
            holds[window] = left
        } else {
            holds.remove(window)
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
