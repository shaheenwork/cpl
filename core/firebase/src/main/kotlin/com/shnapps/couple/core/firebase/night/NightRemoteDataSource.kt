package com.shnapps.couple.core.firebase.night

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import com.google.firebase.functions.FirebaseFunctions
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.firebase.callFunction
import com.shnapps.couple.core.firebase.firestoreCall
import com.shnapps.couple.core.model.ChapterReaction
import com.shnapps.couple.core.model.Mode
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
// One session, one data source: building a night, every move of its session, and the two
// documents each phone writes for itself (its answer and its heartbeat).
@Suppress("TooManyFunctions")
interface NightRemoteDataSource {
    suspend fun generate(settings: NightSettings): Outcome<ServerNightPayload>

    /**
     * The night the couple did not configure (§14.2): the server chooses its shape and returns
     * what it chose. [mystery] keeps that shape from the phone as well.
     */
    suspend fun surprise(mystery: Boolean, mode: Mode): Outcome<ServerNightPayload>

    /**
     * "Make it more…" (§14.6): the night as this phone rearranged it, written back so the
     * couple's record and the session agree with what is played. Only ids already released for
     * that night are accepted, and only before it starts.
     */
    suspend fun remixNight(experienceId: String, chapters: List<Pair<String, String>>): Outcome<Unit>

    /** [twoPhones]: each partner answers on their own, rather than one phone passed over (§12). */
    suspend fun createSession(experienceId: String, twoPhones: Boolean = false): Outcome<SessionPayload>

    /** "I'm in" — the signed-in user's own, and nobody else's (§3.1). */
    suspend fun markReady(sessionId: String): Outcome<SessionPayload>

    /** Starts the night when [fromChapter] is -1; otherwise, finishes that chapter. */
    suspend fun advance(sessionId: String, fromChapter: Int, playedItemId: String?): Outcome<AdvancePayload>

    suspend fun endSession(sessionId: String, action: SessionAction): Outcome<SessionPayload>

    /**
     * This phone's own answer for the chapter it is on (§12.2). The only session document a
     * client writes; the server turns the pair into the reveal both partners read.
     */
    suspend fun submitAnswer(
        coupleId: String,
        sessionId: String,
        uid: String,
        chapter: Int,
        answer: String?,
    ): Outcome<Unit>

    /** "I've seen the reveal", this phone's own. */
    suspend fun markSeen(sessionId: String, chapter: Int): Outcome<SessionPayload>

    /** The reveal for [chapter] once the server has written it: both partners' answers. */
    fun observeReveal(coupleId: String, sessionId: String, chapter: Int): Flow<RevealDoc?>

    /** "I'm still here", every twenty seconds while a session is on screen (§12.3). */
    suspend fun sendPresence(coupleId: String, sessionId: String, uid: String): Outcome<Unit>

    /** When each phone was last in the session. */
    fun observePresence(coupleId: String, sessionId: String): Flow<Map<String, Long>>

    fun observeCouple(coupleId: String): Flow<CoupleNightState>

    fun observeSession(coupleId: String, sessionId: String): Flow<SessionDoc?>

    suspend fun experience(coupleId: String, experienceId: String): Outcome<ExperienceDoc>

    /** The signed-in user's own ask-first items for a night (§3.2). Empty when there are none. */
    suspend fun askFirstItems(coupleId: String, experienceId: String, uid: String): Outcome<List<String>>

    suspend fun sendFeedback(coupleId: String, uid: String, feedback: FeedbackPayload): Outcome<Unit>
}

data class ChapterPayload(val index: Int, val kind: String, val contentId: String, val intensity: Int)

/**
 * The night's own settings as the server built it (§14.2): what the phone asked for, or —
 * for a surprise — what the server chose on the couple's behalf.
 */
data class NightSettingsPayload(
    val mode: String,
    val durationMin: Int,
    val moods: List<String>,
    val intensity: Int,
    val leadership: String,
    val novelty: String,
)

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
    /** Null from a server that predates surprises; the phone then keeps what it asked for. */
    val settings: NightSettingsPayload? = null,
    /** A Mystery Night: the phone shows nothing of its shape (§14.2). */
    val mystery: Boolean = false,
)

/** What both partners answered for one chapter, written by the server (§12.2). */
data class RevealDoc(val chapter: Int, val answers: Map<String, String?>)

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
    /** NIGHT or GAME: which player the session plays. Sessions from before games are nights. */
    val experienceType: String = "NIGHT",
    /** ONE_DEVICE or TWO_DEVICE (§12). */
    val kind: String = "ONE_DEVICE",
    /** Who has answered the chapter under way, and who has seen its reveal. */
    val submittedUids: Set<String> = emptySet(),
    val seenUids: Set<String> = emptySet(),
    /**
     * Served from this phone's own cache rather than the server (§12.3). Firestore does not
     * fail a listener when the network goes: it goes quiet and serves what it has, and this is
     * how it says so.
     */
    val fromCache: Boolean = false,
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

    override suspend fun surprise(mystery: Boolean, mode: Mode): Outcome<ServerNightPayload> =
        functions.callFunction("surpriseNight", mapOf("mystery" to mystery, "mode" to mode.name)) { it.toNight() }

    override suspend fun remixNight(experienceId: String, chapters: List<Pair<String, String>>): Outcome<Unit> {
        val payload = mapOf(
            "experienceId" to experienceId,
            "chapters" to chapters.map { (contentId, kind) -> mapOf("contentId" to contentId, "kind" to kind) },
        )
        return functions.callFunction("remixNight", payload) { }
    }

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
        return functions.callFunction("generateExperience", request) { it.toNight() }
    }

    override suspend fun createSession(experienceId: String, twoPhones: Boolean): Outcome<SessionPayload> {
        val payload = mapOf("experienceId" to experienceId, "kind" to if (twoPhones) "TWO_DEVICE" else "ONE_DEVICE")
        return functions.callFunction("createSession", payload) { it.toSession() }
    }

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

    override suspend fun submitAnswer(
        coupleId: String,
        sessionId: String,
        uid: String,
        chapter: Int,
        answer: String?,
    ): Outcome<Unit> = firestoreCall {
        // The id says which chapter and whose answer it is: the rules check both (§7.3 D–F).
        firestore.collection(COUPLES).document(coupleId).collection(SESSIONS).document(sessionId)
            .collection(SUBMISSIONS).document("${chapter}_$uid")
            .set(
                mapOf(
                    "chapter" to chapter,
                    "byUid" to uid,
                    "answer" to answer,
                    "at" to FieldValue.serverTimestamp(),
                ),
            ).await()
    }

    override suspend fun markSeen(sessionId: String, chapter: Int): Outcome<SessionPayload> =
        functions.callFunction("markSeen", mapOf("sessionId" to sessionId, "chapter" to chapter)) { it.toSession() }

    override fun observeReveal(coupleId: String, sessionId: String, chapter: Int): Flow<RevealDoc?> = callbackFlow {
        val registration = firestore.collection(COUPLES).document(coupleId)
            .collection(SESSIONS).document(sessionId)
            .collection(ROUNDS).document(chapter.toString())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.takeIf { it.exists() }?.toReveal(chapter))
            }
        awaitClose { registration.remove() }
    }

    override suspend fun sendPresence(coupleId: String, sessionId: String, uid: String): Outcome<Unit> = firestoreCall {
        firestore.collection(COUPLES).document(coupleId).collection(SESSIONS).document(sessionId)
            .collection(PRESENCE).document(uid)
            .set(mapOf("at" to FieldValue.serverTimestamp())).await()
    }

    override fun observePresence(coupleId: String, sessionId: String): Flow<Map<String, Long>> = callbackFlow {
        val registration = firestore.collection(COUPLES).document(coupleId)
            .collection(SESSIONS).document(sessionId).collection(PRESENCE)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(
                    snapshot?.documents.orEmpty()
                        .mapNotNull { doc -> doc.getTimestamp("at")?.let { doc.id to it.toDate().time } }
                        .toMap(),
                )
            }
        awaitClose { registration.remove() }
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
            // Metadata changes included: a snapshot that starts coming from the cache is the
            // only sign Firestore gives that this phone is cut off (§12.3).
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
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
private const val SUBMISSIONS = "submissions"
private const val ROUNDS = "rounds"
private const val PRESENCE = "presence"

/** A night as the server returned it: generated to order, or the app's own idea. */
private fun Map<*, *>.toNight(): ServerNightPayload = ServerNightPayload(
    experienceId = this["experienceId"] as String,
    chapters = (this["chapters"] as List<*>).map { (it as Map<*, *>).toChapter("contentId") },
    alternates = (this["alternates"] as List<*>).filterIsInstance<String>(),
    effectiveIntensity = (this["effectiveIntensity"] as Number).toInt(),
    seed = (this["seed"] as Number).toLong(),
    engineVersion = (this["engineVersion"] as Number).toInt(),
    askFirstItems = (this["askFirstItems"] as? List<*>).orEmpty().filterIsInstance<String>(),
    focusIncluded = this["focusIncluded"] == true,
    settings = (this["settings"] as? Map<*, *>)?.toNightSettings(),
    mystery = this["mystery"] == true,
)

private fun Map<*, *>.toNightSettings(): NightSettingsPayload? {
    // All of it, or none: a server too old to send the settings leaves the phone with what it
    // asked for, and a surprise then falls back to its own defaults.
    val mode = this["mode"] as? String
    val leadership = this["leadership"] as? String
    val novelty = this["novelty"] as? String
    val durationMin = (this["durationMin"] as? Number)?.toInt()
    val intensity = (this["intensity"] as? Number)?.toInt()
    if (mode == null || leadership == null || novelty == null) return null
    if (durationMin == null || intensity == null) return null
    return NightSettingsPayload(
        mode = mode,
        durationMin = durationMin,
        moods = (this["moods"] as? List<*>).orEmpty().filterIsInstance<String>(),
        intensity = intensity,
        leadership = leadership,
        novelty = novelty,
    )
}

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
        experienceType = getString("experienceType") ?: "NIGHT",
        kind = getString("kind") ?: "ONE_DEVICE",
        submittedUids = (get("submittedBy") as? Map<*, *>).orEmpty().keys.filterIsInstance<String>().toSet(),
        seenUids = (get("seenBy") as? Map<*, *>).orEmpty().keys.filterIsInstance<String>().toSet(),
        fromCache = metadata.isFromCache,
    )
}

/** The reveal as the server wrote it: what each partner answered for the chapter. */
private fun DocumentSnapshot.toReveal(chapter: Int): RevealDoc {
    val answers = (get("answers") as? Map<*, *>).orEmpty()
        .mapNotNull { (uid, answer) -> (uid as? String)?.let { it to answer as? String } }
        .toMap()
    return RevealDoc(chapter = (getLong("chapter") ?: chapter).toInt(), answers = answers)
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
