package com.shnapps.couple.feature.instant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shnapps.couple.core.data.instant.InstantPlayRepository
import com.shnapps.couple.core.engine.InstantCard
import com.shnapps.couple.core.engine.InstantDeck
import com.shnapps.couple.core.model.ContentPack
import com.shnapps.couple.core.model.Heat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A clock running on a card that suggested one. Never started on anyone's behalf. */
data class CardTimer(val totalSeconds: Int, val remainingSeconds: Int) {
    val finished: Boolean get() = remainingSeconds <= 0
    val progress: Float get() = if (totalSeconds <= 0) 0f else remainingSeconds.toFloat() / totalSeconds
}

/** Immutable screen state (BUILD_PROMPT.md §4.1; enforced by :architecture). */
data class InstantPlayUiState(
    val card: InstantCard? = null,
    val heat: Heat = Heat.DEFAULT,
    /** The lanes this heat has cards in, and the one being dealt from (D-125). */
    val lanes: List<ContentPack> = emptyList(),
    val lane: String? = null,
    val played: Int = 0,
    val streak: Int = 0,
    val loading: Boolean = true,
    val refusing: InstantCard? = null,
    val offeringHotter: Heat? = null,
    /** Where they are in this run of cards: 0 until the first is done. */
    val position: Int = 0,
    val runLength: Int = InstantPlayViewModel.RUN_LENGTH,
    val timer: CardTimer? = null,
    /** The run is over. Nothing ends the evening — it offers to carry on. */
    val runComplete: Boolean = false,
) {
    /** The heat is reachable while a card is up, and not while the screen is asking something. */
    val showsHeatPicker: Boolean
        get() = card != null && refusing == null && offeringHotter == null && !runComplete

    /** What the lane is called, for the card's own eyebrow. Null while dealing from all of them. */
    val laneLabel: String? get() = lanes.firstOrNull { it.id == lane }?.title
}

/**
 * The card, and the two taps that move it on (REDESIGN_PROMPT.md §5.3).
 *
 * Three rules are worth stating, because each replaces a screen the app used to have:
 *
 * - **Skip asks nothing.** No reason, no dialog, no record beyond this evening's position.
 * - **"Too far" asks one question**, and the answer is written to this phone alone. The partner is
 *   never told, and nothing about it leaves the device while there is no account.
 * - **The heat goes up when they are going well**, offered after a run of yeses rather than set in
 *   a form at the start. It is offered, never applied.
 */
@HiltViewModel
class InstantPlayViewModel @Inject constructor(
    private val instant: InstantPlayRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InstantPlayUiState())
    val uiState: StateFlow<InstantPlayUiState> = _uiState.asStateFlow()

    private var deck: InstantDeck? = null
    private var position = 0
    private var ticking: Job? = null

    /**
     * What this run has already been dealt, on top of what the phone remembers (D-124).
     *
     * Kept here as well as in DataStore so the next card is chosen without waiting for a write to
     * come back round through the flow.
     */
    private var dealt = emptySet<String>()

    /** This evening's shuffle. A new run gets a new one, which is why two evenings differ. */
    private var seed = System.currentTimeMillis()

    /** Everything this phone has ever been dealt, as the deck last saw it. */
    private var played = emptySet<String>()

    init {
        viewModelScope.launch {
            combine(instant.deck, instant.heat, instant.played, instant.packs, instant.lane, ::Deal)
                .collect { deal ->
                    deck = deal.deck
                    played = deal.played
                    _uiState.update {
                        it.copy(
                            card = deal.deck.next(position, deal.heat, seed, played + dealt),
                            heat = deal.heat,
                            lanes = deal.lanes,
                            lane = deal.lane,
                            loading = false,
                        )
                    }
                }
        }
    }

    /** The five things a card depends on, named so the combine above reads as one thing. */
    private data class Deal(
        val deck: InstantDeck,
        val heat: Heat,
        val played: Set<String>,
        val lanes: List<ContentPack>,
        val lane: String?,
    )

    /** They did it. Five in a row is when the app asks whether to turn things up. */
    fun done() {
        seen()
        val streak = _uiState.value.streak + 1
        val hotter = _uiState.value.heat.hotter()
        if (streak >= STREAK_BEFORE_OFFERING && hotter != null) {
            stopTimer()
            _uiState.update { it.copy(streak = 0, offeringHotter = hotter) }
            return
        }
        _uiState.update { it.copy(streak = streak) }
        advance()
    }

    /**
     * The clock a card suggested, started because someone pressed it.
     *
     * Never started for them: a countdown that begins on its own turns an invitation into an
     * instruction, and some of these cards are better without one at all.
     */
    fun startTimer() {
        val seconds = _uiState.value.card?.minutes?.times(SECONDS_PER_MINUTE) ?: return
        ticking?.cancel()
        _uiState.update { it.copy(timer = CardTimer(seconds, seconds)) }
        ticking = viewModelScope.launch {
            while (true) {
                delay(TICK_MS)
                val timer = _uiState.value.timer ?: return@launch
                if (timer.finished) return@launch
                _uiState.update { it.copy(timer = timer.copy(remainingSeconds = timer.remainingSeconds - 1)) }
            }
        }
    }

    fun stopTimer() {
        ticking?.cancel()
        ticking = null
        _uiState.update { it.copy(timer = null) }
    }

    /**
     * Six cards in, the app says so and asks. It never decides the evening is over.
     *
     * Carrying on is a new run: a new shuffle and a fresh climb, rather than card seven of one
     * long list. What has been seen is still remembered, so nothing comes round twice.
     */
    fun keepGoing() {
        seed = System.currentTimeMillis()
        position = 0
        _uiState.update { it.copy(runComplete = false, position = 0, card = card()) }
    }

    /** Not that one. Nothing is asked, and it is recorded as seen rather than as refused. */
    fun skip() {
        seen()
        _uiState.update { it.copy(streak = 0) }
        advance()
    }

    /**
     * Deal from one pack, or from all of them (D-125).
     *
     * A new lane starts a new run for the same reason "keep going" does: the point of asking for
     * After dark is to get one now, not behind the cards already queued from the old lane.
     */
    fun setLane(pack: String?) {
        if (pack == _uiState.value.lane) return
        seed = System.currentTimeMillis()
        position = 0
        dealt = emptySet()
        stopTimer()
        _uiState.update { it.copy(lane = pack, position = 0, runComplete = false, streak = 0) }
        viewModelScope.launch { instant.setLane(pack) }
    }

    /**
     * That card has been seen, so it does not come round again (D-124).
     *
     * Both "done" and "skip" record it: a skip is not a refusal, but it is not a card anybody
     * wants dealt again ten minutes later either.
     */
    private fun seen() {
        val card = _uiState.value.card ?: return
        val size = deck?.size ?: return
        dealt = dealt + card.id
        viewModelScope.launch { instant.markPlayed(card, size) }
    }

    fun tooFar() {
        _uiState.update { it.copy(refusing = it.card) }
    }

    fun dismissSheet() {
        _uiState.update { it.copy(refusing = null, offeringHotter = null) }
    }

    /** "Just this one", or "never show me these again" — the second writes the card's own themes. */
    fun refuse(alsoItsKind: Boolean) {
        val card = _uiState.value.refusing ?: return
        dealt = dealt + card.id
        _uiState.update { it.copy(refusing = null, streak = 0) }
        viewModelScope.launch {
            instant.refuse(card, alsoItsKind)
            // The deck rebuilds itself from the refusal, so the next card comes from the narrower
            // one rather than from where this one was.
            advance()
        }
    }

    fun turnUp(heat: Heat) {
        _uiState.update { it.copy(offeringHotter = null) }
        viewModelScope.launch {
            instant.setHeat(heat)
            advance()
        }
    }

    fun keepHeat() {
        _uiState.update { it.copy(offeringHotter = null) }
        advance()
    }

    private fun advance() {
        position += 1
        stopTimer()
        _uiState.update {
            val place = it.position + 1
            it.copy(
                card = card(),
                played = it.played + 1,
                position = place,
                runComplete = place >= RUN_LENGTH,
            )
        }
    }

    /** The card for where the run is now: at the heat, and one they have not had (D-124). */
    private fun card(): InstantCard? =
        deck?.next(position, _uiState.value.heat, seed, played + dealt)

    companion object {
        /** Long enough to mean they are enjoying it, short enough to arrive in one evening. */
        private const val STREAK_BEFORE_OFFERING = 5

        /** An evening's worth of cards before the app checks whether they want more. */
        const val RUN_LENGTH = 6

        private const val SECONDS_PER_MINUTE = 60
        private const val TICK_MS = 1_000L
    }
}
