package com.shnapps.couple.core.testing

import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.mutual.MutualRepository
import com.shnapps.couple.core.model.MutualMatch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory [MutualRepository]. Tests play the server: [reveal] makes a match appear, the way
 * a release batch would.
 */
class FakeMutualRepository(
    initial: List<MutualMatch> = emptyList(),
) : MutualRepository {

    private val state = MutableStateFlow(initial)
    override val matches: StateFlow<List<MutualMatch>> = state

    var markSeenResult: Outcome<Unit> = Outcome.Success(Unit)
    val seenCalls = mutableListOf<String>()
    var releaseCalls = 0
        private set

    fun reveal(match: MutualMatch) = state.update { it + match }

    /** The server takes a match back (a partner changed their answer). */
    fun withdraw(itemId: String) = state.update { matches -> matches.filterNot { it.itemId == itemId } }

    override suspend fun markSeen(itemId: String): Outcome<Unit> {
        seenCalls += itemId
        return markSeenResult.also {
            if (it is Outcome.Success) {
                state.update { matches -> matches.map { m -> if (m.itemId == itemId) m.copy(seen = true) else m } }
            }
        }
    }

    override suspend fun releaseNow(): Outcome<Int> {
        releaseCalls++
        return Outcome.Success(0)
    }
}
