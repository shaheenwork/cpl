package com.shnapps.couple.core.data.night

import com.shnapps.couple.core.data.content.ContentBundleParser
import com.shnapps.couple.core.engine.Chapter
import com.shnapps.couple.core.engine.Engine
import com.shnapps.couple.core.engine.ServerNight
import com.shnapps.couple.core.engine.Validator
import com.shnapps.couple.core.model.ChapterKind
import com.shnapps.couple.core.model.ContentItem
import com.shnapps.couple.core.model.ContentStatus
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.Leadership
import com.shnapps.couple.core.model.Mode
import com.shnapps.couple.core.model.NightSettings
import com.shnapps.couple.core.model.Novelty
import java.io.File

/**
 * A night as the server could have built it, over the real shipped catalogue: a valid
 * 15-minute Together night at FLIRTY, and a pool of alternates around it — enough for the
 * phone's swaps to have real choices, and real rules to keep.
 */
internal object NightFixtures {

    val catalogue: List<ContentItem> by lazy {
        val bundle = ContentBundleParser.parse(File(BUNDLE_PATH).readText())
        bundle.items.mapNotNull { ContentBundleParser.parseItem(it.json) }
    }
    val byId: Map<String, ContentItem> by lazy { catalogue.associateBy { it.id } }

    const val CEILING = 2
    const val SEED = 7L

    val settings = NightSettings(
        mode = Mode.TOGETHER,
        durationMin = 15,
        intensity = Intensity.FLIRTY,
        leadership = Leadership.SWITCH,
        novelty = Novelty.MIXED,
    )

    private val permitted: List<ContentItem> by lazy {
        catalogue
            .filter { Mode.TOGETHER in it.modes && it.intensity.level <= CEILING }
            .filter { it.status == ContentStatus.PUBLISHED }
            .sortedBy { it.id }
    }

    /** The first valid warm-up → middle → finale night the catalogue offers, by id. */
    val chapters: List<Chapter> by lazy {
        val warmUps = permitted.filter { ChapterKind.WARM_UP in it.chapterKinds && it.intensity.level == 1 }
        val middles = permitted.filter { ChapterKind.TEASE in it.chapterKinds }
        val finales = permitted.filter { ChapterKind.FINALE in it.chapterKinds && it.intensity.level == CEILING }
        warmUps.firstNotNullOf { warm ->
            middles.firstNotNullOfOrNull { middle ->
                finales.firstNotNullOfOrNull { finale ->
                    val chapters = listOf(
                        Chapter(0, ChapterKind.WARM_UP, warm.id, warm.intensity.level),
                        Chapter(1, ChapterKind.TEASE, middle.id, middle.intensity.level),
                        Chapter(2, ChapterKind.FINALE, finale.id, finale.intensity.level),
                    )
                    val distinct = setOf(warm.id, middle.id, finale.id).size == 3
                    chapters.takeIf { distinct && Validator.validate(it, 15, CEILING, Mode.TOGETHER, byId).isEmpty() }
                }
            }
        }
    }

    /** Other permitted items for each of the night's kinds: what "Not this one" can reach for. */
    val alternates: List<String> by lazy {
        val used = chapters.map { it.itemId }.toSet()
        listOf(ChapterKind.WARM_UP, ChapterKind.TEASE, ChapterKind.FINALE).flatMap { kind ->
            permitted.filter { kind in it.chapterKinds && it.id !in used }.take(ALTERNATES_PER_KIND).map { it.id }
        }.distinct()
    }

    val server: ServerNight by lazy {
        ServerNight(HeldNight.requestOf(settings, SEED), chapters, alternates, CEILING, Engine.VERSION)
    }

    private const val ALTERNATES_PER_KIND = 4
    private const val BUNDLE_PATH = "../../content/dist/bundle.json"
}
