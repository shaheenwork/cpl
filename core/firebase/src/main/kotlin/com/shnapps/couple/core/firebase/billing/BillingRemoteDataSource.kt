package com.shnapps.couple.core.firebase.billing

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.firebase.callFunction
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/** What the server says the couple is entitled to. Written only by it (§7.2, §7.3 row I). */
data class EntitlementDoc(
    val tier: String,
    val state: String?,
    val expiresAtMillis: Long?,
)

/**
 * Subscriptions, from the phone's side (BUILD_PROMPT.md §19).
 *
 * The phone hands over the purchase token and nothing else: what it is worth is Google's answer
 * and the server's to write. Reading is from the couple document, which both partners may read —
 * so a phone never has to look at its partner's account to know they are covered.
 */
interface BillingRemoteDataSource {
    fun observe(coupleId: String): Flow<EntitlementDoc>

    /** Reports a purchase. The server verifies it before anything is written. */
    suspend fun verify(purchaseToken: String): Outcome<String>

    /** What this account is entitled to, as the server holds it: for a restore or a reinstall. */
    suspend fun current(): Outcome<EntitlementDoc>
}

@Singleton
class FirebaseBillingRemoteDataSource @Inject constructor(
    private val functions: FirebaseFunctions,
    private val firestore: FirebaseFirestore,
) : BillingRemoteDataSource {

    override fun observe(coupleId: String): Flow<EntitlementDoc> = callbackFlow {
        val registration = firestore.collection("couples").document(coupleId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val entitlement = snapshot?.get("entitlement") as? Map<*, *>
                trySend(
                    EntitlementDoc(
                        tier = entitlement?.get("tier") as? String ?: "FREE",
                        state = entitlement?.get("state") as? String,
                        expiresAtMillis = (entitlement?.get("expiresAt") as? com.google.firebase.Timestamp)
                            ?.toDate()?.time,
                    ),
                )
            }
        awaitClose { registration.remove() }
    }

    override suspend fun verify(purchaseToken: String): Outcome<String> =
        functions.callFunction("verifyPurchase", mapOf("purchaseToken" to purchaseToken)) { it["tier"] as String }

    override suspend fun current(): Outcome<EntitlementDoc> =
        functions.callFunction("currentEntitlement", emptyMap<String, Any>()) { result ->
            EntitlementDoc(
                tier = result["tier"] as? String ?: "FREE",
                state = result["state"] as? String,
                expiresAtMillis = (result["expiresAtMillis"] as? Number)?.toLong(),
            )
        }
}
