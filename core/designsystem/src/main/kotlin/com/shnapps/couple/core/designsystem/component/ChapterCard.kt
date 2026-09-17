package com.shnapps.couple.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.DisplayFontFamily
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle

/**
 * One chapter of a night — `01 — Warm Up` (BUILD_PROMPT.md §16, §14.6).
 *
 * A night is chapters, never a scrollable list of everything; the numeral in the display
 * serif is what makes it read as a programme rather than a to-do list.
 *
 * Titles of locked chapters are withheld by the caller, not by this component: §17 says
 * the couple may know the *shape* of the night ("Tonight has 7 chapters") but not its
 * contents. Pass [title] as null while a chapter is still sealed.
 */
@Composable
fun ChapterCard(
    index: Int,
    state: ChapterState,
    modifier: Modifier = Modifier,
    title: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = AfterhoursTheme.colors
    val spacing = AfterhoursTheme.spacing

    val numeral = index.toString().padStart(2, '0')
    val shownTitle = title ?: "Still to come"

    val accent = when (state) {
        ChapterState.Locked -> colors.textFaint
        ChapterState.Active -> MaterialTheme.colorScheme.primary
        ChapterState.Complete -> colors.textMuted
    }

    // State is never conveyed by colour alone (§20): there is a word for it too.
    val stateWord = when (state) {
        ChapterState.Locked -> "Locked"
        ChapterState.Active -> "Now"
        ChapterState.Complete -> "Done"
    }

    CinematicCard(
        modifier = modifier.semantics {
            contentDescription = "Chapter $index, $shownTitle, $stateWord"
        },
        onClick = if (state == ChapterState.Locked) null else onClick,
        glowing = state == ChapterState.Active,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = numeral,
                fontFamily = DisplayFontFamily,
                style = MaterialTheme.typography.displaySmall,
                color = accent,
                modifier = Modifier.widthIn(min = 56.dp),
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                Text(
                    text = stateWord.uppercase(),
                    style = EyebrowTextStyle,
                    color = accent,
                )
                Text(
                    text = shownTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (state == ChapterState.Locked) {
                        colors.textFaint
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            if (state == ChapterState.Complete) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = colors.textMuted,
                )
            }
        }
    }
}
