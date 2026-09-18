package com.shnapps.couple.feature.preferences

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.component.PreferenceSwipeCard
import com.shnapps.couple.core.designsystem.theme.AfterhoursEasing
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.decorativeTween
import com.shnapps.couple.core.model.PreferenceAnswer
import kotlinx.coroutines.launch

/** Test tag of the card the user can swipe. */
internal const val DISCOVERY_CARD_TAG = "discoveryCard"

/** One frame of the deck: the card and where it sits, so transitions know their direction. */
private data class DeckFrame(val position: Int, val card: DiscoveryCard)

@Composable
internal fun AnsweringStep(
    state: PreferenceDiscoveryUiState,
    onAnswer: (PreferenceAnswer) -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    onFinishLater: () -> Unit,
) {
    val card = state.card ?: return
    val motion = AfterhoursTheme.motion
    val reduceMotion = AfterhoursTheme.reduceMotion

    StepColumn {
        DeckHeader(position = state.position, deckSize = state.deckSize, onBack = onBack, onFinishLater = onFinishLater)

        // Forward slides in from the right, back from the left: the deck has a direction, and
        // the motion says which way the user just went. Collapses to a cut under reduce-motion.
        AnimatedContent(
            targetState = DeckFrame(state.position, card),
            contentKey = { it.position },
            transitionSpec = {
                val forward = targetState.position > initialState.position
                val direction = if (forward) 1 else -1
                val slide = decorativeTween<IntOffset>(motion.standard, reduceMotion, AfterhoursEasing.Decelerate)
                val fade = decorativeTween<Float>(motion.standard, reduceMotion)
                val enter = slideInHorizontally(slide) { width -> direction * width / SLIDE_FRACTION } + fadeIn(fade)
                val exit = slideOutHorizontally(slide) { width -> -direction * width / SLIDE_FRACTION } + fadeOut(fade)
                enter togetherWith exit
            },
            label = "discoveryDeck",
        ) { frame ->
            SwipeableCard(
                key = frame.position,
                onSwipeLeft = onSkip,
                onSwipeRight = if (frame.position > 1) onBack else null,
            ) {
                PreferenceSwipeCard(
                    prompt = frame.card.item.prompt,
                    description = frame.card.item.description,
                    category = frame.card.categoryTitle,
                    selected = frame.card.answer,
                    onAnswer = onAnswer,
                )
            }
        }

        GlowButton(
            text = "Skip for now",
            onClick = onSkip,
            style = GlowButtonStyle.Quiet,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
internal fun EditingStep(
    state: PreferenceDiscoveryUiState,
    onAnswer: (PreferenceAnswer) -> Unit,
    onClearAnswer: () -> Unit,
    onBack: () -> Unit,
) {
    val card = state.card ?: return
    StepColumn {
        BackRow(label = "Back to my answers", onBack = onBack)
        PreferenceSwipeCard(
            prompt = card.item.prompt,
            description = card.item.description,
            category = card.categoryTitle,
            selected = card.answer,
            onAnswer = onAnswer,
        )
        if (card.answer != null) {
            GlowButton(
                text = "Remove my answer",
                onClick = onClearAnswer,
                style = GlowButtonStyle.Quiet,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun DeckHeader(position: Int, deckSize: Int, onBack: () -> Unit, onFinishLater: () -> Unit) {
    val spacing = AfterhoursTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = if (position > 1) "Previous card" else "Back to the start",
                    tint = AfterhoursTheme.colors.textMuted,
                )
            }
            Text(
                text = "$position of $deckSize",
                style = MaterialTheme.typography.labelLarge,
                color = AfterhoursTheme.colors.textMuted,
                modifier = Modifier.weight(1f),
            )
            GlowButton(text = "Finish later", onClick = onFinishLater, style = GlowButtonStyle.Quiet)
        }
        LinearProgressIndicator(
            // Progress through the deck so far: the card on screen is not done yet.
            progress = { if (deckSize == 0) 0f else (position - 1).toFloat() / deckSize },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            drawStopIndicator = {},
        )
    }
}

/** A labelled back control: one target, announced once. */
@Composable
internal fun BackRow(label: String, onBack: () -> Unit) {
    val spacing = AfterhoursTheme.spacing
    Row(
        modifier = Modifier
            .heightIn(min = spacing.touchTarget)
            .clickable(role = Role.Button, onClick = onBack)
            .padding(end = spacing.md),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            tint = AfterhoursTheme.colors.textMuted,
        )
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = AfterhoursTheme.colors.textMuted)
    }
}

/**
 * The swipe, layered over the card's buttons as an accelerator — never instead of them
 * (BUILD_PROMPT.md §20): left skips, right goes back a card.
 *
 * Neither direction answers. A swipe is too easy to make by accident for something as
 * private as this, so answering always takes a deliberate tap.
 */
@Composable
private fun SwipeableCard(
    key: Int,
    onSwipeLeft: () -> Unit,
    onSwipeRight: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    val offset = remember(key) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val reduceMotion = AfterhoursTheme.reduceMotion

    Box(
        modifier = Modifier
            .testTag(DISCOVERY_CARD_TAG)
            .graphicsLayer {
                translationX = offset.value
                // A slight tilt makes the card feel held. Movement still follows the finger
                // under reduce-motion, since the user is doing it; only the flourish goes.
                rotationZ = if (reduceMotion || size.width == 0f) 0f else offset.value / size.width * MAX_TILT_DEGREES
            }
            .pointerInput(key, onSwipeRight != null) {
                val threshold = size.width * SWIPE_THRESHOLD_FRACTION
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val travelled = offset.value
                        when {
                            travelled < -threshold -> onSwipeLeft()
                            travelled > threshold && onSwipeRight != null -> onSwipeRight()
                            else -> scope.launch { offset.animateTo(0f, spring()) }
                        }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f, spring()) } },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        scope.launch { offset.snapTo(offset.value + amount) }
                    },
                )
            },
    ) {
        content()
    }
}

private const val SLIDE_FRACTION = 3
private const val SWIPE_THRESHOLD_FRACTION = 0.3f
private const val MAX_TILT_DEGREES = 6f
