package com.shnapps.couple.core.testing

import com.shnapps.couple.core.data.taxonomy.TaxonomyRepository
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.Mode
import com.shnapps.couple.core.model.PreferenceItem
import com.shnapps.couple.core.model.Taxonomy
import com.shnapps.couple.core.model.TaxonomyCategory
import com.shnapps.couple.core.model.TaxonomyTheme
import kotlinx.coroutines.flow.MutableStateFlow

class FakeTaxonomyRepository(
    initial: Taxonomy = TestTaxonomy.taxonomy,
) : TaxonomyRepository {
    override val taxonomy = MutableStateFlow(initial)
}

/**
 * A small, fixed taxonomy for tests: two categories, six items, floors 1 to 4.
 *
 * Deliberately not the real `content/taxonomy.json`, which will keep changing as content
 * is written. Tests that care about the real file read it directly.
 */
object TestTaxonomy {
    const val VERSION = 3

    val romantic = item("mood_romantic", "Romantic nights", Intensity.SOFT, "mood", "mood_gentle")
    val playful = item("mood_playful", "Playful", Intensity.SOFT, "mood", "mood_gentle")
    val flirty = item("mood_flirty", "Flirty", Intensity.FLIRTY, "mood", "mood_charged")
    val intense = item("mood_intense", "Intense", Intensity.BOLD, "mood", "mood_charged")
    val takeTheLead = item("power_take_the_lead", "Taking the lead", Intensity.FLIRTY, "power", "power_leading")
    val followInstructions =
        item("power_follow_instructions", "Following instructions", Intensity.NAUGHTY, "power", "power_yielding")

    val taxonomy = Taxonomy(
        version = VERSION,
        categories = listOf(
            TaxonomyCategory(
                id = "mood",
                title = "Mood",
                subtitle = "The feeling you want in the room.",
                themes = listOf(
                    TaxonomyTheme("mood_gentle", "Gentle", listOf(romantic, playful)),
                    TaxonomyTheme("mood_charged", "Charged", listOf(flirty, intense)),
                ),
            ),
            TaxonomyCategory(
                id = "power",
                title = "Power & dynamics",
                subtitle = "Who leads, who follows. Always by agreement.",
                themes = listOf(
                    TaxonomyTheme("power_leading", "Leading", listOf(takeTheLead)),
                    TaxonomyTheme("power_yielding", "Letting go", listOf(followInstructions)),
                ),
            ),
        ),
    )

    private fun item(id: String, prompt: String, floor: Intensity, categoryId: String, themeId: String) =
        PreferenceItem(
            id = id,
            prompt = prompt,
            description = "About $prompt.",
            intensityFloor = floor,
            modes = setOf(Mode.TOGETHER, Mode.APART),
            categoryId = categoryId,
            themeId = themeId,
        )
}
