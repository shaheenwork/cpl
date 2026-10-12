package com.shnapps.couple.core.testing

import android.app.Activity
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.billing.BillingRepository
import com.shnapps.couple.core.data.billing.LevelPacks
import com.shnapps.couple.core.data.billing.PackOffer
import com.shnapps.couple.core.model.Entitlement
import com.shnapps.couple.core.model.Intensity
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [BillingRepository] that behaves the way the real one does: a purchase is recorded on
 * the phone, a restore asks the store, and a failure leaves the couple exactly as free as they
 * were. A real object with real state, not a mock (D-013).
 */
class FakeBillingRepository(
    initial: Entitlement = Entitlement(),
) : BillingRepository {

    val current = MutableStateFlow(initial)
    override val entitlement = current

    /** What the partner has bought, as the couple's record would say it (D-149). */
    val partner = MutableStateFlow<Set<Intensity>>(emptySet())
    override val partnerPacks = partner

    /** How many times something has started sharing what this phone holds. */
    var shares = 0
        private set

    override suspend fun shareOwnership() {
        shares += 1
        awaitCancellation()
    }

    /** How many times something has started keeping what the store holds up to date (D-152). */
    var keeps = 0
        private set

    override suspend fun keepRestored() {
        keeps += 1
        awaitCancellation()
    }

    /** What this fake store holds for the account, for a restore to find. */
    var holding: Set<Intensity> = emptySet()

    var failNext: AppError? = null

    /** What has been bought through this fake, in order, so a test can assert the one tap. */
    val bought = mutableListOf<Intensity>()

    /** How many times the store has been asked what it holds. */
    var restores = 0
        private set

    override suspend fun offers(): Outcome<List<PackOffer>> = Outcome.Success(
        LevelPacks.productIds.mapNotNull { product ->
            LevelPacks.levelOf(product)?.let { PackOffer(it, product, price = "£${it.level}.99") }
        },
    )

    override suspend fun buy(activity: Activity, level: Intensity): Outcome<Set<Intensity>> {
        failNext?.let {
            failNext = null
            return Outcome.Failure(it)
        }
        bought += level
        holding = holding + level
        return Outcome.Success(setOf(level))
    }

    override suspend fun restore(): Outcome<Set<Intensity>> {
        restores += 1
        failNext?.let {
            failNext = null
            return Outcome.Failure(it)
        }
        return Outcome.Success(holding)
    }
}
