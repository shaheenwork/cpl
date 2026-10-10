package com.shnapps.couple.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.shnapps.couple.core.designsystem.theme.AfterhoursEasing
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle

/**
 * A card dealt face down, turned over by a tap (D-143).
 *
 * The single most charged mechanic in a game like this is not knowing what is coming, then finding
 * out. The deck used to spend it before the screen appeared; this keeps it.
 *
 * Two promises, the same ones [RevealCard] makes:
 *
 * - **Face down means absent.** Until [revealed], [content] is not composed at all, so a screen
 *   reader or a screenshot finds the back of a card and nothing else.
 * - **The turn keeps its timing under reduce-motion**, as a cross-fade rather than a rotation: it is
 *   a reveal, and a reveal that snaps open is not one.
 *
 * The content appears at the halfway point of the turn, when the card is edge-on. That is also when
 * its height is allowed to change, which is why a long card does not jump.
 */
@Composable
fun TurnOverCard(
    revealed: Boolean,
    onReveal: () -> Unit,
    modifier: Modifier = Modifier,
    /** Small caps at the top of the back: which card this is in the run. */
    label: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduceMotion = AfterhoursTheme.reduceMotion
    val motion = AfterhoursTheme.motion
    val turn by animateFloatAsState(
        targetValue = if (revealed) FULL_TURN else 0f,
        animationSpec = tween(
            durationMillis = if (reduceMotion) 0 else motion.reveal,
            easing = AfterhoursEasing.Anticipation,
        ),
        label = "turnOver",
    )
    // Under reduce-motion the turn is a fade, at the reveal's own pace.
    val fade by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(durationMillis = motion.reveal, easing = AfterhoursEasing.Decelerate),
        label = "turnOverFade",
    )
    val showingFace = revealed && (reduceMotion || turn >= HALF_TURN)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                cameraDistance = CAMERA * density
                // Past halfway the face is drawn, so the rotation carries on from its own back.
                rotationY = when {
                    reduceMotion -> 0f
                    showingFace -> turn - FULL_TURN
                    else -> turn
                }
            },
    ) {
        if (showingFace) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (reduceMotion) fade else 1f),
                content = content,
            )
        } else {
            CardBack(label = label, onReveal = onReveal)
        }
    }
}

/** The back of every card: velvet, a double gold border, the sigil, and an invitation. */
@Composable
private fun CardBack(label: String?, onReveal: () -> Unit) {
    val colors = AfterhoursTheme.colors
    val tier = AfterhoursTheme.tier
    val spacing = AfterhoursTheme.spacing
    val shape = MaterialTheme.shapes.extraLarge
    val gold = colors.brass
    val velvet = Brush.verticalGradient(
        listOf(tier.container, MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.colorScheme.background),
    )
    val heat = Brush.radialGradient(listOf(tier.accent.copy(alpha = HEAT_GLOW), tier.accent.copy(alpha = 0f)))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = BACK_HEIGHT)
            .bloom(color = tier.rim, cornerRadius = spacing.lg, spread = spacing.xl, alpha = BACK_GLOW)
            .clip(shape)
            .background(velvet)
            .background(heat)
            .border(width = spacing.hairline, color = gold.copy(alpha = OUTER_EDGE), shape = shape)
            .padding(INSET)
            .border(width = spacing.hairline, color = gold.copy(alpha = INNER_EDGE), shape = MaterialTheme.shapes.large)
            .clickable(role = Role.Button, onClickLabel = "Reveal the card", onClick = onReveal)
            .semantics { contentDescription = "A card, face down. Tap to reveal it." }
            .padding(vertical = spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.md, Alignment.CenterVertically),
    ) {
        label?.let {
            Text(
                text = it.uppercase(),
                style = EyebrowTextStyle,
                color = gold,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
        Sigil(color = gold, modifier = Modifier.size(width = SIGIL_WIDTH, height = SIGIL_HEIGHT))
        Text(
            text = "tap to reveal",
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = MaterialTheme.typography.headlineMedium.fontFamily,
                fontStyle = FontStyle.Italic,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

/**
 * The ornament on every card back: two diamonds around a ring with a keyhole in it.
 *
 * Abstract on purpose. It says "something is behind this" without saying what, which is the whole
 * of a card's back, and it gives nothing away on a lock-screen preview or over a shoulder. Drawn
 * from path data in a 150 × 200 box, the same drawing the prototype's SVG makes.
 */
@Composable
fun Sigil(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        val unit = size.width / SIGIL_VIEW_WIDTH
        val stroke = Stroke(width = 1.dp.toPx() / unit)
        scale(unit, unit, pivot = Offset.Zero) {
            for (layer in SIGIL) {
                drawPath(layer.path, color.copy(alpha = layer.alpha), style = if (layer.filled) Fill else stroke)
            }
        }
    }
}

/** One stroke of the sigil, and how strongly it is drawn. */
private class SigilLayer(data: String, val alpha: Float, val filled: Boolean = false) {
    val path = PathParser().parsePathString(data).toPath()
}

private val SIGIL = listOf(
    // The outer diamond, and the inner one.
    SigilLayer("M75 26L122 100L75 174L28 100Z", alpha = 0.75f),
    SigilLayer("M75 50L106 100L75 150L44 100Z", alpha = 0.5f),
    // The ring, and its halo.
    SigilLayer("M48 100a27 27 0 1 0 54 0a27 27 0 1 0 -54 0", alpha = 1f),
    SigilLayer("M44 100a31 31 0 1 0 62 0a31 31 0 1 0 -62 0", alpha = 0.35f),
    // The keyhole.
    SigilLayer("M69 94a6 6 0 1 0 12 0a6 6 0 1 0 -12 0M72 99L69.5 113H80.5L78 99Z", alpha = 0.2f, filled = true),
    // The four ticks at the compass points, and the dots on them.
    SigilLayer("M75 6V26M75 174V194M8 100H28M122 100H142", alpha = 0.6f),
    SigilLayer(
        "M73.4 16a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0 -3.2 0M73.4 184a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0 -3.2 0" +
            "M14.4 100a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0 -3.2 0M132.4 100a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0 -3.2 0",
        alpha = 1f,
        filled = true,
    ),
    // The corner flourishes.
    SigilLayer("M10 40Q10 10 40 10M140 40Q140 10 110 10M10 160Q10 190 40 190M140 160Q140 190 110 190", alpha = 0.55f),
)

private const val HALF_TURN = 90f
private const val FULL_TURN = 180f
private const val CAMERA = 14f
private const val BACK_GLOW = 0.45f
private const val HEAT_GLOW = 0.22f
private const val OUTER_EDGE = 0.32f
private const val INNER_EDGE = 0.2f
private const val SIGIL_VIEW_WIDTH = 150f
private val INSET = 7.dp
private val BACK_HEIGHT = 420.dp
private val SIGIL_WIDTH = 150.dp
private val SIGIL_HEIGHT = 200.dp
