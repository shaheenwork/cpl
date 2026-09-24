package com.shnapps.couple.feature.memories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shnapps.couple.core.designsystem.component.CinematicCard
import com.shnapps.couple.core.designsystem.component.EmptyState
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.model.Memory
import com.shnapps.couple.core.model.MemoryKind
import com.shnapps.couple.core.ui.BackRow
import com.shnapps.couple.core.ui.ErrorBanner
import com.shnapps.couple.core.ui.ScreenColumn
import com.shnapps.couple.core.ui.ScreenHeading
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
@Composable
internal fun MemoriesContent(
    state: MemoriesUiState,
    actions: MemoriesActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        ScreenColumn {
            BackRow(label = "Back", onBack = actions.onBack)
            ScreenHeading(
                eyebrow = "Our memories",
                headline = "The ones you kept.",
                body = "Your own words about your own nights. What was in them stays where it was.",
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm)) {
                for (filter in MemoryFilter.entries) {
                    GlowButton(
                        text = filter.label,
                        onClick = { actions.onFilter(filter) },
                        style = if (filter == state.filter) GlowButtonStyle.Primary else GlowButtonStyle.Secondary,
                        leadingEmoji = if (filter == MemoryFilter.FAVORITES) "❤️" else "📖",
                    )
                }
            }
            state.error?.let { ErrorBanner(message = it.message(), onDismiss = actions.onDismissError) }
            if (state.empty) {
                EmptyState(
                    title = if (state.filter == MemoryFilter.FAVORITES) "Nothing marked yet." else "Nothing kept yet.",
                    body = "At the end of a night, you can keep it here — a title, a note, the " +
                        "moment you want to remember.",
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm)) {
                for (memory in state.shown) {
                    MemoryCard(memory, actions)
                }
            }
            if (state.hasMore && state.filter == MemoryFilter.ALL) {
                GlowButton(
                    text = if (state.loadingMore) "Looking…" else "Show more",
                    onClick = actions.onMore,
                    style = GlowButtonStyle.Quiet,
                    enabled = !state.loadingMore,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun MemoryCard(memory: Memory, actions: MemoriesActions) {
    CinematicCard(modifier = Modifier.fillMaxWidth(), glowing = memory.favorite) {
        Text(text = memory.eyebrow(), style = EyebrowTextStyle, color = AfterhoursTheme.colors.brass)
        Text(
            text = memory.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        memory.note?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium, color = AfterhoursTheme.colors.textMuted)
        }
        memory.favoriteMoment?.let {
            Text(
                text = "“$it”",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlowButton(
                text = if (memory.favorite) "Loved" else "Love it",
                onClick = { actions.onFavorite(memory) },
                style = if (memory.favorite) GlowButtonStyle.Primary else GlowButtonStyle.Secondary,
                leadingEmoji = "❤️",
            )
            GlowButton(
                text = "Forget it",
                onClick = { actions.onForget(memory) },
                style = GlowButtonStyle.Quiet,
            )
        }
    }
}

/** The shape of the evening: moods, length, and how much of it they played. */
private fun Memory.eyebrow(): String {
    val moods = mood.joinToString(" · ") { it.label.uppercase() }
    val shape = buildList {
        if (kind == MemoryKind.MYSTERY) add("MYSTERY NIGHT")
        if (moods.isNotEmpty()) add(moods)
        add("$durationMin MIN")
        if (chapterCount > 0) add(if (finished) "ALL $chapterCount" else "$playedThrough OF $chapterCount")
        if (mediaIds.isNotEmpty()) add("📎 ${mediaIds.size}")
    }
    return shape.joinToString(" · ")
}

private fun MemoriesError.message(): String = when (this) {
    MemoriesError.OFFLINE -> "You're offline. That one needs the network."
    MemoriesError.OTHER -> "That didn't work. Try again."
}
