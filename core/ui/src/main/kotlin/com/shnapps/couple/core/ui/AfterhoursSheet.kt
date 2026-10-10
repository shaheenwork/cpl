package com.shnapps.couple.core.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme

/**
 * A sheet that rises over a screen: the heat, the lanes, the limits, "too far".
 *
 * Drawn in the screen's own window rather than as a dialog, for two reasons. It stays inside
 * `SecureScreen`'s protection without a second window to protect, and a screenshot test can see it.
 * Back closes it, and so does a tap on the dimmed screen above — both are "not now", never a
 * choice.
 *
 * Call it last inside a [Box] that fills the screen, so it lies over everything else.
 */
@Composable
fun BoxScope.AfterhoursSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val motion = AfterhoursTheme.motion
    val spacing = AfterhoursTheme.spacing
    val reduce = AfterhoursTheme.reduceMotion
    if (visible) BackHandler(onBack = onDismiss)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(if (reduce) 0 else motion.standard)),
        exit = fadeOut(tween(if (reduce) 0 else motion.standard)),
        modifier = Modifier.matchParentSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = "Close",
                    onClick = onDismiss,
                ),
        )
    }
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(tween(if (reduce) 0 else motion.slow)) { it },
        exit = slideOutVertically(tween(if (reduce) 0 else motion.standard)) { it },
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        val shape = RoundedCornerShape(topStart = spacing.lg, topEnd = spacing.lg)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = MAX_HEIGHT)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(width = spacing.hairline, color = AfterhoursTheme.colors.rimTop, shape = shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .semantics { paneTitle = title },
        ) {
            Box(
                modifier = Modifier
                    .padding(top = spacing.sm, bottom = spacing.xs)
                    .align(Alignment.CenterHorizontally)
                    .width(spacing.xl)
                    .height(spacing.xs)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(AfterhoursTheme.colors.edgeFaint)
                    .clearAndSetSemantics { },
            )
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = spacing.gutter)
                    .padding(top = spacing.sm, bottom = spacing.lg),
                verticalArrangement = Arrangement.spacedBy(spacing.group),
                content = content,
            )
        }
    }
}

private const val SCRIM = 0.62f
private val MAX_HEIGHT = 640.dp
