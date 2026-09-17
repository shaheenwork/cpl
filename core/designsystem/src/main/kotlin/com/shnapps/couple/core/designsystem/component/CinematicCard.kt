package com.shnapps.couple.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme

/**
 * The base surface for everything the product presents as content: a chapter, a prompt, a
 * discovery, a memory.
 *
 * Large, softly rounded and generously padded, per BUILD_PROMPT.md §15.1 — "large
 * cinematic cards" with plenty of negative space. The vertical wash and hairline edge do
 * the work a drop shadow would do on a light theme.
 */
@Composable
fun CinematicCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.large,
    glowing: Boolean = false,
    contentDescription: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AfterhoursTheme.colors
    val spacing = AfterhoursTheme.spacing
    // Bound to a local so the semantics setter below is not shadowed by the parameter.
    val description = contentDescription

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (glowing) Modifier.glow(colors.glow) else Modifier)
            .then(
                if (description != null) {
                    Modifier.semantics { this.contentDescription = description }
                } else {
                    Modifier
                },
            ),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(spacing.hairline, colors.edgeFaint),
    ) {
        Column(
            modifier = Modifier
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    ),
                )
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(spacing.lg),
            content = content,
        )
    }
}
