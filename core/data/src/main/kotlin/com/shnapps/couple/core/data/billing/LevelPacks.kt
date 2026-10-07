package com.shnapps.couple.core.data.billing

import com.shnapps.couple.core.model.Intensity

/**
 * What there is to buy (V2 rule 5).
 *
 * Three products, one per level above the free pair, and **each one includes the levels below it**.
 * That is not a discount, it is the only shape the rest of the app can hold: a level is a ceiling
 * everywhere — the deck climbs towards it, a run escalates towards it, a game's rounds are drawn
 * under it — so a couple who owned Extreme and not Spicy would have a deck with a hole in the
 * middle of it and a climb that skipped two rungs. The top product is therefore also the bundle:
 * "Everything" and "the Extreme pack" are the same purchase, and the store says so rather than
 * listing a fourth tile that does the same thing for a different price.
 *
 * One-time products, not subscriptions. Nothing here expires, which is the point: a couple who
 * bought Hot in March still have Hot in November without having thought about it since.
 */
object LevelPacks {

    /** The product id for the pack that opens [level], or null when that level is free anyway. */
    fun productOf(level: Intensity): String? = PRODUCTS[level]

    /** The level a product id opens, or null when the store offered something we do not sell. */
    fun levelOf(productId: String): Intensity? =
        PRODUCTS.entries.firstOrNull { it.value == productId }?.key

    /** Every product id, for asking the store what it holds and what it costs. */
    val productIds: List<String> get() = PRODUCTS.values.toList()

    private val PRODUCTS = mapOf(
        Intensity.SPICY to "afterhours.pack.spicy",
        Intensity.HOT to "afterhours.pack.hot",
        Intensity.EXTREME to "afterhours.pack.everything",
    )
}
