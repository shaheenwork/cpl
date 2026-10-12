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
import com.shnapps.couple.core.data.billing.HeldPurchase
import com.shnapps.couple.core.data.billing.HeldState
import com.shnapps.couple.core.data.billing.LevelPacks
import com.shnapps.couple.core.data.billing.PackOffer
import com.shnapps.couple.core.data.billing.PurchaseTriage
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
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
 *
 * What counts as bought, and what has to be acknowledged, is decided by [PurchaseTriage] (D-152),
 * which has tests; this class only talks to Play.
 */
@Singleton
class PlayBillingClient @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : BillingClient {

    private val awaiting = PendingPurchase()
    private val lock = Mutex()

    /** For work nobody is waiting on: acknowledging a payment that cleared while the app was open. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override val updates: Flow<Unit> = changes.asSharedFlow()

    private val listener = PurchasesUpdatedListener { result, updated -> onUpdated(result, updated.orEmpty()) }

    private val client: PlayClient = PlayClient.newBuilder(context)
        .setListener(listener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    override suspend fun offers(): Outcome<List<PackOffer>> = lock.withLock {
        when (val connected = connect()) {
            is Outcome.Failure -> connected
            is Outcome.Success -> details().map { it.mapNotNull(::offerOf) }
        }
    }

    override suspend fun purchase(activity: Activity, productId: String): Outcome<String> = lock.withLock {
        when (val connected = connect()) {
            is Outcome.Failure -> connected
            is Outcome.Success -> when (val listed = details()) {
                is Outcome.Failure -> listed
                is Outcome.Success -> {
                    val details = listed.value.firstOrNull { it.productId == productId }
                    // Not "nothing to restore": the store has no such product, which is a setup
                    // fact (missing or inactive in the Play Console), not a refund.
                    if (details == null) {
                        Outcome.Failure(AppError.Validation("not-for-sale"))
                    } else {
                        launch(activity, details)
                    }
                }
            }
        }
    }

    override suspend fun owned(): Outcome<Set<String>> = lock.withLock {
        when (val connected = connect()) {
            is Outcome.Failure -> connected
            is Outcome.Success -> {
                val result = client.queryPurchasesAsync(
                    QueryPurchasesParams.newBuilder().setProductType(PlayClient.ProductType.INAPP).build(),
                )
                // An error is not "holds nothing": reporting it as that told a couple with a
                // perfectly good purchase that there was nothing to restore.
                if (result.billingResult.responseCode != PlayClient.BillingResponseCode.OK) {
                    Outcome.Failure(result.billingResult.toAppError())
                } else {
                    val held = result.purchasesList.map(::heldOf)
                    // Every restore also finishes what an earlier run did not: a payment made just
                    // before the app was killed is acknowledged here, inside Play's three days.
                    PurchaseTriage.toAcknowledge(held).forEach { acknowledge(it) }
                    Outcome.Success(PurchaseTriage.owned(held))
                }
            }
        }
    }

    /**
     * Play told the app about a purchase it did not ask for: a payment that was pending has
     * cleared, or a code was redeemed in the Play app. If a purchase flow is open it is that flow's
     * answer; otherwise the payment is acknowledged and the repository is told to look again.
     */
    private fun onUpdated(result: BillingResult, updated: List<Purchase>) {
        if (awaiting.isWaiting) {
            awaiting.complete(settle(result, updated.map(::heldOf)))
            return
        }
        if (result.responseCode != PlayClient.BillingResponseCode.OK) return
        scope.launch {
            lock.withLock {
                PurchaseTriage.toAcknowledge(updated.map(::heldOf)).forEach { acknowledge(it) }
            }
            changes.emit(Unit)
        }
    }

    /** What a purchase flow that has just ended produced: a pack, a payment still clearing, or nothing. */
    private fun settle(result: BillingResult, held: List<HeldPurchase>): Outcome<String> {
        if (result.responseCode != PlayClient.BillingResponseCode.OK) return Outcome.Failure(result.toAppError())
        PurchaseTriage.settled(held)?.let { return Outcome.Success(it.token) }
        return Outcome.Failure(
            if (PurchaseTriage.pending(held)) AppError.Validation("pending") else AppError.Unknown(),
        )
    }

    private fun heldOf(purchase: Purchase) = HeldPurchase(
        token = purchase.purchaseToken,
        products = purchase.products,
        state = when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> HeldState.PURCHASED
            Purchase.PurchaseState.PENDING -> HeldState.PENDING
            else -> HeldState.UNSPECIFIED
        },
        acknowledged = purchase.isAcknowledged,
    )

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
            awaiting.cancel()
            return Outcome.Failure(launched.toAppError())
        }
        return when (val settled = awaiting.await()) {
            is Outcome.Failure -> settled
            is Outcome.Success -> {
                // If this fails the pack is still theirs: the purchase is held unacknowledged, and
                // the next restore acknowledges it (D-152).
                acknowledge(settled.value)
                settled
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
    private suspend fun details(): Outcome<List<ProductDetails>> {
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
        val result = client.queryProductDetails(params)
        return if (result.billingResult.responseCode == PlayClient.BillingResponseCode.OK) {
            Outcome.Success(result.productDetailsList.orEmpty())
        } else {
            Outcome.Failure(result.billingResult.toAppError())
        }
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

/**
 * One purchase at a time: the listener belongs to the client, the flow that started it does not.
 *
 * Once a flow has been answered it is no longer waiting, so a later update from Play, such as a
 * payment that was pending and has cleared, is seen as what it is: news nobody asked for.
 */
private class PendingPurchase {
    @Volatile
    private var pending: CompletableDeferred<Outcome<String>>? = null

    val isWaiting: Boolean get() = pending?.isActive == true

    fun expect() {
        pending = CompletableDeferred()
    }

    fun complete(result: Outcome<String>) {
        pending?.complete(result)
    }

    /** The flow never opened, so nothing will answer it. */
    fun cancel() {
        pending?.cancel()
        pending = null
    }

    suspend fun await(): Outcome<String> = pending?.await() ?: Outcome.Failure(AppError.Unknown())
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
    // The store knows the app and not the product (inactive, or not in this track), or the build is
    // not one Play recognises. Both read the same to a couple: this pack cannot be bought here yet.
    PlayClient.BillingResponseCode.ITEM_UNAVAILABLE,
    PlayClient.BillingResponseCode.DEVELOPER_ERROR,
    -> AppError.Validation("not-for-sale")
    else -> AppError.Unknown()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PlayBillingModule {
    @Binds
    abstract fun bindBillingClient(impl: PlayBillingClient): BillingClient
}
