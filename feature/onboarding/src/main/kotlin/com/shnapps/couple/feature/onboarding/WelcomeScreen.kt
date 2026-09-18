package com.shnapps.couple.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.designsystem.theme.decorativeTween
import com.shnapps.couple.core.ui.SecureScreen

/**
 * The welcome sequence (§14.1).
 *
 * Paged rather than scrolled, because each beat is a promise and they land better one at a
 * time. Skippable throughout — nobody should have to read marketing copy to reach their
 * own app.
 */
@Composable
fun WelcomeScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SecureScreen()

    var index by remember { mutableIntStateOf(0) }
    val spacing = AfterhoursTheme.spacing
    // transitionSpec below is not a @Composable scope, so these are read here.
    val crossfadeMillis = AfterhoursTheme.motion.slow
    val reduceMotion = AfterhoursTheme.reduceMotion
    val beat = welcomeBeats[index]
    val isLast = index == welcomeBeats.lastIndex

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = spacing.gutter, vertical = spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            if (!isLast) {
                GlowButton(text = "Skip", onClick = onFinished, style = GlowButtonStyle.Quiet)
            }
        }

        Spacer(Modifier.weight(1f))

        AnimatedContent(
            targetState = beat,
            transitionSpec = {
                val spec = decorativeTween<Float>(
                    durationMillis = crossfadeMillis,
                    reduceMotion = reduceMotion,
                )
                // A slow cross-dissolve, never a snap (§15.2).
                fadeIn(spec) togetherWith fadeOut(spec)
            },
            label = "welcomeBeat",
        ) { current ->
            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                Text(
                    text = current.eyebrow,
                    style = EyebrowTextStyle,
                    color = AfterhoursTheme.colors.brass,
                )
                Text(
                    text = current.headline,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = current.body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = AfterhoursTheme.colors.textMuted,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        PageIndicator(
            count = welcomeBeats.size,
            selected = index,
            modifier = Modifier.fillMaxWidth(),
        )

        GlowButton(
            text = if (isLast) "Let's go" else "Next",
            onClick = { if (isLast) onFinished() else index++ },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PageIndicator(count: Int, selected: Int, modifier: Modifier = Modifier) {
    val spacing = AfterhoursTheme.spacing
    Row(
        modifier = modifier
            .height(spacing.md)
            .semantics { contentDescription = "Step ${selected + 1} of $count" },
        horizontalArrangement = Arrangement.spacedBy(spacing.sm, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { position ->
            Surface(
                modifier = Modifier.size(if (position == selected) 8.dp else 6.dp),
                shape = CircleShape,
                // Inactive dots use `outline`, not `edgeFaint`: they convey position, so WCAG
                // 1.4.11 wants 3:1 against the ground. edgeFaint fell well short of that and
                // was nearly invisible in the screenshot.
                color = if (position == selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
            ) {}
        }
    }
}
