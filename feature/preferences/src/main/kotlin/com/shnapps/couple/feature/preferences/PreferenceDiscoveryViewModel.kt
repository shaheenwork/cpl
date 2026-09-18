package com.shnapps.couple.feature.preferences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shnapps.couple.core.analytics.AnalyticsEvent
import com.shnapps.couple.core.analytics.AnalyticsLogger
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.auth.AuthRepository
import com.shnapps.couple.core.data.preferences.PreferenceRepository
import com.shnapps.couple.core.data.taxonomy.TaxonomyRepository
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.PreferenceAnswer
import com.shnapps.couple.core.model.PreferenceItem
import com.shnapps.couple.core.model.Taxonomy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where the user is in private discovery. */
sealed interface DiscoveryStage {
    data object Loading : DiscoveryStage

    /** Why this is private, how far the questions go, and a way in. */
    data object Intro : DiscoveryStage

    /** One card at a time through this session's deck. */
    data object Answering : DiscoveryStage

    /** The deck is finished, or the user stopped for now. */
    data object Done : DiscoveryStage

    /** Everything the user has answered, to look over or change. */
    data object Review : DiscoveryStage

    /** One earlier answer, open to change or remove. */
    data object Editing : DiscoveryStage
}

/** A card on screen, with whatever the user already said about it. */
data class DiscoveryCard(
    val item: PreferenceItem,
    val categoryTitle: String,
    val answer: PreferenceAnswer?,
)

data class ReviewSection(val categoryTitle: String, val entries: List<ReviewEntry>)

data class ReviewEntry(val item: PreferenceItem, val answer: PreferenceAnswer)

data class PreferenceDiscoveryUiState(
    val stage: DiscoveryStage = DiscoveryStage.Loading,
    val contentLevel: Intensity = Intensity.FLIRTY,
    /** Unanswered cards at [contentLevel]: what starting would deal. */
    val remainingCount: Int = 0,
    /** Every answer the user has given, at any level. */
    val answeredCount: Int = 0,
    val card: DiscoveryCard? = null,
    /** 1-based position of [card] in this session's deck. Zero outside [DiscoveryStage.Answering]. */
    val position: Int = 0,
    val deckSize: Int = 0,
    val answeredThisSession: Int = 0,
    val review: List<ReviewSection> = emptyList(),
    val errorMessage: String? = null,
) {
    /** Whether back steps within discovery rather than leaving it. */
    val handlesBack: Boolean
        get() = stage == DiscoveryStage.Answering ||
            stage == DiscoveryStage.Review ||
            stage == DiscoveryStage.Editing
}

/**
 * Private preference discovery (BUILD_PROMPT.md §9.5, §14.7).
 *
 * Every answer is the user's alone (§5.1). Nothing in this ViewModel knows or asks about a
 * partner: matching happens on the server, which only ever materialises the fact that both
 * were positive (§5.2). Analytics gets counts, never an answer (§17.3).
 *
 * A session deals a **snapshot** of the unanswered cards when it starts, gentlest first,
 * so answering never reshuffles what is still to come. Answers are saved as they are
 * given; there is nothing to lose by leaving halfway, and skipped cards come back next
 * time.
 */
@HiltViewModel
class PreferenceDiscoveryViewModel @Inject constructor(
    taxonomyRepository: TaxonomyRepository,
    private val preferences: PreferenceRepository,
    private val auth: AuthRepository,
    private val analytics: AnalyticsLogger,
) : ViewModel() {

    private data class Inputs(
        val taxonomy: Taxonomy,
        val answers: Map<String, PreferenceAnswer>,
        val contentLevel: Intensity,
    )

    private data class Session(
        val stage: DiscoveryStage = DiscoveryStage.Intro,
        val deck: List<String> = emptyList(),
        val index: Int = 0,
        val editing: String? = null,
        val answered: Set<String> = emptySet(),
        /** Where closing the review returns to. */
        val reviewReturn: DiscoveryStage = DiscoveryStage.Intro,
        val error: String? = null,
    )

    private val session = MutableStateFlow(Session())

    private val contentLevel = auth.currentProfile
        .map { it?.contentLevel ?: Intensity.FLIRTY }
        .distinctUntilChanged()

    private val inputs: StateFlow<Inputs?> =
        combine(taxonomyRepository.taxonomy, preferences.answers, contentLevel, ::Inputs)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val uiState: StateFlow<PreferenceDiscoveryUiState> =
        combine(inputs, session, ::render)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PreferenceDiscoveryUiState())

    /**
     * The user's own ceiling. Only ever theirs: the couple goes as far as the more cautious
     * partner, which the server works out, so nobody can raise it alone.
     */
    fun setContentLevel(level: Intensity) {
        if (level == inputs.value?.contentLevel) return
        viewModelScope.launch {
            if (auth.setContentLevel(level) is Outcome.Failure) {
                session.update { it.copy(error = LEVEL_NOT_SAVED) }
            }
        }
    }

    fun start() {
        val current = inputs.value ?: return
        val deck = deckFor(current).map { it.id }
        if (deck.isEmpty()) return
        session.update { Session(stage = DiscoveryStage.Answering, deck = deck) }
        analytics.log(AnalyticsEvent.PreferencesStarted)
    }

    fun answer(answer: PreferenceAnswer) {
        val current = session.value
        when (current.stage) {
            DiscoveryStage.Answering -> {
                val itemId = current.deck.getOrNull(current.index) ?: return
                save(itemId, answer)
                advance(answeredId = itemId)
            }
            DiscoveryStage.Editing -> {
                val itemId = current.editing ?: return
                save(itemId, answer)
                session.update { it.copy(stage = DiscoveryStage.Review, editing = null) }
            }
            else -> Unit
        }
    }

    /** Leaves the card unanswered. It comes back in a later session. */
    fun skip() {
        if (session.value.stage == DiscoveryStage.Answering) advance(answeredId = null)
    }

    /** Steps back within discovery: to the previous card, out of an edit, or out of the review. */
    fun back() = session.update { current ->
        when (current.stage) {
            DiscoveryStage.Answering -> if (current.index > 0) {
                current.copy(index = current.index - 1)
            } else {
                current.copy(stage = DiscoveryStage.Intro)
            }
            DiscoveryStage.Editing -> current.copy(stage = DiscoveryStage.Review, editing = null)
            DiscoveryStage.Review -> current.copy(stage = current.reviewReturn)
            else -> current
        }
    }

    /** Stops for now. Everything answered is already saved. */
    fun finishLater() = session.update { current ->
        if (current.stage == DiscoveryStage.Answering) current.copy(stage = DiscoveryStage.Done) else current
    }

    fun openReview() = session.update { current ->
        val returnTo = if (current.stage == DiscoveryStage.Done) DiscoveryStage.Done else DiscoveryStage.Intro
        current.copy(stage = DiscoveryStage.Review, editing = null, reviewReturn = returnTo)
    }

    fun edit(itemId: String) = session.update { it.copy(stage = DiscoveryStage.Editing, editing = itemId) }

    /** Forgets the answer being edited: the item goes back to unanswered. */
    fun clearAnswer() {
        val itemId = session.value.editing ?: return
        session.update { it.copy(stage = DiscoveryStage.Review, editing = null) }
        viewModelScope.launch {
            if (preferences.clearAnswer(itemId) is Outcome.Failure) {
                session.update { it.copy(error = ANSWER_NOT_REMOVED) }
            }
        }
    }

    fun dismissError() = session.update { it.copy(error = null) }

    private fun save(itemId: String, answer: PreferenceAnswer) {
        // Unchanged answers are not rewritten: a write costs money and moves updatedAt,
        // which would make an old answer look freshly given.
        if (inputs.value?.answers?.get(itemId) == answer) return
        viewModelScope.launch {
            // Firestore applies the write locally at once and syncs when it can, so an
            // offline answer does not block the next card. A failure here is a refusal.
            if (preferences.setAnswer(itemId, answer) is Outcome.Failure) {
                session.update { it.copy(error = ANSWER_NOT_SAVED) }
            }
        }
    }

    private fun advance(answeredId: String?) {
        var finished = false
        session.update { current ->
            val answered = if (answeredId != null) current.answered + answeredId else current.answered
            val next = current.index + 1
            finished = next >= current.deck.size
            if (finished) {
                current.copy(stage = DiscoveryStage.Done, answered = answered)
            } else {
                current.copy(index = next, answered = answered)
            }
        }
        if (finished) {
            // A count, never which items or what was said (§17.3).
            analytics.log(AnalyticsEvent.PreferencesCompleted(answeredCount = session.value.answered.size))
        }
    }

    private fun render(inputs: Inputs?, session: Session): PreferenceDiscoveryUiState {
        if (inputs == null) return PreferenceDiscoveryUiState(stage = DiscoveryStage.Loading)
        val (taxonomy, answers, level) = inputs

        val cardId = when (session.stage) {
            DiscoveryStage.Answering -> session.deck.getOrNull(session.index)
            DiscoveryStage.Editing -> session.editing
            else -> null
        }
        val card = cardId?.let(taxonomy::item)?.let { item ->
            DiscoveryCard(
                item = item,
                categoryTitle = taxonomy.category(item.categoryId)?.title.orEmpty(),
                answer = answers[item.id],
            )
        }

        return PreferenceDiscoveryUiState(
            stage = session.stage,
            contentLevel = level,
            remainingCount = deckFor(inputs).size,
            answeredCount = answers.keys.count { taxonomy.item(it) != null },
            card = card,
            position = if (session.stage == DiscoveryStage.Answering) session.index + 1 else 0,
            deckSize = session.deck.size,
            answeredThisSession = session.answered.size,
            review = if (session.stage == DiscoveryStage.Review) reviewOf(taxonomy, answers) else emptyList(),
            errorMessage = session.error,
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        const val ANSWER_NOT_SAVED = "That answer didn't save. Try it again in a moment."
        const val ANSWER_NOT_REMOVED = "That answer wasn't removed. Try again in a moment."
        const val LEVEL_NOT_SAVED = "That change didn't save. Try again in a moment."

        /**
         * The unanswered cards at the user's level, gentlest first. A stable sort, so the
         * authored order holds within each level and every session deals the same way.
         */
        fun deckFor(inputs: Inputs): List<PreferenceItem> = inputs.taxonomy.items
            .filter { it.isAvailableAt(inputs.contentLevel) && it.id !in inputs.answers }
            .sortedBy { it.intensityFloor.level }

        fun reviewOf(taxonomy: Taxonomy, answers: Map<String, PreferenceAnswer>): List<ReviewSection> =
            taxonomy.categories.mapNotNull { category ->
                val entries = category.themes
                    .flatMap { it.items }
                    .mapNotNull { item -> answers[item.id]?.let { ReviewEntry(item, it) } }
                if (entries.isEmpty()) null else ReviewSection(category.title, entries)
            }
    }
}
