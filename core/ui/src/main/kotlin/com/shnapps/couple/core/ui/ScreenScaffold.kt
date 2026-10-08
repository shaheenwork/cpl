package com.shnapps.couple.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shnapps.couple.core.designsystem.component.LocalBarExtra
import com.shnapps.couple.core.designsystem.icon.AfterhoursIcons
import com.shnapps.couple.core.designsystem.theme.AfterhoursEasing
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.designsystem.theme.decorativeTween

/**
 * The frame every screen is drawn in (DESIGN_PROMPT.md §5.5).
 *
 * Two things it fixes, and both are structural rather than decorative:
 *
 *  1. **Nothing anchored the top or the bottom.** Content began 32 dp down and ended wherever
 *     it ended. The card player's actions — the only things a thumb needs — sat in the middle
 *     of the screen with 40% of it empty below them.
 *  2. **A primary action scrolled away.** Anything at the end of a long column is unreachable
 *     without scrolling to it first, which is the wrong order: a person decides, then reaches.
 *
 * So: a 56 dp bar over the scene, content that scrolls *under* a docked action, and a scrim
 * between them so the one does not collide with the other. [dock] is the screen's one primary
 * action and whatever sits beside it; everything else scrolls.
 *
 * The bar's hairline appears only once something has scrolled under it — a divider drawn over
 * empty space is a line for its own sake.
 */
@Composable
fun ScreenScaffold(
    modifier: Modifier = Modifier,
    title: String? = null,
    onBack: (() -> Unit)? = null,
    backLabel: String = "Back",
    trailing: (@Composable () -> Unit)? = null,
    belowBar: (@Composable () -> Unit)? = null,
    dock: (@Composable ColumnScope.() -> Unit)? = null,
    /**
     * Centres the content in whatever height is left over.
     *
     * For a screen that is one object — a card, a moment, a question — rather than a list.
     * Without it the card sits at the top and the rest of the screen is the void this scaffold
     * exists to remove.
     */
    centred: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = AfterhoursTheme.spacing
    val scroll = rememberScrollState()
    val scrolled by remember { derivedStateOf { scroll.value > 0 } }
    val hairline by animateFloatAsState(
        targetValue = if (scrolled) 1f else 0f,
        animationSpec = decorativeTween(
            AfterhoursTheme.motion.standard,
            AfterhoursTheme.reduceMotion,
            AfterhoursEasing.Standard,
        ),
        label = "barHairline",
    )

    Column(modifier = modifier.fillMaxSize()) {
        TopBar(title = title, onBack = onBack, backLabel = backLabel, trailing = trailing)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(spacing.hairline)
                .alpha(hairline)
                .background(AfterhoursTheme.colors.rimTop),
        )
        belowBar?.invoke()

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(horizontal = spacing.gutter)
                .padding(top = spacing.screenTop, bottom = spacing.lg),
            verticalArrangement = if (centred) {
                Arrangement.spacedBy(spacing.group, Alignment.CenterVertically)
            } else {
                Arrangement.spacedBy(spacing.group)
            },
            content = content,
        )

        if (dock != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // The scene fades up behind the dock so content passes under it rather
                    // than stopping at a hard edge.
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.background.copy(alpha = 0f),
                                MaterialTheme.colorScheme.background,
                            ),
                        ),
                    )
                    .padding(horizontal = spacing.gutter)
                    .padding(top = spacing.lg, bottom = spacing.dock),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
                content = dock,
            )
        }
    }
}

@Composable
private fun TopBar(
    title: String?,
    onBack: (() -> Unit)?,
    backLabel: String,
    trailing: (@Composable () -> Unit)?,
) {
    val spacing = AfterhoursTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(spacing.bar)
            .padding(horizontal = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(spacing.touchTarget)) {
                Icon(
                    imageVector = AfterhoursIcons.Back,
                    contentDescription = backLabel,
                    tint = AfterhoursTheme.colors.textMuted,
                )
            }
        } else {
            Box(modifier = Modifier.size(spacing.touchTarget))
        }

        Text(
            text = title?.uppercase().orEmpty(),
            style = EyebrowTextStyle,
            color = AfterhoursTheme.colors.textFaint,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )

        // The chat, when there is one, sits beside the screen's own control rather than on top
        // of it. The bar decides where; the layer only decides what (LocalBarExtra).
        val extra = LocalBarExtra.current
        extra?.invoke()
        when {
            trailing != null -> trailing()
            // With an extra and no trailing control, the extra is the trailing control: a blank
            // 48 dp box after it would push it off the edge it belongs on.
            extra == null -> Box(modifier = Modifier.size(spacing.touchTarget))
        }
    }
}

/**
 * The wordmark, for the one screen that has no title above it (§7).
 *
 * Not a logo, and it does not need to be: the name set in the body face at the eyebrow's own
 * tracking says whose app this is without asking for a graphic.
 */
@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Text(
        text = "AFTERHOURS",
        style = EyebrowTextStyle.copy(letterSpacing = WORDMARK_TRACKING),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

/**
 * A control in the bar: an icon and a word, for something that is not the screen's business.
 *
 * "Too far" lives here rather than in the dock. It is a safety control and it must always be
 * reachable, but as a third filled button beside Done it competed with the thing a couple is
 * actually doing — and a safety control that looks like a choice is a worse safety control.
 */
@Composable
fun BarAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = AfterhoursTheme.spacing
    Row(
        modifier = modifier
            // A safety control is small on purpose and must still be a full target (§20).
            .heightIn(min = spacing.touchTarget)
            .widthIn(min = spacing.touchTarget)
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AfterhoursTheme.colors.textFaint,
            modifier = Modifier.size(BAR_ICON),
        )
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = AfterhoursTheme.colors.textFaint)
    }
}

private val BAR_ICON = 16.dp
private val WORDMARK_TRACKING = 3.2.sp
