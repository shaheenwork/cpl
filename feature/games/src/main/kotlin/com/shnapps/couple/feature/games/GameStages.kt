package com.shnapps.couple.feature.games

import com.shnapps.couple.core.model.NightSession
import com.shnapps.couple.core.model.SessionEndReason
import com.shnapps.couple.core.model.SessionKind
import com.shnapps.couple.core.model.SessionState

/**
 * Which stage of a game a phone shows, from the server's session — always the truth — and
 * this phone's own game in hand. Pure, so every rule of the player is tested without a screen.
 */
internal object GameStages {

    fun stageOf(state: GameUiState, awaitingSession: Boolean): GameStage {
        val session = state.session
        return when {
            state.missing -> GameStage.Missing
            state.started == null || awaitingSession -> GameStage.Loading
            session == null -> GameStage.Intro
            else -> forSession(session, state)
        }
    }

    private fun forSession(session: NightSession, state: GameUiState): GameStage {
        // A pause shows the game where it paused, veiled; the controls stay (§3.1).
        val paused = session.state == SessionState.PAUSED
        val current = if (paused) session.pausedFrom ?: SessionState.READY else session.state
        return when (current) {
            SessionState.WAITING_FOR_PARTNER -> GameStage.Consent
            SessionState.READY -> if (session.isHost) GameStage.Ready else GameStage.Elsewhere
            // Every state a chapter is under way in: on two phones both sides play it; on one,
            // only the phone it is played on (§12.1, D-059).
            SessionState.CHAPTER_ACTIVE,
            SessionState.AWAITING_SUBMISSIONS,
            SessionState.BOTH_SUBMITTED,
            SessionState.REVEAL,
            SessionState.CHAPTER_COMPLETE,
            -> if (session.isHost || session.kind == SessionKind.TWO_DEVICE) playing(state) else GameStage.Elsewhere
            SessionState.COMPLETED ->
                if (session.isHost) closing(state) else GameStage.Ended(SessionEndReason.COMPLETED)
            SessionState.ABANDONED, SessionState.PAUSED ->
                GameStage.Ended(session.endReason ?: SessionEndReason.STOPPED)
        }
    }

    /** The game itself, once this phone holds it. */
    private fun playing(state: GameUiState): GameStage =
        state.run?.let { GameStage.Playing(it.moment) } ?: GameStage.Loading

    /** The close: the check-in first, then the score (§3.1). */
    private fun closing(state: GameUiState): GameStage = if (state.closed) GameStage.Results else GameStage.HowAreWe

    /** The stages a game is under way in: each carries PAUSE and STOP (§3.1). */
    fun isLive(stage: GameStage): Boolean =
        stage == GameStage.Ready || stage is GameStage.Playing || stage == GameStage.Elsewhere
}
