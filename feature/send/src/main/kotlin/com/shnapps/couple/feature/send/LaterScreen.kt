package com.shnapps.couple.feature.send

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shnapps.couple.core.designsystem.component.CinematicCard
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.icon.AfterhoursIcons
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.model.Countdown
import com.shnapps.couple.core.model.Gate
import com.shnapps.couple.core.model.OpenWhenTrigger
import com.shnapps.couple.core.model.Surprise
import com.shnapps.couple.core.model.SurpriseState
import com.shnapps.couple.core.ui.BackRow
import com.shnapps.couple.core.ui.ErrorBanner
import com.shnapps.couple.core.ui.PremiumNudge
import com.shnapps.couple.core.ui.ScreenColumn
import com.shnapps.couple.core.ui.ScreenHeading
import com.shnapps.couple.core.ui.SecureScreen

/**
 * Leaving something for later (BUILD_PROMPT.md §5.7, §13): a surprise for a time, an "Open when…"
 * for a moment, and the countdowns they are both waiting through.
 *
 * Both of them can see that something is waiting. Only the server can deliver it, and what is in
 * it is unreadable until it does — the screen asks, and says so plainly when the answer is no.
 */
@Composable
fun LaterScreen(
    onPaywall: (Gate) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LaterViewModel = hiltViewModel(),
) {
    SecureScreen()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaterContent(
        state = state,
        actions = LaterActions(
            onTitle = viewModel::setTitle,
            onText = viewModel::setText,
            onWhen = viewModel::setWhen,
            onTrigger = viewModel::setTrigger,
            onLeave = viewModel::leave,
            onPull = viewModel::pull,
            onOpen = viewModel::openArrived,
            onClose = viewModel::close,
            onAddCountdown = viewModel::addCountdown,
            onRemoveCountdown = viewModel::removeCountdown,
            onDismissLeft = viewModel::dismissLeft,
            onDismissError = viewModel::dismissError,
            onDismissGate = viewModel::dismissGate,
            onPaywall = onPaywall,
            onBack = onBack,
        ),
        modifier = modifier,
    )
}

/** Everything this screen can do, gathered so the content stays easy to preview. */
internal data class LaterActions(
    val onTitle: (String) -> Unit = {},
    val onPaywall: (Gate) -> Unit = {},
    val onDismissGate: () -> Unit = {},
    val onText: (String) -> Unit = {},
    val onWhen: (LeaveWhen) -> Unit = {},
    val onTrigger: (OpenWhenTrigger) -> Unit = {},
    val onLeave: () -> Unit = {},
    val onPull: (OpenWhenTrigger) -> Unit = {},
    val onOpen: (Surprise) -> Unit = {},
    val onClose: () -> Unit = {},
    val onAddCountdown: (String, Int) -> Unit = { _, _ -> },
    val onRemoveCountdown: (String) -> Unit = {},
    val onDismissLeft: () -> Unit = {},
    val onDismissError: () -> Unit = {},
    val onBack: () -> Unit = {},
)

@Composable
internal fun LaterContent(state: LaterUiState, actions: LaterActions, modifier: Modifier = Modifier) {
    val open = state.open
    if (open != null) {
        OpenedContent(open.first, open.second.text, actions, modifier)
        return
    }
    Column(modifier = modifier) {
        ScreenColumn {
            BackRow(label = "Back", onBack = actions.onBack)
            ScreenHeading(
                eyebrow = "For later",
                headline = "Leave something for them.",
                body = "They'll know something is waiting. What's in it stays yours until it's time.",
            )
            state.error?.let { ErrorBanner(message = it.message(), onDismiss = actions.onDismissError) }
            state.gate?.let { gate ->
                PremiumNudge(
                    gate = gate,
                    onSeeWhatsIncluded = { actions.onPaywall(gate) },
                    onDismiss = actions.onDismissGate,
                )
            }
            state.arrived.forEach { surprise -> Arrived(surprise, actions.onOpen) }
            if (state.leftIt) {
                CinematicCard(modifier = Modifier.fillMaxWidth(), glowing = true) {
                    Text(text = "LEFT", style = EyebrowTextStyle, color = AfterhoursTheme.colors.brass)
                    Text(
                        text = "It's waiting for them.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                GlowButton(
                    text = "Leave another",
                    onClick = actions.onDismissLeft,
                    style = GlowButtonStyle.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Leaver(state, actions)
            }
            Moments(state, actions)
            Waiting(state)
            Countdowns(state, actions)
        }
    }
}

/** What to leave, and when it arrives. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Leaver(state: LaterUiState, actions: LaterActions) {
    OutlinedTextField(
        value = state.title,
        onValueChange = actions.onTitle,
        label = { Text("What to call it") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.text,
        onValueChange = actions.onText,
        label = { Text("What's inside") },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
    )
    Text(text = "WHEN", style = EyebrowTextStyle, color = AfterhoursTheme.colors.textFaint)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm),
    ) {
        for (option in LeaveWhen.entries) {
            GlowButton(
                text = option.label,
                onClick = { actions.onWhen(option) },
                style = if (option == state.leaveWhen) GlowButtonStyle.Primary else GlowButtonStyle.Quiet,
            )
        }
    }
    if (state.leaveWhen == LeaveWhen.WHEN_THEY) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm),
        ) {
            for (trigger in OpenWhenTrigger.entries) {
                GlowButton(
                    text = trigger.label,
                    onClick = { actions.onTrigger(trigger) },
                    style = if (trigger == state.trigger) GlowButtonStyle.Secondary else GlowButtonStyle.Quiet,
                )
            }
        }
    }
    GlowButton(
        text = if (state.busy) "Leaving it…" else "Leave it",
        onClick = actions.onLeave,
        enabled = state.canLeave,
        leadingIcon = AfterhoursIcons.Gift,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Something that has arrived and is waiting to be opened. */
@Composable
private fun Arrived(surprise: Surprise, onOpen: (Surprise) -> Unit) {
    CinematicCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onOpen(surprise) },
        glowing = true,
        contentDescription = "Something has arrived: ${surprise.title}",
    ) {
        Text(text = "IT'S HERE", style = EyebrowTextStyle, color = AfterhoursTheme.colors.brass)
        Text(
            text = surprise.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "They left this for you. Open it.",
            style = MaterialTheme.typography.bodyMedium,
            color = AfterhoursTheme.colors.textMuted,
        )
    }
}

/** "Open when…": the moments they have left something for. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Moments(state: LaterUiState, actions: LaterActions) {
    if (state.moments.isEmpty()) return
    Text(text = "OPEN WHEN…", style = EyebrowTextStyle, color = AfterhoursTheme.colors.textMuted)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm),
    ) {
        for (trigger in state.moments) {
            GlowButton(
                text = trigger.label,
                onClick = { actions.onPull(trigger) },
                style = GlowButtonStyle.Secondary,
                enabled = !state.busy,
            )
        }
    }
}

/** What is still to come, on either side. */
@Composable
private fun Waiting(state: LaterUiState) {
    val waiting = state.surprises.filter { it.state == SurpriseState.SCHEDULED }
    if (waiting.isEmpty()) return
    Text(text = "WAITING", style = EyebrowTextStyle, color = AfterhoursTheme.colors.textMuted)
    Column(verticalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm)) {
        for (surprise in waiting) {
            val moment = surprise.trigger?.label?.lowercase()
            val line = when {
                surprise.mine && moment != null -> "${surprise.title} — for when $moment"
                surprise.mine -> "${surprise.title} — waiting for them"
                moment != null -> "Something for when $moment"
                else -> "Something is coming"
            }
            Text(
                text = line,
                style = MaterialTheme.typography.bodyLarge,
                color = AfterhoursTheme.colors.textMuted,
            )
        }
    }
}

/** Countdowns: big days to wait through together (§13). */
@Composable
private fun Countdowns(state: LaterUiState, actions: LaterActions) {
    Text(text = "COUNTDOWNS", style = EyebrowTextStyle, color = AfterhoursTheme.colors.textMuted)
    Column(verticalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm)) {
        for (countdown in state.countdowns) {
            CountdownCard(countdown) { actions.onRemoveCountdown(countdown.id) }
        }
        GlowButton(
            text = "Until we meet — a week",
            onClick = { actions.onAddCountdown("Until we meet", DEFAULT_COUNTDOWN_DAYS) },
            style = GlowButtonStyle.Quiet,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CountdownCard(countdown: Countdown, onRemove: () -> Unit) {
    CinematicCard(modifier = Modifier.fillMaxWidth(), onClick = onRemove, contentDescription = countdown.title) {
        Text(text = countdown.title.uppercase(), style = EyebrowTextStyle, color = AfterhoursTheme.colors.brass)
        Text(
            text = "Waiting.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** What was inside, once it may be read. */
@Composable
private fun OpenedContent(
    surprise: Surprise,
    text: String?,
    actions: LaterActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        ScreenColumn {
            BackRow(label = "Back", onBack = actions.onClose)
            ScreenHeading(eyebrow = "From them", headline = surprise.title, body = text)
            GlowButton(
                text = "Done",
                onClick = actions.onClose,
                style = GlowButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private const val DEFAULT_COUNTDOWN_DAYS = 7

private fun LaterError.message(): String = when (this) {
    LaterError.OFFLINE -> "You're offline. That one needs the network."
    LaterError.NOT_YET -> "Not yet — that one hasn't arrived."
    LaterError.NOTHING_WAITING -> "Nothing is waiting for that moment."
    LaterError.OTHER -> "That didn't work. Try again."
}
