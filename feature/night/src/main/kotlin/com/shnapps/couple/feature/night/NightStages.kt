package com.shnapps.couple.feature.night

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.shnapps.couple.core.designsystem.component.ChapterCard
import com.shnapps.couple.core.designsystem.component.ChapterState
import com.shnapps.couple.core.designsystem.component.CinematicCard
import com.shnapps.couple.core.designsystem.component.CountdownRing
import com.shnapps.couple.core.designsystem.component.EmptyState
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.component.LockedCard
import com.shnapps.couple.core.designsystem.component.WaitingForPartner
import com.shnapps.couple.core.designsystem.theme.AfterhoursEasing
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.DisplayFontFamily
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.model.BoundaryLevel
import com.shnapps.couple.core.model.SessionEndReason
import com.shnapps.couple.core.ui.ScreenColumn
import com.shnapps.couple.core.ui.ScreenHeading
import kotlinx.coroutines.delay

@Composable
internal fun LoadingStage() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        WaitingForPartner(message = "Getting tonight ready…")
    }
}

@Composable
internal fun MissingStage(onHome: () -> Unit) {
    EmptyState(
        title = "This night isn't here any more.",
        body = "It may have ended on the other phone. Start a new one whenever you like.",
        actionLabel = "Back home",
        onAction = onHome,
    )
}

/** "Tonight has 7 chapters" (§14.6): the shape of the night, and nothing of what is in it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ShapeStage(state: NightUiState, actions: NightActions) {
    val night = state.night ?: return
    val spacing = AfterhoursTheme.spacing
    val peak = night.chapters.maxOf { it.intensity }
    ScreenColumn {
        ScreenHeading(
            eyebrow = "Tonight",
            headline = "Tonight has ${night.chapters.size} chapters.",
            body = "About ${night.totalMinutes} minutes, rising to ${peak.label}. " +
                "One at a time — what's inside each stays sealed until you get there.",
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            night.chapters.forEach { chapter ->
                Text(
                    text = (chapter.index + 1).toString().padStart(2, '0'),
                    fontFamily = DisplayFontFamily,
                    style = MaterialTheme.typography.headlineMedium,
                    color = AfterhoursTheme.colors.textFaint,
                )
            }
        }
        if (night.settings.focus != null) {
            Text(
                text = if (night.focusIncluded) {
                    "Built around something you both chose."
                } else {
                    "Nothing fits what you both chose at this length and level — tonight is in its spirit."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = AfterhoursTheme.colors.brass,
            )
        }
        if (night.needsBothConsents) {
            Text(
                text = "It goes up to ${peak.label}, so you'll both say you're in first — each on your own phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = AfterhoursTheme.colors.textMuted,
            )
        }
        GlowButton(
            text = "Begin",
            onClick = actions.onBegin,
            leadingEmoji = "🔥",
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        )
        GlowButton(
            text = "Not tonight",
            onClick = actions.onHome,
            style = GlowButtonStyle.Quiet,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Each partner's own "I'm in", on their own phone, before a BOLD or WILD night (§3.1). */
@Composable
internal fun ConsentStage(state: NightUiState, actions: NightActions) {
    val session = state.session ?: return
    val peak = state.night?.chapters?.maxOf { it.intensity }?.label ?: "BOLD"
    ScreenColumn {
        when {
            !session.consented -> ScreenHeading(
                eyebrow = "Before we begin",
                headline = if (session.isHost) "This one goes up to $peak." else "Your partner has a night ready.",
                body = if (session.isHost) {
                    "You both need to say you're in — each of you, on your own phone. " +
                        "Not tonight is always an answer."
                } else {
                    "It goes up to $peak. Are you in? Not tonight is always an answer."
                },
            )
            else -> WaitingForPartner(
                message = "You're in.",
                detail = "Waiting for your partner's \"I'm in\" — on their phone.",
            )
        }
        if (!session.consented) {
            GlowButton(
                text = "I'm in",
                onClick = actions.onConsent,
                leadingEmoji = "🔥",
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        GlowButton(
            text = "Not tonight",
            onClick = actions.onDecline,
            style = GlowButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A locked moment before the first chapter (§15.2 "locked card → countdown → unlock"). */
@Composable
internal fun CountdownStage(state: NightUiState, actions: NightActions) {
    var remaining by remember { mutableIntStateOf(COUNTDOWN_FROM) }
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(SECOND_MS)
            remaining -= 1
        }
        actions.onCountdownDone()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(AfterhoursTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.lg, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Here we go.",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        CountdownRing(
            progress = remaining.toFloat() / COUNTDOWN_FROM,
            value = remaining.coerceAtLeast(1).toString(),
            unit = "seconds",
        )
        // The first chapter could not be reached: try again from here.
        if (state.error != null && remaining == 0) {
            GlowButton(text = "Try again", onClick = actions.onCountdownDone, enabled = !state.busy)
        }
    }
}

/**
 * A quick check before a chapter (§3.1, §3.2): always before something this phone's user asked
 * to be asked about first, and at other moments too, so a check-in never gives a limit away.
 */
@Composable
internal fun CheckInStage(index: Int, state: NightUiState, actions: NightActions) {
    ScreenColumn {
        ScreenHeading(
            eyebrow = "A quick check-in",
            headline = "Before the next one — still good?",
            body = "Either of you can swap it, or stop the night. No reasons needed.",
        )
        GlowButton(text = "Keep going", onClick = { actions.onKeepGoing(index) }, modifier = Modifier.fillMaxWidth())
        GlowButton(
            text = "Swap this one",
            onClick = { actions.onNotThisOne(index) },
            style = GlowButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.nothingElseFits) NothingElseFits(index, actions)
    }
}

@Composable
internal fun ChapterStage(index: Int, open: Boolean, state: NightUiState, actions: NightActions) {
    val night = state.night ?: return
    val chapter = night.chapters.getOrNull(index) ?: return
    if (!open) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(AfterhoursTheme.spacing.gutter),
            contentAlignment = Alignment.Center,
        ) {
            LockedCard(
                title = "Chapter ${index + 1}",
                eyebrow = chapter.kind.label,
                subtitle = "Tap to open",
                onClick = { actions.onOpen(index) },
            )
        }
        return
    }
    val lead = NightScript.leadFor(night.settings.leadership, index, night.seed)
    val colors = AfterhoursTheme.colors
    ScreenColumn {
        // Right after "Not this one", the offer comes first; the new chapter waits below it.
        state.keepOut?.let { KeepOut(it, index, actions) }
        ChapterCard(index = index + 1, state = ChapterState.Active, title = chapter.item.title)
        CinematicCard {
            Text(
                text = "${chapter.kind.label} · ${chapter.intensity.label}".uppercase(),
                style = EyebrowTextStyle,
                color = colors.brass,
            )
            Text(
                text = chapter.item.subtitle,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = AfterhoursTheme.spacing.sm),
            )
            Text(text = chapter.item.body, style = MaterialTheme.typography.bodyLarge, color = colors.textMuted)
            Text(
                text = lead.line(),
                style = MaterialTheme.typography.labelLarge,
                color = colors.brass,
                modifier = Modifier.padding(top = AfterhoursTheme.spacing.md),
            )
        }
        GlowButton(
            text = "Done",
            onClick = { actions.onDone(index) },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        )
        GlowButton(
            text = "Not this one",
            onClick = { actions.onNotThisOne(index) },
            style = GlowButtonStyle.Quiet,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.nothingElseFits) NothingElseFits(index, actions)
    }
}

/** "Not this one", part two (§3.1): one tap to keep what it was about out. */
@Composable
private fun KeepOut(offer: KeepOutOffer, index: Int, actions: NightActions) {
    CinematicCard {
        Text(
            text = "Swapped. Keep this kind of thing out?",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        offer.themes.forEach { (themeId, title) ->
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = AfterhoursTheme.colors.textMuted,
                modifier = Modifier.padding(top = AfterhoursTheme.spacing.sm),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm)) {
                GlowButton(
                    text = "Not tonight",
                    onClick = { actions.onKeepOut(themeId, BoundaryLevel.NOT_TONIGHT, index) },
                    style = GlowButtonStyle.Secondary,
                    modifier = Modifier.weight(1f),
                )
                GlowButton(
                    text = "Never",
                    onClick = { actions.onKeepOut(themeId, BoundaryLevel.NEVER, index) },
                    style = GlowButtonStyle.Secondary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        GlowButton(text = "Just this once", onClick = actions.onDismissKeepOut, style = GlowButtonStyle.Quiet)
    }
}

@Composable
private fun NothingElseFits(index: Int, actions: NightActions) {
    CinematicCard {
        Text(
            text = "Nothing else released for tonight fits here.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        GlowButton(
            text = "Skip it",
            onClick = { actions.onDone(index) },
            style = GlowButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
        GlowButton(text = "Keep it", onClick = actions.onDismissNothingElse, style = GlowButtonStyle.Quiet)
    }
}

/** "Something interesting is coming…" (§14.6): a breath between chapters, never a snap. */
@Composable
internal fun BetweenStage(nextIndex: Int, actions: NightActions) {
    val motion = AfterhoursTheme.motion
    val reduceMotion = AfterhoursTheme.reduceMotion
    LaunchedEffect(nextIndex) {
        delay(motion.ambient.toLong())
        actions.onBetweenDone(nextIndex)
    }
    val transition = rememberInfiniteTransition(label = "between")
    val breath by transition.animateFloat(
        initialValue = if (reduceMotion) 1f else BREATH_LOW,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(motion.bloom, easing = AfterhoursEasing.Breathing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(AfterhoursTheme.spacing.gutter),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Something interesting is coming…",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .alpha(breath)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** The partner's phone while the night plays on the other one (D-059). */
@Composable
internal fun ElsewhereStage(state: NightUiState) {
    ScreenColumn {
        ScreenHeading(
            eyebrow = "Tonight",
            headline = "Tonight is playing on the other phone.",
            body = if (state.chapterNumber == 0) {
                "It's about to begin. You can pause or stop it from here, any time."
            } else {
                "Chapter ${state.chapterNumber} of ${state.chapterCount}. You can pause or stop it from here, any time."
            },
        )
    }
}

/** However a night ends, it ends warmly, and never says who ended it (§3.1). */
@Composable
internal fun EndedStage(reason: SessionEndReason, onHome: () -> Unit) {
    val (headline, body) = when (reason) {
        SessionEndReason.DECLINED -> "Not tonight. All good." to "Another night, whenever you both feel like it."
        SessionEndReason.EXPIRED ->
            "This night has ended." to "It waited a long while. Start a new one whenever you like."
        else -> "Ending here. All good." to "Nothing is lost, and nobody did anything wrong."
    }
    ScreenColumn {
        ScreenHeading(eyebrow = "Tonight", headline = headline, body = body)
        GlowButton(text = "Back home", onClick = onHome, modifier = Modifier.fillMaxWidth())
    }
}

private const val COUNTDOWN_FROM = 3

/** A countdown counts real seconds: this is a clock, not an animation. */
private const val SECOND_MS = 1_000L
private const val BREATH_LOW = 0.55f
