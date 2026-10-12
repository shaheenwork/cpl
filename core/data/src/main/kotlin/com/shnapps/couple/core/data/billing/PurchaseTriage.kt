package com.shnapps.couple.core.data.billing

/** Where a payment is, as the store reports it. Anything the store has not settled is not a purchase. */
enum class HeldState { PURCHASED, PENDING, UNSPECIFIED }

/**
 * One purchase as the store holds it, with the store's own types taken out so the decisions below
 * can be tested without a store.
 *
 * [products] is a list because the store allows a basket; [token] is what the store asks for when
 * a purchase is acknowledged.
 */
data class HeldPurchase(
    val token: String,
    val products: List<String>,
    val state: HeldState,
    val acknowledged: Boolean,
)

/**
 * What to make of the purchases a store account holds (D-152).
 *
 * Three rules, each of which the first version of the Play client got wrong by omission:
 *
 * - **Only a settled payment is a pack.** A card that is slow to clear, or a cash payment, reports
 *   `PENDING`. That is not money received, and it must not open a level.
 * - **A settled payment has to be acknowledged.** Play refunds a purchase the app has not
 *   acknowledged within three days. Acknowledging only at the moment of purchase loses the packs of
 *   anybody whose phone died, or whose network dropped, between paying and the acknowledgement —
 *   so every settled purchase that is not acknowledged is acknowledged, whenever the store is next asked.
 * - **Only packs we sell count.** A product id the store returns that [LevelPacks] does not know is
 *   somebody else's concern.
 */
object PurchaseTriage {

    private fun HeldPurchase.isPack(): Boolean = products.any { LevelPacks.levelOf(it) != null }

    /** The product ids of packs that are paid for. */
    fun owned(held: List<HeldPurchase>): Set<String> = held
        .filter { it.state == HeldState.PURCHASED }
        .flatMap { it.products }
        .filter { LevelPacks.levelOf(it) != null }
        .toSet()

    /** The tokens of settled packs the store has not been told about yet. */
    fun toAcknowledge(held: List<HeldPurchase>): List<String> = held
        .filter { it.state == HeldState.PURCHASED && !it.acknowledged && it.isPack() }
        .map { it.token }

    /** The first settled pack among what a purchase just produced, or null when none has settled. */
    fun settled(held: List<HeldPurchase>): HeldPurchase? =
        held.firstOrNull { it.state == HeldState.PURCHASED && it.isPack() }

    /** Whether a pack is paid for but not yet cleared: the case that deserves its own sentence. */
    fun pending(held: List<HeldPurchase>): Boolean =
        held.any { it.state == HeldState.PENDING && it.isPack() }
}
