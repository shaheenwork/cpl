package com.shnapps.couple.feature.preferences

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.shnapps.couple.core.designsystem.component.label
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle

/**
 * Everything the user has answered, grouped by category, each one open to change.
 *
 * Only ever the user's own answers, and only ever on their own phone: this list is the
 * product keeping its promise visibly (§5.1).
 */
@Composable
internal fun ReviewStep(
    state: PreferenceDiscoveryUiState,
    onEdit: (String) -> Unit,
    onBack: () -> Unit,
) {
    val spacing = AfterhoursTheme.spacing
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = spacing.gutter, vertical = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                BackRow(label = "Back", onBack = onBack)
                Heading(
                    eyebrow = "Only you can see this",
                    headline = "Your answers",
                    body = "Tap any of them to change it, or to take it back.",
                )
            }
        }

        state.review.forEach { section ->
            item(key = "section-${section.categoryTitle}") {
                Text(
                    text = section.categoryTitle.uppercase(),
                    style = EyebrowTextStyle,
                    color = AfterhoursTheme.colors.textFaint,
                    modifier = Modifier.padding(top = spacing.md, bottom = spacing.xs),
                )
            }
            items(section.entries, key = { it.item.id }) { entry ->
                ReviewRow(entry = entry, onClick = { onEdit(entry.item.id) })
            }
        }
    }
}

@Composable
private fun ReviewRow(entry: ReviewEntry, onClick: () -> Unit) {
    val spacing = AfterhoursTheme.spacing
    val colors = AfterhoursTheme.colors
    val answer = entry.answer.label
    val shape = MaterialTheme.shapes.medium
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = spacing.touchTarget)
            .clip(shape)
            .clickable(onClickLabel = "Change", role = Role.Button, onClick = onClick)
            // One announcement for the row, in words: the emoji and the colour stay visual.
            .clearAndSetSemantics { contentDescription = "${entry.item.prompt}. Your answer: $answer." },
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = entry.item.prompt,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (entry.answer.secret) "👀 $answer" else answer,
                style = MaterialTheme.typography.labelLarge,
                color = if (entry.answer.value.isPositive) colors.brass else colors.textMuted,
            )
        }
    }
}
