package com.shnapps.couple.core.engine

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.shnapps.couple.core.engine.EngineTestData.array
import com.shnapps.couple.core.engine.EngineTestData.int
import com.shnapps.couple.core.engine.EngineTestData.string
import com.shnapps.couple.core.engine.EngineTestData.strings
import com.shnapps.couple.core.engine.EngineTestData.toChapter
import com.shnapps.couple.core.engine.EngineTestData.toItem
import com.shnapps.couple.core.engine.EngineTestData.toPlan
import com.shnapps.couple.core.engine.EngineTestData.toRequest
import com.shnapps.couple.core.model.ChapterKind
import com.shnapps.couple.core.model.Mode
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Test

/**
 * The Phase 9 exit criterion's other half: this implementation computes exactly what the
 * server's TypeScript engine computed for every shared vector (engine/engine-fixtures.json) —
 * the same random numbers, the same plans, the same nights, the same verdicts.
 *
 * The server's suite checks the file is what its engine builds; this suite checks the client
 * agrees, independently. Two implementations of engine/SPEC.md agreeing bit for bit is the
 * proof that a seed means the same night everywhere (BUILD_PROMPT.md §10.6).
 */
class SharedVectorsTest {

    private val fixtures = EngineTestData.fixtures

    @Test
    fun `the engine versions agree`() {
        assertThat(fixtures.int("engineVersion")).isEqualTo(Engine.VERSION)
    }

    @Test
    fun `the random sequence is bit for bit the server's`() {
        for (entry in fixtures.array("rng")) {
            val case = entry.jsonObject
            val seed = case.getValue("seed").jsonPrimitive.long
            val rng = Rng(seed)
            val draws = List(6) { rng.next() }
            val ints = List(6) { rng.nextInt(10) }
            assertWithMessage("draws for seed $seed")
                .that(draws).isEqualTo(case.getValue("draws").jsonArray.map { it.jsonPrimitive.double })
            assertWithMessage("ints for seed $seed")
                .that(ints).isEqualTo(case.getValue("ints").jsonArray.map { it.jsonPrimitive.int })
            assertWithMessage("attempt seeds for $seed")
                .that((0..4).map { Rng.attemptSeed(seed, it) })
                .isEqualTo(case.getValue("attemptSeeds").jsonArray.map { it.jsonPrimitive.long })
        }
    }

    @Test
    fun `every plan matches`() {
        val cases = fixtures.array("plan")
        assertThat(cases).isNotEmpty()
        for (entry in cases) {
            val case = entry.jsonObject
            val request = case.getValue("request").jsonObject.toRequest()
            val available = case.getValue("available").strings().mapTo(HashSet()) { ChapterKind.valueOf(it) }
            val result = Planner.plan(
                durationMin = request.durationMin,
                moods = request.moods,
                leadership = request.leadership,
                effective = case.int("effective"),
                available = available,
                rng = Rng(request.seed),
            )
            val expected = case.getValue("expected")
            if (expected is JsonPrimitive) {
                assertWithMessage(case.string("name"))
                    .that(result).isEqualTo(Planner.Result.Failed(PlanFailure.valueOf(expected.content)))
            } else {
                assertWithMessage(case.string("name"))
                    .that(result).isEqualTo(Planner.Result.Planned(expected.jsonObject.toPlan()))
            }
        }
    }

    @Test
    fun `every assembly matches`() {
        val byId = EngineTestData.fixtureCatalogue.associateBy { it.id }
        val cases = fixtures.array("assemble")
        assertThat(cases).isNotEmpty()
        for (entry in cases) {
            val case = entry.jsonObject
            val scores = case.getValue("scores").jsonObject.mapValues { it.value.jsonPrimitive.double }
            val input = Assembler.Input(
                pool = case.getValue("pool").strings().map { byId.getValue(it) },
                scores = scores,
                plan = case.getValue("plan").jsonObject.toPlan(),
                durationMin = case.int("durationMin"),
                effective = case.int("effective"),
                repetitionWeight = case.getValue("repetitionWeight").jsonPrimitive.double,
            )
            val expected = case.getValue("expected")
            val actual = Assembler.assemble(input)
            if (expected is JsonNull) {
                assertWithMessage(case.string("name")).that(actual).isNull()
            } else {
                assertWithMessage(case.string("name"))
                    .that(actual).isEqualTo(expected.jsonArray.map { it.jsonObject.toChapter() })
            }
        }
    }

    @Test
    fun `every verdict matches`() {
        val validate = fixtures.getValue("validate").jsonObject
        val hand = validate.array("items").map { it.jsonObject.toItem() }.associateBy { it.id }
        val catalogue = EngineTestData.fixtureCatalogue.associateBy { it.id }
        fun check(case: JsonObject, items: Map<String, com.shnapps.couple.core.model.ContentItem>) {
            val verdict = Validator.validate(
                chapters = case.array("chapters").map { it.jsonObject.toChapter() },
                durationMin = case.int("durationMin"),
                effective = case.int("effective"),
                mode = Mode.valueOf(case.string("mode")),
                items = items,
            )
            assertWithMessage(case.string("name"))
                .that(verdict.map { it.name }).isEqualTo(case.getValue("expected").strings())
        }
        validate.array("cases").forEach { check(it.jsonObject, hand) }
        validate.array("assembled").forEach { check(it.jsonObject, catalogue) }
    }
}
