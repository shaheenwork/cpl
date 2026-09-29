package com.shnapps.couple.core.engine

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.shnapps.couple.core.model.ChapterKind
import com.shnapps.couple.core.model.ContentStatus
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.Leadership
import com.shnapps.couple.core.model.Mode
import com.shnapps.couple.core.model.Mood
import com.shnapps.couple.core.model.Novelty
import org.junit.Test

/**
 * Local remixes within the boundaries (BUILD_PROMPT.md §5.5, §14.6, §21.1
 * "remix-within-boundaries"): whatever is asked, a remix uses only what the server released
 * for the night, never goes above the night's ceiling, and always keeps every rule.
 */
class RemixerTest {

    private val catalogue = EngineTestData.realCatalogue
    private val byId = catalogue.associateBy { it.id }
    private val ceiling = 4

    private val request = NightRequest(
        mode = Mode.TOGETHER,
        durationMin = 60,
        moods = setOf(Mood.NAUGHTY),
        intensity = 5,
        leadership = Leadership.ME,
        novelty = Novelty.MIXED,
        seed = 11,
    )

    /**
     * A server's night and pool, stood in for with the client's own assembler over a sample
     * of the catalogue: what matters here is only that the pool is a bounded subset.
     */
    private val serverNight: ServerNight by lazy {
        val permitted = catalogue.filter {
            Mode.TOGETHER in it.modes && it.intensity.level <= ceiling && it.status == ContentStatus.PUBLISHED
        }
        val sample = ChapterKind.entries.flatMap { kind ->
            permitted.filter { kind in it.chapterKinds }
                .sortedBy { it.id }
                .filterIndexed { index, _ -> index % 3 == 0 }
                .take(6)
        }.distinctBy { it.id }
        val scores = sample.associate { it.id to PublicScore.score(it, request, ceiling, ClientContext()) }
        val kinds = sample.flatMapTo(HashSet()) { it.chapterKinds }
        // Like the pipeline: the first seed whose night passes every rule.
        val chapters = (1L..50L).firstNotNullOf { seed ->
            val planned = Planner.plan(60, request.moods, request.leadership, ceiling, kinds, Rng(seed))
            val plan = (planned as? Planner.Result.Planned)?.plan
            plan?.let { Assembler.assemble(Assembler.Input(sample, scores, it, 60, ceiling, 3.0)) }
                ?.takeIf { Validator.validate(it, 60, ceiling, Mode.TOGETHER, byId).isEmpty() }
        }
        val chosen = chapters.map { it.itemId }.toSet()
        ServerNight(request, chapters, sample.map { it.id }.filter { it !in chosen }, ceiling, Engine.VERSION)
    }

    private val pool: PermittedPool by lazy { PermittedPool.fromServer(serverNight) { byId[it] } }

    private val night: Night by lazy { Night(request, serverNight.chapters, ceiling) }

    private fun assertInsideTheNight(result: Night, label: String) {
        val items = result.chapters.map { byId.getValue(it.itemId) }
        assertWithMessage("$label: only released items").that(result.chapters.map { it.itemId }).containsNoneIn(
            catalogue.map { it.id }.filterNot { it in pool.ids },
        )
        assertWithMessage("$label: never above the ceiling").that(items.maxOf { it.intensity.level }).isAtMost(ceiling)
        assertWithMessage("$label: keeps every rule")
            .that(Validator.validate(result.chapters, result.request.durationMin, ceiling, Mode.TOGETHER, byId))
            .isEmpty()
    }

    @Test
    fun `the night the pool came from is itself valid`() {
        assertThat(Validator.validate(night.chapters, 60, ceiling, Mode.TOGETHER, byId)).isEmpty()
        assertThat(pool.items.size).isLessThan(catalogue.size / 4)
    }

    @Test
    fun `every remix stays within the pool, the ceiling and the rules`() {
        val remixer = Remixer()
        var made = 0
        for (modifier in RemixModifier.entries) {
            for (seed in 1L..20L) {
                val result = remixer.remix(night, modifier, pool, seed) ?: continue
                made++
                assertInsideTheNight(result, "$modifier seed $seed")
                assertThat(result.chapters.map { it.itemId }).isNotEqualTo(night.chapters.map { it.itemId })
            }
        }
        assertThat(made).isAtLeast(RemixModifier.entries.size * 10)
    }

    @Test
    fun `more intense climbs sooner, never higher`() {
        val remixer = Remixer()
        fun meanIntensity(modifier: RemixModifier) = (1L..30L)
            .mapNotNull { remixer.remix(night, modifier, pool, it) }
            .flatMap { result -> result.chapters.map { it.intensity } }
            .average()
        assertThat(meanIntensity(RemixModifier.MORE_INTENSE)).isGreaterThan(meanIntensity(RemixModifier.SLOWER))
    }

    @Test
    fun `shorter steps down one length, and the shortest has nowhere to go`() {
        val shorter = Remixer().remix(night, RemixModifier.SHORTER, pool, 5)
        checkNotNull(shorter)
        assertThat(shorter.request.durationMin).isEqualTo(45)
        assertThat(shorter.chapters.size).isIn(chapterRange(45))

        val quarterHour = night.copy(request = request.copy(durationMin = 15))
        assertThat(Remixer().remix(quarterHour, RemixModifier.SHORTER, pool, 5)).isNull()
    }

    @Test
    fun `remixing is deterministic`() {
        val remixer = Remixer()
        for (modifier in RemixModifier.entries) {
            assertThat(remixer.remix(night, modifier, pool, 99)).isEqualTo(remixer.remix(night, modifier, pool, 99))
        }
    }

    @Test
    fun `an item the server did not release is never used, however well it would fit`() {
        // The best roleplay item the whole catalogue has for this night — outside the pool.
        val tempting = catalogue
            .filter { it.id !in pool.ids && Mode.TOGETHER in it.modes }
            .filter { ChapterKind.ROLEPLAY in it.chapterKinds && it.intensity.level <= ceiling }
            .maxBy { PublicScore.score(it, request.copy(moods = setOf(Mood.ROLEPLAY)), ceiling, ClientContext()) }
        val remixer = Remixer()
        for (seed in 1L..40L) {
            val result = remixer.remix(night, RemixModifier.MORE_ROLEPLAY, pool, seed) ?: continue
            assertThat(result.chapters.map { it.itemId }).doesNotContain(tempting.id)
        }
    }

    @Test
    fun `a pool drops anything that could not have been permitted`() {
        val released = serverNight.alternates.take(3)
        val strays = listOf(
            byId.getValue(released[0]).copy(status = ContentStatus.DISABLED),
            byId.getValue(released[1]).copy(modes = setOf(Mode.APART)),
            byId.getValue(released[2]).copy(intensity = Intensity.EXTREME),
        ).associateBy { it.id }
        val withStray = serverNight.copy(alternates = serverNight.alternates + "no_such_item")
        val tampered = PermittedPool.fromServer(withStray) { strays[it] ?: byId[it] }
        assertThat(tampered.ids).containsNoneIn(released + "no_such_item")
        assertThat(tampered.ids).containsAtLeastElementsIn(serverNight.chapters.map { it.itemId })
    }

    @Test
    fun `not this one swaps exactly one chapter, for something released and not refused`() {
        val remixer = Remixer()
        for (index in night.chapters.indices) {
            val refused = setOf(night.chapters[index].itemId)
            val result = remixer.swap(night, index, pool, refused) ?: continue
            result.chapters.forEachIndexed { at, chapter ->
                if (at == index) {
                    assertThat(chapter.itemId).isNotIn(night.chapters.map { it.itemId })
                    assertThat(chapter.itemId).isIn(pool.ids)
                    val limit = if (chapter.kind == ChapterKind.FINALE) ceiling else night.chapters[at].intensity
                    assertThat(chapter.intensity).isAtMost(limit)
                } else {
                    assertThat(chapter).isEqualTo(night.chapters[at])
                }
            }
            assertInsideTheNight(result, "swap $index")
        }
    }

    @Test
    fun `a swap with nothing left to offer says so`() {
        val everything = pool.ids
        assertThat(Remixer().swap(night, 1, pool, everything)).isNull()
    }

    @Test
    fun `a night the pool cannot resolve is not swapped`() {
        val foreign = night.copy(chapters = night.chapters.map { it.copy(itemId = it.itemId + "_elsewhere") })
        assertThat(Remixer().swap(foreign, 0, pool)).isNull()
    }

    @Test
    fun `a pool only ever shrinks`() {
        val removed = pool.ids.take(3).toSet()
        val smaller = pool.without(removed + "never_released")
        assertThat(smaller.ids).isEqualTo(pool.ids - removed)
        assertThat(smaller.effectiveIntensity).isEqualTo(pool.effectiveIntensity)
        assertThat(pool.without(setOf("never_released"))).isSameInstanceAs(pool)
    }

    @Test
    fun `a swap never brings back what the pool lost`() {
        val index = 1
        val lost = pool.ids - night.chapters.map { it.itemId }.toSet()
        val smaller = pool.without(lost)
        // Only the night's own items are left, so nothing unused can stand in.
        assertThat(Remixer().swap(night, index, smaller, setOf(night.chapters[index].itemId))).isNull()
    }

    @Test
    fun `a night's focus lifts exactly the items about it, by W_FOCUS`() {
        val focus = catalogue.flatMap { it.boostedByPreferences }.groupingBy { it }.eachCount().maxBy { it.value }.key
        val focused = request.copy(focus = focus)
        for (item in catalogue.take(120)) {
            val lift = PublicScore.score(item, focused, ceiling, ClientContext()) -
                PublicScore.score(item, request, ceiling, ClientContext())
            val expected = if (PublicScore.isFocusItem(item, focus)) PublicScore.W_FOCUS else 0.0
            assertWithMessage(item.id).that(lift).isWithin(1e-9).of(expected)
        }
    }
}
