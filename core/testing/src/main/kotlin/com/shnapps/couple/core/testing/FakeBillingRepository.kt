package com.shnapps.couple.core.testing

import android.app.Activity
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.billing.BillingRepository
import com.shnapps.couple.core.data.billing.PremiumOffer
import com.shnapps.couple.core.model.Entitlement
import com.shnapps.couple.core.model.SubscriptionState
import com.shnapps.couple.core.model.Tier
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [BillingRepository] that behaves the way the real one does: buying produces nothing
 * until the *server* says what it is worth, so a failure leaves the couple exactly as free as
 * they were.
 */
class FakeBillingRepository(
    initial: Entitlement = Entitlement(),
) : BillingRepository {

    val current = MutableStateFlow(initial)
    override val entitlement = current

    var offer = PremiumOffer(productId = "afterhours.premium.monthly", price = "£4.99", period = "P1M")
    var failNext: AppError? = null
    var restorable: Boolean = false

    var purchases = 0
        private set

    override suspend fun offer(): Outcome<PremiumOffer> = Outcome.Success(offer)

    override suspend fun subscribe(activity: Activity): Outcome<Entitlement> {
        purchases += 1
        failNext?.let {
            failNext = null
            return Outcome.Failure(it)
        }
        return granted()
    }

    override suspend fun restore(): Outcome<Entitlement> {
        failNext?.let {
            failNext = null
            return Outcome.Failure(it)
        }
        if (!restorable) return Outcome.Failure(AppError.NotFound())
        return granted()
    }

    private fun granted(): Outcome<Entitlement> {
        val entitlement = Entitlement(tier = Tier.PREMIUM, state = SubscriptionState.ACTIVE)
        current.value = entitlement
        return Outcome.Success(entitlement)
    }
}
