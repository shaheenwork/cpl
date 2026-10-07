package com.shnapps.couple.billing

import android.app.Activity
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.billing.BillingClient
import com.shnapps.couple.core.data.billing.LevelPacks
import com.shnapps.couple.core.data.billing.PackOffer
import com.shnapps.couple.core.model.Intensity
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The `dev` build's store (BUILD_PROMPT.md §4.3, D-091).
 *
 * It sells nothing and charges nobody: it prices the packs in play money and returns a token
 * beginning `dev-`, which the emulator's verifier accepts and a real one never would. Everything
 * above it — the record on the phone, the ceiling, the locks on the dial — is the code that runs
 * in production.
 *
 * It remembers a purchase for as long as the process lives, so a device check can buy Hot, see the
 * dial open, and still have it after walking back to home. It is deliberately *not* persisted:
 * `dev-couple.js free` exists for taking it away again, and a fresh launch is the honest free
 * state.
 */
@Singleton
class FakeBillingClient @Inject constructor() : BillingClient {

    private val bought = mutableSetOf<String>()

    override suspend fun offers(): Outcome<List<PackOffer>> = Outcome.Success(
        LevelPacks.productIds.mapNotNull { product ->
            LevelPacks.levelOf(product)?.let { level ->
                PackOffer(level = level, productId = product, price = price(level))
            }
        },
    )

    override suspend fun purchase(activity: Activity, productId: String): Outcome<String> {
        bought += productId
        return Outcome.Success("dev-" + System.currentTimeMillis())
    }

    override suspend fun owned(): Outcome<Set<String>> = Outcome.Success(bought.toSet())

    /**
     * Three different numbers, so the store screen can be judged on a device.
     *
     * Marked "(dev)" because nothing here charges anybody, and a price with no such mark on a
     * screenshot is the sort of thing that ends up in a store listing.
     */
    private fun price(level: Intensity): String = when (level) {
        Intensity.SPICY -> "2.99 (dev)"
        Intensity.HOT -> "4.99 (dev)"
        else -> "6.99 (dev)"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DevBillingModule {
    @Binds
    abstract fun bindBillingClient(impl: FakeBillingClient): BillingClient
}
