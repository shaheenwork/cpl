package com.shnapps.couple.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.shnapps.couple.core.designsystem.theme.AfterhoursEasing
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.decorativeTween
import com.shnapps.couple.core.model.ContentPack

/**
 * Which kind of night this is (D-125).
 *
 * The app ships twelve packs of curated content — Tease, Confessions, Power dynamics, After dark —
 * and until now **nothing in the app could name one of them**. `ContentPack` was parsed out of the
 * bundle and used by exactly one screen, the debug inspector, which has no release twin. So a
 * couple who wanted the bold half of the content had no way to ask for it, and no way to find out
 * it existed: the only dial was the tier, and the heat is a ceiling rather than a subject.
 *
 * It is a scrolling row rather than a grid because it is the second question on the screen, not the
 * first. The heat still decides how far; this decides what kind.
 *
 * Only lanes with something to deal are ever passed in. A lane that would open on an empty screen
 * is the dead end §6 of the rebuild removed everywhere else.
 */
@Composable
fun LanePicker(
    lanes: List<ContentPack>,
    selected: String?,
    onLane: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (lanes.isEmpty()) return
    val spacing = AfterhoursTheme.spacing

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Text(
            text = "WHAT KIND",
            style = MaterialTheme.typography.labelSmall,
            color = AfterhoursTheme.colors.textFaint,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Lane(label = "Anything", isSelected = selected == null, onClick = { onLane(null) })
            lanes.forEach { pack ->
                Lane(
                    label = pack.title,
                    isSelected = pack.id == selected,
                    onClick = { onLane(pack.id) },
                )
            }
        }
    }
}

@Composable
private fun Lane(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val spacing = AfterhoursTheme.spacing
    val reduceMotion = AfterhoursTheme.reduceMotion
    val tier = AfterhoursTheme.tier

    val fill by animateColorAsState(
        targetValue = if (isSelected) tier.container else Color.Transparent,
        animationSpec = decorativeTween(AfterhoursTheme.motion.standard, reduceMotion, AfterhoursEasing.Standard),
        label = "laneFill",
    )
    val text by animateColorAsState(
        targetValue = if (isSelected) tier.onContainer else AfterhoursTheme.colors.textMuted,
        animationSpec = decorativeTween(AfterhoursTheme.motion.standard, reduceMotion, AfterhoursEasing.Standard),
        label = "laneLabel",
    )

    Box(
        modifier = Modifier
            .heightIn(min = spacing.touchTarget)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(fill)
            .border(
                width = HAIRLINE,
                color = if (isSelected) Color.Transparent else AfterhoursTheme.colors.edgeFaint,
                shape = MaterialTheme.shapes.extraSmall,
            )
            .selectable(selected = isSelected, onClick = onClick, role = Role.RadioButton)
            .semantics { contentDescription = label }
            .padding(horizontal = spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = text)
    }
}

private val HAIRLINE = 1.dp
