package com.shnapps.couple.core.testing

import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.boundaries.BoundaryRepository
import com.shnapps.couple.core.model.Boundary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** In-memory [BoundaryRepository]. A successful write lands in [boundaries] immediately. */
class FakeBoundaryRepository(
    initial: Map<String, Boundary> = emptyMap(),
) : BoundaryRepository {

    private val state = MutableStateFlow(initial)
    override val boundaries: StateFlow<Map<String, Boundary>> = state

    var setResult: Outcome<Unit> = Outcome.Success(Unit)
    var clearResult: Outcome<Unit> = Outcome.Success(Unit)

    /** Every write attempted, in order — including ones that failed. */
    val writes = mutableListOf<Pair<String, Boundary>>()
    val clears = mutableListOf<String>()

    override suspend fun setBoundary(themeId: String, boundary: Boundary): Outcome<Unit> {
        writes += themeId to boundary
        return setResult.also { if (it is Outcome.Success) state.update { current -> current + (themeId to boundary) } }
    }

    override suspend fun clearBoundary(themeId: String): Outcome<Unit> {
        clears += themeId
        return clearResult.also { if (it is Outcome.Success) state.update { current -> current - themeId } }
    }
}
