package com.shnapps.couple.feature.discovery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shnapps.couple.core.designsystem.component.EmptyState
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.component.RevealCard
import com.shnapps.couple.core.designsystem.component.RevealState
import com.shnapps.couple.core.designsystem.theme.AfterhoursEasing
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.model.MatchLevel
import com.shnapps.couple.core.ui.BackRow
import com.shnapps.couple.core.ui.ScreenColumn
import com.shnapps.couple.core.ui.ScreenHeading
import com.shnapps.couple.core.ui.SecureScreen
import kotlinx.coroutines.delay

/**
 * Mutual discovery (BUILD_PROMPT.md §14.7). A `SecureScreen`: what a couple both chose is
 * as private as what each of them answered (§3.4).
 *
 * [onBuildNight] is null until Build Our Night exists (Phase 10); the button then says so
 * rather than pretending.
 */
@Composable
fun MutualDiscoveryScreen(
    onBuildNight: ((String) -> Unit)?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MutualDiscoveryViewModel = hiltViewModel(),
) {
    SecureScreen()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MutualDiscoveryContent(
        state = state,
        onReveal = viewModel::reveal,
        onRevealed = viewModel::onRevealed,
        onNext = viewModel::next,
        onBuildNight = onBuildNight,
        onDone = onDone,
        modifier = modifier,
    )
}

@Composable
internal fun MutualDiscoveryContent(
    state: MutualDiscoveryUiState,
    onReveal: () -> Unit,
    onRevealed: () -> Unit,
    onNext: () -> Unit,
    onBuildNight: ((String) -> Unit)?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        when {
            state.loading -> Unit
            state.current != null -> RevealStage(state, onReveal, onRevealed, onNext, onBuildNight, onDone)
            else -> SharedStage(state.shared, onDone)
        }
    }
}

@Composable
private fun RevealStage(
    state: MutualDiscoveryUiState,
    onReveal: () -> Unit,
    onRevealed: () -> Unit,
    onNext: () -> Unit,
    onBuildNight: ((String) -> Unit)?,
    onDone: () -> Unit,
) {
    val card = state.current ?: return
    val motion = AfterhoursTheme.motion
    val colors = AfterhoursTheme.colors

    // The reveal keeps its timing even under reduce-motion (§15.2): cutting straight to the
    // answer would spend the one moment this screen exists for. The secret one takes a beat
    // longer, for the "WAIT…" before the card opens.
    LaunchedEffect(card.itemId, state.step) {
        if (state.step == RevealStep.Revealing) {
            delay((if (state.isSecret) motion.reveal + motion.bloom else motion.bloom).toLong())
            onRevealed()
        }
    }

    ScreenColumn {
        BackRow(label = "Back", onBack = onDone)
        ScreenHeading(
            eyebrow = "Only you two",
            headline = when {
                state.step == RevealStep.Concealed -> "Something you both chose."
                state.isSecret -> "You both picked this."
                else -> "You both chose this."
            },
            body = if (state.step == RevealStep.Concealed) newCaption(state.moreAfter + 1) else null,
        )

        SecretBanner(visible = state.isSecret && state.step != RevealStep.Concealed)

        RevealCard(
            state = state.step.toRevealState(),
            concealedLabel = if (state.isSecret) SECRET_EMOJI else "?",
            concealedDescription = "Something you both chose, not revealed yet",
            // Brass for the secret one, softened so the resting halo frames the card rather
            // than flooding what follows it; the bloom's peak still flares.
            glowColor = if (state.isSecret) colors.brass.copy(alpha = SECRET_GLOW_ALPHA) else colors.glow,
        ) {
            Text(text = card.categoryTitle.uppercase(), style = EyebrowTextStyle, color = colors.textFaint)
            Text(
                text = card.prompt,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = AfterhoursTheme.spacing.sm),
            )
            Text(text = card.description, style = MaterialTheme.typography.bodyLarge, color = colors.textMuted)
            Text(
                text = card.level.line,
                style = MaterialTheme.typography.labelLarge,
                color = colors.brass,
                modifier = Modifier.padding(top = AfterhoursTheme.spacing.md),
            )
        }

        // Room for the card's halo, so it glows around the card and not under the buttons.
        Spacer(modifier = Modifier.height(AfterhoursTheme.spacing.sm))

        when (state.step) {
            RevealStep.Concealed -> GlowButton(
                text = "Reveal it",
                onClick = onReveal,
                leadingEmoji = SECRET_EMOJI,
                modifier = Modifier.fillMaxWidth(),
            )
            RevealStep.Revealing -> Unit
            RevealStep.Revealed -> RevealedActions(card.itemId, state.moreAfter, onBuildNight, onNext)
        }
    }
}

/** "WAIT… YOU BOTH PICKED THIS 👀" — the first beat of the secret reveal (§14.7). */
@Composable
private fun SecretBanner(visible: Boolean) {
    val motion = AfterhoursTheme.motion
    val reduceMotion = AfterhoursTheme.reduceMotion
    AnimatedVisibility(
        visible = visible,
        // The banner's arrival is part of the reveal, so it keeps its fade under
        // reduce-motion; only the swell is decorative and goes.
        enter = fadeIn(tween(motion.reveal, easing = AfterhoursEasing.Decelerate)) +
            scaleIn(
                initialScale = if (reduceMotion) 1f else SECRET_START_SCALE,
                animationSpec = tween(motion.reveal, easing = AfterhoursEasing.Anticipation),
            ),
    ) {
        Text(
            text = "WAIT… YOU BOTH PICKED THIS $SECRET_EMOJI",
            style = MaterialTheme.typography.headlineSmall,
            color = AfterhoursTheme.colors.brass,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Assertive },
        )
    }
}

@Composable
private fun RevealedActions(itemId: String, moreAfter: Int, onBuildNight: ((String) -> Unit)?, onNext: () -> Unit) {
    // Every reveal ends with one tap into a night (§14.7).
    GlowButton(
        text = "Build a night around this",
        onClick = { onBuildNight?.invoke(itemId) },
        enabled = onBuildNight != null,
        leadingEmoji = "🔥",
        modifier = Modifier.fillMaxWidth(),
    )
    if (onBuildNight == null) {
        Text(
            text = "Building a night around it arrives in a later update.",
            style = MaterialTheme.typography.bodySmall,
            color = AfterhoursTheme.colors.textMuted,
        )
    }
    GlowButton(
        text = if (moreAfter > 0) "Next · $moreAfter more" else "See everything you share",
        onClick = onNext,
        style = GlowButtonStyle.Secondary,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SharedStage(shared: List<MatchCard>, onDone: () -> Unit) {
    val spacing = AfterhoursTheme.spacing
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = spacing.gutter, vertical = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                BackRow(label = "Back", onBack = onDone)
                ScreenHeading(
                    eyebrow = "Only you two",
                    headline = "Your curiosities",
                    // The truth, counted — never a compatibility score (§14.10).
                    body = if (shared.isEmpty()) null else "${things(shared.size)} you both chose.",
                )
            }
        }
        if (shared.isEmpty()) {
            item {
                EmptyState(
                    title = "Nothing yet",
                    body = "Matches arrive slowly, a few at a time, so neither of you can tell who " +
                        "answered when.",
                )
            }
        }
        items(shared, key = { it.itemId }) { card -> SharedRow(card) }
        item {
            GlowButton(
                text = "Done",
                onClick = onDone,
                style = GlowButtonStyle.Secondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = spacing.md),
            )
        }
    }
}

@Composable
private fun SharedRow(card: MatchCard) {
    val spacing = AfterhoursTheme.spacing
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = "${card.prompt}. ${card.level.line}" },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            val colors = AfterhoursTheme.colors
            Text(text = card.categoryTitle.uppercase(), style = EyebrowTextStyle, color = colors.textFaint)
            Text(
                text = card.prompt,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(text = card.level.line, style = MaterialTheme.typography.labelMedium, color = colors.brass)
        }
    }
}

/** How a match is described. Says what the two of them share, never who said what. */
internal val MatchLevel.line: String
    get() = when (this) {
        MatchLevel.BOTH_YES -> "You both said yes."
        MatchLevel.BOTH_CURIOUS -> "You're both curious."
        MatchLevel.MIXED_POSITIVE -> "You're both into it."
        MatchLevel.BOTH_SECRET -> "You were both secretly curious."
    }

private fun RevealStep.toRevealState(): RevealState = when (this) {
    RevealStep.Concealed -> RevealState.Concealed
    RevealStep.Revealing -> RevealState.Revealing
    RevealStep.Revealed -> RevealState.Revealed
}

private fun newCaption(count: Int): String = if (count == 1) "1 new." else "$count new."

private fun things(count: Int): String = if (count == 1) "1 thing" else "$count things"

private const val SECRET_EMOJI = "👀"
private const val SECRET_START_SCALE = 0.8f
private const val SECRET_GLOW_ALPHA = 0.6f
