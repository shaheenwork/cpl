package com.shnapps.couple.core.data.content

import com.shnapps.couple.core.model.ChapterKind
import com.shnapps.couple.core.model.ContentItem
import com.shnapps.couple.core.model.ContentPack
import com.shnapps.couple.core.model.ContentStatus
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.InteractionType
import com.shnapps.couple.core.model.MediaKind
import com.shnapps.couple.core.model.Mode
import com.shnapps.couple.core.model.Mood
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Reads content bundles (BUILD_PROMPT.md §9.2) and the items stored from them.
 *
 * Tolerant in the same way as the taxonomy parser, because bundles are downloaded: one
 * written for a newer app may carry fields, moods or chapter kinds this version does not
 * know. Unknown fields are ignored and unknown list entries dropped. An item this version
 * cannot play safely — unknown interaction type, intensity, status or required media, no
 * mode or chapter it can use, or missing its boundary exclusions — is dropped whole, never
 * guessed at. The shipped bundle is held to the strict standard instead: ContentBundleFileTest
 * fails the build on anything this parser would quietly skip.
 *
 * A bundle in a format this version does not understand is refused outright.
 */
object ContentBundleParser {

    const val SUPPORTED_FORMAT = 1

    private val json = Json { ignoreUnknownKeys = true }

    /** @throws UnsupportedBundleException for another format, SerializationException if malformed. */
    fun parse(text: String): ParsedBundle {
        val bundle = json.decodeFromString<BundleDto>(text)
        if (bundle.format != SUPPORTED_FORMAT) throw UnsupportedBundleException(bundle.format)
        return ParsedBundle(
            format = bundle.format,
            contentVersion = bundle.contentVersion,
            taxonomyVersion = bundle.taxonomyVersion,
            taxonomyJson = bundle.taxonomy.toString(),
            packsJson = json.encodeToString(bundle.packs),
            // Stored whole, unknown fields included: a later app version reads them from the
            // cache without downloading the bundle again.
            items = bundle.items.mapNotNull { raw ->
                val header = decodeOrNull<ItemHeaderDto>(raw) ?: return@mapNotNull null
                RawItem(header.id, header.version, header.pack, header.status, raw.toString())
            },
        )
    }

    /** An item as stored in the cache or carried by a delta; null if this version cannot use it. */
    fun parseItem(text: String): ContentItem? = try {
        json.decodeFromString<ItemDto>(text).toContentItem()
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    fun parsePacks(text: String): List<ContentPack> = json.decodeFromString<List<PackDto>>(text).mapNotNull { pack ->
        val (low, high) = pack.intensity.takeIf { it.size == 2 } ?: return@mapNotNull null
        ContentPack(pack.id, pack.title, pack.description, low..high)
    }

    private inline fun <reified T> decodeOrNull(raw: JsonObject): T? = try {
        json.decodeFromJsonElement<T>(raw)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}

/** A bundle as read, ready to install. */
data class ParsedBundle(
    val format: Int,
    val contentVersion: Int,
    val taxonomyVersion: Int,
    val taxonomyJson: String,
    val packsJson: String,
    val items: List<RawItem>,
)

/** One bundled item: the columns the cache indexes, and the item itself as JSON. */
data class RawItem(
    val id: String,
    val version: Int,
    val pack: String,
    val status: String,
    val json: String,
)

class UnsupportedBundleException(format: Int) : IllegalStateException(
    "Content bundle format $format is not supported (expected ${ContentBundleParser.SUPPORTED_FORMAT})",
)

internal fun ItemDto.toContentItem(): ContentItem? {
    val level = Intensity.entries.firstOrNull { it.level == intensity }
    val type = InteractionType.entries.firstOrNull { it.name == interactionType }
    val lifecycle = ContentStatus.fromId(status)
    val media = requiresMedia?.let { name -> MediaKind.entries.firstOrNull { it.name == name } }
    val knownModes = modes.mapNotNull { name -> Mode.entries.firstOrNull { it.name == name } }.toSet()
    val kinds = chapterKinds.mapNotNull { name -> ChapterKind.entries.firstOrNull { it.name == name } }.toSet()
    val unknownMedia = requiresMedia != null && media == null
    if (level == null || type == null || lifecycle == null) return null
    if (unknownMedia || knownModes.isEmpty() || kinds.isEmpty()) return null
    return ContentItem(
        id = id,
        version = version,
        pack = pack,
        category = category,
        title = title,
        subtitle = subtitle,
        body = body,
        tags = tags.toSet(),
        intensity = level,
        modes = knownModes,
        interactionType = type,
        durationMin = durationMin,
        moods = moods.mapNotNull(Mood::fromId).toSet(),
        requiredMutualPreferences = requiredMutualPreferences.toSet(),
        boostedByPreferences = boostedByPreferences.toSet(),
        excludedByBoundaries = excludedByBoundaries.toSet(),
        chapterKinds = kinds,
        noveltyWeight = noveltyWeight,
        repeatCooldownDays = repeatCooldownDays,
        requiresMedia = media,
        status = lifecycle,
    )
}

// The file format. Internal so the strict file test reads the shipped bundle through the
// same types the app does.

@Serializable
internal data class BundleDto(
    val format: Int,
    val contentVersion: Int,
    val taxonomyVersion: Int,
    val packs: List<PackDto>,
    val taxonomy: JsonObject,
    val items: List<JsonObject>,
)

@Serializable
internal data class PackDto(
    val id: String,
    val title: String,
    val description: String = "",
    val intensity: List<Int>,
)

@Serializable
internal data class ItemHeaderDto(
    val id: String,
    val version: Int,
    val pack: String,
    val status: String,
)

/**
 * The exclusion and requirement lists have no defaults on purpose: an item that arrives
 * without them fails to decode and is dropped, rather than reaching a night with no
 * boundaries attached (fail closed, BUILD_PROMPT.md §3.2).
 */
@Serializable
internal data class ItemDto(
    val id: String,
    val version: Int,
    val pack: String,
    val category: String,
    val title: String,
    val subtitle: String,
    val body: String,
    val tags: List<String> = emptyList(),
    val intensity: Int,
    val modes: List<String>,
    val interactionType: String,
    val durationMin: Int,
    val moods: List<String> = emptyList(),
    val requiredMutualPreferences: List<String>,
    val boostedByPreferences: List<String> = emptyList(),
    val excludedByBoundaries: List<String>,
    val chapterKinds: List<String>,
    val noveltyWeight: Double,
    val repeatCooldownDays: Int,
    val requiresMedia: String? = null,
    val status: String,
)
