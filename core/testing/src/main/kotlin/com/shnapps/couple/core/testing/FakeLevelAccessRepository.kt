package com.shnapps.couple.core.testing

import com.shnapps.couple.core.data.entitlement.LevelAccess
import com.shnapps.couple.core.data.entitlement.LevelAccessRepository
import com.shnapps.couple.core.model.Intensity
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * How far a phone may go, for a test. A real object with real state, not a mock (D-013).
 *
 * It starts at [LevelAccess.FREE] rather than open, because the free state is the one most worth
 * asserting against: a test that forgets to say what it is testing gets locks rather than silently
 * getting everything.
 */
class FakeLevelAccessRepository(
    initial: LevelAccess = LevelAccess.FREE,
) : LevelAccessRepository {

    override val access = MutableStateFlow(initial)

    /** What a test says the store charges. Priced, because an unpriced store is its own test. */
    var prices: Map<Intensity, String> = LevelAccess.PACKS.associateWith { "£${it.level}.99" }

    override suspend fun packPrices(): Map<Intensity, String> = prices
}
