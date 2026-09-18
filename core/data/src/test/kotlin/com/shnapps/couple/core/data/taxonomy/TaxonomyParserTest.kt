package com.shnapps.couple.core.data.taxonomy

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.Mode
import org.junit.Test

/**
 * The parser's tolerance. A remotely updated taxonomy written for a newer app must still
 * load on this one: whatever this version cannot represent is dropped, never fatal.
 */
class TaxonomyParserTest {

    @Test
    fun `parses items with the theme they map to`() {
        val taxonomy = TaxonomyParser.parse(json(item("sensory_blindfold", floor = 3)))

        val item = taxonomy.item("sensory_blindfold")!!
        assertThat(item.intensityFloor).isEqualTo(Intensity.NAUGHTY)
        assertThat(item.modes).containsExactly(Mode.TOGETHER, Mode.APART)
        assertThat(item.categoryId).isEqualTo("cat")
        // The boundary theme is always the theme the item is filed under.
        assertThat(item.themeId).isEqualTo("theme")
    }

    @Test
    fun `ignores fields it does not know`() {
        val text = """
            {"version": 2, "futureField": true, "categories": [{"id": "cat", "title": "C", "subtitle": "S",
              "themes": [{"id": "theme", "title": "T", "icon": "star",
                "items": [{"id": "a", "prompt": "A", "description": "D", "intensityFloor": 1,
                  "modes": ["TOGETHER"], "premium": true}]}]}]}
        """.trimIndent()

        assertThat(TaxonomyParser.parse(text).items.map { it.id }).containsExactly("a")
    }

    @Test
    fun `drops an item whose intensity it does not know, and keeps the rest`() {
        val taxonomy = TaxonomyParser.parse(json(item("known", floor = 2), item("too_high", floor = 6)))

        assertThat(taxonomy.items.map { it.id }).containsExactly("known")
    }

    @Test
    fun `keeps only the modes it knows, and drops an item left with none`() {
        val taxonomy = TaxonomyParser.parse(
            json(
                item("mixed", modes = """["TOGETHER", "SOMEDAY"]"""),
                item("unknown_only", modes = """["SOMEDAY"]"""),
            ),
        )

        assertThat(taxonomy.items.map { it.id }).containsExactly("mixed")
        assertThat(taxonomy.item("mixed")!!.modes).containsExactly(Mode.TOGETHER)
    }

    @Test
    fun `drops a theme or category left empty`() {
        val taxonomy = TaxonomyParser.parse(json(item("too_high", floor = 9)))

        assertThat(taxonomy.categories).isEmpty()
    }

    @Test
    fun `filters items by the content level they need`() {
        val taxonomy = TaxonomyParser.parse(json(item("gentle", floor = 1), item("bold", floor = 4)))

        val atFlirty = taxonomy.items.filter { it.isAvailableAt(Intensity.FLIRTY) }.map { it.id }
        val atBold = taxonomy.items.filter { it.isAvailableAt(Intensity.BOLD) }.map { it.id }

        assertThat(atFlirty).containsExactly("gentle")
        assertThat(atBold).containsExactly("gentle", "bold")
    }

    private fun item(id: String, floor: Int = 1, modes: String = """["TOGETHER", "APART"]""") =
        """{"id": "$id", "prompt": "Prompt", "description": "Description", "intensityFloor": $floor, "modes": $modes}"""

    private fun json(vararg items: String) = """
        {"version": 1, "categories": [{"id": "cat", "title": "Category", "subtitle": "Sub",
          "themes": [{"id": "theme", "title": "Theme", "items": [${items.joinToString()}]}]}]}
    """.trimIndent()
}
