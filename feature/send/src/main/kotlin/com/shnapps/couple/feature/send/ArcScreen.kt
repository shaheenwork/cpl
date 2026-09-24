package com.shnapps.couple.feature.send

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shnapps.couple.core.designsystem.component.CinematicCard
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.model.Arc
import com.shnapps.couple.core.model.ArcDay
import com.shnapps.couple.core.model.ArcDayState
import com.shnapps.couple.core.model.Gate
import com.shnapps.couple.core.ui.BackRow
import com.shnapps.couple.core.ui.ErrorBanner
import com.shnapps.couple.core.ui.PremiumNudge
import com.shnapps.couple.core.ui.ScreenColumn
import com.shnapps.couple.core.ui.ScreenHeading
import com.shnapps.couple.core.ui.SecureScreen
import kotlinx.coroutines.delay

/**
 * A few days of this (BUILD_PROMPT.md §13): one, three, seven or fourteen days, a night each.
 *
 * The plan is visible from the start — which beat each day is — and nothing of a day's night
 * exists until the day comes. A day that is not due, or whose day before is unfinished, says so
 * because the server said so.
 */
@Composable
fun ArcScreen(
    onNight: (experienceId: String) -> Unit,
    onPaywall: (Gate) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ArcViewModel = hiltViewModel(),
) {
    SecureScreen()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // What is due is a question about the clock, so it is asked again while the screen is open —
    // a day that comes round at midnight must not wait for the next state change to say so.
    val due by produceState(initialValue = viewModel.dayDue(), state.arc) {
        while (true) {
            value = viewModel.dayDue()
            delay(DUE_CHECK_MS)
        }
    }
    LaunchedEffect(state.openedExperienceId) {
        state.openedExperienceId?.let {
            viewModel.onShown()
            onNight(it)
        }
    }
    ArcContent(
        state = state,
        due = due,
        actions = ArcActions(
            onStart = viewModel::start,
            onOpenDay = viewModel::openDay,
            onEnd = viewModel::end,
            onDismissError = viewModel::dismissError,
            onDismissGate = viewModel::dismissGate,
            onPaywall = onPaywall,
            onBack = onBack,
        ),
        modifier = modifier,
    )
}

/** Everything this screen can do, gathered so the content stays easy to preview. */
internal data class ArcActions(
    val onStart: (Int) -> Unit = {},
    val onPaywall: (Gate) -> Unit = {},
    val onDismissGate: () -> Unit = {},
    val onOpenDay: (ArcDay) -> Unit = {},
    val onEnd: () -> Unit = {},
    val onDismissError: () -> Unit = {},
    val onBack: () -> Unit = {},
)

@Composable
internal fun ArcContent(
    state: ArcUiState,
    due: ArcDay?,
    actions: ArcActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        ScreenColumn {
            BackRow(label = "Back", onBack = actions.onBack)
            val arc = state.arc
            if (arc == null) {
                Chooser(state, actions)
            } else {
                Running(arc, due, state, actions)
            }
            state.error?.let { ErrorBanner(message = it.message(), onDismiss = actions.onDismissError) }
            state.gate?.let { gate ->
                PremiumNudge(
                    gate = gate,
                    onSeeWhatsIncluded = { actions.onPaywall(gate) },
                    onDismiss = actions.onDismissGate,
                )
            }
        }
    }
}

/** Nothing running: how many days. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chooser(state: ArcUiState, actions: ArcActions) {
    ScreenHeading(
        eyebrow = "A few days of this",
        headline = "Make it last longer.",
        body = "A night a day, each one picking up where the last left off. Curiosity, teasing, " +
            "a secret, a challenge — and a last night at the end of it.",
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm),
    ) {
        for (length in state.lengths) {
            GlowButton(
                text = if (length == 1) "One night" else "$length days",
                onClick = { actions.onStart(length) },
                style = if (length == DEFAULT_LENGTH) GlowButtonStyle.Primary else GlowButtonStyle.Secondary,
                enabled = !state.busy,
            )
        }
    }
}

/** One running: the plan, and today's day. */
@Composable
private fun Running(arc: Arc, due: ArcDay?, state: ArcUiState, actions: ArcActions) {
    val done = arc.days.count { it.state == ArcDayState.DONE }
    ScreenHeading(
        eyebrow = arc.title,
        headline = if (due != null) "Day ${due.index + 1}: ${due.title.lowercase()}." else "Nothing due yet.",
        body = "$done of ${arc.lengthDays} played. A day opens once the one before it has been.",
    )
    if (due != null) {
        GlowButton(
            text = if (state.opening) "Opening…" else "Open today",
            onClick = { actions.onOpenDay(due) },
            leadingEmoji = "🌙",
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm)) {
        for (day in arc.days) {
            DayCard(day)
        }
    }
    GlowButton(
        text = "End it here",
        onClick = actions.onEnd,
        style = GlowButtonStyle.Quiet,
        enabled = !state.busy,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DayCard(day: ArcDay) {
    val mark = when (day.state) {
        ArcDayState.DONE -> "PLAYED"
        ArcDayState.READY -> "OPEN"
        ArcDayState.LOCKED -> "LOCKED"
    }
    CinematicCard(modifier = Modifier.fillMaxWidth(), glowing = day.state == ArcDayState.READY) {
        Text(text = "DAY ${day.index + 1} · $mark", style = EyebrowTextStyle, color = AfterhoursTheme.colors.brass)
        Text(
            text = day.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private const val DEFAULT_LENGTH = 7

/** How often the screen asks again whether a day has come. */
private const val DUE_CHECK_MS = 5_000L

private fun ArcError.message(): String = when (this) {
    ArcError.OFFLINE -> "You're offline. That one needs the network."
    ArcError.NOT_YET -> "Not yet — that day hasn't come."
    ArcError.FINISH_YESTERDAY -> "Finish the day before this one first."
    ArcError.OTHER -> "That didn't work. Try again."
}
