package com.shnapps.couple.feature.night

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.shnapps.couple.core.designsystem.component.MoodChipRow
import com.shnapps.couple.core.designsystem.component.WaitingForPartner
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.model.Gate
import com.shnapps.couple.core.model.Heat
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.Mood
import com.shnapps.couple.core.ui.BackRow
import com.shnapps.couple.core.ui.ErrorBanner
import com.shnapps.couple.core.ui.HeatPicker
import com.shnapps.couple.core.ui.PremiumNudge
import com.shnapps.couple.core.ui.ScreenColumn
import com.shnapps.couple.core.ui.ScreenHeading
import com.shnapps.couple.core.ui.SecureScreen

/**
 * "WHAT ARE WE IN THE MOOD FOR?" (BUILD_PROMPT.md §14.5). A `SecureScreen`: a night built
 * around something both partners chose names that thing.
 */
@Composable
fun BuildNightScreen(
    onPaywall: (Gate) -> Unit,
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
            onHeat = viewModel::setHeat,
            onBuild = viewModel::build,
            onExpress = viewModel::buildExpress,
            onDismissError = viewModel::dismissError,
            onDismissGate = viewModel::dismissGate,
            onPaywall = onPaywall,
            onOpenNight = onOpenNight,
            onBack = onBack,
        ),
        modifier = modifier,
    )
}

/** Everything the builder can ask for, gathered so the content stays easy to preview. */
internal data class BuildNightActions(
    val onPaywall: (Gate) -> Unit = {},
    val onDismissGate: () -> Unit = {},
    val onDuration: (Int) -> Unit = {},
    val onMood: (Mood) -> Unit = {},
    val onHeat: (Heat) -> Unit = {},
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
    val planned = state.plannedChapters
    if (planned != null) {
        PlannedContent(chapters = planned, onDone = actions.onBack, modifier = modifier)
        return
    }
    Column(modifier = modifier) {
        ScreenColumn {
            BackRow(label = "Back", onBack = actions.onBack)
            if (state.forPartner) {
                ScreenHeading(
                    eyebrow = "For them",
                    headline = "What are they in the mood for?",
                    body = "You choose the shape. What goes in it is built for them, behind their " +
                        "own limits — and you won't see it either.",
                )
            } else {
                ScreenHeading(
                    eyebrow = "Plan the evening",
                    headline = "Three questions, then we build it.",
                    body = state.focusPrompt?.let { "Built around something you both chose: $it." },
                )
            }
            state.activeNight?.let { night ->
                // One night at a time (D-059): say so before anyone sets five dials.
                Text(
                    text = "There's an evening under way already.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = AfterhoursTheme.colors.brass,
                )
                GlowButton(
                    text = "Go to it",
                    onClick = { actions.onOpenNight(night.experienceId) },
                    style = GlowButtonStyle.Secondary,
                )
            }

            // Three questions, which is what home promises. Who leads and how novel it is were
            // two more dials for something nobody had asked for yet; the engine still takes both,
            // at the defaults the model already had (§6, U5).
            Section("How long") {
                DurationPicker(selectedMinutes = settings.durationMin, onSelect = actions.onDuration)
            }
            Section("How far") {
                HeatPicker(selected = state.heat, onHeat = actions.onHeat)
                state.sharedLevel?.takeIf { it != Intensity.WILD }?.let {
                    Text(
                        text = "You've both agreed to stop at ${Heat.covering(it).label}. Nothing goes past it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AfterhoursTheme.colors.textMuted,
                    )
                }
            }
            Section("What are you in the mood for") {
                MoodChipRow(selected = settings.moods, onToggle = actions.onMood)
            }

            state.error?.let { ErrorBanner(message = it.message(), onDismiss = actions.onDismissError) }
            // Before anything is built: the one place a limit may be mentioned at all (§19).
            state.gate?.let { gate ->
                PremiumNudge(
                    gate = gate,
                    onSeeWhatsIncluded = { actions.onPaywall(gate) },
                    onDismiss = actions.onDismissGate,
                )
            }

            if (state.building) {
                WaitingForPartner(
                    message = if (state.forPartner) "Planning it…" else "Building your evening…",
                    detail = "Only from what you've both said yes to.",
                )
            } else {
                GlowButton(
                    text = if (state.forPartner) "Plan it for them" else "Build the evening",
                    onClick = actions.onBuild,
                    leadingEmoji = if (state.forPartner) "👀" else "🔥",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Planned, and gone from this phone (§2.5 #2): the planner is told the shape and no more. What
 * is in it is built behind their partner's limits, and only their partner will ever see it.
 */
@Composable
private fun PlannedContent(chapters: Int, onDone: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        ScreenColumn {
            ScreenHeading(
                eyebrow = "It's ready",
                headline = "$chapters cards, waiting for them.",
                body = "They'll see that something is waiting. Neither of you sees what until " +
                    "they open it.",
            )
            GlowButton(
                text = "Done",
                onClick = onDone,
                style = GlowButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
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
                    text = "There's an evening under way already.",
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
            // Before anything is built: the one place a limit may be mentioned at all (§19).
            state.gate?.let { gate ->
                PremiumNudge(
                    gate = gate,
                    onSeeWhatsIncluded = { actions.onPaywall(gate) },
                    onDismiss = actions.onDismissGate,
                )
            }
            if (state.building) {
                WaitingForPartner(
                    message = "Building your evening…",
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

private fun BuildError.message(): String = when (this) {
    BuildError.NOT_ENOUGH_CONTENT ->
        "Nothing fits an evening that short at that heat. Try a longer one, or a gentler one."
    BuildError.RATE_LIMITED -> "That's a lot in a short time. Try again in a little while."
    BuildError.OFFLINE -> "We couldn't reach the server. Evenings are built there, behind both your limits."
    BuildError.OUT_OF_DATE -> "Your content needs a moment to catch up. Try again shortly."
    BuildError.OTHER -> "Something went wrong building your evening. Try again."
}
