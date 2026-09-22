package com.shnapps.couple.feature.night

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shnapps.couple.core.designsystem.component.CinematicCard
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.model.NightSession
import com.shnapps.couple.core.model.SessionState

/**
 * A night under way, on the home screen: how the other partner learns there is an "I'm in" to
 * give, and how either of them finds their way back after closing the app. Shows nothing when
 * there is no night.
 */
@Composable
fun ActiveNightCard(
    onOpen: (experienceId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActiveNightViewModel = hiltViewModel(),
) {
    val session by viewModel.active.collectAsStateWithLifecycle()
    session?.let { ActiveNightContent(it, onOpen, modifier) }
}

@Composable
internal fun ActiveNightContent(session: NightSession, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val line = when {
        session.state == SessionState.PAUSED -> "Tonight is paused. Either of you can pick it up."
        session.state == SessionState.WAITING_FOR_PARTNER && !session.consented && session.isHost ->
            "Tonight is waiting for your \"I'm in\"."
        session.state == SessionState.WAITING_FOR_PARTNER && !session.consented ->
            "Your partner has a night ready. Are you in?"
        session.state == SessionState.WAITING_FOR_PARTNER -> "Waiting for your partner's \"I'm in\"."
        session.isHost -> "Tonight is waiting. Pick up where you left off."
        else -> "A night is playing on the other phone."
    }
    CinematicCard(modifier = modifier, onClick = { onOpen(session.experienceId) }, glowing = true) {
        Text(text = "TONIGHT", style = EyebrowTextStyle, color = AfterhoursTheme.colors.brass)
        Text(text = line, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
