package com.shnapps.couple.core.testing

import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.game.GameRepository
import com.shnapps.couple.core.data.game.SessionLink
import com.shnapps.couple.core.data.game.StartedGame
import com.shnapps.couple.core.engine.game.ReleasedGame
import com.shnapps.couple.core.engine.game.ServerGame
import com.shnapps.couple.core.model.ExperienceType
import com.shnapps.couple.core.model.Game
import com.shnapps.couple.core.model.GamePlayers
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.NightSession
import com.shnapps.couple.core.model.SessionEndReason
import com.shnapps.couple.core.model.SessionKind
import com.shnapps.couple.core.model.SessionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [GameRepository] that plays the server's part, as FakeNightRepository does for
 * nights: a started game is released from [TestGames]' prompts, and its session moves through
 * the states the callables would move it through.
 */
// One fake for one interface: it has as many functions as GameRepository has, plus the few a
// test uses to play the partner's phone.
@Suppress("TooManyFunctions")
class FakeGameRepository(
    games: List<Game> = TestGames.games,
) : GameRepository {

    override val games = MutableStateFlow(games)
    val savedPlayers = MutableStateFlow<GamePlayers?>(null)
    override val players: Flow<GamePlayers?> = savedPlayers

    val session = MutableStateFlow<NightSession?>(null)

    /** The reveals the server has written, by round: what each seat answered (§12.2). */
    val reveals = MutableStateFlow<Map<Int, Map<Int, String?>>>(emptyMap())

    /** Set while this phone is cut off from the session (§12.3). */
    val reconnecting = MutableStateFlow(false)

    /** Every answer this phone sent, and every round it said it had seen. */
    val submitted = mutableListOf<Pair<Int, String?>>()
    val seenRounds = mutableListOf<Int>()
    val presenceSent = mutableListOf<String>()

    /** Every call, in order: "start:GAME:2", "open", "consent", "begin", "finish:0:id", "stop"… */
    val calls = mutableListOf<String>()
    var failNext: AppError? = null

    /** What the server answers with next, in place of the default game. */
    var startResult: Outcome<StartedGame>? = null

    /** What the couple's limits rule out, reported on the next begin or round finished. */
    var blockedNext: Set<String> = emptySet()

    /** The partner, on their own phone. */
    fun partnerConsents() = session.update { current ->
        current?.let { it.copy(partnerConsented = true, state = if (it.consented) SessionState.READY else it.state) }
    }

    fun partnerStops() = session.update {
        it?.copy(state = SessionState.ABANDONED, endReason = SessionEndReason.STOPPED)
    }

    fun partnerPauses() = session.update { it?.copy(state = SessionState.PAUSED, pausedFrom = it.state) }

    override suspend fun rememberPlayers(players: GamePlayers) {
        calls += "players"
        savedPlayers.value = players
    }

    override suspend fun start(
        gameId: String,
        intensity: Intensity,
    ): Outcome<StartedGame> = call("start:$gameId:${intensity.level}") {
        startResult?.let { return it }
        released(gameId, intensity)
    }

    override suspend fun load(experienceId: String): Outcome<StartedGame> = call("load") {
        startResult?.let { return it }
        released(experienceId.removePrefix(EXPERIENCE_PREFIX).removeSuffix(BOLD_SUFFIX), Intensity.EXTREME)
    }

    override fun observeSession(sessionId: String): Flow<SessionLink> =
        combine(session, reconnecting) { current, cut ->
            SessionLink(current?.takeIf { it.id == sessionId }, reconnecting = cut)
        }

    override suspend fun submit(sessionId: String, round: Int, answer: String?): Outcome<Unit> =
        call("submit:$round:$answer") {
            submitted += round to answer
            session.update { current ->
                current?.copy(
                    submitted = true,
                    state = if (current.partnerSubmitted) SessionState.REVEAL else SessionState.AWAITING_SUBMISSIONS,
                )
            }
        }

    override suspend fun markSeen(sessionId: String, round: Int): Outcome<Unit> = call("seen:$round") {
        seenRounds += round
        session.update { current ->
            current?.let {
                it.copy(seen = true, state = if (it.partnerSeen) SessionState.CHAPTER_COMPLETE else it.state)
            }
        }
    }

    override fun observeReveal(sessionId: String, round: Int): Flow<Map<Int, String?>?> = reveals.map { it[round] }

    /** One heartbeat, then done: a test says how long a session is watched by what it does. */
    override fun keepPresent(sessionId: String): Flow<Unit> = flow {
        presenceSent += sessionId
        emit(Unit)
    }

    /** The partner, on their own phone: answers [round], which the server then reveals. */
    fun partnerSubmits(round: Int, answers: Map<Int, String?>) {
        session.update { current -> current?.copy(partnerSubmitted = true, state = SessionState.REVEAL) }
        reveals.update { it + (round to answers) }
    }

    /** The partner, on their own phone: has seen the reveal. */
    fun partnerSeesReveal() = session.update { current ->
        current?.let { it.copy(partnerSeen = true, state = if (it.seen) SessionState.CHAPTER_COMPLETE else it.state) }
    }

    override suspend fun openSession(experienceId: String, twoPhones: Boolean): Outcome<String> =
        call(if (twoPhones) "open:two" else "open") {
            val consent = experienceId.endsWith(BOLD_SUFFIX)
            session.value = NightSession(
                id = SESSION_ID,
                experienceId = experienceId,
                state = if (consent) SessionState.WAITING_FOR_PARTNER else SessionState.READY,
                pausedFrom = null,
                currentChapter = -1,
                chapterCount = ROUNDS,
                consentRequired = consent,
                consented = false,
                partnerConsented = false,
                isHost = true,
                endReason = null,
                experienceType = ExperienceType.GAME,
                kind = if (twoPhones) SessionKind.TWO_DEVICE else SessionKind.ONE_DEVICE,
            )
            SESSION_ID
        }

    override suspend fun consent(sessionId: String): Outcome<Unit> = call("consent") {
        session.update { current ->
            current?.let {
                it.copy(consented = true, state = if (it.partnerConsented) SessionState.READY else it.state)
            }
        }
    }

    override suspend fun decline(sessionId: String): Outcome<Unit> = call("decline") {
        session.update { it?.copy(state = SessionState.ABANDONED, endReason = SessionEndReason.DECLINED) }
    }

    override suspend fun begin(sessionId: String): Outcome<Set<String>> = call("begin") {
        session.update { it?.copy(state = SessionState.CHAPTER_ACTIVE, currentChapter = 0) }
        blockedNext.also { blockedNext = emptySet() }
    }

    override suspend fun finishRound(sessionId: String, index: Int, playedPromptId: String?): Outcome<Set<String>> =
        call("finish:$index:$playedPromptId") {
            session.update { current ->
                current?.let {
                    if (index >= it.chapterCount - 1) {
                        it.copy(state = SessionState.COMPLETED, endReason = SessionEndReason.COMPLETED)
                    } else {
                        // A new chapter starts clean: what each phone has sent and seen is per
                        // chapter, and the server clears it (§12.1).
                        it.copy(
                            currentChapter = index + 1,
                            state = SessionState.CHAPTER_ACTIVE,
                            submitted = false,
                            partnerSubmitted = false,
                            seen = false,
                            partnerSeen = false,
                        )
                    }
                }
            }
            blockedNext.also { blockedNext = emptySet() }
        }

    override suspend fun pause(sessionId: String): Outcome<Unit> = call("pause") {
        session.update { it?.copy(state = SessionState.PAUSED, pausedFrom = it.state) }
    }

    override suspend fun resume(sessionId: String): Outcome<Unit> = call("resume") {
        session.update { it?.copy(state = it.pausedFrom ?: SessionState.READY, pausedFrom = null) }
    }

    override suspend fun stop(sessionId: String): Outcome<Unit> = call("stop") {
        session.update { it?.copy(state = SessionState.ABANDONED, endReason = SessionEndReason.STOPPED) }
    }

    /** Three rounds of [gameId] from [TestGames], and what is left over as alternates. */
    private fun released(gameId: String, intensity: Intensity): StartedGame {
        val game = games.value.first { it.id == gameId }
        val prompts = TestGames.prompts.filter { it.game == gameId }
        val rounds = if (game.promptKinds.isEmpty()) {
            prompts.take(ROUNDS).map { listOf(it.id) }
        } else {
            (0 until ROUNDS).map { round ->
                game.promptKinds.map { kind -> prompts.filter { it.kind == kind }[round].id }
            }
        }
        val used = rounds.flatten().toSet()
        val server = ServerGame(gameId, rounds, prompts.map { it.id }.filterNot { it in used }, intensity.level)
        val suffix = if (intensity.requiresBothPartyConsent) BOLD_SUFFIX else ""
        return StartedGame(
            experienceId = "$EXPERIENCE_PREFIX$gameId$suffix",
            released = ReleasedGame.fromServer(server, game) { id -> prompts.firstOrNull { it.id == id } },
        )
    }

    private inline fun <T> call(name: String, action: () -> T): Outcome<T> {
        calls += name
        failNext?.let {
            failNext = null
            return Outcome.Failure(it)
        }
        return Outcome.Success(action())
    }

    companion object {
        const val SESSION_ID = "game_session_test"
        const val EXPERIENCE_PREFIX = "game_"

        /** A game started at BOLD or WILD: its session waits for both partners' "I'm in". */
        const val BOLD_SUFFIX = "_bold"
        const val ROUNDS = 3
    }
}
