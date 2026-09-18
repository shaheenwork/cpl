package com.shnapps.couple.core.data.content

import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.firebase.content.ContentDelta
import com.shnapps.couple.core.firebase.content.ContentRemoteDataSource

/** Small, hand-written bundles, so each test states exactly what it relies on. */
internal object ContentFixtures {

    fun item(
        id: String,
        version: Int = 1,
        title: String = "Title of $id",
        intensity: Int = 1,
        status: String = "published",
        extra: String = "",
    ): String =
        """{$extra"id": "$id", "version": $version, "title": "$title", "subtitle": "A subtitle.", """ +
            """"body": "The body of $id, long enough to be real.", "pack": "FLIRT", "category": "teasing", """ +
            """"tags": ["teasing"], "intensity": $intensity, "modes": ["TOGETHER", "APART"], """ +
            """"interactionType": "CHALLENGE", "durationMin": 5, "moods": ["naughty"], """ +
            """"requiredMutualPreferences": [], "boostedByPreferences": ["teasing_verbal"], """ +
            """"excludedByBoundaries": ["teasing_words"], "chapterKinds": ["TEASE"], "noveltyWeight": 0.5, """ +
            """"repeatCooldownDays": 21, "requiresMedia": null, "status": "$status", "locale": "en", """ +
            """"createdAt": "2026-09-18T00:00:00.000Z", "updatedAt": "2026-09-18T00:00:00.000Z"}"""

    fun bundle(contentVersion: Int, items: List<String>, taxonomyPrompt: String = "Verbal teasing"): String = """
        {
          "format": 1,
          "contentVersion": $contentVersion,
          "taxonomyVersion": 1,
          "packs": [
            {"id": "FLIRT", "title": "Flirt", "description": "The fun of wanting each other.", "intensity": [1, 5]}
          ],
          "taxonomy": {"version": 1, "categories": [{"id": "teasing", "title": "Teasing",
            "subtitle": "Making them wait.", "themes": [{"id": "teasing_words", "title": "Words",
            "items": [{"id": "teasing_verbal", "prompt": "$taxonomyPrompt", "description": "Saying it.",
            "intensityFloor": 2, "modes": ["TOGETHER", "APART"]}]}]}]},
          "items": [
            ${items.joinToString(",\n")}
          ]
        }
    """.trimIndent()
}

internal class FakeShippedContent(var text: String) : ShippedContent {
    var reads = 0
        private set

    override suspend fun version(): Int = ContentBundleParser.parse(text).contentVersion

    override suspend fun read(): String {
        reads += 1
        return text
    }
}

internal class FakeContentRemote : ContentRemoteDataSource {
    var latest: Outcome<Int> = Outcome.Success(1)
    val bundles = mutableMapOf<Int, String>()
    val deltas = mutableListOf<ContentDelta>()
    val downloads = mutableListOf<Int>()
    val cursors = mutableListOf<Long>()

    override suspend fun latestVersion(): Outcome<Int> = latest

    override suspend fun downloadBundle(version: Int): Outcome<String> {
        downloads += version
        return bundles[version]?.let { Outcome.Success(it) } ?: Outcome.Failure(AppError.NotFound())
    }

    override suspend fun deltasSince(cursorMillis: Long): Outcome<List<ContentDelta>> {
        cursors += cursorMillis
        return Outcome.Success(deltas.filter { it.updatedAtMillis >= cursorMillis }.sortedBy { it.updatedAtMillis })
    }
}
