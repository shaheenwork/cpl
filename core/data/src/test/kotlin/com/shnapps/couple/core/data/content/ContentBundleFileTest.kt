package com.shnapps.couple.core.data.content

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.shnapps.couple.core.data.taxonomy.TaxonomyParser
import com.shnapps.couple.core.model.ChapterKind
import com.shnapps.couple.core.model.ContentStatus
import com.shnapps.couple.core.model.InteractionType
import com.shnapps.couple.core.model.MediaKind
import com.shnapps.couple.core.model.Mood
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.io.File

/**
 * The shipped bundle, `content/dist/bundle.json`, read the way the app reads it — and held
 * to the strict standard (BUILD_PROMPT.md §9.4).
 *
 * The app's parser is tolerant so that a newer downloaded bundle never breaks an older app.
 * That tolerance must never hide a mistake in the bundle we ship: here nothing may be
 * dropped, no list entry may go unrecognised, and the content vocabulary must match the
 * app's enums exactly. tools/validate-content checks the same files from the authoring
 * side; this is the app's half of that contract.
 *
 * The files are declared inputs of the test task, so editing them re-runs these tests.
 */
class ContentBundleFileTest {

    private val text = File(BUNDLE_PATH)
        .also { check(it.isFile) { "Expected the bundle at ${it.absolutePath}" } }
        .readText()
    private val strict = Json

    // Items carry authoring fields the app has no use for (locale, timestamps).
    private val lenient = Json { ignoreUnknownKeys = true }
    private val raw: BundleDto = strict.decodeFromString(text)
    private val parsed = ContentBundleParser.parse(text)

    @Test
    fun `is in the format this app reads, at the version content json declares`() {
        assertThat(raw.format).isEqualTo(ContentBundleParser.SUPPORTED_FORMAT)
        val declared = strict.decodeFromString<ContentVersionDto>(File(CONTENT_JSON_PATH).readText())
        assertThat(parsed.contentVersion).isEqualTo(declared.contentVersion)
    }

    @Test
    fun `every item is read in full - nothing dropped, nothing unrecognised`() {
        val rawIds = raw.items.map { it.getValue("id").jsonPrimitive.content }
        assertThat(parsed.items.map { it.id }).containsExactlyElementsIn(rawIds)

        raw.items.forEach { element ->
            val dto = lenient.decodeFromJsonElement<ItemDto>(element)
            val item = dto.toContentItem()
            assertWithMessage("${dto.id} is dropped by the app").that(item).isNotNull()
            checkNotNull(item)
            assertWithMessage("${dto.id} modes").that(item.modes.map { it.name }).containsExactlyElementsIn(dto.modes)
            assertWithMessage("${dto.id} moods").that(item.moods.map { it.id }).containsExactlyElementsIn(dto.moods)
            assertWithMessage("${dto.id} chapterKinds").that(item.chapterKinds.map { it.name })
                .containsExactlyElementsIn(dto.chapterKinds)
            assertWithMessage("${dto.id} status").that(item.status).isEqualTo(ContentStatus.PUBLISHED)
            assertWithMessage("${dto.id} exclusions").that(item.excludedByBoundaries).isNotEmpty()
        }
    }

    @Test
    fun `ids are unique`() {
        assertThat(parsed.items.map { it.id }).containsNoDuplicates()
    }

    @Test
    fun `carries the taxonomy the app ships`() {
        val fromBundle = TaxonomyParser.parse(parsed.taxonomyJson)
        val fromSource = TaxonomyParser.parse(File(TAXONOMY_PATH).readText())
        assertThat(fromBundle).isEqualTo(fromSource)
        assertThat(parsed.taxonomyVersion).isEqualTo(fromSource.version)
    }

    @Test
    fun `every pack is readable and every item belongs to one`() {
        val packs = ContentBundleParser.parsePacks(parsed.packsJson)
        assertThat(packs.map { it.id }).containsExactlyElementsIn(raw.packs.map { it.id })
        assertThat(parsed.items.map { it.pack }.toSet()).isEqualTo(packs.map { it.id }.toSet())
        packs.forEach { pack ->
            val levels = parsed.items.filter { it.pack == pack.id }
                .mapNotNull { ContentBundleParser.parseItem(it.json) }
                .map { it.intensity.level }
            assertWithMessage("${pack.id} has items").that(levels).isNotEmpty()
            assertWithMessage("${pack.id} intensities")
                .that(pack.intensityRange.toList())
                .containsAtLeastElementsIn(levels.toSet())
        }
    }

    @Test
    fun `the content vocabulary and the app enums are the same lists`() {
        // content/vocabulary.json is what authors may use; these enums are what the app can
        // play. A value in one and not the other is content the app silently drops, or an
        // app feature no content can ever reach.
        val vocabulary = strict.decodeFromString<VocabularyDto>(File(VOCABULARY_PATH).readText())

        assertThat(vocabulary.moods).containsExactlyElementsIn(Mood.entries.map { it.id }).inOrder()
        assertThat(vocabulary.interactionTypes)
            .containsExactlyElementsIn(InteractionType.entries.map { it.name })
            .inOrder()
        assertThat(vocabulary.chapterKinds).containsExactlyElementsIn(ChapterKind.entries.map { it.name }).inOrder()
        assertThat(vocabulary.media).containsExactlyElementsIn(MediaKind.entries.map { it.name }).inOrder()
        assertThat(vocabulary.statuses).containsExactlyElementsIn(ContentStatus.entries.map { it.id }).inOrder()
    }

    private companion object {
        // Test tasks run with the module directory (core/data) as the working directory.
        const val BUNDLE_PATH = "../../content/dist/bundle.json"
        const val TAXONOMY_PATH = "../../content/taxonomy.json"
        const val VOCABULARY_PATH = "../../content/vocabulary.json"
        const val CONTENT_JSON_PATH = "../../content/content.json"
    }
}

@Serializable
private data class ContentVersionDto(val description: String, val contentVersion: Int)

@Serializable
private data class VocabularyDto(
    val description: String,
    val moods: List<String>,
    val interactionTypes: List<String>,
    val chapterKinds: List<String>,
    val media: List<String>,
    val statuses: List<String>,
    val tags: List<String>,
    val boundaryKeywords: List<BoundaryKeywordDto>,
)

@Serializable
private data class BoundaryKeywordDto(val pattern: String, val themes: List<String>)
