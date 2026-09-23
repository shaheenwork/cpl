package com.shnapps.couple.core.data.night

import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.ApplicationScope
import com.shnapps.couple.core.common.Clock
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.auth.AuthRepository
import com.shnapps.couple.core.data.boundaries.BoundaryRepository
import com.shnapps.couple.core.data.content.ContentRepository
import com.shnapps.couple.core.data.mutual.MutualRepository
import com.shnapps.couple.core.engine.Chapter
import com.shnapps.couple.core.engine.ClientContext
import com.shnapps.couple.core.engine.Engine
import com.shnapps.couple.core.engine.Night
import com.shnapps.couple.core.engine.PermittedPool
import com.shnapps.couple.core.engine.RemixModifier
import com.shnapps.couple.core.engine.Remixer
import com.shnapps.couple.core.engine.ServerNight
import com.shnapps.couple.core.firebase.night.ChapterPayload
import com.shnapps.couple.core.firebase.night.CoupleNightState
import com.shnapps.couple.core.firebase.night.FeedbackPayload
import com.shnapps.couple.core.firebase.night.NightRemoteDataSource
import com.shnapps.couple.core.firebase.night.NightSettingsPayload
import com.shnapps.couple.core.firebase.night.SessionAction
import com.shnapps.couple.core.firebase.night.SessionDoc
import com.shnapps.couple.core.model.Boundary
import com.shnapps.couple.core.model.BoundaryLevel
import com.shnapps.couple.core.model.ChapterKind
import com.shnapps.couple.core.model.ChapterReaction
import com.shnapps.couple.core.model.ContentItem
import com.shnapps.couple.core.model.ExperienceType
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.Leadership
import com.shnapps.couple.core.model.Mode
import com.shnapps.couple.core.model.Mood
import com.shnapps.couple.core.model.NightSession
import com.shnapps.couple.core.model.NightSettings
import com.shnapps.couple.core.model.Novelty
import com.shnapps.couple.core.model.PlannedNight
import com.shnapps.couple.core.model.SessionKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Building a night and playing it (BUILD_PROMPT.md §3.1, §10, §12, §14.5–14.6).
 *
 * The server builds every night behind both partners' boundaries; this phone holds what it
 * released and plays it, one chapter at a time. Every step of a night — its start, each
 * chapter, a pause, a stop, each partner's "I'm in" — is decided by the server, and the phone
 * follows the session document it listens to. What the phone decides alone is only ever a
 * rearrangement of what it was given: "Not this one", and taking a limit set mid-night out of
 * the rest of it (DECISIONS.md D-048, D-062).
 */
@Suppress("TooManyFunctions") // One night, one repository: every step of a session moves the night held here.
interface NightRepository {
    /** The couple's night under way, from either phone, or null when there is none. */
    val activeSession: Flow<NightSession?>

    /** The couple's shared content level: the most any night can reach (§3.1). */
    val sharedLevel: Flow<Intensity?>

    /** Asks the server for a night and holds it on this phone, ready to play. */
    suspend fun build(settings: NightSettings): Outcome<PlannedNight>

    /**
     * The night nobody configured (§14.2): the server chooses its shape and this phone holds
     * it like any other. [mystery] asks for a Mystery Night, whose shape is never shown.
     */
    suspend fun surprise(mystery: Boolean = false): Outcome<PlannedNight>

    /** The night [experienceId]: the one held here, or fetched again after a restart. */
    suspend fun load(experienceId: String): Outcome<PlannedNight>

    fun observeSession(sessionId: String): Flow<NightSession?>

    /**
     * Opens the night's session — the couple's one night under way (§12) — and returns its id.
     * Where it stands is always read from [observeSession], never assumed.
     */
    suspend fun start(experienceId: String): Outcome<String>

    /** "I'm in" — this user's own, and nobody else's (§3.1). */
    suspend fun consent(sessionId: String): Outcome<Unit>

    suspend fun decline(sessionId: String): Outcome<Unit>

    /** Moves to the first chapter. Returns the night, with anything now ruled out taken out. */
    suspend fun begin(experienceId: String, sessionId: String): Outcome<PlannedNight>

    /** Chapter [index] is done: moves on, and returns the night as it now stands. */
    suspend fun finishChapter(experienceId: String, sessionId: String, index: Int): Outcome<PlannedNight>

    suspend fun pause(sessionId: String): Outcome<Unit>

    suspend fun resume(sessionId: String): Outcome<Unit>

    /** STOP (§3.1): no confirmation, and nothing records who. */
    suspend fun stop(sessionId: String): Outcome<Unit>

    /** "Not this one" (§3.1): another released item for chapter [index], or null if none fits. */
    fun swap(experienceId: String, index: Int): PlannedNight?

    /**
     * "Make it more…" (§14.6): the night rebuilt from the same released pool, the way [modifier]
     * asks — never past the night's own ceiling, and never with anything the server did not
     * release. The couple's record is brought into line with it, because the session counts its
     * chapters; a night the server will not accept is not kept. Null when the pool cannot make
     * that night at all.
     */
    suspend fun makeIt(experienceId: String, modifier: RemixModifier): PlannedNight?

    /**
     * Keeps [themeId] out: the user's own boundary at [level], and out of the rest of tonight
     * from chapter [fromIndex] at once, before the server has even recomputed the filters.
     */
    suspend fun keepOut(
        experienceId: String,
        themeId: String,
        level: BoundaryLevel,
        fromIndex: Int,
    ): Outcome<PlannedNight>

    /** This user's reaction to a chapter they played, private to them (D-063). */
    suspend fun react(experienceId: String, chapterIndex: Int, reaction: ChapterReaction): Outcome<Unit>
}

// Implements NightRepository (see there) plus the few private steps of holding a night.
@Suppress("TooManyFunctions")
@Singleton
class DefaultNightRepository @Inject constructor(
    private val authRepository: AuthRepository,
    private val remote: NightRemoteDataSource,
    private val content: ContentRepository,
    private val boundaries: BoundaryRepository,
    private val mutual: MutualRepository,
    private val clock: Clock,
    @ApplicationScope appScope: CoroutineScope,
) : NightRepository {

    private val lock = Mutex()

    /** The one night this phone holds. A phone plays one night at a time (D-059). */
    @Volatile
    private var held: HeldNight? = null

    private val ids: Flow<Pair<String, String>?> = authRepository.currentProfile
        .map { profile -> profile?.coupleId?.let { profile.uid to it } }
        .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val couple: Flow<Pair<String, CoupleNightState>?> = ids
        .flatMapLatest { pair ->
            val coupleId = pair?.second
            if (coupleId == null) {
                flowOf(null)
            } else {
                // Refused the moment a couple ends; end quietly (D-025).
                remote.observeCouple(coupleId)
                    .map { coupleId to it }
                    .catch { emit(coupleId to CoupleNightState(activeSessionId = null, sharedLevel = null)) }
            }
        }
        .shareIn(appScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val activeSession: Flow<NightSession?> = couple
        .map { state -> state?.let { (coupleId, couple) -> couple.activeSessionId?.let { coupleId to it } } }
        .distinctUntilChanged()
        .flatMapLatest { active ->
            if (active == null) flowOf(null) else sessionFlow(active.first, active.second)
        }

    override val sharedLevel: Flow<Intensity?> = couple.map { state ->
        state?.second?.sharedLevel?.let { level -> Intensity.entries.firstOrNull { it.level == level } }
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeSession(sessionId: String): Flow<NightSession?> =
        ids.flatMapLatest { pair -> if (pair == null) flowOf(null) else sessionFlow(pair.second, sessionId) }

    private suspend fun sessionFlow(coupleId: String, sessionId: String): Flow<NightSession?> {
        val uid = authRepository.currentProfile.first()?.uid
        return remote.observeSession(coupleId, sessionId)
            .map { doc -> doc?.toSession(uid) }
            .catch { emit(null) }
    }

    override suspend fun build(settings: NightSettings): Outcome<PlannedNight> = lock.withLock {
        val night = when (val result = remote.generate(settings)) {
            is Outcome.Failure -> return result
            is Outcome.Success -> result.value
        }
        val server = ServerNight(
            request = HeldNight.requestOf(settings, night.seed),
            chapters = night.chapters.map { it.toChapter() },
            alternates = night.alternates,
            effectiveIntensity = night.effectiveIntensity,
            engineVersion = night.engineVersion,
        )
        hold(night.experienceId, settings, server, night.askFirstItems.toSet(), night.focusIncluded)
    }

    override suspend fun surprise(mystery: Boolean): Outcome<PlannedNight> = lock.withLock {
        val night = when (val result = remote.surprise(mystery, Mode.TOGETHER)) {
            is Outcome.Failure -> return result
            is Outcome.Success -> result.value
        }
        // The server chose the shape, and says what it chose: the phone holds it exactly as it
        // holds a night the couple asked for, so "Not this one" and "Make it more…" still work.
        val settings = night.settings?.toSettings() ?: NightSettings(
            mode = Mode.TOGETHER,
            durationMin = DEFAULT_SURPRISE_MIN,
            moods = emptySet(),
            intensity = Intensity.ofLevel(night.effectiveIntensity),
            leadership = Leadership.RANDOM,
            novelty = Novelty.SURPRISE_ME,
        )
        val server = ServerNight(
            request = HeldNight.requestOf(settings, night.seed),
            chapters = night.chapters.map { it.toChapter() },
            alternates = night.alternates,
            effectiveIntensity = night.effectiveIntensity,
            engineVersion = night.engineVersion,
        )
        hold(night.experienceId, settings, server, night.askFirstItems.toSet(), night.focusIncluded, night.mystery)
    }

    override suspend fun load(experienceId: String): Outcome<PlannedNight> = lock.withLock {
        heldFor(experienceId)?.let { return Outcome.Success(it.planned()) }
        val (uid, coupleId) = ids.first() ?: return Outcome.Failure(AppError.Unauthenticated())
        val doc = when (val result = remote.experience(coupleId, experienceId)) {
            is Outcome.Failure -> return result
            is Outcome.Success -> result.value
        }
        val askFirst = remote.askFirstItems(coupleId, experienceId, uid).getOrNull().orEmpty()
        val settings = NightSettings(
            mode = Mode.entries.firstOrNull { it.name == doc.mode } ?: Mode.TOGETHER,
            durationMin = doc.durationMin,
            moods = doc.moods.mapNotNullTo(LinkedHashSet()) { Mood.fromId(it) },
            intensity = Intensity.entries.firstOrNull { it.level == doc.intensity } ?: Intensity.SOFT,
            leadership = Leadership.entries.firstOrNull { it.name == doc.leadership } ?: Leadership.RANDOM,
            novelty = Novelty.entries.firstOrNull { it.name == doc.novelty } ?: Novelty.MIXED,
            focus = doc.focus,
        )
        val server = ServerNight(
            request = HeldNight.requestOf(settings, doc.seed),
            chapters = doc.chapters.map { it.toChapter() },
            alternates = doc.alternates,
            effectiveIntensity = doc.effectiveIntensity,
            engineVersion = Engine.VERSION,
        )
        hold(experienceId, settings, server, askFirst.toSet(), doc.focusIncluded)
    }

    /** Resolves what the server released against this phone's content, and holds it. */
    @Suppress("LongParameterList") // A held night is what the server released, in full.
    private suspend fun hold(
        experienceId: String,
        settings: NightSettings,
        server: ServerNight,
        askFirstItems: Set<String>,
        focusIncluded: Boolean,
        mystery: Boolean = false,
    ): Outcome<PlannedNight> {
        var pool = poolFor(server)
        if (server.chapters.any { it.itemId !in pool.ids }) {
            // The server knows content this phone has not got yet: catch up once, then retry.
            content.sync()
            pool = poolFor(server)
        }
        if (server.chapters.any { it.itemId !in pool.ids }) {
            return Outcome.Failure(AppError.Validation(CONTENT_OUT_OF_DATE))
        }
        val night = Night(server.request, server.chapters, server.effectiveIntensity)
        val next = HeldNight(
            experienceId = experienceId,
            settings = settings,
            pool = pool,
            night = night,
            serverIds = server.chapters.map { it.itemId },
            askFirstItems = askFirstItems,
            focusIncluded = focusIncluded,
            mystery = mystery,
        )
        held = next
        return Outcome.Success(next.planned())
    }

    private fun heldFor(experienceId: String): HeldNight? = held?.takeIf { it.experienceId == experienceId }

    private suspend fun poolFor(server: ServerNight): PermittedPool {
        val byId: Map<String, ContentItem> = content.items.first().associateBy { it.id }
        return PermittedPool.fromServer(server) { byId[it] }
    }

    override suspend fun start(experienceId: String): Outcome<String> =
        remote.createSession(experienceId).map { it.sessionId }

    override suspend fun consent(sessionId: String): Outcome<Unit> = remote.markReady(sessionId).map { }

    override suspend fun decline(sessionId: String): Outcome<Unit> = end(sessionId, SessionAction.DECLINE)

    override suspend fun begin(experienceId: String, sessionId: String): Outcome<PlannedNight> =
        advance(experienceId, sessionId, -1)

    override suspend fun finishChapter(experienceId: String, sessionId: String, index: Int): Outcome<PlannedNight> =
        advance(experienceId, sessionId, index)

    private suspend fun advance(experienceId: String, sessionId: String, fromChapter: Int): Outcome<PlannedNight> =
        lock.withLock {
            val current = heldFor(experienceId) ?: return Outcome.Failure(AppError.NotFound())
            val swappedTo = if (fromChapter >= 0) current.unreportedSwap(fromChapter) else null
            when (val result = remote.advance(sessionId, fromChapter, swappedTo)) {
                is Outcome.Failure -> result
                is Outcome.Success -> {
                    val reported = if (swappedTo != null) current.reported(fromChapter) else current
                    val blocked = result.value.blocked.toSet()
                    val next = reported.withBlocked(blocked, result.value.session.currentChapter, remixer())
                    held = next
                    Outcome.Success(next.planned())
                }
            }
        }

    override suspend fun pause(sessionId: String): Outcome<Unit> = end(sessionId, SessionAction.PAUSE)

    override suspend fun resume(sessionId: String): Outcome<Unit> = end(sessionId, SessionAction.RESUME)

    override suspend fun stop(sessionId: String): Outcome<Unit> = end(sessionId, SessionAction.STOP)

    private suspend fun end(sessionId: String, action: SessionAction): Outcome<Unit> =
        remote.endSession(sessionId, action).map { }

    override fun swap(experienceId: String, index: Int): PlannedNight? {
        val current = heldFor(experienceId) ?: return null
        // Instant and offline: ranked by what was last known, without waiting on anything.
        val next = current.swapped(index, Remixer(cachedContext)) ?: return null
        held = next
        return next.planned()
    }

    override suspend fun makeIt(experienceId: String, modifier: RemixModifier): PlannedNight? = lock.withLock {
        val current = heldFor(experienceId) ?: return null
        // A new seed: "more intense" twice in a row should not give the same night twice.
        val next = current.remixed(modifier, remixer(), clock.nowMillis()) ?: return null
        // The night is also the couple's record, and a session counts its chapters (§12): the
        // server has to agree to it before this phone plays it.
        val chapters = next.night.chapters.map { it.itemId to it.kind.name }
        return when (remote.remixNight(experienceId, chapters)) {
            is Outcome.Failure -> null
            is Outcome.Success -> {
                held = next.copy(serverIds = next.night.chapters.map { it.itemId })
                next.planned()
            }
        }
    }

    override suspend fun keepOut(
        experienceId: String,
        themeId: String,
        level: BoundaryLevel,
        fromIndex: Int,
    ): Outcome<PlannedNight> {
        when (val result = boundaries.setBoundary(themeId, Boundary(level))) {
            is Outcome.Failure -> return result
            is Outcome.Success -> Unit
        }
        return lock.withLock {
            val current = heldFor(experienceId) ?: return Outcome.Failure(AppError.NotFound())
            val next = current.withBlocked(current.itemsAbout(themeId), fromIndex, remixer())
            held = next
            Outcome.Success(next.planned())
        }
    }

    override suspend fun react(experienceId: String, chapterIndex: Int, reaction: ChapterReaction): Outcome<Unit> {
        val (uid, coupleId) = ids.first() ?: return Outcome.Failure(AppError.Unauthenticated())
        val night = heldFor(experienceId)?.night ?: return Outcome.Failure(AppError.NotFound())
        val contentId = night.chapters.getOrNull(chapterIndex)?.itemId ?: return Outcome.Failure(AppError.NotFound())
        return remote.sendFeedback(coupleId, uid, FeedbackPayload(experienceId, chapterIndex, contentId, reaction))
    }

    // ---- ranking for swaps -----------------------------------------------------------------

    /** What this phone may rank by (engine/SPEC.md §4 with private terms neutral). */
    @Volatile
    private var cachedContext: ClientContext = ClientContext()

    private suspend fun remixer(): Remixer {
        // Revealed matches are known to both partners; nothing private is used (§5.4).
        val matches = withTimeoutOrNull(MATCHES_TIMEOUT_MS) { mutual.matches.first() }.orEmpty()
        cachedContext = ClientContext(mutual = matches.associate { it.itemId to it.level }, nowMs = clock.nowMillis())
        return Remixer(cachedContext)
    }

    companion object {
        /** The server knows content this phone does not, even after a sync. */
        const val CONTENT_OUT_OF_DATE = "content-out-of-date"

        /** Only ever a fallback: a server too old to say how long the surprise it built runs. */
        private const val DEFAULT_SURPRISE_MIN = 45
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val MATCHES_TIMEOUT_MS = 2_000L
    }
}

internal fun SessionDoc.toSession(uid: String?, partnerLastSeenMillis: Long? = null): NightSession = NightSession(
    id = id,
    experienceId = experienceId,
    state = state,
    pausedFrom = pausedFrom,
    currentChapter = currentChapter,
    chapterCount = chapterCount,
    consentRequired = consentRequired,
    consented = uid != null && uid in consentedUids,
    partnerConsented = consentedUids.any { it != uid },
    isHost = hostUid == uid,
    endReason = endReason,
    experienceType = ExperienceType.entries.firstOrNull { it.name == experienceType } ?: ExperienceType.NIGHT,
    kind = SessionKind.entries.firstOrNull { it.name == kind } ?: SessionKind.ONE_DEVICE,
    submitted = uid != null && uid in submittedUids,
    partnerSubmitted = submittedUids.any { it != uid },
    seen = uid != null && uid in seenUids,
    partnerSeen = seenUids.any { it != uid },
    partnerLastSeenMillis = partnerLastSeenMillis,
)

/** The settings the server says a night was built with (§14.2). */
private fun NightSettingsPayload.toSettings(): NightSettings = NightSettings(
    mode = Mode.entries.firstOrNull { it.name == mode } ?: Mode.TOGETHER,
    durationMin = durationMin,
    moods = moods.mapNotNullTo(LinkedHashSet()) { Mood.fromId(it) },
    intensity = Intensity.entries.firstOrNull { it.level == intensity } ?: Intensity.SOFT,
    leadership = Leadership.entries.firstOrNull { it.name == leadership } ?: Leadership.RANDOM,
    novelty = Novelty.entries.firstOrNull { it.name == novelty } ?: Novelty.MIXED,
)

private fun ChapterPayload.toChapter(): Chapter = Chapter(
    index = index,
    kind = ChapterKind.entries.firstOrNull { it.name == kind } ?: ChapterKind.WILDCARD,
    itemId = contentId,
    intensity = intensity,
)
