package com.shnapps.couple.core.data.billing

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * What the store holds, and what to do about it (D-152). These are the cases a real store produces
 * and a fake never does: a payment still clearing, a purchase nobody acknowledged, and a product
 * this app does not sell.
 */
class PurchaseTriageTest {

    private fun held(
        product: String,
        state: HeldState = HeldState.PURCHASED,
        acknowledged: Boolean = true,
        token: String = "token-$product",
    ) = HeldPurchase(token = token, products = listOf(product), state = state, acknowledged = acknowledged)

    @Test
    fun `a settled pack is owned`() {
        val owned = PurchaseTriage.owned(listOf(held(HOT), held(SPICY)))

        assertThat(owned).containsExactly(HOT, SPICY)
    }

    @Test
    fun `a payment that has not cleared opens nothing`() {
        val pending = held(HOT, state = HeldState.PENDING, acknowledged = false)

        assertThat(PurchaseTriage.owned(listOf(pending))).isEmpty()
        assertThat(PurchaseTriage.settled(listOf(pending))).isNull()
        assertThat(PurchaseTriage.pending(listOf(pending))).isTrue()
    }

    @Test
    fun `a state the store has not explained opens nothing and is not called pending`() {
        val odd = held(HOT, state = HeldState.UNSPECIFIED, acknowledged = false)

        assertThat(PurchaseTriage.owned(listOf(odd))).isEmpty()
        assertThat(PurchaseTriage.pending(listOf(odd))).isFalse()
        assertThat(PurchaseTriage.toAcknowledge(listOf(odd))).isEmpty()
    }

    @Test
    fun `a paid pack nobody acknowledged is acknowledged, so Play does not refund it`() {
        val forgotten = held(EVERYTHING, acknowledged = false, token = "forgotten")

        assertThat(PurchaseTriage.toAcknowledge(listOf(held(SPICY), forgotten))).containsExactly("forgotten")
    }

    @Test
    fun `a pending payment is not acknowledged, because there is nothing yet to acknowledge`() {
        val pending = held(HOT, state = HeldState.PENDING, acknowledged = false)

        assertThat(PurchaseTriage.toAcknowledge(listOf(pending))).isEmpty()
    }

    @Test
    fun `a product this app does not sell is nobody's business here`() {
        val other = held("something.else", acknowledged = false)

        assertThat(PurchaseTriage.owned(listOf(other))).isEmpty()
        assertThat(PurchaseTriage.toAcknowledge(listOf(other))).isEmpty()
        assertThat(PurchaseTriage.settled(listOf(other))).isNull()
    }

    @Test
    fun `the purchase that just settled is the pack, not whatever else was held`() {
        val settled = PurchaseTriage.settled(
            listOf(held("something.else"), held(HOT, token = "the-one")),
        )

        assertThat(settled?.token).isEqualTo("the-one")
    }

    private companion object {
        const val SPICY = "afterhours.pack.spicy"
        const val HOT = "afterhours.pack.hot"
        const val EVERYTHING = "afterhours.pack.everything"
    }
}
