package com.shnapps.couple.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Motion tokens (BUILD_PROMPT.md §15.2).
 *
 * Most of the animation budget in this product is spent on **anticipation** — blurred
 * cards blooming into focus, locks opening, "someone is choosing…" breathing away on the
 * other device. Transitions are slow cross-dissolves, never snaps.
 *
 * Two hard rules from §15.2 and §20:
 *  - No animation may block input for more than [MAX_BLOCKING_MS].
 *  - Everything respects the system reduce-motion setting, and the fallback has to be
 *    meaningful rather than jarring — a reveal still reveals, it just stops performing.
 */
@Immutable
data class Motion(
    /** Immediate state flips: selection, toggles. */
    val quick: Int = 120,
    /** The default for most property animations. */
    val standard: Int = 240,
    /** Deliberate, noticeable. Chapter-to-chapter cross-dissolve. */
    val slow: Int = 400,
    /** A reveal. Long enough to feel like a moment, short enough not to annoy. */
    val reveal: Int = 700,
    /** Glow blooming behind a revealed card. */
    val bloom: Int = 1_200,
    /** Ambient loops: breathing, waiting, "your partner is choosing". */
    val ambient: Int = 2_400,
) {
    companion object {
        /**
         * An animation may hold input for at most this long (§15.2). Anything longer
         * runs behind an interactive surface, never in front of it.
         */
        const val MAX_BLOCKING_MS = 400
    }
}

/**
 * Easings. [Anticipation] is the signature curve: a slow, restrained start that gathers
 * pace late, so a card feels like it is being uncovered rather than sliding in.
 */
object AfterhoursEasing {
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Decelerate: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)
    val Accelerate: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)
    val Anticipation: Easing = CubicBezierEasing(0.65f, 0f, 0.15f, 1f)
    val Breathing: Easing = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)
    val Linear: Easing = LinearEasing
}

val LocalMotion = staticCompositionLocalOf { Motion() }

/**
 * True when the user has asked the system to reduce or disable animation.
 *
 * Provided by `AfterhoursTheme`, which reads `ANIMATOR_DURATION_SCALE`. Components should
 * branch on this rather than on any of their own flags, so the behaviour is consistent
 * everywhere.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

/**
 * Collapses a duration to zero when reduce-motion is on.
 *
 * Used for decorative movement only. A reveal whose *purpose* is to withhold information
 * keeps its timing — the card still turns over, it simply stops performing, because
 * skipping it outright would leak the content early and break the anticipation the whole
 * product runs on.
 */
fun Int.orInstantIfReduced(reduceMotion: Boolean): Int = if (reduceMotion) 0 else this

/** A [tween] that honours reduce-motion, for decorative movement. */
fun <T> decorativeTween(
    durationMillis: Int,
    reduceMotion: Boolean,
    easing: Easing = AfterhoursEasing.Standard,
): FiniteAnimationSpec<T> = tween(
    durationMillis = durationMillis.orInstantIfReduced(reduceMotion),
    easing = easing,
)
