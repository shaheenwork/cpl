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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** A game the server started, released to this phone and ready to play (§11). */
data class StartedGame(val experienceId: String, val released: ReleasedGame)

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

    fun observeSession(sessionId: String): Flow<NightSession?>

    /** Opens the game's session — the couple's one experience under way — and returns its id. */
    suspend fun openSession(experienceId: String): Outcome<String>

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
    override fun observeSession(sessionId: String): Flow<NightSession?> = ids.flatMapLatest { pair ->
        if (pair == null) {
            flowOf(null)
        } else {
            val (uid, coupleId) = pair
            sessions.observeSession(coupleId, sessionId).map { it?.toSession(uid) }.catch { emit(null) }
        }
    }

    override suspend fun openSession(experienceId: String): Outcome<String> =
        sessions.createSession(experienceId).map { it.sessionId }

    override suspend fun consent(sessionId: String): Outcome<Unit> = sessions.markReady(sessionId).map { }

    override suspend fun decline(sessionId: String): Outcome<Unit> = end(sessionId, SessionAction.DECLINE)

    override suspend fun begin(sessionId: String): Outcome<Set<String>> =
        sessions.advance(sessionId, fromChapter = -1, playedItemId = null).map { it.blocked.toSet() }

    override suspend fun finishRound(sessionId: String, index: Int, playedPromptId: String?): Outcome<Set<String>> =
        sessions.advance(sessionId, fromChapter = index, playedItemId = playedPromptId).map { it.blocked.toSet() }

    override suspend fun pause(sessionId: String): Outcome<Unit> = end(sessionId, SessionAction.PAUSE)

    override suspend fun resume(sessionId: String): Outcome<Unit> = end(sessionId, SessionAction.RESUME)

    override suspend fun stop(sessionId: String): Outcome<Unit> = end(sessionId, SessionAction.STOP)

    private suspend fun end(sessionId: String, action: SessionAction): Outcome<Unit> =
        sessions.endSession(sessionId, action).map { }

    companion object {
        /** The server started a game this phone's content does not have. */
        const val GAME_UNKNOWN = "game-unknown"

        /** The server knows content this phone does not, even after a sync. */
        const val CONTENT_OUT_OF_DATE = "content-out-of-date"
    }
}
