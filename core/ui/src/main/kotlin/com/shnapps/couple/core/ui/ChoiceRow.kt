package com.shnapps.couple.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.shnapps.couple.core.designsystem.icon.AfterhoursIcons
import com.shnapps.couple.core.designsystem.theme.AfterhoursEasing
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.designsystem.theme.decorativeTween

/**
 * One thing you can choose (DESIGN_PROMPT.md §5.3).
 *
 * A row, not a card. It was drawn as a card, and ten of them in a column read as ten separate
 * objects rather than as a list — which is what happens when everything on a screen carries the
 * same fill, the same radius and the same border. Separation here comes from the gap between
 * rows and nothing else: no rim, no shadow.
 *
 * A game on home, a shape of an evening inside Start, the library, apart, a settings row: all of
 * them are this. The whole row is one target, so a screen reader hears one thing and a thumb has
 * the full width.
 *
 * [locked] greys it and replaces the trailing chevron with a lock. A row that needs an account
 * still shows, and still says what it would do — a person cannot want something they have never
 * been shown.
 */
@Composable
fun ChoiceRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: String? = null,
    icon: ImageVector? = null,
    locked: Boolean = false,
) {
    val spacing = AfterhoursTheme.spacing
    val colors = AfterhoursTheme.colors
    // The same press as every button in the app, so a row and a button feel like one language.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) PRESSED_SCALE else 1f,
        animationSpec = decorativeTween(
            AfterhoursTheme.motion.quick,
            AfterhoursTheme.reduceMotion,
            AfterhoursEasing.Standard,
        ),
        label = "rowPress",
    )
    val words = listOfNotNull(
        title,
        subtitle,
        trailing?.takeIf { !locked },
        NEEDS_ACCOUNT.takeIf { locked },
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = spacing.touchTarget)
            .scale(press)
            .background(
                color = if (pressed) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = PRESSED_FILL)
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
                shape = MaterialTheme.shapes.small,
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics { contentDescription = words.joinToString(". ") }
            .padding(horizontal = spacing.md, vertical = spacing.item),
        horizontalArrangement = Arrangement.spacedBy(spacing.item),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (locked) colors.textFaint else colors.textMuted,
                modifier = Modifier.size(LEAD),
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (locked) colors.textMuted else MaterialTheme.colorScheme.onSurface,
            )
            subtitle?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
            }
        }

        if (locked) {
            Icon(
                imageVector = AfterhoursIcons.Lock,
                contentDescription = null,
                tint = colors.textFaint,
                modifier = Modifier.size(GO),
            )
        } else {
            trailing?.let {
                Text(text = it.uppercase(), style = EyebrowTextStyle, color = AfterhoursTheme.heat.onDark)
            }
            Icon(
                imageVector = AfterhoursIcons.Forward,
                contentDescription = null,
                tint = colors.textFaint,
                modifier = Modifier.size(GO),
            )
        }
    }
}

private val LEAD = 20.dp
private val GO = 16.dp

/** The app's one press language: a scale, and a brighter fill. Never a bigger halo. */
private const val PRESSED_SCALE = 0.985f
private const val PRESSED_FILL = 0.05f

/** What a locked row says it needs, for anyone listening rather than looking. */
private const val NEEDS_ACCOUNT = "Needs an account"
