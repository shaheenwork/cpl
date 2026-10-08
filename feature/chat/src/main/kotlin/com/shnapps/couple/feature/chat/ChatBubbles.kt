package com.shnapps.couple.feature.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shnapps.couple.core.designsystem.icon.AfterhoursIcons
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.model.MediaItem

/**
 * A photo in a bubble.
 *
 * **Every photo is the same size.** The tile is [wide] across and [PHOTO_RATIO] tall whatever the
 * photo's own shape, and the photo is cropped into it; tapping shows the whole thing. Sizing each
 * tile to its own image made a tall photo into a tall bubble and a panorama into a sliver, and
 * turned the column into a ragged mess.
 *
 * Four states, and the difference between two of them is the whole feature: an ordinary photo is
 * fetched as it arrives, a once-only one is not fetched until it is tapped, because fetching it is
 * what deletes it (D-083).
 */
@Composable
internal fun PhotoTile(
    shown: Shown?,
    wide: Dp,
    burnAfterView: Boolean,
    gone: Boolean,
    onLook: () -> Unit,
    onOpen: () -> Unit,
) {
    val tile = Modifier.width(wide).aspectRatio(PHOTO_RATIO)
    when {
        shown is Shown.Photo -> Image(
            painter = BitmapPainter(shown.image),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = tile.tap(onOpen),
        )

        gone -> Plate(tile, AfterhoursIcons.Eye, "Seen. It's gone now.", onClick = null)

        burnAfterView && shown !is Shown.Loading ->
            Plate(tile, AfterhoursIcons.Lock, "Once only. Tap to look.", onClick = onLook)

        shown is Shown.Failed -> Plate(tile, AfterhoursIcons.Eye, "Didn't load. Tap to try again.", onLook)

        else -> Shimmer(tile)
    }
}

/**
 * A voice note: play, waveform, length — at the same width as a photo, for the same reason.
 *
 * The waveform travelled with the record rather than being measured here, so both phones draw the
 * same shape. A note from before there were waveforms draws a flat bar, and still plays. [onPlay]
 * is null for one that is still uploading: there is nothing to play yet.
 */
@Composable
internal fun VoiceStrip(
    levels: List<Int>,
    seconds: Int,
    fraction: Float,
    playing: Boolean,
    mine: Boolean,
    wide: Dp,
    onPlay: (() -> Unit)?,
) {
    val spacing = AfterhoursTheme.spacing
    val ink = if (mine) AfterhoursTheme.tier.onContainer else AfterhoursTheme.tier.onDark
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        modifier = Modifier
            .width(wide)
            .let { if (onPlay == null) it else it.tap(onPlay) }
            .padding(horizontal = spacing.sm, vertical = spacing.xs),
    ) {
        Box(modifier = Modifier.size(spacing.touchTarget), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (playing) AfterhoursIcons.Pause else AfterhoursIcons.Play,
                contentDescription = null,
                tint = ink,
                modifier = Modifier.size(GLYPH),
            )
        }
        Waveform(
            levels = levels,
            fraction = fraction,
            played = ink,
            rest = AfterhoursTheme.colors.textFaint,
            modifier = Modifier.weight(1f).height(WAVE),
        )
        Text(
            text = clock(seconds),
            style = MaterialTheme.typography.bodySmall,
            color = AfterhoursTheme.colors.textFaint,
        )
    }
}

/**
 * The bars.
 *
 * Decorative: the duration beside it carries the meaning, so a screen reader is told nothing here
 * rather than being read forty numbers.
 */
@Composable
internal fun Waveform(
    levels: List<Int>,
    fraction: Float,
    played: Color,
    rest: Color,
    modifier: Modifier = Modifier,
) {
    val bars = levels.ifEmpty { List(MediaItem.WAVEFORM_BARS) { FLAT } }
    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        val step = size.width / bars.size
        // In pixels, so the cap has to be converted: a bare 2f is a hairline on a dense screen
        // and a fat bar on a cheap one.
        val thickness = (step * BAR_SHARE).coerceAtMost(MAX_BAR.dp.toPx())
        bars.forEachIndexed { index, level ->
            val tall = (size.height * (level.coerceIn(0, FULL).toFloat() / FULL)).coerceAtLeast(thickness)
            val x = step * index + step / 2
            drawLine(
                color = if (index.toFloat() / bars.size < fraction) played else rest,
                start = Offset(x, (size.height - tall) / 2),
                end = Offset(x, (size.height + tall) / 2),
                strokeWidth = thickness,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** A covered tile: a once-only photo, one that has gone, or one that would not load. */
@Composable
private fun Plate(tile: Modifier, icon: ImageVector, words: String, onClick: (() -> Unit)?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm),
        modifier = tile
            // Lowest, not highest: surfaceContainerHighest is the same colour as the bubble this
            // sits on, so a plate drawn in it is invisible — which is how the first draft looked.
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .let { if (onClick == null) it else it.tap(onClick) }
            .padding(AfterhoursTheme.spacing.md),
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.BottomCenter) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AfterhoursTheme.colors.brass,
                modifier = Modifier.size(GLYPH),
            )
        }
        Text(
            text = words,
            style = EyebrowTextStyle,
            color = AfterhoursTheme.colors.textMuted,
            modifier = Modifier.weight(1f),
        )
    }
}

/** A photo on its way: the shape it will be, breathing, rather than a spinner on a grey box. */
@Composable
private fun Shimmer(tile: Modifier) {
    val alpha = if (AfterhoursTheme.reduceMotion) {
        DIM
    } else {
        rememberInfiniteTransition(label = "shimmer").animateFloat(
            initialValue = DIM,
            targetValue = BRIGHT,
            animationSpec = infiniteRepeatable(tween(SHIMMER_MS), RepeatMode.Reverse),
            label = "shimmer",
        ).value
    }
    Box(modifier = tile.background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = alpha)))
}

/** A corner nudged in on the side a bubble is on, so a run of them points at whoever said it. */
internal fun bubbleShape(mine: Boolean, first: Boolean): RoundedCornerShape {
    val round = BUBBLE_CORNER.dp
    val tail = TAIL_CORNER.dp
    return when {
        !first -> RoundedCornerShape(round)
        mine -> RoundedCornerShape(topStart = round, topEnd = tail, bottomEnd = round, bottomStart = round)
        else -> RoundedCornerShape(topStart = tail, topEnd = round, bottomEnd = round, bottomStart = round)
    }
}

/** `0:07`. Minutes and seconds, never a bare count of seconds. */
internal fun clock(seconds: Int): String {
    val minutes = seconds / A_MINUTE
    val rest = (seconds % A_MINUTE).toString().padStart(DIGITS, '0')
    return "$minutes:$rest"
}

/**
 * A tap on something inside a bubble.
 *
 * No ripple: what is tapped is a photo or a waveform, and a ripple over either looks like a
 * rendering fault rather than a response.
 */
@Composable
internal fun Modifier.tap(onClick: () -> Unit): Modifier = clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    onClick = onClick,
)

private const val FULL = 100
private const val A_MINUTE = 60
private const val DIGITS = 2
private const val FLAT = 12
private const val BAR_SHARE = 0.45f
private const val MAX_BAR = 2
private const val DIM = 0.35f
private const val BRIGHT = 0.7f
private const val SHIMMER_MS = 900
private const val BUBBLE_CORNER = 16
private const val TAIL_CORNER = 4

private val WAVE = 28.dp
private val GLYPH = 20.dp
