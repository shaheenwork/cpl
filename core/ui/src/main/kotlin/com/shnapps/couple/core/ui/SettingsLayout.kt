package com.shnapps.couple.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shnapps.couple.core.designsystem.component.bloom
import com.shnapps.couple.core.designsystem.component.rim
import com.shnapps.couple.core.designsystem.icon.AfterhoursIcons
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle

/**
 * A titled group of rows: an eyebrow, then one surface with a hairline between the rows (D-146).
 *
 * Settings used to be text — a heading, a line, a heading, a line — which is what a list is when
 * nothing separates what is a control from what is a caption. A group is one object: the rows
 * inside it are *its* rows, and the gap between groups is the only separation they need.
 *
 * The hairlines are the container's own colour showing through a one-pixel gap between rows, so no
 * row has to know whether it is the last one, and a group of one has none.
 */
@Composable
fun SettingsGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = AfterhoursTheme.spacing
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        title?.let {
            Text(
                text = it.uppercase(),
                style = EyebrowTextStyle,
                color = AfterhoursTheme.colors.textFaint,
                modifier = Modifier
                    .padding(start = spacing.xs)
                    .semantics { heading() },
            )
        }
        val shape = MaterialTheme.shapes.large
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(AfterhoursTheme.colors.edgeFaint)
                .rim(shape),
            verticalArrangement = Arrangement.spacedBy(spacing.hairline),
            content = content,
        )
    }
}

/**
 * One row of a [SettingsGroup]: an icon in a tile, what it is, and what it does or says (D-146).
 *
 * With [onClick] the whole row is one target, 64 dp tall and as wide as the group, and ends in a
 * chevron; without it, the row is a fact. [value] is a short word at the end — "On", "Off" — for a
 * row whose state is worth knowing before opening it.
 */
@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    value: String? = null,
    tone: SettingsTone = SettingsTone.Normal,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val base = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interaction,
            indication = null,
            role = Role.Button,
            onClick = onClick,
        )
    } else {
        Modifier
    }
    RowFrame(
        icon = icon,
        title = title,
        detail = detail,
        tone = tone,
        pressed = pressed,
        modifier = modifier.then(base),
        description = listOfNotNull(title, detail, value).joinToString(". "),
    ) {
        value?.let {
            Text(
                text = it.uppercase(),
                style = EyebrowTextStyle,
                color = if (tone == SettingsTone.Danger) {
                    MaterialTheme.colorScheme.error
                } else {
                    AfterhoursTheme.tier.onDark
                },
            )
        }
        trailing?.invoke()
        if (onClick != null && trailing == null) {
            Icon(
                imageVector = AfterhoursIcons.Forward,
                contentDescription = null,
                tint = AfterhoursTheme.colors.textFaint,
                modifier = Modifier.size(CHEVRON),
            )
        }
    }
}

/** A row that is a switch: the whole row toggles it, and a screen reader hears one switch. */
@Composable
fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    val tier = AfterhoursTheme.tier
    RowFrame(
        icon = icon,
        title = title,
        detail = detail,
        tone = SettingsTone.Normal,
        pressed = false,
        modifier = modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        description = listOfNotNull(title, detail).joinToString(". "),
    ) {
        // The row is the control; the switch only draws its state, so it takes no tap of its own.
        Switch(
            checked = checked,
            onCheckedChange = null,
            modifier = Modifier.clearAndSetSemantics { },
            colors = SwitchDefaults.colors(
                checkedTrackColor = tier.accent,
                checkedThumbColor = tier.onAccent,
                checkedBorderColor = tier.rim,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                uncheckedThumbColor = AfterhoursTheme.colors.textMuted,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}

@Composable
private fun RowFrame(
    icon: ImageVector,
    title: String,
    detail: String?,
    tone: SettingsTone,
    pressed: Boolean,
    description: String,
    modifier: Modifier,
    end: @Composable () -> Unit,
) {
    val spacing = AfterhoursTheme.spacing
    val ground = MaterialTheme.colorScheme.surfaceContainer
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (pressed) MaterialTheme.colorScheme.surfaceContainerHigh else ground)
            .heightIn(min = ROW_HEIGHT)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = spacing.md, vertical = spacing.item),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon = icon, tone = tone)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (tone == SettingsTone.Danger) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            detail?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AfterhoursTheme.colors.textMuted,
                    overflow = TextOverflow.Visible,
                )
            }
        }
        end()
    }
}

/** The tile an icon sits in: a gold-tinted square for most things, the safety colour for one. */
@Composable
fun IconTile(icon: ImageVector, modifier: Modifier = Modifier, tone: SettingsTone = SettingsTone.Normal) {
    val colors = AfterhoursTheme.colors
    val danger = tone == SettingsTone.Danger
    Box(
        modifier = modifier
            .size(TILE)
            .clip(MaterialTheme.shapes.small)
            .background(if (danger) MaterialTheme.colorScheme.errorContainer else colors.brassContainer)
            .border(
                width = AfterhoursTheme.spacing.hairline,
                color = (if (danger) MaterialTheme.colorScheme.error else colors.brass).copy(alpha = TILE_EDGE),
                shape = MaterialTheme.shapes.small,
            )
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (danger) MaterialTheme.colorScheme.error else colors.brass,
            modifier = Modifier.size(TILE_ICON),
        )
    }
}

/**
 * The one card at the top of a settings screen that says how things stand (D-146).
 *
 * A larger tile, a title and a sentence, and whatever the state asks of them below — a button, or
 * nothing. [lit] gives it the tier's bloom for the state that is *working*: a pair, a lock that is
 * on. Off is quiet, because nothing is happening.
 */
@Composable
fun StatusCard(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    lit: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val spacing = AfterhoursTheme.spacing
    val colors = AfterhoursTheme.colors
    val shape = MaterialTheme.shapes.extraLarge
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (lit) {
                    Modifier.bloom(
                        color = AfterhoursTheme.tier.rim,
                        cornerRadius = spacing.lg,
                        spread = spacing.lg,
                        alpha = CARD_GLOW,
                    )
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .rim(shape, top = if (lit) AfterhoursTheme.tier.rim.copy(alpha = LIT_RIM) else colors.rimTop)
            .padding(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(HERO_TILE)
                    .clip(MaterialTheme.shapes.medium)
                    .background(if (lit) AfterhoursTheme.tier.container else colors.brassContainer)
                    .border(
                        width = spacing.hairline,
                        color = (if (lit) AfterhoursTheme.tier.rim else colors.brass).copy(alpha = TILE_EDGE),
                        shape = MaterialTheme.shapes.medium,
                    )
                    .clearAndSetSemantics { },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (lit) AfterhoursTheme.tier.onDark else colors.brass,
                    modifier = Modifier.size(HERO_ICON),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(text = body, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
            }
        }
        content()
    }
}

private val ROW_HEIGHT = 64.dp
private val TILE = 40.dp
private val TILE_ICON = 20.dp
private val HERO_TILE = 52.dp
private val HERO_ICON = 26.dp
private val CHEVRON = 16.dp
private const val TILE_EDGE = 0.35f
private const val CARD_GLOW = 0.5f
private const val LIT_RIM = 0.5f
