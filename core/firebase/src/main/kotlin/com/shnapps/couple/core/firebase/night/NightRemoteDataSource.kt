package com.shnapps.couple.core.firebase.night

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.functions.FirebaseFunctions
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.firebase.callFunction
import com.shnapps.couple.core.firebase.firestoreCall
import com.shnapps.couple.core.model.ChapterReaction
import com.shnapps.couple.core.model.NightSettings
import com.shnapps.couple.core.model.SessionEndReason
import com.shnapps.couple.core.model.SessionState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Building and playing a night (BUILD_PROMPT.md §8, §10, §12): the engine's callable, the
 * session callables, and the two documents a phone listens to while a night is on — the
 * couple's (which night is under way) and the session's (where it stands).
 *
 * Every change to a night goes through a callable that checks it on the server. The only
 * thing a phone writes itself is its own reaction to a chapter it played, which the rules
 * hold to exactly that (DECISIONS.md D-063).
 */
interface NightRemoteDataSource {
    suspend fun generate(settings: NightSettings): Outcome<ServerNightPayload>

    suspend fun createSession(experienceId: String): Outcome<SessionPayload>

    /** "I'm in" — the signed-in user's own, and nobody else's (§3.1). */
    suspend fun markReady(sessionId: String): Outcome<SessionPayload>

    /** Starts the night when [fromChapter] is -1; otherwise, finishes that chapter. */
    suspend fun advance(sessionId: String, fromChapter: Int, playedItemId: String?): Outcome<AdvancePayload>

    suspend fun endSession(sessionId: String, action: SessionAction): Outcome<SessionPayload>

    fun observeCouple(coupleId: String): Flow<CoupleNightState>

    fun observeSession(coupleId: String, sessionId: String): Flow<SessionDoc?>

    suspend fun experience(coupleId: String, experienceId: String): Outcome<ExperienceDoc>

    /** The signed-in user's own ask-first items for a night (§3.2). Empty when there are none. */
    suspend fun askFirstItems(coupleId: String, experienceId: String, uid: String): Outcome<List<String>>

    suspend fun sendFeedback(coupleId: String, uid: String, feedback: FeedbackPayload): Outcome<Unit>
}

data class ChapterPayload(val index: Int, val kind: String, val contentId: String, val intensity: Int)

/** What generateExperience returned: ids only, resolved against the content on the phone. */
data class ServerNightPayload(
    val experienceId: String,
    val chapters: List<ChapterPayload>,
    val alternates: List<String>,
    val effectiveIntensity: Int,
    val seed: Long,
    val engineVersion: Int,
    val askFirstItems: List<String>,
    val focusIncluded: Boolean,
)

data class SessionPayload(
    val sessionId: String,
    val state: SessionState,
    val currentChapter: Int,
    val chapterCount: Int,
    val consentRequired: Boolean,
)

data class AdvancePayload(
    val session: SessionPayload,
    /** Still-to-come items the couple's limits no longer permit (DECISIONS.md D-062). */
    val blocked: List<String>,
)

enum class SessionAction { PAUSE, RESUME, STOP, DECLINE }

/** The couple document, as far as a night is concerned. */
data class CoupleNightState(
    val activeSessionId: String?,
    /** The couple's shared content level: the more careful partner's (§3.1). */
    val sharedLevel: Int?,
)

/** `couples/{cid}/sessions/{sid}`: written only by the server. */
data class SessionDoc(
    val id: String,
    val experienceId: String,
    val hostUid: String,
    val state: SessionState,
    val pausedFrom: SessionState?,
    val currentChapter: Int,
    val chapterCount: Int,
    val consentRequired: Boolean,
    val consentedUids: Set<String>,
    val endReason: SessionEndReason?,
)

/** `couples/{cid}/experiences/{id}`: the night as the server stored it. */
data class ExperienceDoc(
    val id: String,
    val mode: String,
    val durationMin: Int,
    val moods: List<String>,
    val intensity: Int,
    val effectiveIntensity: Int,
    val leadership: String,
    val novelty: String,
    val focus: String?,
    val focusIncluded: Boolean,
    val chapters: List<ChapterPayload>,
    val alternates: List<String>,
    val status: String,
    val playedThrough: Int,
    val sessionId: String?,
    val seed: Long,
)

data class FeedbackPayload(
    val experienceId: String,
    val chapterIndex: Int,
    val contentId: String,
    val reaction: ChapterReaction,
)

@Singleton
class FirebaseNightRemoteDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
) : NightRemoteDataSource {

    override suspend fun generate(settings: NightSettings): Outcome<ServerNightPayload> {
        val request = buildMap<String, Any> {
            put("mode", settings.mode.name)
            put("durationMin", settings.durationMin)
            put("moods", settings.moods.map { it.id })
            put("intensity", settings.intensity.level)
            put("leadership", settings.leadership.name)
            put("novelty", settings.novelty.name)
            settings.focus?.let { put("focus", it) }
        }
        return functions.callFunction("generateExperience", request) { data ->
            ServerNightPayload(
                experienceId = data["experienceId"] as String,
                chapters = (data["chapters"] as List<*>).map { (it as Map<*, *>).toChapter("contentId") },
                alternates = (data["alternates"] as List<*>).filterIsInstance<String>(),
                effectiveIntensity = (data["effectiveIntensity"] as Number).toInt(),
                seed = (data["seed"] as Number).toLong(),
                engineVersion = (data["engineVersion"] as Number).toInt(),
                askFirstItems = (data["askFirstItems"] as? List<*>).orEmpty().filterIsInstance<String>(),
                focusIncluded = data["focusIncluded"] == true,
            )
        }
    }

    override suspend fun createSession(experienceId: String): Outcome<SessionPayload> =
        functions.callFunction("createSession", mapOf("experienceId" to experienceId)) { it.toSession() }

    override suspend fun markReady(sessionId: String): Outcome<SessionPayload> =
        functions.callFunction("markReady", mapOf("sessionId" to sessionId)) { it.toSession() }

    override suspend fun advance(sessionId: String, fromChapter: Int, playedItemId: String?): Outcome<AdvancePayload> {
        val payload = buildMap<String, Any> {
            put("sessionId", sessionId)
            put("fromChapter", fromChapter)
            playedItemId?.let { put("playedItemId", it) }
        }
        return functions.callFunction("advanceSession", payload) { data ->
            AdvancePayload(data.toSession(), (data["blocked"] as? List<*>).orEmpty().filterIsInstance<String>())
        }
    }

    override suspend fun endSession(sessionId: String, action: SessionAction): Outcome<SessionPayload> {
        val payload = mapOf("sessionId" to sessionId, "action" to action.name)
        return functions.callFunction("endSession", payload) { it.toSession() }
    }

    override fun observeCouple(coupleId: String): Flow<CoupleNightState> = callbackFlow {
        val registration = firestore.collection(COUPLES).document(coupleId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(
                CoupleNightState(
                    activeSessionId = snapshot?.getString("activeSessionId"),
                    sharedLevel = snapshot?.getLong("contentLevelEffective")?.toInt(),
                ),
            )
        }
        awaitClose { registration.remove() }
    }

    override fun observeSession(coupleId: String, sessionId: String): Flow<SessionDoc?> = callbackFlow {
        val registration = firestore.collection(COUPLES).document(coupleId)
            .collection(SESSIONS).document(sessionId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.takeIf { it.exists() }?.toSessionDoc())
            }
        awaitClose { registration.remove() }
    }

    override suspend fun experience(coupleId: String, experienceId: String): Outcome<ExperienceDoc> {
        val snapshot = firestoreCall {
            firestore.collection(COUPLES).document(coupleId).collection(EXPERIENCES).document(experienceId)
                .get(Source.SERVER).await()
        }
        return when (snapshot) {
            is Outcome.Failure -> snapshot
            is Outcome.Success -> snapshot.value.toExperienceDoc()?.let { Outcome.Success(it) }
                ?: Outcome.Failure(AppError.NotFound())
        }
    }

    override suspend fun askFirstItems(coupleId: String, experienceId: String, uid: String): Outcome<List<String>> =
        firestoreCall {
            val snapshot = firestore.collection(COUPLES).document(coupleId).collection(EXPERIENCES)
                .document(experienceId).collection(ASK_FIRST).document(uid)
                .get(Source.SERVER).await()
            (snapshot.get("items") as? List<*>).orEmpty().filterIsInstance<String>()
        }

    override suspend fun sendFeedback(coupleId: String, uid: String, feedback: FeedbackPayload): Outcome<Unit> =
        firestoreCall {
            val id = "${feedback.experienceId}_${feedback.chapterIndex}_$uid"
            val document = firestore.collection(COUPLES).document(coupleId).collection(FEEDBACK).document(id)
            // Feedback is create-only: a retry after a write that did land finds it already there.
            if (document.get(Source.SERVER).await().exists()) return@firestoreCall
            document.set(
                mapOf(
                    "experienceId" to feedback.experienceId,
                    "chapterIndex" to feedback.chapterIndex,
                    "contentId" to feedback.contentId,
                    "reaction" to feedback.reaction.name,
                    "byUid" to uid,
                    "at" to FieldValue.serverTimestamp(),
                ),
            ).await()
        }
}

private const val COUPLES = "couples"
private const val SESSIONS = "sessions"
private const val EXPERIENCES = "experiences"
private const val ASK_FIRST = "askFirst"
private const val FEEDBACK = "feedback"

private fun Map<*, *>.toChapter(idKey: String): ChapterPayload {
    // The callable names a chapter's content `contentId`; the stored night keeps `contentIds`.
    val id = if (idKey == "contentId") this["contentId"] as String else (this["contentIds"] as List<*>).first()
    return ChapterPayload(
        index = (this["index"] as Number).toInt(),
        kind = this["kind"] as String,
        contentId = id as String,
        intensity = (this["intensity"] as Number).toInt(),
    )
}

private fun stateOf(name: Any?): SessionState? = SessionState.entries.firstOrNull { it.name == name }

private fun Map<*, *>.toSession(): SessionPayload = SessionPayload(
    sessionId = this["sessionId"] as String,
    // A state this version does not know is treated as over: never played on regardless.
    state = stateOf(this["state"]) ?: SessionState.ABANDONED,
    currentChapter = (this["currentChapter"] as Number).toInt(),
    chapterCount = (this["chapterCount"] as Number).toInt(),
    consentRequired = this["consentRequired"] == true,
)

/** Tolerant parse, failing closed: a session it cannot read is one the phone will not play. */
@Suppress("ReturnCount") // One early return per required field: fail closed on each.
private fun DocumentSnapshot.toSessionDoc(): SessionDoc? {
    val state = stateOf(getString("state")) ?: return null
    return SessionDoc(
        id = id,
        experienceId = getString("experienceId") ?: return null,
        hostUid = getString("hostUid") ?: return null,
        state = state,
        pausedFrom = stateOf(getString("pausedFrom")),
        currentChapter = getLong("currentChapter")?.toInt() ?: return null,
        chapterCount = getLong("chapterCount")?.toInt() ?: return null,
        consentRequired = getBoolean("consentRequired") == true,
        consentedUids = (get("consentAcks") as? Map<*, *>).orEmpty().keys.filterIsInstance<String>().toSet(),
        endReason = SessionEndReason.entries.firstOrNull { it.name == getString("endReason") },
    )
}

@Suppress("ReturnCount") // One early return per required field: fail closed on each.
private fun DocumentSnapshot.toExperienceDoc(): ExperienceDoc? {
    if (!exists()) return null
    val chapters = (get("chapters") as? List<*>)?.map { (it as Map<*, *>).toChapter("contentIds") } ?: return null
    return ExperienceDoc(
        id = id,
        mode = getString("mode") ?: return null,
        durationMin = getLong("durationMin")?.toInt() ?: return null,
        moods = (get("mood") as? List<*>).orEmpty().filterIsInstance<String>(),
        intensity = getLong("intensity")?.toInt() ?: return null,
        effectiveIntensity = getLong("effectiveIntensity")?.toInt() ?: return null,
        leadership = getString("leadership") ?: return null,
        novelty = getString("novelty") ?: return null,
        focus = getString("focus"),
        focusIncluded = getBoolean("focusIncluded") == true,
        chapters = chapters,
        alternates = (get("alternates") as? List<*>).orEmpty().filterIsInstance<String>(),
        status = getString("status") ?: return null,
        playedThrough = getLong("playedThrough")?.toInt() ?: 0,
        sessionId = getString("sessionId"),
        seed = getLong("seed") ?: 0L,
    )
}
