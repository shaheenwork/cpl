package com.shnapps.couple.core.testing

import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.content.ContentRepository
import com.shnapps.couple.core.data.content.ContentSyncResult
import com.shnapps.couple.core.model.ChapterKind
import com.shnapps.couple.core.model.ContentItem
import com.shnapps.couple.core.model.ContentPack
import com.shnapps.couple.core.model.ContentState
import com.shnapps.couple.core.model.ContentStatus
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.InteractionType
import com.shnapps.couple.core.model.Mode
import com.shnapps.couple.core.model.Mood
import com.shnapps.couple.core.model.Taxonomy
import kotlinx.coroutines.flow.MutableStateFlow

class FakeContentRepository(
    items: List<ContentItem> = TestContent.items,
    packs: List<ContentPack> = TestContent.packs,
    taxonomy: Taxonomy = TestTaxonomy.taxonomy,
) : ContentRepository {
    override val items = MutableStateFlow(items)
    override val packs = MutableStateFlow(packs)
    override val taxonomy = MutableStateFlow(taxonomy)
    override val state = MutableStateFlow(
        ContentState(contentVersion = 1, itemCount = items.size, deltaCount = 0, lastSyncMillis = null),
    )

    var syncResult: Outcome<ContentSyncResult> =
        Outcome.Success(ContentSyncResult(installedVersion = null, deltasReceived = 0))
    var syncCalls = 0
        private set

    override suspend fun ensureInstalled(): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun sync(): Outcome<ContentSyncResult> {
        syncCalls += 1
        return syncResult
    }
}

/** A handful of fixed items for tests, across two packs, both modes and three levels. */
object TestContent {
    val packs = listOf(
        ContentPack("FLIRT", "Flirt", "The fun of wanting each other.", 1..5),
        ContentPack("AFTER_DARK", "After dark", "Later, bolder.", 3..5),
    )

    val softTogether = item("flirt_soft", "FLIRT", Intensity.SOFT, setOf(Mode.TOGETHER))
    val flirtyApart = item("flirt_apart", "FLIRT", Intensity.FLIRTY, setOf(Mode.APART))
    val boldBoth = item("afterdark_bold", "AFTER_DARK", Intensity.BOLD, setOf(Mode.TOGETHER, Mode.APART))

    val items = listOf(softTogether, flirtyApart, boldBoth)

    fun item(id: String, pack: String, intensity: Intensity, modes: Set<Mode>) = ContentItem(
        id = id,
        version = 1,
        pack = pack,
        category = "teasing",
        title = "Title of $id",
        subtitle = "A subtitle.",
        body = "The body of $id.",
        tags = setOf("teasing"),
        intensity = intensity,
        modes = modes,
        interactionType = InteractionType.CHALLENGE,
        durationMin = 5,
        moods = setOf(Mood.NAUGHTY),
        requiredMutualPreferences = emptySet(),
        boostedByPreferences = setOf("teasing_verbal"),
        excludedByBoundaries = setOf("teasing_words"),
        chapterKinds = setOf(ChapterKind.TEASE),
        noveltyWeight = 0.5,
        repeatCooldownDays = 21,
        requiresMedia = null,
        status = ContentStatus.PUBLISHED,
    )
}
