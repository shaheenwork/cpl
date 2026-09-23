package com.shnapps.couple.core.testing

import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.night.NightRepository
import com.shnapps.couple.core.engine.RemixModifier
import com.shnapps.couple.core.model.BoundaryLevel
import com.shnapps.couple.core.model.ChapterKind
import com.shnapps.couple.core.model.ChapterReaction
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.Mode
import com.shnapps.couple.core.model.NightChapter
import com.shnapps.couple.core.model.NightPlan
import com.shnapps.couple.core.model.NightSession
import com.shnapps.couple.core.model.NightSettings
import com.shnapps.couple.core.model.PlannedNight
import com.shnapps.couple.core.model.SessionEndReason
import com.shnapps.couple.core.model.SessionState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [NightRepository] that plays the server's part: sessions move through the same
 * states the callables would move them through, so a ViewModel test can drive a whole night —
 * consent, chapters, pause, stop — and assert on what the phone asked for.
 */
// One fake for one interface: it has as many functions as NightRepository has.
@Suppress("TooManyFunctions")
class FakeNightRepository(
    var night: PlannedNight = TestNights.gentle,
) : NightRepository {

    val session = MutableStateFlow<NightSession?>(null)
    override val activeSession: Flow<NightSession?> = session
    val level = MutableStateFlow<Intensity?>(Intensity.WILD)
    override val sharedLevel: Flow<Intensity?> = level

    /** Every call, in order: "build", "start", "consent", "begin", "finish:0", "stop"… */
    val calls = mutableListOf<String>()
    val built = mutableListOf<NightSettings>()
    val reactions = mutableListOf<Pair<Int, ChapterReaction>>()

    var buildResults: MutableList<Outcome<PlannedNight>> = mutableListOf()
    var loadResult: Outcome<PlannedNight>? = null
    var failNext: AppError? = null

    /** What "Not this one" swaps chapter [index] for, or null when nothing fits. */
    var swapWith: (Int) -> PlannedNight? = { null }

    /** What "Make it more…" gives back, or null when the pool cannot make it (§14.6). */
    var remixWith: (RemixModifier) -> PlannedNight? = { night }

    /** What the server chooses when nobody configures anything (§14.2). */
    var surpriseResult: Outcome<PlannedNight>? = null

    /** What either partner has planned for the other (§2.5 #2). */
    val plannedNights = MutableStateFlow<List<NightPlan>>(emptyList())

    /** When set, finishing a chapter waits for it: a request still on its way. */
    var holdFinish: CompletableDeferred<Unit>? = null

    /** The partner, on their own phone: says "I'm in". */
    fun partnerConsents() = session.update { current ->
        current?.let {
            val ready = it.consented
            it.copy(partnerConsented = true, state = if (ready) SessionState.READY else it.state)
        }
    }

    /** The partner, on their own phone: stops the night. */
    fun partnerStops() = session.update {
        it?.copy(state = SessionState.ABANDONED, endReason = SessionEndReason.STOPPED)
    }

    fun partnerPauses() = session.update { it?.copy(state = SessionState.PAUSED, pausedFrom = it.state) }

    override suspend fun build(settings: NightSettings): Outcome<PlannedNight> {
        calls += "build"
        built += settings
        return if (buildResults.isNotEmpty()) buildResults.removeAt(0) else Outcome.Success(night)
    }

    override suspend fun surprise(mystery: Boolean): Outcome<PlannedNight> {
        calls += if (mystery) "surprise:mystery" else "surprise"
        return surpriseResult ?: Outcome.Success(night.copy(mystery = mystery))
    }

    override suspend fun planForPartner(settings: NightSettings): Outcome<Int> {
        calls += "plan"
        built += settings
        return Outcome.Success(night.chapters.size)
    }

    override val plans: Flow<List<NightPlan>> = plannedNights

    override suspend fun openPlan(experienceId: String): Outcome<Unit> = call("openPlan:$experienceId") { }

    override suspend fun load(experienceId: String): Outcome<PlannedNight> {
        calls += "load"
        return loadResult ?: Outcome.Success(night)
    }

    override fun observeSession(sessionId: String): Flow<NightSession?> =
        session.map { current -> current?.takeIf { it.id == sessionId } }

    override suspend fun start(experienceId: String): Outcome<String> = call("start") {
        session.value = NightSession(
            id = SESSION_ID,
            experienceId = experienceId,
            state = if (night.needsBothConsents) SessionState.WAITING_FOR_PARTNER else SessionState.READY,
            pausedFrom = null,
            currentChapter = -1,
            chapterCount = night.chapters.size,
            consentRequired = night.needsBothConsents,
            consented = false,
            partnerConsented = false,
            isHost = true,
            endReason = null,
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

    override suspend fun begin(experienceId: String, sessionId: String): Outcome<PlannedNight> = call("begin") {
        session.update { it?.copy(state = SessionState.CHAPTER_ACTIVE, currentChapter = 0) }
        night
    }

    override suspend fun finishChapter(experienceId: String, sessionId: String, index: Int): Outcome<PlannedNight> {
        holdFinish?.await()
        return call("finish:$index") {
            session.update { current ->
                current?.let {
                    if (index >= it.chapterCount - 1) {
                        it.copy(state = SessionState.COMPLETED, endReason = SessionEndReason.COMPLETED)
                    } else {
                        it.copy(currentChapter = index + 1)
                    }
                }
            }
            night
        }
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

    override fun swap(experienceId: String, index: Int): PlannedNight? {
        calls += "swap:$index"
        return swapWith(index)?.also { night = it }
    }

    override suspend fun makeIt(experienceId: String, modifier: RemixModifier): PlannedNight? {
        calls += "makeIt:${modifier.name}"
        return remixWith(modifier)?.also { night = it }
    }

    override suspend fun keepOut(
        experienceId: String,
        themeId: String,
        level: BoundaryLevel,
        fromIndex: Int,
    ): Outcome<PlannedNight> = call("keepOut:$themeId:$level") { night }

    override suspend fun react(experienceId: String, chapterIndex: Int, reaction: ChapterReaction): Outcome<Unit> =
        call("react:$chapterIndex") { reactions += chapterIndex to reaction }

    private inline fun <T> call(name: String, action: () -> T): Outcome<T> {
        calls += name
        failNext?.let {
            failNext = null
            return Outcome.Failure(it)
        }
        return Outcome.Success(action())
    }

    companion object {
        const val SESSION_ID = "session_test"
    }
}

/** Nights for tests, made from [TestContent]'s kind of item. */
object TestNights {
    private fun chapter(index: Int, kind: ChapterKind, id: String, intensity: Intensity) =
        NightChapter(index, kind, TestContent.item(id, "FLIRT", intensity, setOf(Mode.TOGETHER)))

    /** A three-chapter night at FLIRTY: no consent needed. */
    val gentle = PlannedNight(
        experienceId = "night_gentle",
        settings = NightSettings(durationMin = 15, intensity = Intensity.FLIRTY),
        chapters = listOf(
            chapter(0, ChapterKind.WARM_UP, "warm_glance", Intensity.SOFT),
            chapter(1, ChapterKind.TEASE, "tease_whisper", Intensity.FLIRTY),
            chapter(2, ChapterKind.FINALE, "finale_slow_dance", Intensity.FLIRTY),
        ),
        effectiveIntensity = Intensity.FLIRTY,
        askFirstItems = emptySet(),
        focusIncluded = false,
        seed = 1L,
    )

    /** A night that climbs to BOLD: both partners must say "I'm in" first (§3.1). */
    val bold = PlannedNight(
        experienceId = "night_bold",
        settings = NightSettings(durationMin = 15, intensity = Intensity.BOLD),
        chapters = listOf(
            chapter(0, ChapterKind.WARM_UP, "warm_dare", Intensity.NAUGHTY),
            chapter(1, ChapterKind.CONTROL, "control_rules", Intensity.BOLD),
            chapter(2, ChapterKind.FINALE, "finale_bold", Intensity.BOLD),
        ),
        effectiveIntensity = Intensity.BOLD,
        askFirstItems = emptySet(),
        focusIncluded = false,
        seed = 2L,
    )
}
