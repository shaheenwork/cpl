package com.shnapps.couple.feature.boundaries

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shnapps.couple.core.designsystem.component.BoundarySlider
import com.shnapps.couple.core.designsystem.component.CinematicCard
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.component.IntensityDial
import com.shnapps.couple.core.designsystem.component.label
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.designsystem.theme.decorativeTween
import com.shnapps.couple.core.model.Boundary
import com.shnapps.couple.core.model.BoundaryLevel
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.ui.BackRow
import com.shnapps.couple.core.ui.ErrorBanner
import com.shnapps.couple.core.ui.ScreenColumn
import com.shnapps.couple.core.ui.ScreenHeading
import com.shnapps.couple.core.ui.SecureScreen

/**
 * The user's private boundaries (BUILD_PROMPT.md §3.2, §14.9).
 *
 * A `SecureScreen`: this is the most sensitive list in the product — what someone has ruled
 * out — so no screenshots and no app-switcher thumbnail (§3.4).
 */
@Composable
fun BoundariesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BoundariesViewModel = hiltViewModel(),
) {
    SecureScreen()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(enabled = state.handlesBack, onBack = viewModel::back)

    BoundariesContent(
        state = state,
        onLevelChange = viewModel::setContentLevel,
        onEdit = viewModel::edit,
        onSelectLevel = viewModel::selectLevel,
        onNoteChange = viewModel::onNoteChange,
        onSaveNote = viewModel::saveNote,
        onClear = viewModel::clearBoundary,
        onCloseEditor = viewModel::back,
        onDismissError = viewModel::dismissError,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun BoundariesContent(
    state: BoundariesUiState,
    onLevelChange: (Intensity) -> Unit,
    onEdit: (String) -> Unit,
    onSelectLevel: (BoundaryLevel) -> Unit,
    onNoteChange: (String) -> Unit,
    onSaveNote: () -> Unit,
    onClear: () -> Unit,
    onCloseEditor: () -> Unit,
    onDismissError: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = AfterhoursTheme.motion
    val reduceMotion = AfterhoursTheme.reduceMotion

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = state,
            contentKey = { it.stage },
            transitionSpec = {
                fadeIn(decorativeTween(motion.standard, reduceMotion)) togetherWith
                    fadeOut(decorativeTween(motion.quick, reduceMotion))
            },
            label = "boundariesStage",
        ) { shown ->
            when (shown.stage) {
                BoundariesStage.Loading -> Unit
                BoundariesStage.List -> BoundaryList(shown, onLevelChange, onEdit, onBack)
                BoundariesStage.Editing -> shown.editor?.let { editor ->
                    BoundaryEditorStep(editor, onSelectLevel, onNoteChange, onSaveNote, onClear, onCloseEditor)
                }
            }
        }

        if (state.errorMessage != null) {
            ErrorBanner(
                message = state.errorMessage,
                onDismiss = onDismissError,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun BoundaryList(
    state: BoundariesUiState,
    onLevelChange: (Intensity) -> Unit,
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
                ScreenHeading(
                    eyebrow = "Only you",
                    headline = "Your limits",
                    body = "Your partner never sees these. Anything either of you rules out is quietly " +
                        "left out for you both, and nobody is told why.",
                )
                ContentLevelCard(state.contentLevel, onLevelChange)
            }
        }

        state.sections.forEach { section ->
            item(key = "section-${section.categoryTitle}") {
                Text(
                    text = section.categoryTitle.uppercase(),
                    style = EyebrowTextStyle,
                    color = AfterhoursTheme.colors.textFaint,
                    modifier = Modifier.padding(top = spacing.md, bottom = spacing.xs),
                )
            }
            items(section.themes, key = { it.themeId }) { row ->
                ThemeRowItem(row = row, onClick = { onEdit(row.themeId) })
            }
        }
    }
}

@Composable
private fun ContentLevelCard(level: Intensity, onLevelChange: (Intensity) -> Unit) {
    val spacing = AfterhoursTheme.spacing
    CinematicCard {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            Text(
                text = "How far can things go?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            IntensityDial(selected = level, onSelect = onLevelChange)
            Text(
                text = "Together, you only ever go as far as the more careful of you.",
                style = MaterialTheme.typography.bodySmall,
                color = AfterhoursTheme.colors.textMuted,
            )
        }
    }
}

@Composable
private fun ThemeRowItem(row: ThemeRow, onClick: () -> Unit) {
    val spacing = AfterhoursTheme.spacing
    val shape = MaterialTheme.shapes.medium
    val status = row.boundary?.level?.label ?: "Not set"
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = spacing.touchTarget)
            .clip(shape)
            .clickable(onClickLabel = "Change", role = Role.Button, onClick = onClick)
            // One announcement for the whole row, in words; the colour stays visual.
            .clearAndSetSemantics { contentDescription = "${row.title}. Covers ${row.covers}. Your limit: $status." },
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // A single-item theme is often named after its item; saying it twice is noise.
                if (!row.covers.equals(row.title, ignoreCase = true)) {
                    Text(
                        text = row.covers,
                        style = MaterialTheme.typography.bodySmall,
                        color = AfterhoursTheme.colors.textMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(text = status, style = MaterialTheme.typography.labelLarge, color = statusColor(row.boundary))
        }
    }
}

@Composable
private fun statusColor(boundary: Boundary?): Color = when (boundary?.level) {
    null -> AfterhoursTheme.colors.textFaint
    BoundaryLevel.NEVER -> MaterialTheme.colorScheme.error
    BoundaryLevel.NOT_TONIGHT -> AfterhoursTheme.colors.textMuted
    BoundaryLevel.ASK_FIRST -> AfterhoursTheme.colors.brass
    BoundaryLevel.CURIOUS -> MaterialTheme.colorScheme.primary
    BoundaryLevel.ALWAYS_OK -> MaterialTheme.colorScheme.onSurface
}

@Composable
private fun BoundaryEditorStep(
    editor: BoundaryEditor,
    onSelectLevel: (BoundaryLevel) -> Unit,
    onNoteChange: (String) -> Unit,
    onSaveNote: () -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
) {
    ScreenColumn {
        BackRow(label = "Back to my limits", onBack = onClose)
        ScreenHeading(
            eyebrow = editor.categoryTitle,
            headline = editor.title,
            body = "Covers ${editor.covers}.".takeUnless { editor.covers.equals(editor.title, ignoreCase = true) },
        )

        CinematicCard {
            BoundarySlider(selected = editor.level, onSelect = onSelectLevel)
        }

        OutlinedTextField(
            value = editor.note,
            onValueChange = onNoteChange,
            enabled = editor.hasBoundary,
            label = { Text("A note to yourself") },
            supportingText = {
                Text(
                    if (editor.hasBoundary) {
                        "Only you see this. ${editor.note.length}/${Boundary.MAX_NOTE_LENGTH}"
                    } else {
                        "Choose a level first."
                    },
                )
            },
            maxLines = NOTE_MAX_LINES,
            // While typing, the save button sits below the keyboard; Done saves from where
            // the thumb already is.
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (editor.canSaveNote) onSaveNote() }),
            modifier = Modifier.fillMaxWidth(),
        )

        if (editor.canSaveNote) {
            GlowButton(text = "Save note", onClick = onSaveNote, modifier = Modifier.fillMaxWidth())
        }
        if (editor.hasBoundary) {
            GlowButton(
                text = "Clear this limit",
                onClick = onClear,
                style = GlowButtonStyle.Quiet,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

private const val NOTE_MAX_LINES = 4
