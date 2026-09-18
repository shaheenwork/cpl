package com.shnapps.couple.core.data.taxonomy

import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.Mode
import com.shnapps.couple.core.model.PreferenceItem
import com.shnapps.couple.core.model.Taxonomy
import com.shnapps.couple.core.model.TaxonomyCategory
import com.shnapps.couple.core.model.TaxonomyTheme
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Turns taxonomy JSON (`content/taxonomy.json`) into the domain [Taxonomy].
 *
 * Tolerant on purpose, because the taxonomy is remotely updatable (BUILD_PROMPT.md §9.5):
 * a file written for a newer app may carry fields, modes or intensities this version does
 * not know. Unknown fields are ignored and an item this version cannot represent is dropped,
 * rather than the whole taxonomy failing to load. The bundled file is held to the strict
 * standard instead — `TaxonomyFileTest` fails the build on anything this parser would
 * quietly skip.
 */
object TaxonomyParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): Taxonomy = json.decodeFromString<TaxonomyDto>(text).toTaxonomy()
}

internal fun TaxonomyDto.toTaxonomy(): Taxonomy = Taxonomy(
    version = version,
    categories = categories
        .map { category ->
            TaxonomyCategory(
                id = category.id,
                title = category.title,
                subtitle = category.subtitle,
                themes = category.themes
                    .map { theme ->
                        TaxonomyTheme(
                            id = theme.id,
                            title = theme.title,
                            items = theme.items.mapNotNull { it.toItem(category.id, theme.id) },
                        )
                    }
                    .filter { it.items.isNotEmpty() },
            )
        }
        .filter { it.themes.isNotEmpty() },
)

private fun ItemDto.toItem(categoryId: String, themeId: String): PreferenceItem? {
    val floor = Intensity.entries.firstOrNull { it.level == intensityFloor } ?: return null
    val knownModes = modes.mapNotNull { name -> Mode.entries.firstOrNull { it.name == name } }.toSet()
    if (knownModes.isEmpty()) return null
    return PreferenceItem(
        id = id,
        prompt = prompt,
        description = description,
        intensityFloor = floor,
        modes = knownModes,
        categoryId = categoryId,
        themeId = themeId,
    )
}

// The file format. Internal so the strict validation in the test suite can read the raw
// file through the same types the app does.

@Serializable
internal data class TaxonomyDto(
    val version: Int,
    val categories: List<CategoryDto>,
)

@Serializable
internal data class CategoryDto(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val themes: List<ThemeDto>,
)

@Serializable
internal data class ThemeDto(
    val id: String,
    val title: String,
    val items: List<ItemDto>,
)

@Serializable
internal data class ItemDto(
    val id: String,
    val prompt: String,
    val description: String = "",
    val intensityFloor: Int,
    val modes: List<String>,
)
