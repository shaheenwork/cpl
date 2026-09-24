package com.shnapps.couple.core.firebase.surprise

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.firebase.callFunction
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/** A day of an arc as the server holds it. */
data class ArcDayDoc(
    val index: Int,
    val title: String,
    val state: String,
    val unlockAtMillis: Long,
    val experienceId: String?,
)

/** An arc as the server holds it: the plan, and where each day stands. */
data class ArcDoc(
    val id: String,
    val title: String,
    val lengthDays: Int,
    val state: String,
    val days: List<ArcDayDoc>,
)

/**
 * A few days of this (BUILD_PROMPT.md §13). Every write is a callable: which day is open is a
 * fact about the clock and about what the couple has played, never something a phone decides.
 */
interface ArcRemoteDataSource {
    suspend fun start(lengthDays: Int): Outcome<String>

    /** Opens the day and returns the night built for it. */
    suspend fun openDay(arcId: String, dayIndex: Int): Outcome<String>

    suspend fun close(arcId: String): Outcome<Unit>

    fun observe(coupleId: String): Flow<List<ArcDoc>>
}

@Singleton
class FirebaseArcRemoteDataSource @Inject constructor(
    private val functions: FirebaseFunctions,
    private val firestore: FirebaseFirestore,
) : ArcRemoteDataSource {

    override suspend fun start(lengthDays: Int): Outcome<String> =
        functions.callFunction("startArc", mapOf("lengthDays" to lengthDays)) { it["arcId"] as String }

    override suspend fun openDay(arcId: String, dayIndex: Int): Outcome<String> =
        functions.callFunction("openArcDay", mapOf("arcId" to arcId, "dayIndex" to dayIndex)) {
            it["experienceId"] as String
        }

    override suspend fun close(arcId: String): Outcome<Unit> =
        functions.callFunction("closeArc", mapOf("arcId" to arcId)) { }

    override fun observe(coupleId: String): Flow<List<ArcDoc>> = callbackFlow {
        val registration = firestore.collection("couples").document(coupleId).collection("arcs")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents.orEmpty().mapNotNull { it.toArc() })
            }
        awaitClose { registration.remove() }
    }
}

private fun DocumentSnapshot.toArc(): ArcDoc? {
    val lengthDays = getLong("lengthDays")?.toInt() ?: return null
    val days = (get("days") as? List<*>).orEmpty().mapNotNull { (it as? Map<*, *>)?.toDay() }
    return ArcDoc(
        id = id,
        title = getString("title").orEmpty(),
        lengthDays = lengthDays,
        state = getString("state") ?: "ACTIVE",
        days = days.sortedBy { it.index },
    )
}

private fun Map<*, *>.toDay(): ArcDayDoc? {
    val index = (this["index"] as? Number)?.toInt() ?: return null
    return ArcDayDoc(
        index = index,
        title = this["title"] as? String ?: "",
        state = this["state"] as? String ?: "LOCKED",
        unlockAtMillis = (this["unlockAt"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: 0L,
        experienceId = this["experienceId"] as? String,
    )
}
