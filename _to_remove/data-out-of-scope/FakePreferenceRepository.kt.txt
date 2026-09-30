package com.shnapps.couple.core.testing

import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.preferences.PreferenceRepository
import com.shnapps.couple.core.model.PreferenceAnswer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory [PreferenceRepository]. A successful write lands in [answers] immediately, the
 * way Firestore's local cache reports a write before the server confirms it.
 */
class FakePreferenceRepository(
    initial: Map<String, PreferenceAnswer> = emptyMap(),
) : PreferenceRepository {

    private val state = MutableStateFlow(initial)
    override val answers: StateFlow<Map<String, PreferenceAnswer>> = state

    var setResult: Outcome<Unit> = Outcome.Success(Unit)
    var clearResult: Outcome<Unit> = Outcome.Success(Unit)

    /** Every write attempted, in order — including ones that failed. */
    val writes = mutableListOf<Pair<String, PreferenceAnswer>>()
    val clears = mutableListOf<String>()

    override suspend fun setAnswer(itemId: String, answer: PreferenceAnswer): Outcome<Unit> {
        writes += itemId to answer
        return setResult.also { if (it is Outcome.Success) state.update { current -> current + (itemId to answer) } }
    }

    override suspend fun clearAnswer(itemId: String): Outcome<Unit> {
        clears += itemId
        return clearResult.also { if (it is Outcome.Success) state.update { current -> current - itemId } }
    }
}
