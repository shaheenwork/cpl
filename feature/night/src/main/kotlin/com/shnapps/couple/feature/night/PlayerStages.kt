package com.shnapps.couple.feature.night

import com.shnapps.couple.core.model.NightSession
import com.shnapps.couple.core.model.PlannedNight
import com.shnapps.couple.core.model.SessionEndReason
import com.shnapps.couple.core.model.SessionState

/** The host's own steps between the server's states: the phone's alone, never stored. */
internal sealed interface PlayerStep {
    data object None : PlayerStep

    data class CheckIn(val index: Int) : PlayerStep

    data class Open(val index: Int, val open: Boolean) : PlayerStep

    data class Between(val nextIndex: Int) : PlayerStep

    data object Feedback : PlayerStep

    data object Done : PlayerStep
}

/** The chapter a step is about, or -1 for a step that is about none. */
private val PlayerStep.chapterIndex: Int
    get() = when (this) {
        is PlayerStep.CheckIn -> index
        is PlayerStep.Open -> index
        is PlayerStep.Between -> nextIndex
        else -> -1
    }

/**
 * Which stage of a night a phone shows (BUILD_PROMPT.md §14.6), from the server's session —
 * always the truth — and this phone's own steps between its states. Pure, so every rule of
 * the player is tested without a screen.
 */
internal object PlayerStages {

    fun stageOf(night: PlannedNight?, session: NightSession?, awaitingSession: Boolean, step: PlayerStep): NightStage =
        when {
            night == null || awaitingSession -> NightStage.Loading
            session == null -> NightStage.Shape
            else -> forSession(session, night, step)
        }

    private fun forSession(session: NightSession, night: PlannedNight, step: PlayerStep): NightStage {
        // A pause shows the night where it paused, veiled; the controls stay (§3.1).
        val paused = session.state == SessionState.PAUSED
        val state = if (paused) session.pausedFrom ?: SessionState.READY else session.state
        return when (state) {
            SessionState.WAITING_FOR_PARTNER -> NightStage.Consent
            SessionState.READY -> if (session.isHost) NightStage.Countdown else NightStage.Elsewhere
            SessionState.CHAPTER_ACTIVE ->
                if (session.isHost) chapter(session.currentChapter, night, step) else NightStage.Elsewhere
            SessionState.COMPLETED -> close(step)
            SessionState.ABANDONED, SessionState.PAUSED ->
                NightStage.Ended(session.endReason ?: SessionEndReason.STOPPED)
        }
    }

    private fun chapter(index: Int, night: PlannedNight, step: PlayerStep): NightStage = when {
        // This phone has moved on and the server is catching up: keep the moment between.
        step.chapterIndex > index -> NightStage.Between(step.chapterIndex)
        // Being passed over (D-062): never shown, not even sealed.
        index in night.unplayable -> NightStage.Between(index + 1)
        step.chapterIndex == index -> onChapter(index, step)
        NightScript.checkInDue(night, index) -> NightStage.CheckIn(index)
        else -> NightStage.Chapter(index, open = false)
    }

    /** Where this phone is within chapter [index]. */
    private fun onChapter(index: Int, step: PlayerStep): NightStage = when (step) {
        is PlayerStep.CheckIn -> NightStage.CheckIn(index)
        is PlayerStep.Open -> NightStage.Chapter(index, step.open)
        else -> NightStage.Between(index)
    }

    private fun close(step: PlayerStep): NightStage = when (step) {
        PlayerStep.Feedback -> NightStage.Feedback
        PlayerStep.Done -> NightStage.Done
        else -> NightStage.HowAreWe
    }
}
