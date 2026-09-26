package com.shnapps.couple.feature.instant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shnapps.couple.core.designsystem.component.CinematicCard
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.model.Game
import com.shnapps.couple.core.model.Heat
import com.shnapps.couple.core.ui.ScreenColumn
import com.shnapps.couple.core.ui.ScreenHeading
import com.shnapps.couple.core.ui.SecureScreen

/**
 * Where an evening starts when nobody has signed in (REDESIGN_PROMPT.md §5.2).
 *
 * One big Start, then the games by name. What is gone is the six-field form: no mood picker, no
 * length picker, no intensity ladder, no who-leads, no novelty slider. The heat sits at the bottom
 * where a thumb reaches it, and that is the only setting this screen has.
 *
 * Apart needs a partner, so it is the one thing here that asks for an account — at the moment it is
 * wanted, and saying what it buys.
 */
@Composable
fun InstantHomeScreen(
    onStartNow: () -> Unit,
    onGame: (String) -> Unit,
    onApart: () -> Unit,
    onPlanEvening: () -> Unit,
    viewModel: InstantHomeViewModel = hiltViewModel(),
) {
    SecureScreen()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    InstantHomeContent(
        state = state,
        onStartNow = onStartNow,
        onGame = onGame,
        onApart = onApart,
        onPlanEvening = onPlanEvening,
        onHeat = viewModel::setHeat,
    )
}

@Suppress("LongParameterList") // A home screen: every way into an evening.
@Composable
internal fun InstantHomeContent(
    state: InstantHomeUiState,
    onStartNow: () -> Unit,
    onGame: (String) -> Unit,
    onApart: () -> Unit,
    onPlanEvening: () -> Unit,
    onHeat: (Heat) -> Unit,
) {
    ScreenColumn {
        ScreenHeading(
            eyebrow = "Tonight",
            headline = "Pick something. Or let us pick.",
            body = "One phone between you, passed back and forth.",
        )

        GlowButton(text = "Start now", onClick = onStartNow, modifier = Modifier.fillMaxWidth())

        Text(text = "OR PLAY SOMETHING", style = EyebrowTextStyle, color = AfterhoursTheme.colors.textFaint)
        state.games.forEach { game -> GameTile(game = game, onClick = { onGame(game.id) }) }

        Text(text = "WHEN YOU'RE NOT TOGETHER", style = EyebrowTextStyle, color = AfterhoursTheme.colors.textFaint)
        GlowButton(
            text = "Apart — send them something",
            onClick = onApart,
            style = GlowButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
        GlowButton(
            text = "Plan a whole evening",
            onClick = onPlanEvening,
            style = GlowButtonStyle.Quiet,
            modifier = Modifier.fillMaxWidth(),
        )

        HeatPicker(selected = state.heat, onHeat = onHeat)
    }
}

/** A game, said in its own words: what it is called, and what it does to an evening. */
@Composable
private fun GameTile(game: Game, onClick: () -> Unit) {
    CinematicCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(GAME_TEXT_WIDTH),
                verticalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.xxs),
            ) {
                Text(
                    text = game.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = game.tagline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AfterhoursTheme.colors.textMuted,
                )
            }
            GlowButton(text = "Play", onClick = onClick, style = GlowButtonStyle.Secondary)
        }
    }
}

/** How much of a tile the words take, leaving the button room at every width. */
private const val GAME_TEXT_WIDTH = 0.66f
