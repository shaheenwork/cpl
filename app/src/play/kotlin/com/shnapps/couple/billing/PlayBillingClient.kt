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
import com.shnapps.couple.core.data.billing.PremiumOffer
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
 * Google Play Billing (BUILD_PROMPT.md §19), for `staging` and `prod` only — `dev` has a fake and
 * never loads this library at all (§4.3, D-091).
 *
 * All this produces is a purchase token. What it is worth is verified server-side against
 * `androidpublisher`, and the entitlement is written there: a phone that lies about a purchase
 * gets nothing, because nothing here writes anything (§7.2, §7.3 row I).
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

    override suspend fun offer(): Outcome<PremiumOffer> = lock.withLock {
        when (val connected = connect()) {
            is Outcome.Failure -> connected
            is Outcome.Success -> when (val details = details()) {
                is Outcome.Failure -> details
                is Outcome.Success -> Outcome.Success(offerOf(details.value))
            }
        }
    }

    override suspend fun purchase(activity: Activity): Outcome<String> = lock.withLock {
        when (val connected = connect()) {
            is Outcome.Failure -> connected
            is Outcome.Success -> when (val details = details()) {
                is Outcome.Failure -> details
                is Outcome.Success -> launch(activity, details.value)
            }
        }
    }

    override suspend fun restore(): Outcome<String?> = lock.withLock {
        when (val connected = connect()) {
            is Outcome.Failure -> connected
            is Outcome.Success -> {
                val held = client.queryPurchasesAsync(
                    QueryPurchasesParams.newBuilder().setProductType(PlayClient.ProductType.SUBS).build(),
                )
                Outcome.Success(
                    held.purchasesList
                        .firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                        ?.purchaseToken,
                )
            }
        }
    }

    private fun offerOf(details: ProductDetails): PremiumOffer {
        val phase = details.subscriptionOfferDetails
            ?.firstOrNull()
            ?.pricingPhases
            ?.pricingPhaseList
            ?.firstOrNull()
        return PremiumOffer(
            productId = PRODUCT,
            price = phase?.formattedPrice.orEmpty(),
            period = phase?.billingPeriod.orEmpty(),
        )
    }

    private suspend fun launch(activity: Activity, details: ProductDetails): Outcome<String> {
        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
            ?: return Outcome.Failure(AppError.NotFound())
        awaiting.expect()
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offerToken)
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
     * Play cancels an unacknowledged purchase after three days. Acknowledging says this app has
     * it; it says nothing about entitlement, which is the server's to decide.
     */
    private suspend fun acknowledge(purchaseToken: String) {
        client.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchaseToken).build(),
        )
    }

    private suspend fun details(): Outcome<ProductDetails> {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT)
                        .setProductType(PlayClient.ProductType.SUBS)
                        .build(),
                ),
            )
            .build()
        val details = client.queryProductDetails(params).productDetailsList?.firstOrNull()
        return if (details == null) Outcome.Failure(AppError.NotFound()) else Outcome.Success(details)
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

    private companion object {
        const val PRODUCT = "afterhours.premium.monthly"
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
