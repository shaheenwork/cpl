package com.shnapps.couple.billing

import android.app.Activity
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.billing.BillingClient
import com.shnapps.couple.core.data.billing.PremiumOffer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The `dev` build's store (BUILD_PROMPT.md §4.3, D-091).
 *
 * It sells nothing and charges nobody: it produces a token beginning `dev-`, which the emulator's
 * verifier accepts and a real one never would. Everything below it — the callable, the server's
 * record, the couple's tier, every gate — is the code that runs in production.
 */
@Singleton
class FakeBillingClient @Inject constructor() : BillingClient {

    override suspend fun offer(): Outcome<PremiumOffer> =
        Outcome.Success(PremiumOffer(productId = PRODUCT, price = "0.00 (dev)", period = "month"))

    override suspend fun purchase(activity: Activity): Outcome<String> =
        Outcome.Success("dev-" + System.currentTimeMillis())

    override suspend fun restore(): Outcome<String?> = Outcome.Success(null)

    private companion object {
        const val PRODUCT = "afterhours.premium.monthly"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DevBillingModule {
    @Binds
    abstract fun bindBillingClient(impl: FakeBillingClient): BillingClient
}
