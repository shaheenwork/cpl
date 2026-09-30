package com.shnapps.couple.feature.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shnapps.couple.core.analytics.AnalyticsEvent
import com.shnapps.couple.core.analytics.AnalyticsLogger
import com.shnapps.couple.core.data.mutual.MutualRepository
import com.shnapps.couple.core.data.taxonomy.TaxonomyRepository
import com.shnapps.couple.core.model.MatchLevel
import com.shnapps.couple.core.model.MutualMatch
import com.shnapps.couple.core.model.Taxonomy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where one reveal is. The screen runs the animation between [Revealing] and [Revealed]. */
enum class RevealStep { Concealed, Revealing, Revealed }

/** A match, in the taxonomy's words. Nothing in it says who answered what. */
data class MatchCard(
    val itemId: String,
    val prompt: String,
    val description: String,
    val categoryTitle: String,
    val level: MatchLevel,
)

data class MutualDiscoveryUiState(
    val loading: Boolean = true,
    /** The match being revealed, one at a time (§14.7). Null when none are new. */
    val current: MatchCard? = null,
    val step: RevealStep = RevealStep.Concealed,
    /** New matches waiting after [current]. */
    val moreAfter: Int = 0,
    /** Matches already revealed to this user, newest first. */
    val shared: List<MatchCard> = emptyList(),
) {
    val isSecret: Boolean get() = current?.level == MatchLevel.BOTH_SECRET
}

/**
 * Mutual discovery (BUILD_PROMPT.md §14.7): what both partners chose, revealed as single,
 * suspenseful moments — never a list of results dumped at once.
 *
 * The server decides when a match exists and when it may be seen (§5.2, §5.3); this only
 * paces the reveal on screen. A card being revealed stays put until the user moves on, even
 * after it is marked seen, so the moment is not cut short by its own bookkeeping.
 */
@HiltViewModel
class MutualDiscoveryViewModel @Inject constructor(
    taxonomyRepository: TaxonomyRepository,
    private val mutual: MutualRepository,
    private val analytics: AnalyticsLogger,
) : ViewModel() {

    private data class Session(
        val pinned: String? = null,
        val step: RevealStep = RevealStep.Concealed,
        /** Revealed on this screen, whether or not the server has recorded it yet. */
        val revealedHere: Set<String> = emptySet(),
        val logged: Boolean = false,
    )

    private val session = MutableStateFlow(Session())

    val uiState: StateFlow<MutualDiscoveryUiState> =
        combine(taxonomyRepository.taxonomy, mutual.matches, session, ::render)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MutualDiscoveryUiState())

    init {
        // Opening this screen is an app open in the sense of §5.3: release anything that has
        // already waited long enough. Failure only means nothing extra arrives yet.
        viewModelScope.launch { mutual.releaseNow() }
    }

    fun reveal() {
        val state = uiState.value
        val current = state.current ?: return
        if (!session.value.logged) {
            // A count only, once per visit: how many were new when the first was opened (§17.3).
            analytics.log(AnalyticsEvent.MutualInterestFound(count = state.moreAfter + 1))
        }
        session.update { it.copy(pinned = current.itemId, step = RevealStep.Revealing, logged = true) }
    }

    /** Called by the screen once the reveal animation has run its course. */
    fun onRevealed() {
        val itemId = session.value.pinned ?: return
        session.update { it.copy(step = RevealStep.Revealed, revealedHere = it.revealedHere + itemId) }
        viewModelScope.launch { mutual.markSeen(itemId) }
    }

    /** On to the next new match, or to everything shared once there are none left. */
    fun next() = session.update { it.copy(pinned = null, step = RevealStep.Concealed) }

    private fun render(taxonomy: Taxonomy, matches: List<MutualMatch>, session: Session): MutualDiscoveryUiState {
        // A match the taxonomy no longer knows (a retired item) is not shown at all.
        val cards = matches.mapNotNull { match -> taxonomy.cardFor(match)?.let { match to it } }
        val newOnes = cards.filter { (match, _) -> !match.seen && match.itemId !in session.revealedHere }

        // The card on screen stays until the user moves on — unless the match itself was
        // withdrawn in the meantime, in which case it goes.
        val pinned = session.pinned?.let { id -> cards.firstOrNull { (match, _) -> match.itemId == id } }
        val current = pinned ?: newOnes.firstOrNull()
        val currentId = current?.first?.itemId

        return MutualDiscoveryUiState(
            loading = false,
            current = current?.second,
            step = if (pinned != null) session.step else RevealStep.Concealed,
            moreAfter = newOnes.count { (match, _) -> match.itemId != currentId },
            shared = cards
                .filter { (match, _) -> match.itemId != currentId && newOnes.none { it.first.itemId == match.itemId } }
                .sortedByDescending { (match, _) -> match.revealedAtEpochMillis }
                .map { it.second },
        )
    }

    private fun Taxonomy.cardFor(match: MutualMatch): MatchCard? {
        val item = item(match.itemId) ?: return null
        return MatchCard(
            itemId = item.id,
            prompt = item.prompt,
            description = item.description,
            categoryTitle = category(item.categoryId)?.title.orEmpty(),
            level = match.level,
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
