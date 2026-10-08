package com.shnapps.couple.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * One control the whole app carries, placed by every bar that has room for it.
 *
 * The chat is the only thing in here, and the reason it needs somewhere to be told about is that
 * it is drawn as a layer over the nav host (D-138) rather than by any screen. Left to itself it
 * floated in a corner and landed directly on top of whatever that screen already had there —
 * on home, the settings button, which simply disappeared under it.
 *
 * So the layer does not place it. It offers it, and each bar puts it where that bar's own controls
 * go: a screen's top bar draws it beside the screen's own trailing control, and a game's control
 * bar draws it beside Pause and Stop. A bar that does not offer it — the age gate, pairing, the
 * lock screen — is a screen with nobody to talk to or nothing to say, and shows nothing.
 *
 * It holds a composable rather than an icon and a lambda on purpose: the layer owns what the
 * control looks like and what it knows (an unread mark, for one), and a bar owns only where it
 * sits. Null is the ordinary case — most of the app runs with no chat at all.
 */
val LocalBarExtra = staticCompositionLocalOf<(@Composable () -> Unit)?> { null }

/** Draws the app-wide control, if there is one. Nothing at all when there is not. */
@Composable
fun BarExtra() {
    LocalBarExtra.current?.invoke()
}
