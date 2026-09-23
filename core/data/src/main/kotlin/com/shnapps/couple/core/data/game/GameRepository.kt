package com.shnapps.couple.core.data.game

import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.auth.AuthRepository
import com.shnapps.couple.core.data.content.ContentRepository
import com.shnapps.couple.core.data.night.toSession
import com.shnapps.couple.core.datastore.AppPreferencesStore
import com.shnapps.couple.core.engine.game.ReleasedGame
import com.shnapps.couple.core.engine.game.ServerGame
import com.shnapps.couple.core.firebase.game.GamePayload
import com.shnapps.couple.core.firebase.game.GameRemoteDataSource
import com.shnapps.couple.core.firebase.night.NightRemoteDataSource
import com.shnapps.couple.core.firebase.night.SessionAction
import com.shnapps.couple.core.model.Game
import com.shnapps.couple.core.model.GamePlayers
import com.shnapps.couple.core.model.GamePrompt
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.NightSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive
import javax.inject.Inject
import javax.inject.Singleton

/** A game the server started, released to this phone and ready to play (§11). */
data class StartedGame(val experienceId: String, val released: ReleasedGame)

/**
 * What this phone knows about a session right now (§12.3). While the listener is down, the last
 * thing the server said stays on screen, marked [reconnecting]: the server's state is the truth,
 * and this is the most recent truth there is.
 */
data class SessionLink(val session: NightSession?, val reconnecting: Boolean = false)

/**
 * Games (BUILD_PROMPT.md §11): the server chooses every round behind both partners'
 * boundaries; this phone resolves what it released and plays it through the same session as
 * a night — "I'm in" from both phones at BOLD and WILD, PAUSE and STOP from either, every
 * round moved on by the server (DECISIONS.md D-070).
 *
 * What the partners answer during a game stays in the game being played on this phone, and
 * is never stored or sent anywhere (D-069). Nor does a game write anyone's limits: the phone is
 * in both partners' hands, so "keep it out" holds for the rest of the game only (D-071).
 */
@Suppress("TooManyFunctions") // One game, one repository: every step of its session goes through here.
interface GameRepository {
    /** The games this version can play: definitions only, safe to list. */
    val games: Flow<List<Game>>

    /** The names the phone was last passed between, if any were given. */
    val players: Flow<GamePlayers?>

    suspend fun rememberPlayers(players: GamePlayers)

    /** Asks the server for [gameId]'s rounds at up to [intensity], resolved on this phone. */
    suspend fun start(gameId: String, intensity: Intensity): Outcome<StartedGame>

    /** The game [experienceId] again, for a phone that restarted mid-game. */
    suspend fun load(experienceId: String): Outcome<StartedGame>

    /**
     * The session, as this phone sees it: the server's state, the partner's presence, and
     * whether this phone is currently cut off from it (§12.3).
     */
    fun observeSession(sessionId: String): Flow<SessionLink>

    /**
     * Opens the game's session — the couple's one experience under way — and returns its id.
     * [twoPhones]: each partner answers on their own, rather than one phone passed over (§12).
     */
    suspend fun openSession(experienceId: String, twoPhones: Boolean = false): Outcome<String>

    /** "I'm in" — this user's own, and nobody else's (§3.1). */
    suspend fun consent(sessionId: String): Outcome<Unit>

    suspend fun decline(sessionId: String): Outcome<Unit>

    /** Moves to the first round. Returns what the couple's limits no longer permit. */
    suspend fun begin(sessionId: String): Outcome<Set<String>>

    /**
     * Round [index] is over, played with [playedPromptId] (null when it was passed over).
     * Returns what the couple's limits no longer permit from here on (D-062).
     */
    suspend fun finishRound(sessionId: String, index: Int, playedPromptId: String?): Outcome<Set<String>>

    /**
     * This phone's own answer for round [round] (§12.2), as one short string — the only thing it
     * writes about a round. The server turns both partners' into the reveal below.
     */
    suspend fun submit(sessionId: String, round: Int, answer: String?): Outcome<Unit>

    /** "I've seen the reveal", this phone's own: the round is over when both have. */
    suspend fun markSeen(sessionId: String, round: Int): Outcome<Unit>

    /** The reveal the server wrote for [round]: what each seat answered, or null until it exists. */
    fun observeReveal(sessionId: String, round: Int): Flow<Map<Int, String?>?>

    /**
     * "I'm still here" (§12.3): while this flow is collected, the phone says so on its own
     * beat. How often is this layer's business, not the caller's.
     */
    fun keepPresent(sessionId: String): Flow<Unit>

    suspend fun pause(sessionId: String): Outcome<Unit>

    suspend fun resume(sessionId: String): Outcome<Unit>

    /** STOP (§3.1): no confirmation, and nothing records who. */
    suspend fun stop(sessionId: String): Outcome<Unit>
}

// Implements GameRepository (see there).
@Suppress("TooManyFunctions")
@Singleton
class DefaultGameRepository @Inject constructor(
    private val authRepository: AuthRepository,
    private val remote: GameRemoteDataSource,
    private val sessions: NightRemoteDataSource,
    private val content: ContentRepository,
    private val preferences: AppPreferencesStore,
) : GameRepository {

    /** The game this phone last started: its player opens it without asking the server again. */
    @Volatile
    private var lastStarted: StartedGame? = null

    private val ids: Flow<Pair<String, String>?> = authRepository.currentProfile
        .map { profile -> profile?.coupleId?.let { profile.uid to it } }
        .distinctUntilChanged()

    override val games: Flow<List<Game>> = content.games

    override val players: Flow<GamePlayers?> = preferences.preferences.map { it.gamePlayers }.distinctUntilChanged()

    override suspend fun rememberPlayers(players: GamePlayers) = preferences.setGamePlayers(players)

    override suspend fun start(gameId: String, intensity: Intensity): Outcome<StartedGame> =
        when (val result = remote.startGame(gameId, intensity.level)) {
            is Outcome.Failure -> result
            is Outcome.Success -> release(result.value).also { released ->
                if (released is Outcome.Success) lastStarted = released.value
            }
        }

    override suspend fun load(experienceId: String): Outcome<StartedGame> {
        lastStarted?.takeIf { it.experienceId == experienceId }?.let { return Outcome.Success(it) }
        val (_, coupleId) = ids.first() ?: return Outcome.Failure(AppError.Unauthenticated())
        return when (val result = remote.game(coupleId, experienceId)) {
            is Outcome.Failure -> result
            is Outcome.Success -> release(result.value)
        }
    }

    /** Resolves what the server released against this phone's content. */
    private suspend fun release(payload: GamePayload): Outcome<StartedGame> {
        val server = ServerGame(payload.gameId, payload.rounds, payload.alternates, payload.effectiveIntensity)
        var released = resolve(server)
        if (released == null || released.rounds.any { !it.playable }) {
            // The server knows content this phone has not got yet: catch up once, then retry.
            content.sync()
            released = resolve(server)
        }
        return when {
            released == null -> Outcome.Failure(AppError.Validation(GAME_UNKNOWN))
            released.rounds.none { it.playable } -> Outcome.Failure(AppError.Validation(CONTENT_OUT_OF_DATE))
            else -> Outcome.Success(StartedGame(payload.experienceId, released))
        }
    }

    private suspend fun resolve(server: ServerGame): ReleasedGame? {
        val game = content.games.first().firstOrNull { it.id == server.gameId } ?: return null
        val byId: Map<String, GamePrompt> = content.prompts.first().associateBy { it.id }
        return ReleasedGame.fromServer(server, game) { byId[it] }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeSession(sessionId: String): Flow<SessionLink> = ids.flatMapLatest { pair ->
        if (pair == null) {
            flowOf(SessionLink(session = null))
        } else {
            val (uid, coupleId) = pair
            combine(linkedSession(coupleId, sessionId), presence(coupleId, sessionId)) { link, seen ->
                val partnerSeen = seen.filterKeys { it != uid }.values.maxOrNull()
                link.copy(session = link.session?.copy(partnerLastSeenMillis = partnerSeen))
            }
        }
    }

    /**
     * The session document, followed through whatever the network does (§12.3): a listener that
     * drops is retried, and until it is back the last state stays on screen, marked as such.
     */
    private fun linkedSession(coupleId: String, sessionId: String): Flow<SessionLink> = flow {
        val uid = ids.first()?.first
        var last: NightSession? = null
        var attempt = 0
        // Until the server has answered once, a cached read is the phone catching up, not a
        // phone cut off: saying "Reconnecting…" before the first word would be noise.
        var heard = false
        while (currentCoroutineContext().isActive) {
            try {
                sessions.observeSession(coupleId, sessionId).collect { doc ->
                    attempt = 0
                    heard = heard || doc?.fromCache == false
                    last = doc?.toSession(uid)
                    emit(SessionLink(last, reconnecting = heard && doc?.fromCache == true))
                }
                return@flow
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                emit(SessionLink(last, reconnecting = true))
                delay(minOf(RECONNECT_MAX_MS, RECONNECT_FIRST_MS shl attempt.coerceAtMost(RECONNECT_SHIFTS)))
                attempt += 1
            }
        }
    }

    private fun presence(coupleId: String, sessionId: String): Flow<Map<String, Long>> =
        sessions.observePresence(coupleId, sessionId).catch { emit(emptyMap()) }.onStart { emit(emptyMap()) }

    override suspend fun openSession(experienceId: String, twoPhones: Boolean): Outcome<String> =
        sessions.createSession(experienceId, twoPhones).map { it.sessionId }

    override suspend fun consent(sessionId: String): Outcome<Unit> = sessions.markReady(sessionId).map { }

    override suspend fun decline(sessionId: String): Outcome<Unit> = end(sessionId, SessionAction.DECLINE)

    override suspend fun begin(sessionId: String): Outcome<Set<String>> =
        sessions.advance(sessionId, fromChapter = -1, playedItemId = null).map { it.blocked.toSet() }

    override suspend fun finishRound(sessionId: String, index: Int, playedPromptId: String?): Outcome<Set<String>> =
        sessions.advance(sessionId, fromChapter = index, playedItemId = playedPromptId).map { it.blocked.toSet() }

    override suspend fun submit(sessionId: String, round: Int, answer: String?): Outcome<Unit> {
        val (uid, coupleId) = ids.first() ?: return Outcome.Failure(AppError.Unauthenticated())
        return sessions.submitAnswer(coupleId, sessionId, uid, round, answer)
    }

    override suspend fun markSeen(sessionId: String, round: Int): Outcome<Unit> =
        sessions.markSeen(sessionId, round).map { }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeReveal(sessionId: String, round: Int): Flow<Map<Int, String?>?> = ids.flatMapLatest { pair ->
        if (pair == null) {
            flowOf(null)
        } else {
            val (_, coupleId) = pair
            // The reveal names each partner by uid; a phone plays a seat, and the host is the first.
            combine(
                sessions.observeSession(coupleId, sessionId).catch { emit(null) },
                sessions.observeReveal(coupleId, sessionId, round).catch { emit(null) },
            ) { doc, reveal ->
                val host = doc?.hostUid
                reveal?.answers?.entries?.associate { (uid, answer) -> (if (uid == host) 0 else 1) to answer }
            }
        }
    }

    override fun keepPresent(sessionId: String): Flow<Unit> = flow {
        while (true) {
            val (uid, coupleId) = ids.first() ?: return@flow
            sessions.sendPresence(coupleId, sessionId, uid)
            emit(Unit)
            delay(PRESENCE_EVERY_MS)
        }
    }

    override suspend fun pause(sessionId: String): Outcome<Unit> = end(sessionId, SessionAction.PAUSE)

    override suspend fun resume(sessionId: String): Outcome<Unit> = end(sessionId, SessionAction.RESUME)

    override suspend fun stop(sessionId: String): Outcome<Unit> = end(sessionId, SessionAction.STOP)

    private suspend fun end(sessionId: String, action: SessionAction): Outcome<Unit> =
        sessions.endSession(sessionId, action).map { }

    companion object {
        /** How often a phone in a session says it is still there (§12.3). */
        const val PRESENCE_EVERY_MS = 20_000L

        /** How long a dropped listener waits before trying again, and the longest it ever waits. */
        const val RECONNECT_FIRST_MS = 1_000L
        const val RECONNECT_MAX_MS = 30_000L
        private const val RECONNECT_SHIFTS = 5

        /** The server started a game this phone's content does not have. */
        const val GAME_UNKNOWN = "game-unknown"

        /** The server knows content this phone does not, even after a sync. */
        const val CONTENT_OUT_OF_DATE = "content-out-of-date"
    }
}
