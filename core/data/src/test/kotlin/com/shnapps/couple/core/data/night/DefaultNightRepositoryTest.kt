package com.shnapps.couple.core.data.night

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.FakeClock
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.engine.RemixModifier
import com.shnapps.couple.core.firebase.night.AdvancePayload
import com.shnapps.couple.core.firebase.night.ChapterPayload
import com.shnapps.couple.core.firebase.night.CoupleNightState
import com.shnapps.couple.core.firebase.night.ExperienceDoc
import com.shnapps.couple.core.firebase.night.FeedbackPayload
import com.shnapps.couple.core.firebase.night.NightRemoteDataSource
import com.shnapps.couple.core.firebase.night.NightSettingsPayload
import com.shnapps.couple.core.firebase.night.PlanDoc
import com.shnapps.couple.core.firebase.night.PlanPayload
import com.shnapps.couple.core.firebase.night.RevealDoc
import com.shnapps.couple.core.firebase.night.ServerNightPayload
import com.shnapps.couple.core.firebase.night.SessionAction
import com.shnapps.couple.core.firebase.night.SessionDoc
import com.shnapps.couple.core.firebase.night.SessionPayload
import com.shnapps.couple.core.model.BoundaryLevel
import com.shnapps.couple.core.model.ChapterReaction
import com.shnapps.couple.core.model.Mode
import com.shnapps.couple.core.model.NightSettings
import com.shnapps.couple.core.model.SessionState
import com.shnapps.couple.core.model.UserProfile
import com.shnapps.couple.core.testing.FakeAuthRepository
import com.shnapps.couple.core.testing.FakeBoundaryRepository
import com.shnapps.couple.core.testing.FakeContentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Test

/**
 * The night repository against a fake backend (BUILD_PROMPT.md §10, §12): nights held as the
 * server released them, swaps reported exactly when played, mid-night limits applied, and a
 * night restored after the app restarts.
 */
class DefaultNightRepositoryTest {

    private val server = NightFixtures.server
    private val serverIds = server.chapters.map { it.itemId }
    private val remote = FakeNightRemote()
    private val content = FakeContentRepository(items = NightFixtures.catalogue)
    private val boundaries = FakeBoundaryRepository()
    private val auth = FakeAuthRepository().apply {
        profile.value = UserProfile(uid = UID, coupleId = COUPLE)
    }

    private fun TestScope.repository() = DefaultNightRepository(
        authRepository = auth,
        remote = remote,
        content = content,
        boundaries = boundaries,
        clock = FakeClock(NOW),
        appScope = backgroundScope,
    )

    @Test
    fun `holds a built night exactly as the server released it`() = runTest {
        val night = (repository().build(NightFixtures.settings) as Outcome.Success).value
        assertThat(night.experienceId).isEqualTo(EXPERIENCE)
        assertThat(night.chapters.map { it.item.id }).containsExactlyElementsIn(serverIds).inOrder()
        assertThat(night.askFirstItems).containsExactly(server.chapters[2].itemId)
        assertThat(remote.generated).containsExactly(NightFixtures.settings)
    }

    @Test
    fun `reports a swap when its chapter is played, and only then`() = runTest {
        val repository = repository()
        repository.build(NightFixtures.settings)
        val swapped = requireNotNull(repository.swap(EXPERIENCE, 0))
        val replacement = swapped.chapters[0].item.id
        assertThat(replacement).isNotEqualTo(server.chapters[0].itemId)

        repository.begin(EXPERIENCE, SESSION)
        repository.finishChapter(EXPERIENCE, SESSION, 0)
        repository.finishChapter(EXPERIENCE, SESSION, 1)
        assertThat(remote.advances).containsExactly(
            Triple(SESSION, -1, null),
            Triple(SESSION, 0, replacement),
            Triple(SESSION, 1, null),
        ).inOrder()
    }

    @Test
    fun `swaps out what the couple's limits no longer permit, before it is shown`() = runTest {
        val repository = repository()
        repository.build(NightFixtures.settings)
        val finale = server.chapters[2].itemId
        remote.blocked = listOf(finale)
        val night = (repository.begin(EXPERIENCE, SESSION) as Outcome.Success).value
        assertThat(night.chapters[2].item.id).isNotEqualTo(finale)
        assertThat(night.chapters.map { it.item.id }).doesNotContain(finale)
    }

    @Test
    fun `keeping a theme out applies to the rest of tonight at once`() = runTest {
        val repository = repository()
        repository.build(NightFixtures.settings)
        val theme = NightFixtures.byId.getValue(server.chapters[2].itemId).excludedByBoundaries.first()
        val night = (repository.keepOut(EXPERIENCE, theme, BoundaryLevel.NEVER, fromIndex = 1) as Outcome.Success).value
        assertThat(boundaries.writes.map { it.first to it.second.level }).containsExactly(theme to BoundaryLevel.NEVER)
        for (chapter in night.chapters.drop(1)) {
            if (chapter.index in night.unplayable) continue
            assertThat(chapter.item.excludedByBoundaries).doesNotContain(theme)
        }
    }

    @Test
    fun `a failed boundary write changes nothing`() = runTest {
        val repository = repository()
        repository.build(NightFixtures.settings)
        boundaries.setResult = Outcome.Failure(AppError.Network())
        val theme = NightFixtures.byId.getValue(server.chapters[2].itemId).excludedByBoundaries.first()
        val result = repository.keepOut(EXPERIENCE, theme, BoundaryLevel.NEVER, 1)
        assertThat(result).isInstanceOf(Outcome.Failure::class.java)
        val night = (repository.load(EXPERIENCE) as Outcome.Success).value
        assertThat(night.chapters[2].item.id).isEqualTo(server.chapters[2].itemId)
    }

    @Test
    fun `restores a night after a restart from what the server stored`() = runTest {
        repository().build(NightFixtures.settings)
        remote.askFirst = listOf(server.chapters[1].itemId)
        val restarted = repository()
        val night = (restarted.load(EXPERIENCE) as Outcome.Success).value
        assertThat(night.chapters.map { it.item.id }).containsExactlyElementsIn(serverIds).inOrder()
        assertThat(night.askFirstItems).containsExactly(server.chapters[1].itemId)
        assertThat(night.settings.durationMin).isEqualTo(NightFixtures.settings.durationMin)
    }

    @Test
    fun `catches up on content once when the server knows more, then says so`() = runTest {
        remote.unknownChapter = true
        val result = repository().build(NightFixtures.settings)
        assertThat(content.syncCalls).isEqualTo(1)
        assertThat((result as Outcome.Failure).error)
            .isEqualTo(AppError.Validation(DefaultNightRepository.CONTENT_OUT_OF_DATE))
    }

    @Test
    fun `a reaction is about the item that was actually played`() = runTest {
        val repository = repository()
        repository.build(NightFixtures.settings)
        val replacement = requireNotNull(repository.swap(EXPERIENCE, 1)).chapters[1].item.id
        repository.react(EXPERIENCE, 1, ChapterReaction.LOVED)
        assertThat(remote.feedback).containsExactly(FeedbackPayload(EXPERIENCE, 1, replacement, ChapterReaction.LOVED))
    }

    @Test
    fun `follows the couple's night under way, and knows whose phone plays it`() = runTest {
        val repository = repository()
        remote.couple.value = CoupleNightState(activeSessionId = SESSION, sharedLevel = 3)
        remote.session.value = sessionDoc(host = "partner", consented = setOf("partner"))
        val session = requireNotNull(repository.activeSession.first())
        assertThat(session.isHost).isFalse()
        assertThat(session.consented).isFalse()
        assertThat(session.partnerConsented).isTrue()
        assertThat(repository.sharedLevel.first()?.level).isEqualTo(3)

        // The night ends and the couple's place clears: the phone follows.
        remote.couple.value = CoupleNightState(activeSessionId = null, sharedLevel = 3)
        assertThat(withTimeout(TIMEOUT_MS) { repository.activeSession.first { it == null } }).isNull()
    }

    private fun sessionDoc(host: String, consented: Set<String>) = SessionDoc(
        id = SESSION,
        experienceId = EXPERIENCE,
        hostUid = host,
        state = SessionState.WAITING_FOR_PARTNER,
        pausedFrom = null,
        currentChapter = -1,
        chapterCount = 3,
        consentRequired = true,
        consentedUids = consented,
        endReason = null,
    )

    /** The backend, in memory: records what it was asked, answers from the fixture night. */
    private inner class FakeNightRemote : NightRemoteDataSource {
        val generated = mutableListOf<NightSettings>()
        val submissions = mutableListOf<Triple<String, Int, String?>>()
        val seen = mutableListOf<Int>()
        val presenceSent = mutableListOf<String>()
        val reveal = MutableStateFlow<RevealDoc?>(null)
        val presence = MutableStateFlow<Map<String, Long>>(emptyMap())

        val advances = mutableListOf<Triple<String, Int, String?>>()
        val feedback = mutableListOf<FeedbackPayload>()
        var blocked: List<String> = emptyList()
        val surprises = mutableListOf<Boolean>()
        val remixes = mutableListOf<List<String>>()
        var remixResult: Outcome<Unit>? = null
        val planned = mutableListOf<NightSettings>()
        val opened = mutableListOf<String>()
        val plans = MutableStateFlow<List<PlanDoc>>(emptyList())
        var askFirst: List<String> = emptyList()
        var unknownChapter = false
        val couple = MutableStateFlow(CoupleNightState(activeSessionId = null, sharedLevel = null))
        val session = MutableStateFlow<SessionDoc?>(null)
        private var current = -1

        private val chapters get() = server.chapters.map {
            val id = if (unknownChapter && it.index == 1) "not_on_this_phone" else it.itemId
            ChapterPayload(it.index, it.kind.name, id, it.intensity)
        }

        override suspend fun generate(settings: NightSettings): Outcome<ServerNightPayload> {
            generated += settings
            return Outcome.Success(
                ServerNightPayload(
                    experienceId = EXPERIENCE,
                    chapters = chapters,
                    alternates = server.alternates,
                    effectiveIntensity = server.effectiveIntensity,
                    seed = NightFixtures.SEED,
                    engineVersion = 1,
                    askFirstItems = listOf(server.chapters[2].itemId),
                    focusIncluded = false,
                ),
            )
        }

        override suspend fun surprise(mystery: Boolean, mode: Mode): Outcome<ServerNightPayload> {
            surprises += mystery
            return Outcome.Success(
                ServerNightPayload(
                    experienceId = EXPERIENCE,
                    chapters = chapters,
                    alternates = server.alternates,
                    effectiveIntensity = server.effectiveIntensity,
                    seed = NightFixtures.SEED,
                    engineVersion = 1,
                    askFirstItems = emptyList(),
                    focusIncluded = false,
                    // The server chose the shape, and says what it chose (§14.2).
                    settings = NightSettingsPayload(
                        mode = "TOGETHER",
                        durationMin = 60,
                        moods = listOf("mysterious"),
                        intensity = server.effectiveIntensity,
                        leadership = "RANDOM",
                        novelty = "SURPRISE_ME",
                    ),
                    mystery = mystery,
                ),
            )
        }

        override suspend fun remixNight(
            experienceId: String,
            chapters: List<Pair<String, String>>,
        ): Outcome<Unit> {
            remixes += chapters.map { it.first }
            return remixResult ?: Outcome.Success(Unit)
        }

        override suspend fun planNight(settings: NightSettings): Outcome<PlanPayload> {
            planned += settings
            return Outcome.Success(PlanPayload(experienceId = PLANNED, chapterCount = server.chapters.size))
        }

        override suspend fun openPlan(experienceId: String): Outcome<Unit> {
            opened += experienceId
            return Outcome.Success(Unit)
        }

        override fun observePlans(coupleId: String): Flow<List<PlanDoc>> = plans

        override suspend fun createSession(experienceId: String, twoPhones: Boolean) =
            Outcome.Success(payload(SessionState.READY))

        override suspend fun markReady(sessionId: String) = Outcome.Success(payload(SessionState.READY))

        override suspend fun advance(
            sessionId: String,
            fromChapter: Int,
            playedItemId: String?,
        ): Outcome<AdvancePayload> {
            advances += Triple(sessionId, fromChapter, playedItemId)
            current = fromChapter + 1
            return Outcome.Success(AdvancePayload(payload(SessionState.CHAPTER_ACTIVE), blocked))
        }

        override suspend fun endSession(sessionId: String, action: SessionAction) =
            Outcome.Success(payload(SessionState.PAUSED))

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

        override fun observeCouple(coupleId: String): Flow<CoupleNightState> = couple

        override fun observeSession(coupleId: String, sessionId: String): Flow<SessionDoc?> = session.map { it }

        override suspend fun experience(coupleId: String, experienceId: String): Outcome<ExperienceDoc> =
            Outcome.Success(
                ExperienceDoc(
                    id = EXPERIENCE,
                    mode = NightFixtures.settings.mode.name,
                    durationMin = NightFixtures.settings.durationMin,
                    moods = emptyList(),
                    intensity = NightFixtures.settings.intensity.level,
                    effectiveIntensity = server.effectiveIntensity,
                    leadership = NightFixtures.settings.leadership.name,
                    novelty = NightFixtures.settings.novelty.name,
                    focus = null,
                    focusIncluded = false,
                    chapters = chapters,
                    alternates = server.alternates,
                    status = "IN_PROGRESS",
                    playedThrough = 0,
                    sessionId = SESSION,
                    seed = NightFixtures.SEED,
                ),
            )

        override suspend fun askFirstItems(coupleId: String, experienceId: String, uid: String) =
            Outcome.Success(askFirst)

        override suspend fun sendFeedback(coupleId: String, uid: String, feedback: FeedbackPayload): Outcome<Unit> {
            this.feedback += feedback
            return Outcome.Success(Unit)
        }

        private fun payload(state: SessionState) = SessionPayload(SESSION, state, current, server.chapters.size, false)
    }

    @Test
    fun `a surprise is held like any night, with the shape the server chose`() = runTest {
        val repository = repository()

        val night = (repository.surprise() as Outcome.Success).value

        assertThat(remote.surprises).containsExactly(false)
        assertThat(night.chapters.map { it.item.id }).isEqualTo(serverIds)
        // What the phone was never asked: it takes it from the server, so a remix still works.
        assertThat(night.settings.durationMin).isEqualTo(60)
        assertThat(night.settings.novelty.name).isEqualTo("SURPRISE_ME")
        assertThat(night.mystery).isFalse()
    }

    @Test
    fun `a mystery night is asked for, and held, as one`() = runTest {
        val repository = repository()
        val night = (repository.surprise(mystery = true) as Outcome.Success).value
        assertThat(remote.surprises).containsExactly(true)
        assertThat(night.mystery).isTrue()
        // Its shape is hidden on screen, not missing: the phone still holds every chapter.
        assertThat(night.chapters).hasSize(serverIds.size)
    }

    @Test
    fun `make it more rebuilds the night from what the server released, and only from that`() = runTest {
        val repository = repository()
        val built = (repository.build(NightSettings()) as Outcome.Success).value

        val remixed = repository.makeIt(built.experienceId, RemixModifier.MORE_INTENSE)

        assertThat(remixed).isNotNull()
        val released = (server.chapters.map { it.itemId } + server.alternates).toSet()
        assertThat(released).containsAtLeastElementsIn(remixed!!.chapters.map { it.item.id })
        // The couple's record is brought into line with it: a session counts its chapters (§12).
        assertThat(remote.remixes.single()).isEqualTo(remixed.chapters.map { it.item.id })
        // A night this phone does not hold cannot be remixed at all.
        assertThat(repository.makeIt("another_night", RemixModifier.SHORTER)).isNull()
    }

    @Test
    fun `a remix the server will not take is not played either`() = runTest {
        val repository = repository()
        val built = (repository.build(NightSettings()) as Outcome.Success).value
        remote.remixResult = Outcome.Failure(AppError.Network())

        assertThat(repository.makeIt(built.experienceId, RemixModifier.MORE_INTENSE)).isNull()

        // The night this phone plays is the one the server still has.
        val held = (repository.load(built.experienceId) as Outcome.Success).value
        assertThat(held.chapters.map { it.item.id }).isEqualTo(built.chapters.map { it.item.id })
    }

    @Test
    fun `a night planned for the partner comes back as a shape, and never as a night to play`() = runTest {
        val repository = repository()

        val chapters = (repository.planForPartner(NightSettings()) as Outcome.Success).value

        assertThat(chapters).isEqualTo(server.chapters.size)
        assertThat(remote.planned).hasSize(1)
        // Nothing is held here: a planned night is the partner's, and this phone cannot play it.
        assertThat(repository.makeIt(PLANNED, RemixModifier.SHORTER)).isNull()
    }

    @Test
    fun `plans say whose they are, and opening one tells the server`() = runTest {
        val repository = repository()
        remote.plans.value = listOf(
            PlanDoc(experienceId = "mine", plannedBy = UID, plannedFor = "partner", chapterCount = 4, opened = false),
            PlanDoc(experienceId = "theirs", plannedBy = "partner", plannedFor = UID, chapterCount = 6, opened = true),
        )

        val plans = repository.plans.first()

        assertThat(plans.map { it.mine }).containsExactly(true, false).inOrder()
        assertThat(plans.map { it.chapterCount }).containsExactly(4, 6).inOrder()
        assertThat(plans.last().opened).isTrue()

        repository.openPlan("theirs")
        assertThat(remote.opened).containsExactly("theirs")
    }

    private companion object {
        const val UID = "uid_host"
        const val COUPLE = "couple1"
        const val EXPERIENCE = "night1"
        const val PLANNED = "planned_night"
        const val SESSION = "session1"
        const val NOW = 1_790_000_000_000L
        const val TIMEOUT_MS = 5_000L
    }
}
