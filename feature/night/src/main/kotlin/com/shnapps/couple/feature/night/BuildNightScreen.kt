package com.shnapps.couple.feature.night

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shnapps.couple.core.designsystem.component.DurationPicker
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.component.IntensityDial
import com.shnapps.couple.core.designsystem.component.MoodChipRow
import com.shnapps.couple.core.designsystem.component.WaitingForPartner
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.Leadership
import com.shnapps.couple.core.model.Mood
import com.shnapps.couple.core.model.Novelty
import com.shnapps.couple.core.ui.BackRow
import com.shnapps.couple.core.ui.ErrorBanner
import com.shnapps.couple.core.ui.ScreenColumn
import com.shnapps.couple.core.ui.ScreenHeading
import com.shnapps.couple.core.ui.SecureScreen

/**
 * "WHAT ARE WE IN THE MOOD FOR?" (BUILD_PROMPT.md §14.5). A `SecureScreen`: a night built
 * around something both partners chose names that thing.
 */
@Composable
fun BuildNightScreen(
    onBuilt: (experienceId: String) -> Unit,
    onOpenNight: (experienceId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BuildNightViewModel = hiltViewModel(),
) {
    SecureScreen()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.builtExperienceId) {
        state.builtExperienceId?.let {
            viewModel.onShown()
            onBuilt(it)
        }
    }
    BuildNightContent(
        state = state,
        actions = BuildNightActions(
            onDuration = viewModel::setDuration,
            onMood = viewModel::toggleMood,
            onIntensity = viewModel::setIntensity,
            onLeadership = viewModel::setLeadership,
            onNovelty = viewModel::setNovelty,
            onBuild = viewModel::build,
            onExpress = viewModel::buildExpress,
            onDismissError = viewModel::dismissError,
            onOpenNight = onOpenNight,
            onBack = onBack,
        ),
        modifier = modifier,
    )
}

/** Everything the builder can ask for, gathered so the content stays easy to preview. */
internal data class BuildNightActions(
    val onDuration: (Int) -> Unit = {},
    val onMood: (Mood) -> Unit = {},
    val onIntensity: (Intensity) -> Unit = {},
    val onLeadership: (Leadership) -> Unit = {},
    val onNovelty: (Novelty) -> Unit = {},
    val onBuild: () -> Unit = {},
    /** "We have 15 minutes": the one choice, and the night at once. Null is "surprise me". */
    val onExpress: (Mood?) -> Unit = {},
    val onDismissError: () -> Unit = {},
    val onOpenNight: (String) -> Unit = {},
    val onBack: () -> Unit = {},
)

@Composable
internal fun BuildNightContent(state: BuildNightUiState, actions: BuildNightActions, modifier: Modifier = Modifier) {
    val settings = state.settings
    if (state.express) {
        ExpressContent(state, actions, modifier)
        return
    }
    Column(modifier = modifier) {
        ScreenColumn {
            BackRow(label = "Back", onBack = actions.onBack)
            ScreenHeading(
                eyebrow = "Build our night",
                headline = "What are we in the mood for?",
                body = state.focusPrompt?.let { "Built around something you both chose: $it." },
            )

            state.activeNight?.let { night ->
                // One night at a time (D-059): say so before anyone sets five dials.
                Text(
                    text = "There's a night under way already.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = AfterhoursTheme.colors.brass,
                )
                GlowButton(
                    text = "Go to it",
                    onClick = { actions.onOpenNight(night.experienceId) },
                    style = GlowButtonStyle.Secondary,
                )
            }

            Section("Time") { DurationPicker(selectedMinutes = settings.durationMin, onSelect = actions.onDuration) }
            Section("Mood") { MoodChipRow(selected = settings.moods, onToggle = actions.onMood) }
            Section("Intensity") {
                val ceiling = state.sharedLevel ?: Intensity.WILD
                IntensityDial(
                    selected = Intensity.effective(settings.intensity, ceiling),
                    onSelect = actions.onIntensity,
                    maxSelectable = ceiling,
                )
                if (ceiling != Intensity.WILD) {
                    Text(
                        text = "Your shared level is ${ceiling.label}. Nothing tonight goes past it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AfterhoursTheme.colors.textMuted,
                    )
                }
            }
            Section("Who leads") {
                OptionRow(Leadership.entries, settings.leadership, { it.label }, actions.onLeadership)
            }
            Section("Novelty") {
                OptionRow(Novelty.entries, settings.novelty, { it.label }, actions.onNovelty)
            }

            state.error?.let { ErrorBanner(message = it.message(), onDismiss = actions.onDismissError) }

            if (state.building) {
                WaitingForPartner(
                    message = "Building your night…",
                    detail = "Only from what you've both said yes to.",
                )
            } else {
                GlowButton(
                    text = "Build our night",
                    onClick = actions.onBuild,
                    leadingEmoji = "🔥",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * "We have 15 minutes" (§14.5): one tap to get here, one mood — or none — and the night is
 * built. Nobody should have to set five dials to play something short.
 */
@Composable
private fun ExpressContent(state: BuildNightUiState, actions: BuildNightActions, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        ScreenColumn {
            BackRow(label = "Back", onBack = actions.onBack)
            ScreenHeading(
                eyebrow = "15 minutes",
                headline = "Something short, then.",
                body = "Pick a mood, or let us. Everything else is already decided.",
            )
            state.activeNight?.let { night ->
                Text(
                    text = "There's a night under way already.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = AfterhoursTheme.colors.brass,
                )
                GlowButton(
                    text = "Go to it",
                    onClick = { actions.onOpenNight(night.experienceId) },
                    style = GlowButtonStyle.Secondary,
                )
            }
            state.error?.let { ErrorBanner(message = it.message(), onDismiss = actions.onDismissError) }
            if (state.building) {
                WaitingForPartner(
                    message = "Building your night…",
                    detail = "Only from what you've both said yes to.",
                )
            } else {
                Section("Mood") {
                    MoodChipRow(selected = emptySet(), onToggle = { mood -> actions.onExpress(mood) })
                }
                GlowButton(
                    text = "Surprise us",
                    onClick = { actions.onExpress(null) },
                    leadingEmoji = "🎲",
                    style = GlowButtonStyle.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm)) {
        Text(text = title.uppercase(), style = EyebrowTextStyle, color = AfterhoursTheme.colors.textFaint)
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> OptionRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    val spacing = AfterhoursTheme.spacing
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                modifier = Modifier.heightIn(min = spacing.touchTarget),
                label = { Text(text = label(option), style = MaterialTheme.typography.labelLarge) },
                shape = MaterialTheme.shapes.small,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

private fun BuildError.message(): String = when (this) {
    BuildError.NOT_ENOUGH_CONTENT ->
        "Nothing fits a night that short at that level. Try a longer night, or a gentler one."
    BuildError.RATE_LIMITED -> "That's a lot of nights for one evening. Try again in a little while."
    BuildError.OFFLINE -> "We couldn't reach the server. Nights are built there, behind both your limits."
    BuildError.OUT_OF_DATE -> "Your content needs a moment to catch up. Try again shortly."
    BuildError.OTHER -> "Something went wrong building your night. Try again."
}
