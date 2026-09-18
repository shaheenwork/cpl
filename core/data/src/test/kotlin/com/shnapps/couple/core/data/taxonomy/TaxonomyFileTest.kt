package com.shnapps.couple.core.data.taxonomy

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.serialization.json.Json
import org.junit.Test
import java.io.File

/**
 * The shipped taxonomy, `content/taxonomy.json`, held to the strict standard
 * (BUILD_PROMPT.md §3.3, §9.4, §9.5).
 *
 * The parser the app uses is tolerant — it drops what it cannot represent, so a newer remote
 * taxonomy never breaks an older app. That tolerance must never hide a mistake in the file
 * we ship, so these tests read it strictly: an unknown or misspelt field fails, and anything
 * the app would silently drop fails.
 *
 * The file is a declared input of the test task, so editing it re-runs these tests.
 */
class TaxonomyFileTest {

    private val text: File = File(TAXONOMY_PATH).also {
        check(it.isFile) { "Expected the taxonomy at ${it.absolutePath}" }
    }

    // No ignoreUnknownKeys: a typo such as "intensityFlor" must fail here, not be skipped.
    private val raw: TaxonomyDto = Json.decodeFromString(text.readText())

    private val categories = raw.categories
    private val themes = categories.flatMap { it.themes }
    private val items = themes.flatMap { it.items }

    @Test
    fun `has a positive version`() {
        assertThat(raw.version).isAtLeast(1)
    }

    @Test
    fun `keeps every required category, in order, rather than one list of kinks`() {
        assertThat(categories.map { it.id }).containsExactlyElementsIn(REQUIRED_CATEGORIES).inOrder()
    }

    @Test
    fun `the app parses it without dropping anything`() {
        val parsed = TaxonomyParser.parse(text.readText())

        assertThat(parsed.version).isEqualTo(raw.version)
        assertThat(parsed.categories.map { it.id }).isEqualTo(categories.map { it.id })
        assertThat(parsed.categories.flatMap { it.themes }.map { it.id }).isEqualTo(themes.map { it.id })
        assertThat(parsed.items.map { it.id }).isEqualTo(items.map { it.id })
    }

    @Test
    fun `ids are well formed, so they are valid document ids under the rules`() {
        val all = categories.map { it.id } + themes.map { it.id } + items.map { it.id }
        all.forEach { id -> assertWithMessage("id '$id'").that(id).matches(ID_PATTERN.toPattern()) }
    }

    @Test
    fun `ids are unique at every level`() {
        assertThat(categories.map { it.id }).containsNoDuplicates()
        assertThat(themes.map { it.id }).containsNoDuplicates()
        assertThat(items.map { it.id }).containsNoDuplicates()
    }

    @Test
    fun `no category or theme is empty`() {
        categories.forEach { assertWithMessage("category ${it.id}").that(it.themes).isNotEmpty() }
        themes.forEach { assertWithMessage("theme ${it.id}").that(it.items).isNotEmpty() }
    }

    @Test
    fun `every item has a floor in range and at least one known mode`() {
        items.forEach { item ->
            assertWithMessage("${item.id} intensityFloor").that(item.intensityFloor).isIn(1..MAX_INTENSITY)
            assertWithMessage("${item.id} modes").that(item.modes).isNotEmpty()
            assertWithMessage("${item.id} modes").that(item.modes).containsNoDuplicates()
            item.modes.forEach { mode ->
                assertWithMessage("${item.id} mode").that(mode).isIn(KNOWN_MODES)
            }
        }
    }

    @Test
    fun `all copy is present and short enough for a card`() {
        categories.forEach { category ->
            assertWithMessage("${category.id} title").that(category.title).isNotEmpty()
            assertWithMessage("${category.id} subtitle").that(category.subtitle).isNotEmpty()
        }
        themes.forEach { assertWithMessage("${it.id} title").that(it.title).isNotEmpty() }
        items.forEach { item ->
            assertWithMessage("${item.id} prompt").that(item.prompt.trim()).isNotEmpty()
            assertWithMessage("${item.id} prompt length").that(item.prompt.length).isAtMost(MAX_PROMPT)
            assertWithMessage("${item.id} description").that(item.description.trim()).isNotEmpty()
            assertWithMessage("${item.id} description length")
                .that(item.description.length).isAtMost(MAX_DESCRIPTION)
        }
    }

    @Test
    fun `copy stays clear of every prohibited theme`() {
        // A tripwire, not a censor: section 3.3 is enforced by authoring, and this catches a
        // careless edit. Word-boundary matches, so "strangers" roleplay between the couple is
        // fine while a third party is not.
        val copy = categories.flatMap { listOf(it.id, it.title, it.subtitle) } +
            themes.flatMap { listOf(it.id, it.title) } +
            items.flatMap { listOf(it.id, it.prompt, it.description) }

        copy.forEach { line ->
            PROHIBITED.forEach { pattern ->
                assertWithMessage("prohibited term '${pattern.pattern}' in \"$line\"")
                    .that(pattern.containsMatchIn(line.lowercase()))
                    .isFalse()
            }
        }
    }

    private companion object {
        // Test tasks run with the module directory (core/data) as the working directory.
        const val TAXONOMY_PATH = "../../content/taxonomy.json"

        const val MAX_INTENSITY = 5
        const val MAX_PROMPT = 40
        const val MAX_DESCRIPTION = 120

        val REQUIRED_CATEGORIES =
            listOf("mood", "power", "teasing", "sensory", "roleplay", "communication", "exploration")
        val KNOWN_MODES = listOf("TOGETHER", "APART")

        // Mirrors the Firestore rule on preference document ids.
        val ID_PATTERN = Regex("^[a-z0-9_]{1,64}$")

        // Section 3.3. Minors, non-consent, illegal acts, harm, third parties, substances,
        // non-consenting bystanders.
        val PROHIBITED = listOf(
            "minor", "minors", "teen\\w*", "underage", "child\\w*", "kids?", "school\\w*", "young",
            "non-?consen\\w*", "coerc\\w*", "forc\\w*", "against (?:their|your|her|his) will",
            "unconscious", "asleep", "sleeping",
            "drunk", "intoxicat\\w*", "alcohol", "drugs?", "substances?", "high",
            "incest\\w*", "step-?(?:brother|sister|mom|mother|dad|father|son|daughter)\\w*",
            "animals?", "bestial\\w*", "illegal",
            "breath ?play", "chok\\w*", "strangl\\w*", "suspension", "electr\\w*", "blood", "knife",
            "needles?", "self-?harm", "degrad\\w*",
            "third", "threesome", "someone else", "another person", "others", "swing\\w*", "swap\\w*",
            "audience", "public", "in front of",
        ).map { Regex("\\b$it\\b") }
    }
}
