package com.shnapps.couple.core.engine

import com.shnapps.couple.core.model.ChapterKind
import com.shnapps.couple.core.model.ContentItem
import com.shnapps.couple.core.model.ContentStatus
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.InteractionType
import com.shnapps.couple.core.model.Leadership
import com.shnapps.couple.core.model.MediaKind
import com.shnapps.couple.core.model.Mode
import com.shnapps.couple.core.model.Mood
import com.shnapps.couple.core.model.Novelty
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.File

/**
 * The files the engine is tested against: the vectors shared with the server's TypeScript
 * engine, and the real catalogue the app ships. Test tasks run in core/engine, so both are
 * two levels up; both are declared inputs of the test task.
 */
internal object EngineTestData {

    val fixtures: JsonObject by lazy {
        Json.parseToJsonElement(File("../../engine/engine-fixtures.json").readText()).jsonObject
    }

    /** The fixtures' synthetic catalogue. */
    val fixtureCatalogue: List<ContentItem> by lazy { fixtures.array("catalogue").map { it.jsonObject.toItem() } }

    /** Every item the app ships (content/dist/bundle.json). */
    val realCatalogue: List<ContentItem> by lazy {
        Json.parseToJsonElement(File("../../content/dist/bundle.json").readText()).jsonObject
            .array("items").map { it.jsonObject.toItem() }
    }

    fun JsonObject.array(key: String): JsonArray = getValue(key).jsonArray

    fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

    fun JsonObject.int(key: String): Int = getValue(key).jsonPrimitive.int

    fun JsonElement.strings(): List<String> = jsonArray.map { it.jsonPrimitive.content }

    /** A bundle or fixture item as the domain type; fixture items carry no copy. */
    fun JsonObject.toItem(): ContentItem = ContentItem(
        id = string("id"),
        version = int("version"),
        pack = string("pack"),
        category = string("category"),
        title = this["title"]?.jsonPrimitive?.content ?: string("id"),
        subtitle = this["subtitle"]?.jsonPrimitive?.content.orEmpty(),
        body = this["body"]?.jsonPrimitive?.content.orEmpty(),
        tags = getValue("tags").strings().toSet(),
        intensity = Intensity.ofLevel(int("intensity")),
        modes = getValue("modes").strings().mapTo(LinkedHashSet()) { Mode.valueOf(it) },
        interactionType = InteractionType.valueOf(string("interactionType")),
        durationMin = int("durationMin"),
        moods = getValue("moods").strings().mapNotNullTo(LinkedHashSet()) { Mood.fromId(it) },
        requiredMutualPreferences = getValue("requiredMutualPreferences").strings().toSet(),
        boostedByPreferences = getValue("boostedByPreferences").strings().toSet(),
        excludedByBoundaries = getValue("excludedByBoundaries").strings().toSet(),
        chapterKinds = getValue("chapterKinds").strings().mapTo(LinkedHashSet()) { ChapterKind.valueOf(it) },
        noveltyWeight = getValue("noveltyWeight").jsonPrimitive.double,
        repeatCooldownDays = int("repeatCooldownDays"),
        requiresMedia = this["requiresMedia"]
            ?.takeIf { it !is JsonNull }
            ?.jsonPrimitive
            ?.content
            ?.let { MediaKind.valueOf(it) },
        status = ContentStatus.fromId(string("status")) ?: error("unknown status"),
    )

    fun JsonObject.toRequest(): NightRequest = NightRequest(
        mode = Mode.valueOf(string("mode")),
        durationMin = int("durationMin"),
        moods = getValue("moods").strings().mapNotNullTo(LinkedHashSet()) { Mood.fromId(it) },
        intensity = int("intensity"),
        leadership = Leadership.valueOf(string("leadership")),
        novelty = Novelty.valueOf(string("novelty")),
        seed = getValue("seed").jsonPrimitive.long,
    )

    fun JsonObject.toChapter(): Chapter = Chapter(
        index = int("index"),
        kind = ChapterKind.valueOf(string("kind")),
        itemId = string("itemId"),
        intensity = int("intensity"),
    )

    fun JsonObject.toPlan(): NightPlan = NightPlan(
        kinds = getValue("kinds").strings().map { ChapterKind.valueOf(it) },
        targets = getValue("targets").jsonArray.map { it.jsonPrimitive.int },
    )
}
