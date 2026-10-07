package com.shnapps.couple.feature.instant

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The clock a card suggested, if anybody started one (REDESIGN_PROMPT.md §5.7).
 *
 * Its own object rather than three fields and a loop inside the player, because it is the one part
 * of that screen with a life of its own: a job, a value that changes on its own, and a rule about
 * when it stops. [show] is how it reaches the screen — it owns the number, the player owns the
 * state it lands in.
 *
 * Nothing here starts on its own. A countdown that begins by itself turns an invitation into an
 * instruction, and some of these cards are better without one at all.
 */
internal class CardClock(
    private val scope: CoroutineScope,
    private val show: (CardTimer?) -> Unit,
) {
    private var ticking: Job? = null
    private var timer: CardTimer? = null

    /** Starts [seconds] on the clock, replacing anything already running. */
    fun start(seconds: Int) {
        ticking?.cancel()
        timer = CardTimer(totalSeconds = seconds, remainingSeconds = seconds)
        show(timer)
        ticking = scope.launch {
            while (true) {
                delay(TICK_MS)
                val running = timer ?: return@launch
                if (running.finished) return@launch
                timer = running.copy(remainingSeconds = running.remainingSeconds - 1)
                show(timer)
            }
        }
    }

    /** Stops it and takes it off the screen. Safe to call when nothing is running. */
    fun stop() {
        ticking?.cancel()
        ticking = null
        timer = null
        show(null)
    }

    private companion object {
        const val TICK_MS = 1_000L
    }
}
