package com.shnapps.couple.core.data.preferences

import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.model.PreferenceAnswer
import kotlinx.coroutines.flow.Flow

/**
 * The signed-in user's private answers (BUILD_PROMPT.md §5.1, §9.5).
 *
 * Owner-only for life. There is no method here, or anywhere, that reads another person's
 * answers — the security rules would refuse it, and the API does not pretend otherwise.
 * Matching against a partner happens on the server, which only ever materialises the fact
 * that both were positive (§5.2, Phase 7).
 */
interface PreferenceRepository {
    /** The user's own answers, keyed by taxonomy item id. Empty when signed out. */
    val answers: Flow<Map<String, PreferenceAnswer>>

    /** Records an answer, replacing any earlier one for the same item. */
    suspend fun setAnswer(itemId: String, answer: PreferenceAnswer): Outcome<Unit>

    /** Forgets an answer: the item goes back to unanswered, as if never asked. */
    suspend fun clearAnswer(itemId: String): Outcome<Unit>
}
