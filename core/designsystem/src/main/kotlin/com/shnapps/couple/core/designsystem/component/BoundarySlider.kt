package com.shnapps.couple.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.model.BoundaryLevel

/**
 * Setting a boundary for one theme (BUILD_PROMPT.md §13, §3.2).
 *
 * Presented as explicit, labelled choices rather than as a continuous slider. A dragged
 * handle invites accidental change, and `NEVER` is the most consequential setting in the
 * product — it removes content for **both** partners and nothing can override it, so it
 * should take a deliberate tap and read unmistakably.
 *
 * Each level carries its own plain-language consequence, because "Ask first" and "Not
 * tonight" are not self-explanatory and guessing wrong here is exactly what the boundary
 * engine exists to prevent.
 *
 * [selected] is null for a theme with no boundary yet: nothing is pre-chosen, so a boundary
 * is only ever set by a deliberate tap.
 */
@Composable
fun BoundarySlider(
    selected: BoundaryLevel?,
    onSelect: (BoundaryLevel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AfterhoursTheme.colors
    val spacing = AfterhoursTheme.spacing

    Column(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        BoundaryLevel.entries.forEach { level ->
            val isSelected = level == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { onSelect(level) },
                    )
                    .heightIn(min = spacing.touchTarget)
                    .padding(vertical = spacing.xs)
                    // One announcement per option: the full sentence, not the sentence and
                    // then each line of text again.
                    .clearAndSetSemantics {
                        contentDescription = "${level.label}. ${level.consequence}"
                    },
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = isSelected, onClick = null)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = level.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (level.isHardExclusion) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Text(
                        text = level.consequence,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                }
            }
        }
    }
}

/** How a boundary level is named on screen. */
val BoundaryLevel.label: String
    get() = when (this) {
        BoundaryLevel.ALWAYS_OK -> "Always OK"
        BoundaryLevel.CURIOUS -> "Curious"
        BoundaryLevel.ASK_FIRST -> "Ask first"
        BoundaryLevel.NOT_TONIGHT -> "Not tonight"
        BoundaryLevel.NEVER -> "Never"
    }

/** Plain language for what each level actually does, so nobody has to guess. */
private val BoundaryLevel.consequence: String
    get() = when (this) {
        BoundaryLevel.ALWAYS_OK -> "Can show up any time."
        BoundaryLevel.CURIOUS -> "Can show up, and we will lean into it."
        BoundaryLevel.ASK_FIRST -> "We will check with you in the moment first."
        BoundaryLevel.NOT_TONIGHT -> "Paused for now. You can change this any time."
        BoundaryLevel.NEVER -> "Removed completely, for both of you. Nothing overrides this."
    }
