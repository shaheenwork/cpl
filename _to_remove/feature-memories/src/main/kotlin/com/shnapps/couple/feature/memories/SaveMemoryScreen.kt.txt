package com.shnapps.couple.feature.memories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.icon.AfterhoursIcons
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.model.MediaItem
import com.shnapps.couple.core.model.MediaKind
import com.shnapps.couple.core.ui.BackRow
import com.shnapps.couple.core.ui.ErrorBanner
import com.shnapps.couple.core.ui.ScreenColumn
import com.shnapps.couple.core.ui.ScreenHeading
import com.shnapps.couple.core.ui.SecureScreen

/**
 * "Save to Our Memories" (BUILD_PROMPT.md §14.8), at the close of a night.
 *
 * Their words about it, and anything they want to keep with it. The date, the length, the moods
 * and the fact that it was played come from the night itself, on the server (D-090) — this screen
 * never sends them, and never sends a single thing that was in the night.
 */
@Composable
fun SaveMemoryScreen(
    onSaved: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SaveMemoryViewModel = hiltViewModel(),
) {
    SecureScreen()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }
    SaveMemoryContent(
        state = state,
        actions = SaveMemoryActions(
            onTitle = viewModel::setTitle,
            onNote = viewModel::setNote,
            onMoment = viewModel::setMoment,
            onRating = viewModel::setRating,
            onAttach = viewModel::toggleAttachment,
            onSave = viewModel::save,
            onDismissError = viewModel::dismissError,
            onBack = onBack,
        ),
        modifier = modifier,
    )
}

/** Everything this screen can do, gathered so the content stays easy to preview. */
internal data class SaveMemoryActions(
    val onTitle: (String) -> Unit = {},
    val onNote: (String) -> Unit = {},
    val onMoment: (String) -> Unit = {},
    val onRating: (Int) -> Unit = {},
    val onAttach: (MediaItem) -> Unit = {},
    val onSave: () -> Unit = {},
    val onDismissError: () -> Unit = {},
    val onBack: () -> Unit = {},
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SaveMemoryContent(
    state: SaveMemoryUiState,
    actions: SaveMemoryActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        ScreenColumn {
            BackRow(label = "Not now", onBack = actions.onBack)
            ScreenHeading(
                eyebrow = "That's the evening",
                headline = "Keep this one?",
                body = "Your own words, for the two of you. What was in the evening stays in the evening.",
            )
            OutlinedTextField(
                value = state.title,
                onValueChange = actions.onTitle,
                label = { Text("What to call it") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.favoriteMoment,
                onValueChange = actions.onMoment,
                label = { Text("The moment you want to remember") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.note,
                onValueChange = actions.onNote,
                label = { Text("Anything else") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(text = "HOW WAS IT", style = EyebrowTextStyle, color = AfterhoursTheme.colors.textFaint)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm)) {
                for (rating in 1..SaveMemoryUiState.MAX_RATING) {
                    val chosen = state.rating == rating
                    IconButton(
                        onClick = { actions.onRating(rating) },
                        modifier = Modifier
                            .size(AfterhoursTheme.spacing.touchTarget)
                            .semantics { contentDescription = "$rating of ${SaveMemoryUiState.MAX_RATING}" },
                    ) {
                        Icon(
                            imageVector = AfterhoursIcons.Star,
                            contentDescription = null,
                            tint = if (chosen || (state.rating ?: 0) > rating) {
                                AfterhoursTheme.heat.onDark
                            } else {
                                AfterhoursTheme.colors.textFaint
                            },
                        )
                    }
                }
            }
            if (state.media.isNotEmpty()) {
                Text(
                    text = "KEEP SOMETHING WITH IT",
                    style = EyebrowTextStyle,
                    color = AfterhoursTheme.colors.textFaint,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm),
                ) {
                    for (item in state.media) {
                        val attached = item.id in state.attached
                        GlowButton(
                            text = item.label(),
                            onClick = { actions.onAttach(item) },
                            style = if (attached) GlowButtonStyle.Primary else GlowButtonStyle.Secondary,
                            enabled = attached || state.canAttachMore,
                        )
                    }
                }
            }
            state.error?.let { ErrorBanner(message = it.message(), onDismiss = actions.onDismissError) }
            GlowButton(
                text = if (state.saving) "Keeping…" else "Save to Our Memories",
                onClick = actions.onSave,
                enabled = state.canSave,
                leadingIcon = AfterhoursIcons.Heart,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** A voice note or a photo, named by what it is and who sent it — never by what is in it. */
private fun MediaItem.label(): String {
    val what = if (kind == MediaKind.VOICE) "Voice note" else "Photo"
    return if (mine) "$what you sent" else "$what from them"
}

private fun SaveMemoryError.message(): String = when (this) {
    SaveMemoryError.OFFLINE -> "You're offline. That one needs the network."
    SaveMemoryError.NOT_PLAYED -> "There's nothing to keep yet — play an evening first."
    SaveMemoryError.OTHER -> "That didn't save. Try again."
}
