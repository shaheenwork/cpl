package com.shnapps.couple.core.data.content

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.model.ChapterKind
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.InteractionType
import com.shnapps.couple.core.model.Mode
import com.shnapps.couple.core.model.Mood
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * How the app reads a bundle written for a different version of itself: newer additions
 * are skipped, anything that would be unsafe to guess at is dropped whole, and a format it
 * does not know is refused.
 */
class ContentBundleParserTest {

    @Test
    fun `reads a bundle, keeping each item whole for the cache`() {
        val parsed = ContentBundleParser.parse(ContentFixtures.bundle(2, listOf(ContentFixtures.item("flirt_one"))))

        assertThat(parsed.contentVersion).isEqualTo(2)
        assertThat(parsed.items.single().id).isEqualTo("flirt_one")
        assertThat(parsed.items.single().json).contains("\"locale\"")
        assertThat(ContentBundleParser.parsePacks(parsed.packsJson).single().intensityRange).isEqualTo(1..5)
    }

    @Test
    fun `maps an item onto the domain`() {
        val item = ContentBundleParser.parseItem(ContentFixtures.item("flirt_one", intensity = 3))!!

        assertThat(item.intensity).isEqualTo(Intensity.NAUGHTY)
        assertThat(item.modes).containsExactly(Mode.TOGETHER, Mode.APART)
        assertThat(item.interactionType).isEqualTo(InteractionType.CHALLENGE)
        assertThat(item.moods).containsExactly(Mood.NAUGHTY)
        assertThat(item.chapterKinds).containsExactly(ChapterKind.TEASE)
        assertThat(item.excludedByBoundaries).containsExactly("teasing_words")
    }

    @Test
    fun `skips fields and list entries from a newer app`() {
        val newer = ContentFixtures.item("flirt_one", extra = """"hologram": true,""")
            .replace("\"moods\": [\"naughty\"]", "\"moods\": [\"naughty\", \"cosmic\"]")
            .replace("\"chapterKinds\": [\"TEASE\"]", "\"chapterKinds\": [\"TEASE\", \"INTERMISSION\"]")
            .replace("\"modes\": [\"TOGETHER\", \"APART\"]", "\"modes\": [\"TOGETHER\", \"APART\", \"ORBIT\"]")

        val item = ContentBundleParser.parseItem(newer)!!

        assertThat(item.moods).containsExactly(Mood.NAUGHTY)
        assertThat(item.chapterKinds).containsExactly(ChapterKind.TEASE)
        assertThat(item.modes).containsExactly(Mode.TOGETHER, Mode.APART)
    }

    @Test
    fun `drops an item it cannot play safely rather than guessing`() {
        val base = ContentFixtures.item("flirt_one")
        val unplayable = listOf(
            base.replace("\"interactionType\": \"CHALLENGE\"", "\"interactionType\": \"HOLOGRAM\""),
            base.replace("\"intensity\": 1", "\"intensity\": 6"),
            base.replace("\"modes\": [\"TOGETHER\", \"APART\"]", "\"modes\": [\"ORBIT\"]"),
            base.replace("\"chapterKinds\": [\"TEASE\"]", "\"chapterKinds\": [\"INTERMISSION\"]"),
            base.replace("\"requiresMedia\": null", "\"requiresMedia\": \"HOLOGRAM\""),
            base.replace("\"status\": \"published\"", "\"status\": \"quarantined\""),
            // Missing its boundary exclusions: fail closed, never shown unguarded.
            base.replace("\"excludedByBoundaries\": [\"teasing_words\"],", ""),
            base.replace("\"requiredMutualPreferences\": [],", ""),
            "not json at all",
        )

        unplayable.forEach { text -> assertThat(ContentBundleParser.parseItem(text)).isNull() }
    }

    @Test
    fun `refuses a bundle format it does not know`() {
        val future = ContentFixtures.bundle(2, emptyList()).replace("\"format\": 1", "\"format\": 2")

        assertThrows(UnsupportedBundleException::class.java) { ContentBundleParser.parse(future) }
        assertThrows(SerializationException::class.java) { ContentBundleParser.parse("{\"format\": 1}") }
    }
}
