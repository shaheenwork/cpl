package com.shnapps.couple.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shnapps.couple.core.designsystem.theme.AfterhoursEasing
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.decorativeTween
import kotlinx.coroutines.delay

/**
 * A screen arriving (DESIGN_PROMPT.md §4.8).
 *
 * `Motion` has had `Anticipation`, `bloom` and `ambient` since Phase 2 and the app spent almost
 * none of it: the only movement anywhere was a button's glow alpha. A screen appeared fully formed,
 * which is the cheapest way for a well-drawn screen to still feel like a web page.
 *
 * Each section rises 8 dp and fades, staggered by [STAGGER] per position, so the eye is led down
 * the screen once and then left alone. It is decorative, so under reduce-motion the screen appears
 * composed and instantly — the information was never in the movement.
 *
 * Applied at the section level rather than per element. Staggering every row of a list would be a
 * cascade, and a cascade is a screensaver.
 */
@Composable
fun Entrance(
    index: Int,
    modifier: Modifier = Modifier,
    rise: Dp = RISE,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduceMotion = AfterhoursTheme.reduceMotion
    var arrived by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!reduceMotion) delay(index.toLong() * STAGGER)
        arrived = true
    }

    val progress by animateFloatAsState(
        targetValue = if (arrived) 1f else 0f,
        animationSpec = decorativeTween(
            durationMillis = AfterhoursTheme.motion.standard,
            reduceMotion = reduceMotion,
            easing = AfterhoursEasing.Decelerate,
        ),
        label = "entrance",
    )

    val offset = with(LocalDensity.current) { rise.toPx() }

    Column(
        modifier = modifier.graphicsLayer {
            alpha = progress
            translationY = offset * (1f - progress)
        },
        content = content,
    )
}

/**
 * A card being laid down (§4.8) — the one moment the app performs, and the moment the product is
 * about.
 *
 * Scale from [DEAL_FROM], a degree and a half of rotation, over `slow` with `Anticipation`: a slow,
 * restrained start that gathers pace late, so it reads as a hand putting something down rather than
 * a view sliding in. [key] restarts it, so each new card deals rather than cross-fading.
 */
@Composable
fun Dealt(key: Any?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val reduceMotion = AfterhoursTheme.reduceMotion
    var settled by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) { settled = true }

    val progress by animateFloatAsState(
        targetValue = if (settled) 1f else 0f,
        animationSpec = decorativeTween(
            durationMillis = AfterhoursTheme.motion.slow,
            reduceMotion = reduceMotion,
            easing = AfterhoursEasing.Anticipation,
        ),
        label = "deal",
    )

    Column(
        modifier = modifier.graphicsLayer {
            alpha = progress
            val scale = DEAL_FROM + (1f - DEAL_FROM) * progress
            scaleX = scale
            scaleY = scale
            rotationZ = DEAL_TILT * (1f - progress)
        },
    ) { content() }
}

private val RISE = 8.dp

/** 40 ms between sections: enough to read as a sequence, short enough not to be a wait. */
const val STAGGER = 40

private const val DEAL_FROM = 0.94f
private const val DEAL_TILT = -1.5f
