package com.shnapps.couple.feature.games

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shnapps.couple.core.designsystem.component.CinematicCard
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.component.IntensityDial
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.model.ExperienceType
import com.shnapps.couple.core.model.Game
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.ui.BackRow
import com.shnapps.couple.core.ui.ErrorBanner
import com.shnapps.couple.core.ui.ScreenColumn
import com.shnapps.couple.core.ui.ScreenHeading
import com.shnapps.couple.core.ui.SecureScreen

/**
 * The games (BUILD_PROMPT.md §11.2): every game the content ships, and the few choices that
 * start one — how far it may go, and the two names the phone is passed between. Which prompts
 * the couple meets is the server's choice, behind both partners' boundaries.
 */
@Composable
fun GamesScreen(
    onStarted: (experienceId: String) -> Unit,
    onOpen: (experienceId: String, type: ExperienceType) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GamesViewModel = hiltViewModel(),
) {
    SecureScreen()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.startedExperienceId) {
        state.startedExperienceId?.let {
            viewModel.onShown()
            onStarted(it)
        }
    }
    GamesContent(
        state = state,
        actions = GamesActions(
            onSelect = viewModel::select,
            onCloseSetup = viewModel::closeSetup,
            onIntensity = viewModel::setIntensity,
            onFirst = viewModel::setFirst,
            onSecond = viewModel::setSecond,
            onSwap = viewModel::swapNames,
            onStart = viewModel::start,
            onDismissError = viewModel::dismissError,
            onOpen = onOpen,
            onBack = onBack,
        ),
        modifier = modifier,
    )
}

/** Everything the games screen can ask for, gathered so the content stays easy to preview. */
internal data class GamesActions(
    val onSelect: (String) -> Unit = {},
    val onCloseSetup: () -> Unit = {},
    val onIntensity: (Intensity) -> Unit = {},
    val onFirst: (String) -> Unit = {},
    val onSecond: (String) -> Unit = {},
    val onSwap: () -> Unit = {},
    val onStart: () -> Unit = {},
    val onDismissError: () -> Unit = {},
    val onOpen: (String, ExperienceType) -> Unit = { _, _ -> },
    val onBack: () -> Unit = {},
)

@Composable
internal fun GamesContent(state: GamesUiState, actions: GamesActions, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        ScreenColumn {
            val selected = state.selected
            if (selected == null) {
                BackRow(label = "Back", onBack = actions.onBack)
                ScreenHeading(
                    eyebrow = "Games",
                    headline = "Play something together.",
                    body = "One phone, passed between you. What you answer stays on it.",
                )
                state.active?.let { active ->
                    // One thing at a time (D-059): say so before anyone picks a game.
                    Text(
                        text = "Something is under way already.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = AfterhoursTheme.colors.brass,
                    )
                    GlowButton(
                        text = "Go to it",
                        onClick = { actions.onOpen(active.experienceId, active.experienceType) },
                        style = GlowButtonStyle.Secondary,
                    )
                }
                state.games.forEach { game -> GameCard(game, onClick = { actions.onSelect(game.id) }) }
            } else {
                BackRow(label = "All games", onBack = actions.onCloseSetup)
                Setup(selected, state, actions)
            }
        }
    }
}

@Composable
private fun GameCard(game: Game, onClick: () -> Unit) {
    CinematicCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        contentDescription = "${game.title}. ${game.tagline}",
    ) {
        Text(text = "${game.rounds} ROUNDS", style = EyebrowTextStyle, color = AfterhoursTheme.colors.brass)
        Text(
            text = game.title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(text = game.tagline, style = MaterialTheme.typography.bodyMedium, color = AfterhoursTheme.colors.textMuted)
    }
}

@Composable
private fun Setup(game: Game, state: GamesUiState, actions: GamesActions) {
    val spacing = AfterhoursTheme.spacing
    ScreenHeading(eyebrow = "${game.rounds} rounds", headline = game.title, body = game.description)

    Section("How far") {
        val ceiling = state.sharedLevel ?: Intensity.WILD
        IntensityDial(selected = state.intensity, onSelect = actions.onIntensity, maxSelectable = ceiling)
        Text(
            text = if (state.intensity.requiresBothPartyConsent) {
                "${state.intensity.name} needs an \"I'm in\" from each of you, on your own phones."
            } else {
                "It warms up to ${state.intensity.name}, and never goes past it."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = AfterhoursTheme.colors.textMuted,
        )
    }

    Section("Who's playing") {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            NameField(label = "Goes first", value = state.first, onChange = actions.onFirst)
            NameField(label = "Goes second", value = state.second, onChange = actions.onSecond)
            GlowButton(text = "Swap who goes first", onClick = actions.onSwap, style = GlowButtonStyle.Quiet)
        }
    }

    state.error?.let { ErrorBanner(message = it.message(), onDismiss = actions.onDismissError) }

    if (state.active != null) {
        Text(
            text = "Something is under way already. Finish it, or stop it, first.",
            style = MaterialTheme.typography.bodyMedium,
            color = AfterhoursTheme.colors.brass,
        )
    }
    GlowButton(
        text = if (state.starting) "Setting it up…" else "Start",
        onClick = actions.onStart,
        enabled = state.canStart,
        leadingEmoji = "🎲",
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun NameField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm)) {
        Text(text = title.uppercase(), style = EyebrowTextStyle, color = AfterhoursTheme.colors.textFaint)
        content()
    }
}

private fun StartError.message(): String = when (this) {
    StartError.NOT_ENOUGH_CONTENT ->
        "There isn't enough for this game at that level within your limits. Try another level, or another game."
    StartError.RATE_LIMITED -> "That's a lot of games in a short while. Try again in a little bit."
    StartError.OFFLINE -> "We couldn't reach the server. Check your connection and try again."
    StartError.OUT_OF_DATE -> "This phone needs the latest content first. Try again in a moment."
    StartError.OTHER -> "Something went wrong. Try that again."
}
