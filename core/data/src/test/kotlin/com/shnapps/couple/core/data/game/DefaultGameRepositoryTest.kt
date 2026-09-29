package com.shnapps.couple.core.data.game

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.firebase.game.GamePayload
import com.shnapps.couple.core.firebase.game.GameRemoteDataSource
import com.shnapps.couple.core.firebase.night.AdvancePayload
import com.shnapps.couple.core.firebase.night.CoupleNightState
import com.shnapps.couple.core.firebase.night.ExperienceDoc
import com.shnapps.couple.core.firebase.night.FeedbackPayload
import com.shnapps.couple.core.firebase.night.NightRemoteDataSource
import com.shnapps.couple.core.firebase.night.PlanDoc
import com.shnapps.couple.core.firebase.night.RevealDoc
import com.shnapps.couple.core.firebase.night.ServerNightPayload
import com.shnapps.couple.core.firebase.night.SessionAction
import com.shnapps.couple.core.firebase.night.SessionDoc
import com.shnapps.couple.core.firebase.night.SessionPayload
import com.shnapps.couple.core.model.ExperienceType
import com.shnapps.couple.core.model.GamePlayers
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.Mode
import com.shnapps.couple.core.model.NightSettings
import com.shnapps.couple.core.model.SessionState
import com.shnapps.couple.core.model.UserProfile
import com.shnapps.couple.core.testing.FakeAppPreferencesStore
import com.shnapps.couple.core.testing.FakeAuthRepository
import com.shnapps.couple.core.testing.FakeContentRepository
import com.shnapps.couple.core.testing.TestGames
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException

/**
 * The game repository against a fake backend (BUILD_PROMPT.md §11, §12): rounds resolved
 * only from what the server released, content caught up when the server knows more, and a
 * game's session driven through the same callables as a night.
 */
class DefaultGameRepositoryTest {

    private val remote = FakeGameRemote()
    private val sessions = FakeSessions()
    private val content = FakeContentRepository()
    private val preferences = FakeAppPreferencesStore()
    private val auth = FakeAuthRepository().apply { profile.value = UserProfile(uid = UID, coupleId = COUPLE) }

    private val repository = DefaultGameRepository(auth, remote, sessions, content, preferences)

    private val rounds = listOf(listOf("tod_t1", "tod_d1"), listOf("tod_t2", "tod_d2"), listOf("tod_t3", "tod_d3"))

    @Test
    fun `a started game holds exactly the rounds and alternates the server released`() = runTest {
        remote.next = payload(rounds, alternates = listOf("tod_t4", "tod_d4"))

        val started = (repository.start(TestGames.TRUTH_OR_DARE, Intensity.SPICY) as Outcome.Success).value

        assertThat(remote.started).containsExactly(TestGames.TRUTH_OR_DARE to 3)
        assertThat(started.experienceId).isEqualTo(EXPERIENCE)
        assertThat(started.released.rounds.map { round -> round.prompts.map { it.id } }).isEqualTo(rounds)
        assertThat(started.released.alternates.map { it.id }).containsExactly("tod_t4", "tod_d4").inOrder()
        assertThat(content.syncCalls).isEqualTo(0)
    }

    @Test
    fun `a prompt the phone has not got yet is caught up once, and a round still missing is passed over`() = runTest {
        remote.next = payload(
            listOf(listOf("tod_t1", "tod_d1"), listOf("tod_t_new", "tod_d2"), listOf("tod_t3", "tod_d3")),
        )

        val started = (repository.start(TestGames.TRUTH_OR_DARE, Intensity.SPICY) as Outcome.Success).value

        assertThat(content.syncCalls).isEqualTo(1)
        assertThat(started.released.rounds.map { it.playable }).containsExactly(true, false, true).inOrder()
    }

    @Test
    fun `a game this phone does not know, or cannot play at all, is refused`() = runTest {
        remote.next = payload(rounds).copy(gameId = "FROM_A_NEWER_APP")
        val unknown = repository.start("FROM_A_NEWER_APP", Intensity.MILD) as Outcome.Failure
        assertThat((unknown.error as AppError.Validation).code).isEqualTo(DefaultGameRepository.GAME_UNKNOWN)

        remote.next = payload(listOf(listOf("gone_1", "gone_2")))
        val empty = repository.start(TestGames.TRUTH_OR_DARE, Intensity.MILD) as Outcome.Failure
        assertThat((empty.error as AppError.Validation).code).isEqualTo(DefaultGameRepository.CONTENT_OUT_OF_DATE)
    }

    @Test
    fun `a restarted phone loads the game again from the couple's own record`() = runTest {
        remote.next = payload(rounds)
        val loaded = (repository.load(EXPERIENCE) as Outcome.Success).value
        assertThat(remote.loaded).containsExactly(COUPLE to EXPERIENCE)
        assertThat(loaded.released.game.id).isEqualTo(TestGames.TRUTH_OR_DARE)
    }

    @Test
    fun `rounds are moved on through the session, reporting what was played`() = runTest {
        sessions.blocked = listOf("tod_d3")
        assertThat((repository.begin(SESSION) as Outcome.Success).value).containsExactly("tod_d3")
        repository.finishRound(SESSION, 0, "tod_d1")
        repository.finishRound(SESSION, 1, null)
        assertThat(sessions.advances).containsExactly(
            Triple(SESSION, -1, null),
            Triple(SESSION, 0, "tod_d1"),
            Triple(SESSION, 1, null),
        )
            .inOrder()
    }

    @Test
    fun `pause, resume, stop and decline go straight to the server`() = runTest {
        repository.pause(SESSION)
        repository.resume(SESSION)
        repository.stop(SESSION)
        repository.decline(SESSION)
        assertThat(sessions.ended).containsExactly(
            SessionAction.PAUSE,
            SessionAction.RESUME,
            SessionAction.STOP,
            SessionAction.DECLINE,
        )
            .inOrder()
    }

    @Test
    fun `the session says it plays a game`() = runTest {
        sessions.session.value = SessionDoc(
            id = SESSION,
            experienceId = EXPERIENCE,
            hostUid = UID,
            state = SessionState.READY,
            pausedFrom = null,
            currentChapter = -1,
            chapterCount = 3,
            consentRequired = false,
            consentedUids = emptySet(),
            endReason = null,
            experienceType = "GAME",
        )
        val session = repository.observeSession(SESSION).first().session!!
        assertThat(session.experienceType).isEqualTo(ExperienceType.GAME)
        assertThat(session.isHost).isTrue()
    }

    @Test
    fun `the game just started opens without asking the server again`() = runTest {
        remote.next = payload(rounds)
        repository.start(TestGames.TRUTH_OR_DARE, Intensity.MILD)
        assertThat((repository.load(EXPERIENCE) as Outcome.Success).value.experienceId).isEqualTo(EXPERIENCE)
        assertThat(remote.loaded).isEmpty()
    }

    @Test
    fun `this phone sends its own answer, and the reveal comes back by seat`() = runTest {
        sessions.session.value = sessionDoc(SessionState.AWAITING_SUBMISSIONS)
        repository.submit(SESSION, round = 1, answer = "P0")
        assertThat(sessions.submissions).containsExactly(Triple(UID, 1, "P0"))
        repository.markSeen(SESSION, round = 1)
        assertThat(sessions.seen).containsExactly(1)

        // The server names each answer by uid; a phone plays a seat, and the host is the first.
        sessions.reveal.value = RevealDoc(chapter = 1, answers = mapOf(UID to "P0", PARTNER to "P1"))
        assertThat(repository.observeReveal(SESSION, 1).first()).containsExactly(0, "P0", 1, "P1")
    }

    @Test
    fun `the partner's heartbeat reaches the session, and this phone's own is not mistaken for it`() = runTest {
        sessions.session.value = sessionDoc(SessionState.CHAPTER_ACTIVE)
        sessions.presence.value = mapOf(UID to 900L, PARTNER to 500L)
        // The first value carries no presence yet: a heartbeat is news, not a starting condition.
        val link = repository.observeSession(SESSION).first { it.session?.partnerLastSeenMillis != null }
        assertThat(link.session?.partnerLastSeenMillis).isEqualTo(500L)

        repository.keepPresent(SESSION).first()
        assertThat(sessions.presenceSent).containsExactly(UID)
    }

    @Test
    fun `a phone reading its own cache is reconnecting, once the server has spoken at all`() = runTest {
        val doc = sessionDoc(SessionState.CHAPTER_ACTIVE)
        sessions.sessionFlow = flow {
            // Firestore serves the cache first, then the server, and falls back to the cache
            // when the network goes: only the last of those is being cut off (§12.3).
            emit(doc.copy(fromCache = true))
            emit(doc)
            emit(doc.copy(fromCache = true))
            awaitCancellation()
        }

        val seen = repository.observeSession(SESSION).take(3).toList()

        assertThat(seen.map { it.reconnecting }).containsExactly(false, false, true).inOrder()
    }

    @Test
    fun `a listener that drops keeps the last state on screen, and says it is reconnecting`() = runTest {
        val doc = sessionDoc(SessionState.CHAPTER_ACTIVE)
        var attempts = 0
        sessions.sessionFlow = flow {
            attempts += 1
            emit(doc)
            if (attempts == 1) throw IOException("the network went away")
            awaitCancellation()
        }

        val seen = repository.observeSession(SESSION).take(3).toList()

        assertThat(seen.map { it.reconnecting }).containsExactly(false, true, false).inOrder()
        // Cut off, the last thing the server said stays on screen (§12.3).
        assertThat(seen.map { it.session?.id }).containsExactly(SESSION, SESSION, SESSION)
    }

    @Test
    fun `the names given for a game stay on the phone for the next one`() = runTest {
        assertThat(repository.players.first()).isNull()
        repository.rememberPlayers(GamePlayers("Alex", "Sam"))
        assertThat(repository.players.first()).isEqualTo(GamePlayers("Alex", "Sam"))
    }

    private fun sessionDoc(state: SessionState) = SessionDoc(
        id = SESSION,
        experienceId = EXPERIENCE,
        hostUid = UID,
        state = state,
        pausedFrom = null,
        currentChapter = 1,
        chapterCount = 3,
        consentRequired = false,
        consentedUids = emptySet(),
        endReason = null,
        experienceType = "GAME",
    )

    private fun payload(rounds: List<List<String>>, alternates: List<String> = emptyList()) = GamePayload(
        experienceId = EXPERIENCE,
        gameId = TestGames.TRUTH_OR_DARE,
        rounds = rounds,
        alternates = alternates,
        effectiveIntensity = 3,
    )

    private class FakeGameRemote : GameRemoteDataSource {
        var next: GamePayload? = null
        val started = mutableListOf<Pair<String, Int>>()
        val loaded = mutableListOf<Pair<String, String>>()

        override suspend fun startGame(gameId: String, intensity: Int): Outcome<GamePayload> {
            started += gameId to intensity
            return Outcome.Success(checkNotNull(next))
        }

        override suspend fun game(coupleId: String, experienceId: String): Outcome<GamePayload> {
            loaded += coupleId to experienceId
            return Outcome.Success(checkNotNull(next))
        }
    }

    /** The session callables, as a game uses them. A game never builds a night. */
    private class FakeSessions : NightRemoteDataSource {
        val advances = mutableListOf<Triple<String, Int, String?>>()
        val submissions = mutableListOf<Triple<String, Int, String?>>()
        val seen = mutableListOf<Int>()
        val presenceSent = mutableListOf<String>()
        val reveal = MutableStateFlow<RevealDoc?>(null)
        val presence = MutableStateFlow<Map<String, Long>>(emptyMap())

        val ended = mutableListOf<SessionAction>()
        var blocked: List<String> = emptyList()
        val session = MutableStateFlow<SessionDoc?>(null)

        /** A session followed some other way: one that drops, for instance. */
        var sessionFlow: Flow<SessionDoc?>? = null

        private fun payload(state: SessionState) = SessionPayload(SESSION, state, 0, 3, consentRequired = false)

        override suspend fun generate(settings: NightSettings): Outcome<ServerNightPayload> =
            error("a game builds no night")

        override suspend fun surprise(mystery: Boolean, mode: Mode): Outcome<ServerNightPayload> =
            error("a game builds no night")

        override suspend fun remixNight(experienceId: String, chapters: List<Pair<String, String>>) =
            error("a game is not remixed")

        override suspend fun planNight(settings: NightSettings) = error("a game is not planned")

        override suspend fun openPlan(experienceId: String) = error("a game is not planned")

        override fun observePlans(coupleId: String): Flow<List<PlanDoc>> = flowOf(emptyList())

        override suspend fun createSession(experienceId: String, twoPhones: Boolean) =
            Outcome.Success(payload(SessionState.READY))

        override suspend fun markReady(sessionId: String) = Outcome.Success(payload(SessionState.READY))

        override suspend fun advance(
            sessionId: String,
            fromChapter: Int,
            playedItemId: String?,
        ): Outcome<AdvancePayload> {
            advances += Triple(sessionId, fromChapter, playedItemId)
            return Outcome.Success(AdvancePayload(payload(SessionState.CHAPTER_ACTIVE), blocked))
        }

        override suspend fun endSession(sessionId: String, action: SessionAction): Outcome<SessionPayload> {
            ended += action
            return Outcome.Success(payload(SessionState.PAUSED))
        }

        override suspend fun submitAnswer(
            coupleId: String,
            sessionId: String,
            uid: String,
            chapter: Int,
            answer: String?,
        ): Outcome<Unit> {
            submissions += Triple(uid, chapter, answer)
            return Outcome.Success(Unit)
        }

        override suspend fun markSeen(sessionId: String, chapter: Int): Outcome<SessionPayload> {
            seen += chapter
            return Outcome.Success(payload(SessionState.CHAPTER_COMPLETE))
        }

        override fun observeReveal(coupleId: String, sessionId: String, chapter: Int): Flow<RevealDoc?> = reveal

        override suspend fun sendPresence(coupleId: String, sessionId: String, uid: String): Outcome<Unit> {
            presenceSent += uid
            return Outcome.Success(Unit)
        }

        override fun observePresence(coupleId: String, sessionId: String): Flow<Map<String, Long>> = presence

        override fun observeCouple(coupleId: String): Flow<CoupleNightState> =
            MutableStateFlow(CoupleNightState(null, null))

        override fun observeSession(coupleId: String, sessionId: String): Flow<SessionDoc?> = sessionFlow ?: session

        override suspend fun experience(coupleId: String, experienceId: String): Outcome<ExperienceDoc> =
            error("a game reads its own record")

        override suspend fun askFirstItems(coupleId: String, experienceId: String, uid: String): Outcome<List<String>> =
            error("a game asks nobody first")

        override suspend fun sendFeedback(coupleId: String, uid: String, feedback: FeedbackPayload): Outcome<Unit> =
            error("a game sends no feedback")
    }

    private companion object {
        const val UID = "uid_host"
        const val PARTNER = "uid_partner"
        const val COUPLE = "couple_1"
        const val EXPERIENCE = "experience_game"
        const val SESSION = "session_game"
    }
}
