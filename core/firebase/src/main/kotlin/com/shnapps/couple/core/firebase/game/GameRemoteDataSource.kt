package com.shnapps.couple.core.firebase.game

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.functions.FirebaseFunctions
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.firebase.callFunction
import com.shnapps.couple.core.firebase.firestoreCall
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starting a game (BUILD_PROMPT.md §11): the server chooses its rounds behind both partners'
 * boundaries and stores them as an experience, played through the same session callables as
 * a night (NightRemoteDataSource). Nothing a partner answers during a game is ever sent.
 */
interface GameRemoteDataSource {
    suspend fun startGame(gameId: String, intensity: Int): Outcome<GamePayload>

    /** A started game as the server stored it, for a phone that restarted mid-game. */
    suspend fun game(coupleId: String, experienceId: String): Outcome<GamePayload>
}

/** A game's rounds as the server chose them: ids only, resolved against the phone's content. */
data class GamePayload(
    val experienceId: String,
    val gameId: String,
    /** Each round's prompt ids, one of each of the game's kinds in the game's order. */
    val rounds: List<List<String>>,
    val alternates: List<String>,
    val effectiveIntensity: Int,
)

@Singleton
class FirebaseGameRemoteDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
) : GameRemoteDataSource {

    override suspend fun startGame(gameId: String, intensity: Int): Outcome<GamePayload> =
        functions.callFunction("startGame", mapOf("game" to gameId, "intensity" to intensity)) { data ->
            GamePayload(
                experienceId = data["experienceId"] as String,
                gameId = data["game"] as String,
                rounds = (data["rounds"] as List<*>).map { round -> (round as Map<*, *>).contentIds() },
                alternates = (data["alternates"] as List<*>).filterIsInstance<String>(),
                effectiveIntensity = (data["effectiveIntensity"] as Number).toInt(),
            )
        }

    override suspend fun game(coupleId: String, experienceId: String): Outcome<GamePayload> {
        val snapshot = firestoreCall {
            firestore.collection(COUPLES).document(coupleId).collection(EXPERIENCES).document(experienceId)
                .get(Source.SERVER).await()
        }
        return when (snapshot) {
            is Outcome.Failure -> snapshot
            is Outcome.Success -> snapshot.value.toGamePayload()?.let { Outcome.Success(it) }
                ?: Outcome.Failure(AppError.NotFound())
        }
    }
}

private const val COUPLES = "couples"
private const val EXPERIENCES = "experiences"

private fun Map<*, *>.contentIds(): List<String> = (this["contentIds"] as? List<*>).orEmpty().filterIsInstance<String>()

/** Tolerant parse, failing closed: a document that is not a game this phone can read is none. */
@Suppress("ReturnCount") // One early return per required field: fail closed on each.
private fun DocumentSnapshot.toGamePayload(): GamePayload? {
    if (!exists() || getString("type") != "GAME") return null
    val chapters = (get("chapters") as? List<*>)?.map { (it as? Map<*, *>)?.contentIds() ?: return null } ?: return null
    return GamePayload(
        experienceId = id,
        gameId = getString("game") ?: return null,
        rounds = chapters,
        alternates = (get("alternates") as? List<*>).orEmpty().filterIsInstance<String>(),
        effectiveIntensity = getLong("effectiveIntensity")?.toInt() ?: return null,
    )
}
