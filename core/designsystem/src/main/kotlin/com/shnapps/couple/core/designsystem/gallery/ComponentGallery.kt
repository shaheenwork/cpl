package com.shnapps.couple.core.designsystem.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import com.shnapps.couple.core.designsystem.component.BoundarySlider
import com.shnapps.couple.core.designsystem.component.ChapterCard
import com.shnapps.couple.core.designsystem.component.ChapterState
import com.shnapps.couple.core.designsystem.component.CinematicCard
import com.shnapps.couple.core.designsystem.component.CountdownRing
import com.shnapps.couple.core.designsystem.component.DurationPicker
import com.shnapps.couple.core.designsystem.component.EmptyState
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.component.IntensityDial
import com.shnapps.couple.core.designsystem.component.LockedCard
import com.shnapps.couple.core.designsystem.component.MoodChipRow
import com.shnapps.couple.core.designsystem.component.PreferenceSwipeCard
import com.shnapps.couple.core.designsystem.component.RevealCard
import com.shnapps.couple.core.designsystem.component.RevealState
import com.shnapps.couple.core.designsystem.component.StopPauseBar
import com.shnapps.couple.core.designsystem.component.VoiceRecorderBar
import com.shnapps.couple.core.designsystem.component.VoiceState
import com.shnapps.couple.core.designsystem.component.WaitingForPartner
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.model.BoundaryLevel
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.Mood

/**
 * Every component in the inventory, on one screen (BUILD_PROMPT.md §15.3).
 *
 * This is the Phase 2 exit criterion and it stays useful afterwards: it is where the
 * design system is reviewed as a whole, and it is what the screenshot tests render, so a
 * change to a token shows up as a diff rather than as a surprise three screens deep.
 *
 * Sections are individually previewable so they can be screenshotted at a sane size.
 */
@Composable
fun ComponentGallery(modifier: Modifier = Modifier) {
    val spacing = AfterhoursTheme.spacing

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        AfterhoursTheme.colors.backdropTop,
                        AfterhoursTheme.colors.backdropBottom,
                    ),
                ),
            )
            .padding(horizontal = spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = spacing.xl),
    ) {
        item { GallerySection("Typography") { TypeSpecimen() } }
        item { GallerySection("Cards") { CardsSpecimen() } }
        item { GallerySection("Anticipation") { AnticipationSpecimen() } }
        item { GallerySection("Buttons") { ButtonsSpecimen() } }
        item { GallerySection("Night controls") { NightControlsSpecimen() } }
        item { GallerySection("Private answers") { PrivateAnswersSpecimen() } }
        item { GallerySection("Waiting and time") { WaitingSpecimen() } }
        item { GallerySection("Voice") { VoiceSpecimen() } }
        item { GallerySection("Safety") { SafetySpecimen() } }
        item { GallerySection("Empty") { EmptySpecimen() } }
    }
}

@Composable
private fun GallerySection(title: String, content: @Composable () -> Unit) {
    val spacing = AfterhoursTheme.spacing
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Text(
            text = title.uppercase(),
            style = EyebrowTextStyle,
            color = AfterhoursTheme.colors.brass,
        )
        HorizontalDivider(color = AfterhoursTheme.colors.edgeFaint)
        content()
    }
}

@Composable
internal fun TypeSpecimen() {
    val spacing = AfterhoursTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text("Tonight could get interesting.", style = MaterialTheme.typography.displaySmall)
        Text("You both chose this.", style = MaterialTheme.typography.headlineMedium)
        Text("Build our night", style = MaterialTheme.typography.titleLarge)
        Text(
            "Nobody else knows what happens in here. It is just ours.",
            style = MaterialTheme.typography.bodyLarge,
            color = AfterhoursTheme.colors.textMuted,
        )
        Text(
            "Only you can see this answer.",
            style = MaterialTheme.typography.bodySmall,
            color = AfterhoursTheme.colors.textFaint,
        )
    }
}

@Composable
internal fun CardsSpecimen() {
    val spacing = AfterhoursTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        CinematicCard {
            Text("A cinematic card", style = MaterialTheme.typography.titleLarge)
            Text(
                "Large, softly rounded, generously padded.",
                style = MaterialTheme.typography.bodyMedium,
                color = AfterhoursTheme.colors.textMuted,
            )
        }
        ChapterCard(index = 1, state = ChapterState.Complete, title = "Warm Up")
        ChapterCard(index = 2, state = ChapterState.Active, title = "Curiosity")
        ChapterCard(index = 3, state = ChapterState.Locked)
    }
}

@Composable
internal fun AnticipationSpecimen() {
    val spacing = AfterhoursTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        RevealCard(state = RevealState.Concealed, concealedLabel = "Not yet") {
            Text("This text is not in the tree while concealed.")
        }
        RevealCard(state = RevealState.Revealed) {
            Text("You both chose this.", style = MaterialTheme.typography.headlineSmall)
        }
        LockedCard(
            title = "Something is waiting for you tonight",
            eyebrow = "Scheduled",
            subtitle = "Unlocks at 9:00 pm",
        )
        LockedCard(title = "Chapter 4", eyebrow = "Mystery", unlocked = true)
    }
}

@Composable
internal fun ButtonsSpecimen() {
    val spacing = AfterhoursTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        GlowButton(text = "Build our night", onClick = {}, leadingEmoji = "🔥")
        GlowButton(text = "Surprise us", onClick = {}, style = GlowButtonStyle.Secondary, leadingEmoji = "🎲")
        GlowButton(text = "Not tonight", onClick = {}, style = GlowButtonStyle.Quiet)
        GlowButton(text = "Unavailable", onClick = {}, enabled = false)
    }
}

@Composable
internal fun NightControlsSpecimen() {
    val spacing = AfterhoursTheme.spacing
    var intensity by remember { mutableStateOf(Intensity.NAUGHTY) }
    var minutes by remember { mutableStateOf(30) }
    var moods by remember { mutableStateOf(setOf(Mood.NAUGHTY, Mood.MYSTERIOUS)) }

    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        IntensityDial(selected = intensity, onSelect = { intensity = it }, maxSelectable = Intensity.BOLD)
        MoodChipRow(
            selected = moods,
            onToggle = { mood -> moods = if (mood in moods) moods - mood else moods + mood },
        )
        DurationPicker(selectedMinutes = minutes, onSelect = { minutes = it })
    }
}

@Composable
internal fun PrivateAnswersSpecimen() {
    val spacing = AfterhoursTheme.spacing
    var boundary by remember { mutableStateOf(BoundaryLevel.CURIOUS) }
    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        PreferenceSwipeCard(
            prompt = "Being told exactly what to do",
            category = "Power and dynamics",
            onAnswer = {},
            onToggleSecret = {},
        )
        CinematicCard {
            Text("Our boundaries", style = MaterialTheme.typography.titleMedium)
            BoundarySlider(selected = boundary, onSelect = { boundary = it })
        }
    }
}

@Composable
internal fun WaitingSpecimen() {
    val spacing = AfterhoursTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        WaitingForPartner(
            message = "Your partner is choosing",
            detail = "You will find out in a moment.",
        )
        CountdownRing(progress = 0.62f, value = "12", unit = "days")
    }
}

@Composable
internal fun VoiceSpecimen() {
    val spacing = AfterhoursTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        VoiceRecorderBar(
            state = VoiceState.Idle,
            elapsedLabel = "0:00",
            onRecord = {}, onStop = {}, onPlay = {}, onDelete = {},
        )
        VoiceRecorderBar(
            state = VoiceState.Recording,
            elapsedLabel = "0:07",
            level = 0.7f,
            onRecord = {}, onStop = {}, onPlay = {}, onDelete = {},
        )
        VoiceRecorderBar(
            state = VoiceState.Recorded,
            elapsedLabel = "0:12",
            onRecord = {}, onStop = {}, onPlay = {}, onDelete = {},
        )
    }
}

@Composable
internal fun SafetySpecimen() {
    StopPauseBar(onPauseToggle = {}, onStop = {})
}

@Composable
internal fun EmptySpecimen() {
    EmptyState(
        title = "Nothing here yet",
        body = "Discover a few things you are both curious about, and this fills up fast.",
        actionLabel = "Discover together",
        onAction = {},
    )
}

@Preview(heightDp = 2400)
@Composable
private fun ComponentGalleryPreview() {
    AfterhoursTheme { ComponentGallery() }
}
