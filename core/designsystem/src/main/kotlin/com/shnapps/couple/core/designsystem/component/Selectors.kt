package com.shnapps.couple.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.model.Mood

/**
 * Mood selection for Build Our Night (BUILD_PROMPT.md §14.5).
 *
 * Multi-select: a night can be romantic *and* mysterious, and the engine weights against
 * the whole set rather than a single choice.
 *
 * The emoji is decorative and is stripped from the accessibility tree — the label already
 * says "Naughty", and TalkBack reading "smirking face" on top of it is noise (§20).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoodChipRow(
    selected: Set<Mood>,
    onToggle: (Mood) -> Unit,
    modifier: Modifier = Modifier,
    moods: List<Mood> = Mood.entries,
) {
    val spacing = AfterhoursTheme.spacing
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        moods.forEach { mood ->
            val isSelected = mood in selected
            FilterChip(
                selected = isSelected,
                onClick = { onToggle(mood) },
                modifier = Modifier
                    .heightIn(min = spacing.touchTarget)
                    .semantics { contentDescription = mood.label },
                label = {
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Text(
                            text = mood.emoji,
                            modifier = Modifier.clearAndSetSemantics { },
                        )
                        Text(text = mood.label, style = MaterialTheme.typography.labelLarge)
                    }
                },
                shape = MaterialTheme.shapes.small,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

/**
 * How long we have tonight (§14.5). The engine turns this into a chapter count (§10.4)
 * and holds the assembled night to within ±20% of it (§10.5).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DurationPicker(
    selectedMinutes: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    options: List<Int> = listOf(15, 30, 45, 60, 90),
) {
    val spacing = AfterhoursTheme.spacing
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        options.forEach { minutes ->
            val isLongest = minutes == options.max()
            val label = if (isLongest) "$minutes min+" else "$minutes min"
            FilterChip(
                selected = minutes == selectedMinutes,
                onClick = { onSelect(minutes) },
                modifier = Modifier
                    .heightIn(min = spacing.touchTarget)
                    .semantics {
                        contentDescription = if (isLongest) {
                            "$minutes minutes or more"
                        } else {
                            "$minutes minutes"
                        }
                    },
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = spacing.xxs),
                    )
                },
                shape = MaterialTheme.shapes.small,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}
