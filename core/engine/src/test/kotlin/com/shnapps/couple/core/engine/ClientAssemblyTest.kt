package com.shnapps.couple.core.engine

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.shnapps.couple.core.model.ContentStatus
import com.shnapps.couple.core.model.Leadership
import com.shnapps.couple.core.model.Mode
import com.shnapps.couple.core.model.Mood
import com.shnapps.couple.core.model.Novelty
import org.junit.Test

/**
 * The client's planner and assembler over the real catalogue (BUILD_PROMPT.md §10.4–10.6,
 * §21.1: chapter assembly, variety enforcement, duration fitting, determinism).
 *
 * Here the pool is everything a mode and ceiling allow — far more generous than a server's
 * pool — so a night that fails a rule would be the assembler's fault, not the pool's.
 */
class ClientAssemblyTest {

    private val catalogue = EngineTestData.realCatalogue.filter { it.status == ContentStatus.PUBLISHED }
    private val byId = catalogue.associateBy { it.id }
    private val moodSets = listOf(
        emptySet(),
        setOf(Mood.ROMANTIC),
        setOf(Mood.NAUGHTY, Mood.BOLD),
        setOf(Mood.ROLEPLAY),
        Mood.entries.toSet(),
    )

    private data class Built(val request: NightRequest, val effective: Int, val chapters: List<Chapter>?)

    private fun build(mode: Mode, durationMin: Int, effective: Int, seed: Long, moods: Set<Mood>): Built {
        val request = NightRequest(mode, durationMin, moods, effective, Leadership.SWITCH, Novelty.MIXED, seed)
        val pool = catalogue.filter { mode in it.modes && it.intensity.level <= effective }
        val scores = pool.associate { it.id to PublicScore.score(it, request, effective, ClientContext()) }
        val kinds = pool.flatMapTo(HashSet()) { it.chapterKinds }
        val chapters = (0 until 5).firstNotNullOfOrNull { attempt ->
            val rng = Rng(Rng.attemptSeed(seed, attempt))
            val planned = Planner.plan(durationMin, moods, request.leadership, effective, kinds, rng)
            (planned as? Planner.Result.Planned)?.plan
                ?.let { Assembler.assemble(Assembler.Input(pool, scores, it, durationMin, effective, 3.0)) }
                ?.takeIf { Validator.validate(it, durationMin, effective, mode, byId).isEmpty() }
        }
        return Built(request, effective, chapters)
    }

    private fun sweep(): List<Built> = buildList {
        var n = 0L
        for (mode in Mode.entries) {
            for (durationMin in listOf(15, 30, 45, 60, 90)) {
                for (effective in 1..5) {
                    for (moods in moodSets) {
                        n++
                        add(build(mode, durationMin, effective, n * 7919, moods))
                    }
                }
            }
        }
    }

    @Test
    fun `almost every request makes a night, and every night keeps every rule`() {
        val built = sweep()
        val nights = built.mapNotNull { it.chapters?.let { chapters -> it to chapters } }
        assertWithMessage("${built.size - nights.size} of ${built.size} found no night")
            .that(nights.size.toDouble() / built.size).isAtLeast(0.99)
        for ((request, chapters) in nights.map { it.first.request to it.second }) {
            val items = chapters.map { byId.getValue(it.itemId) }
            assertThat(items.map { it.id }).containsNoDuplicates()
            assertThat(chapters.last().kind.name).isEqualTo("FINALE")
            assertThat(items.zipWithNext().all { (a, b) -> b.intensity.level <= a.intensity.level + 1 }).isTrue()
            assertThat(chapters.size).isIn(chapterRange(request.durationMin))
        }
    }

    @Test
    fun `the same request and seed give the same night`() {
        for (seed in 1L..40L) {
            val first = build(Mode.APART, 45, 3, seed, setOf(Mood.TALK))
            val second = build(Mode.APART, 45, 3, seed, setOf(Mood.TALK))
            assertThat(second.chapters).isEqualTo(first.chapters)
        }
    }

    @Test
    fun `different seeds give different nights`() {
        val nights = (1L..30L).mapNotNull { seed ->
            build(Mode.TOGETHER, 60, 4, seed, emptySet()).chapters?.map { it.itemId }
        }
        assertThat(nights.toSet().size).isAtLeast(10)
    }

    @Test
    fun `targets rise to the ceiling one level at a time`() {
        for (effective in 1..5) {
            for (count in 3..10) {
                val targets = Planner.targets(count, effective)
                assertThat(targets.last()).isEqualTo(effective)
                assertThat(targets.zipWithNext().all { (a, b) -> b == a || b == a + 1 }).isTrue()
                assertThat(targets.first()).isEqualTo(maxOf(1, effective - 2))
            }
        }
    }
}
