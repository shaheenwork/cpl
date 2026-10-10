package com.shnapps.couple.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shnapps.couple.core.designsystem.component.HeatFlames
import com.shnapps.couple.core.designsystem.component.bloom
import com.shnapps.couple.core.designsystem.icon.AfterhoursIcons
import com.shnapps.couple.core.designsystem.theme.AfterhoursEasing
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.designsystem.theme.TeaseTextStyle
import com.shnapps.couple.core.designsystem.theme.decorativeTween
import com.shnapps.couple.core.designsystem.theme.tierColors
import com.shnapps.couple.core.model.Intensity

/**
 * How far tonight goes: the only setting the app asks for (DESIGN_PROMPT.md §5.4, D-145).
 *
 * Five cells, each the level as flames and its name; a bar under them that runs cool to hot with an
 * ember where tonight sits; and a line that says, in a word or two, what the chosen level means.
 * Flames rather than dots because a flame is the thing itself, where `●●●○○` is an abstraction to be
 * counted — and colour is never the only cue: the count carries the level and the name is on screen.
 *
 * It does **not** stack at large type. Five stacked rows are taller than the screen they are on: at
 * 200% they pushed the age gate's own button off the bottom, which `InstantAccessibilityTest`
 * caught. The flames do not grow with the type, so at large type the cells drop their names and the
 * row keeps its height; the chosen level is still named, once, under the bar.
 *
 * **Only the chosen cell glows**, and only softly: a lit thing in a row of siblings is a smear by
 * construction. Each cell takes its own tier's colour, not the screen's, so the control shows what
 * the choice would do rather than only which one is on.
 *
 * Each names a ceiling rather than a promise: picking Extreme rules nothing out in advance, it does
 * not mean everything will be. Picking it is also the whole of the consent (D-144) — there is no
 * second question after this one.
 *
 * A level in [locked] carries a lock and calls [onLocked] rather than changing anything (V2 rule 5).
 * It stays in its place, at its colour: hiding what has not been bought would make the ramp a
 * different length for different couples.
 */
@Composable
fun TierPicker(
    selected: Intensity,
    onTier: (Intensity) -> Unit,
    modifier: Modifier = Modifier,
    locked: Set<Intensity> = emptySet(),
    onLocked: (Intensity) -> Unit = {},
) {
    val spacing = AfterhoursTheme.spacing
    val named = LocalDensity.current.fontScale <= NAMES_UP_TO

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.item),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(CELL_GAP),
        ) {
            Intensity.entries.forEach { tier ->
                val shut = tier in locked
                Cell(
                    tier = tier,
                    selected = selected,
                    onTier = if (shut) onLocked else onTier,
                    locked = shut,
                    named = named,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        HeatBar(selected = selected)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = hint(selected),
                style = TeaseTextStyle,
                color = AfterhoursTheme.colors.textMuted,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = selected.label.uppercase(),
                style = EyebrowTextStyle,
                color = AfterhoursTheme.tier.onDark,
                modifier = Modifier.padding(top = spacing.xs),
            )
        }
    }
}

/** What a level means, in the voice of the cards written for it. */
private fun hint(tier: Intensity): String = when (tier) {
    Intensity.MILD -> "Warm. Nothing you'd blush about."
    Intensity.FLIRTY -> "A look that lasts a little too long."
    Intensity.SPICY -> "Now you're blushing."
    Intensity.HOT -> "Close, warm, and in no hurry."
    Intensity.EXTREME -> "The furthest this goes. Both of you, or not at all."
}

@Composable
private fun Cell(
    tier: Intensity,
    selected: Intensity,
    onTier: (Intensity) -> Unit,
    locked: Boolean,
    named: Boolean,
    modifier: Modifier,
) {
    val spacing = AfterhoursTheme.spacing
    val reduceMotion = AfterhoursTheme.reduceMotion
    val colors = AfterhoursTheme.colors
    val isSelected = tier == selected
    // Its own tier's colours, not the screen's: the control shows what each choice would do.
    val own = tierColors(tier)
    val shape = MaterialTheme.shapes.medium
    val standard = decorativeTween<Color>(
        AfterhoursTheme.motion.standard,
        reduceMotion,
        AfterhoursEasing.Standard,
    )

    val fill by animateColorAsState(
        targetValue = if (isSelected) {
            own.accent.copy(alpha = SELECTED_FILL)
        } else {
            colors.textMuted.copy(alpha = IDLE_FILL)
        },
        animationSpec = standard,
        label = "cellFill",
    )
    val edge by animateColorAsState(
        targetValue = if (isSelected) {
            own.rim.copy(alpha = SELECTED_EDGE)
        } else {
            colors.textMuted.copy(alpha = IDLE_EDGE)
        },
        animationSpec = standard,
        label = "cellEdge",
    )
    val name by animateColorAsState(
        targetValue = if (isSelected) own.onDark else colors.textFaint,
        animationSpec = standard,
        label = "cellName",
    )
    val flames = when {
        locked -> LOCKED_FLAMES
        isSelected -> 1f
        else -> IDLE_FLAMES
    }

    Box(
        modifier = modifier
            .heightIn(min = CELL_HEIGHT)
            .then(
                if (isSelected) {
                    Modifier.bloom(
                        color = own.accent,
                        cornerRadius = spacing.md,
                        spread = spacing.md,
                        alpha = CELL_GLOW,
                    )
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .background(fill)
            .border(spacing.hairline, edge, shape)
            .selectable(selected = isSelected, onClick = { onTier(tier) }, role = Role.RadioButton)
            // Named for a screen reader whether or not the word is drawn, because the flames alone
            // are "decoration" to anything that is not looking at them.
            .semantics {
                contentDescription = buildString {
                    append("Level ${tier.ordinal + 1} of ${Intensity.entries.size}, ${tier.label}")
                    if (locked) append(", locked")
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(vertical = spacing.sm + spacing.xxs, horizontal = spacing.xxs),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.xs + spacing.xxs),
        ) {
            HeatFlames(
                count = tier.ordinal + 1,
                color = own.accent,
                height = FLAME,
                flicker = isSelected,
                modifier = Modifier.alpha(flames),
            )
            if (named) {
                Text(
                    text = tier.label.uppercase(),
                    style = EyebrowTextStyle.copy(letterSpacing = NAME_TRACKING, fontSize = NAME_SIZE),
                    color = name,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
        }
        if (locked) {
            Icon(
                imageVector = AfterhoursIcons.Lock,
                contentDescription = null,
                tint = colors.brass,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(spacing.xs + spacing.xxs)
                    .size(LOCK),
            )
        }
    }
}

/**
 * The ramp under the cells: lavender to flame, veiled past tonight, with an ember on tonight.
 *
 * Decoration, so it says nothing to a screen reader; the cells and the label under it say it all.
 */
@Composable
private fun HeatBar(selected: Intensity) {
    val reduceMotion = AfterhoursTheme.reduceMotion
    val level = selected.ordinal + 1
    val count = Intensity.entries.size
    val at by animateFloatAsState(
        targetValue = (level - HALF) / count,
        animationSpec = decorativeTween(AfterhoursTheme.motion.slow, reduceMotion, AfterhoursEasing.Standard),
        label = "heatBar",
    )
    val ramp = Intensity.entries.map { tierColors(it).accent } + AfterhoursTheme.colors.ember
    val veil = MaterialTheme.colorScheme.background.copy(alpha = VEIL)
    val front = AfterhoursTheme.tier.accent
    val spark = AfterhoursTheme.colors.brassBright
    val ring = MaterialTheme.colorScheme.background

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(EMBER)
            .clearAndSetSemantics { },
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val bar = BAR.toPx()
            val top = (size.height - bar) / 2f
            val radius = CornerRadius(bar / 2f)
            drawRoundRect(
                brush = Brush.horizontalGradient(ramp),
                topLeft = Offset(0f, top),
                size = Size(size.width, bar),
                cornerRadius = radius,
                alpha = BAR_ALPHA,
            )
            // Past tonight, veiled: the heat still exists, it is simply not where tonight is.
            val edge = size.width * (at + HALF / count)
            drawRoundRect(
                color = veil,
                topLeft = Offset(edge, top),
                size = Size(size.width - edge, bar),
                cornerRadius = radius,
            )
            val centre = Offset(size.width * at, size.height / 2f)
            val ember = size.height / 2f
            drawCircle(color = front.copy(alpha = GLOW), radius = ember * 2f, center = centre)
            drawCircle(color = ring, radius = ember, center = centre)
            drawCircle(
                brush = Brush.radialGradient(listOf(spark, front), center = centre, radius = ember),
                radius = ember - RING.toPx(),
                center = centre,
            )
        }
    }
}

private const val NAMES_UP_TO = 1.3f
private const val CELL_GLOW = 0.6f
private const val SELECTED_FILL = 0.14f
private const val SELECTED_EDGE = 0.55f
private const val IDLE_FILL = 0.05f
private const val IDLE_EDGE = 0.08f
private const val IDLE_FLAMES = 0.55f
private const val LOCKED_FLAMES = 0.25f
private const val VEIL = 0.78f
private const val BAR_ALPHA = 0.9f
private const val GLOW = 0.35f
private const val HALF = 0.5f
private val CELL_GAP = 6.dp
private val CELL_HEIGHT = 72.dp
private val FLAME = 14.dp
private val NAME_SIZE = 10.sp
private val NAME_TRACKING = 0.6.sp
private val LOCK = 14.dp
private val BAR = 6.dp
private val EMBER = 14.dp
private val RING = 3.dp
