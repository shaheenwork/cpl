package com.shnapps.couple.feature.memories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shnapps.couple.core.designsystem.component.EmptyState
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.icon.AfterhoursIcons
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.model.Memory
import com.shnapps.couple.core.model.MemoryKind
import com.shnapps.couple.core.ui.Entrance
import com.shnapps.couple.core.ui.ErrorBanner
import com.shnapps.couple.core.ui.ScreenHeading
import com.shnapps.couple.core.ui.ScreenScaffold
import com.shnapps.couple.core.ui.SecureScreen

/**
 * Our nights (BUILD_PROMPT.md §14.8): what they kept, newest first, twenty at a time.
 *
 * A memory is their own words and the shape of an evening — moods, how long, how many chapters —
 * and never what was in it, so this is the one screen in the app that may list freely (D-090).
 */
@Composable
fun MemoriesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MemoriesViewModel = hiltViewModel(),
) {
    SecureScreen()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MemoriesContent(
        state = state,
        actions = MemoriesActions(
            onFilter = viewModel::setFilter,
            onFavorite = viewModel::toggleFavorite,
            onForget = viewModel::forget,
            onMore = viewModel::loadMore,
            onDismissError = viewModel::dismissError,
            onBack = onBack,
        ),
        modifier = modifier,
    )
}

/** Everything this screen can do, gathered so the content stays easy to preview. */
internal data class MemoriesActions(
    val onFilter: (MemoryFilter) -> Unit = {},
    val onFavorite: (Memory) -> Unit = {},
    val onForget: (Memory) -> Unit = {},
    val onMore: () -> Unit = {},
    val onDismissError: () -> Unit = {},
    val onBack: () -> Unit = {},
)

@OptIn(ExperimentalLayoutApi::class)
@Suppress("CyclomaticComplexMethod") // One screen: a filter, an empty state, a grid and a dock.
@Composable
internal fun MemoriesContent(
    state: MemoriesUiState,
    actions: MemoriesActions,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(
        modifier = modifier,
        title = "Our memories",
        onBack = actions.onBack,
        dock = if (state.hasMore && state.filter == MemoryFilter.ALL) {
            {
                GlowButton(
                    text = if (state.loadingMore) "Looking…" else "Show more",
                    onClick = actions.onMore,
                    style = GlowButtonStyle.Secondary,
                    enabled = !state.loadingMore,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            null
        },
    ) {
        Entrance(index = 0) {
            ScreenHeading(
                eyebrow = "Yours alone",
                headline = "The ones you kept.",
                body = "Your own words about your own evenings. What was in them stays where it was.",
            )
        }

        Entrance(index = 1) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm)) {
                for (filter in MemoryFilter.entries) {
                    GlowButton(
                        text = filter.label,
                        onClick = { actions.onFilter(filter) },
                        style = if (filter == state.filter) GlowButtonStyle.Primary else GlowButtonStyle.Secondary,
                        leadingIcon = if (filter == MemoryFilter.FAVORITES) {
                            AfterhoursIcons.Heart
                        } else {
                            AfterhoursIcons.Bookmark
                        },
                    )
                }
            }
        }

        state.error?.let { ErrorBanner(message = it.message(), onDismiss = actions.onDismissError) }

        if (state.empty) {
            // Never a screen with nothing on it and nowhere to go (§6, U6): what it will hold,
            // and the one thing that fills it.
            EmptyState(
                title = if (state.filter == MemoryFilter.FAVORITES) "Nothing marked yet." else "Nothing kept yet.",
                body = if (state.filter == MemoryFilter.FAVORITES) {
                    "Mark one with a heart and it shows up here."
                } else {
                    "At the end of an evening, you can keep it here — a title, a note, the " +
                        "moment you want to remember."
                },
                actionLabel = if (state.filter == MemoryFilter.FAVORITES) "See them all" else "Play something",
                onAction = if (state.filter == MemoryFilter.FAVORITES) {
                    { actions.onFilter(MemoryFilter.ALL) }
                } else {
                    actions.onBack
                },
            )
        }

        // Two columns, staggered by content rather than by a fixed height (§6.6). A library only
        // grows, and a single column of identical cards is the shape that makes twelve evenings
        // look like one.
        Entrance(index = 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.item)) {
                listOf(0, 1).forEach { column ->
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.item),
                    ) {
                        state.shown.filterIndexed { index, _ -> index % 2 == column }
                            .forEach { memory -> MemoryCard(memory, actions) }
                    }
                }
            }
        }
    }
}

/**
 * One evening, kept.
 *
 * Its date is the display face, because in a grid the date is what a person scans by; the shape of
 * the evening is moods as dots rather than a line of capitals, which is what let twelve of these
 * fit on a screen at all. A kept one takes the brass bookmark — the one place brass appears in the
 * library, so "we loved this" is visible without reading anything.
 */
@Composable
private fun MemoryCard(memory: Memory, actions: MemoriesActions) {
    val spacing = AfterhoursTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.small)
            .clickable { actions.onFavorite(memory) }
            .semantics { contentDescription = "${memory.title}. ${memory.eyebrow()}" }
            .padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Text(
            text = memory.title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        memory.note?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall, color = AfterhoursTheme.colors.textFaint)
        }
        memory.favoriteMoment?.let {
            Text(
                text = "“$it”",
                style = MaterialTheme.typography.bodySmall,
                color = AfterhoursTheme.colors.textMuted,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            memory.mood.forEach {
                Box(
                    modifier = Modifier
                        .size(MOOD_DOT)
                        .background(AfterhoursTheme.heat.accent, CircleShape),
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (memory.favorite) {
                Icon(
                    imageVector = AfterhoursIcons.Bookmark,
                    contentDescription = "Kept",
                    tint = AfterhoursTheme.colors.brass,
                    modifier = Modifier.size(KEPT),
                )
            }
        }
    }
}

private val MOOD_DOT = 6.dp
private val KEPT = 14.dp

/** The shape of the evening: moods, length, and how much of it they played. */
private fun Memory.eyebrow(): String {
    val moods = mood.joinToString(" · ") { it.label.uppercase() }
    val shape = buildList {
        if (kind == MemoryKind.MYSTERY) add("MYSTERY NIGHT")
        if (moods.isNotEmpty()) add(moods)
        add("$durationMin MIN")
        if (chapterCount > 0) add(if (finished) "ALL $chapterCount" else "$playedThrough OF $chapterCount")
        if (mediaIds.isNotEmpty()) add("${mediaIds.size} ATTACHED")
    }
    return shape.joinToString(" · ")
}

private fun MemoriesError.message(): String = when (this) {
    MemoriesError.OFFLINE -> "You're offline. That one needs the network."
    MemoriesError.OTHER -> "That didn't work. Try again."
}
