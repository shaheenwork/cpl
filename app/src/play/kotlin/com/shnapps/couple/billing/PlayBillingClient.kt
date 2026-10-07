package com.shnapps.couple.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.billing.BillingClient
import com.shnapps.couple.core.data.billing.LevelPacks
import com.shnapps.couple.core.data.billing.PackOffer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import com.android.billingclient.api.BillingClient as PlayClient

/**
 * Google Play Billing (V2 rule 5), for `staging` and `prod` only — `dev` has a fake and never loads
 * this library at all (BUILD_PROMPT.md §4.3, D-091).
 *
 * **One-time products, not subscriptions.** A pack is bought once and never expires, so there is
 * nothing to renew, nothing to cancel, and no state where a couple who paid last month find the app
 * quieter this month. That also makes [owned] the authority it needs to be: Play keeps answering
 * with a non-consumable purchase for as long as the account holds it, on this phone and on the next
 * one.
 */
@Singleton
class PlayBillingClient @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : BillingClient {

    private val awaiting = PendingPurchase()
    private val lock = Mutex()

    private val listener = PurchasesUpdatedListener { result, updated ->
        awaiting.complete(
            if (result.responseCode == PlayClient.BillingResponseCode.OK) {
                Outcome.Success(updated.orEmpty().firstOrNull()?.purchaseToken)
            } else {
                Outcome.Failure(result.toAppError())
            },
        )
    }

    private val client: PlayClient = PlayClient.newBuilder(context)
        .setListener(listener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    override suspend fun offers(): Outcome<List<PackOffer>> = lock.withLock {
        when (val connected = connect()) {
            is Outcome.Failure -> connected
            is Outcome.Success -> Outcome.Success(details().mapNotNull(::offerOf))
        }
    }

    override suspend fun purchase(activity: Activity, productId: String): Outcome<String> = lock.withLock {
        when (val connected = connect()) {
            is Outcome.Failure -> connected
            is Outcome.Success -> {
                val details = details().firstOrNull { it.productId == productId }
                if (details == null) Outcome.Failure(AppError.NotFound()) else launch(activity, details)
            }
        }
    }

    override suspend fun owned(): Outcome<Set<String>> = lock.withLock {
        when (val connected = connect()) {
            is Outcome.Failure -> connected
            is Outcome.Success -> {
                val held = client.queryPurchasesAsync(
                    QueryPurchasesParams.newBuilder().setProductType(PlayClient.ProductType.INAPP).build(),
                )
                Outcome.Success(
                    held.purchasesList
                        .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                        .flatMap { it.products }
                        .filter { LevelPacks.levelOf(it) != null }
                        .toSet(),
                )
            }
        }
    }

    /** A one-time product's price, or null for a product the store priced in a way we do not sell. */
    private fun offerOf(details: ProductDetails): PackOffer? {
        val level = LevelPacks.levelOf(details.productId) ?: return null
        val price = details.oneTimePurchaseOfferDetails?.formattedPrice ?: return null
        return PackOffer(level = level, productId = details.productId, price = price)
    }

    private suspend fun launch(activity: Activity, details: ProductDetails): Outcome<String> {
        awaiting.expect()
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build(),
                ),
            )
            .build()
        val launched = client.launchBillingFlow(activity, params)
        if (launched.responseCode != PlayClient.BillingResponseCode.OK) {
            return Outcome.Failure(launched.toAppError())
        }
        return when (val token = awaiting.await()) {
            is Outcome.Failure -> token
            is Outcome.Success -> {
                val purchaseToken = token.value ?: return Outcome.Failure(AppError.Unknown())
                acknowledge(purchaseToken)
                Outcome.Success(purchaseToken)
            }
        }
    }

    /**
     * Play refunds an unacknowledged purchase after three days. Acknowledging says this app has it
     * — and because these are non-consumable, it is never consumed afterwards, which is what makes
     * a restore on a new phone work.
     */
    private suspend fun acknowledge(purchaseToken: String) {
        client.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchaseToken).build(),
        )
    }

    /** All three packs in one query, because the store screen shows all three. */
    private suspend fun details(): List<ProductDetails> {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                LevelPacks.productIds.map { product ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(product)
                        .setProductType(PlayClient.ProductType.INAPP)
                        .build()
                },
            )
            .build()
        return client.queryProductDetails(params).productDetailsList.orEmpty()
    }

    private suspend fun connect(): Outcome<Unit> {
        if (client.isReady) return Outcome.Success(Unit)
        val ready = CompletableDeferred<Outcome<Unit>>()
        client.startConnection(
            object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    ready.complete(
                        if (result.responseCode == PlayClient.BillingResponseCode.OK) {
                            Outcome.Success(Unit)
                        } else {
                            Outcome.Failure(result.toAppError())
                        },
                    )
                }

                override fun onBillingServiceDisconnected() {
                    ready.complete(Outcome.Failure(AppError.Network()))
                }
            },
        )
        return ready.await()
    }
}

/** One purchase at a time: the listener belongs to the client, the flow that started it does not. */
private class PendingPurchase {
    private var pending: CompletableDeferred<Outcome<String?>>? = null

    fun expect() {
        pending = CompletableDeferred()
    }

    fun complete(result: Outcome<String?>) {
        pending?.complete(result)
    }

    suspend fun await(): Outcome<String?> = pending?.await() ?: Outcome.Failure(AppError.Unknown())
}

private fun BillingResult.toAppError(): AppError = when (responseCode) {
    PlayClient.BillingResponseCode.USER_CANCELED -> AppError.Validation("cancelled")
    PlayClient.BillingResponseCode.SERVICE_DISCONNECTED,
    PlayClient.BillingResponseCode.SERVICE_UNAVAILABLE,
    PlayClient.BillingResponseCode.NETWORK_ERROR,
    -> AppError.Network()
    PlayClient.BillingResponseCode.ITEM_ALREADY_OWNED -> AppError.Conflict()
    PlayClient.BillingResponseCode.BILLING_UNAVAILABLE,
    PlayClient.BillingResponseCode.FEATURE_NOT_SUPPORTED,
    -> AppError.Validation("billing-unavailable")
    else -> AppError.Unknown()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PlayBillingModule {
    @Binds
    abstract fun bindBillingClient(impl: PlayBillingClient): BillingClient
}
