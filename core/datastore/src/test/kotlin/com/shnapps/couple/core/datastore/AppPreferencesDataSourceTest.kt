package com.shnapps.couple.core.datastore

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.LimitAnswer
import com.shnapps.couple.core.model.LimitTag
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The real store, against real DataStore — not the fake.
 *
 * This file exists because of a bug it would have caught on the day it was written. `setLimit`
 * built its entry with `"${'$'}{'$'}{tag.name}=…"`, which is the literal text `${'$'}{tag.name}=`
 * rather than the tag's name, so every answer in the limits profile was written under the same
 * meaningless key and read back as nothing at all. Nothing noticed: every test above this layer
 * uses the in-memory fake, which stores a real map and was right all along.
 *
 * So what is asserted here is only ever the round trip — written by this class, read by this class.
 * A fake can prove a ViewModel; only the real thing can prove the thing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppPreferencesDataSourceTest {

    private val store = AppPreferencesDataSource(ApplicationProvider.getApplicationContext())

    @Before
    fun clean() = runTest { store.clear() }

    @Test
    fun `a limit is written under its own tag, and read back as what was said`() = runTest {
        store.setLimit(LimitTag.TOUCH, LimitAnswer.YES)
        store.setLimit(LimitTag.POWER_PLAY, LimitAnswer.NO)

        val limits = store.preferences.first().limits

        assertThat(limits[LimitTag.TOUCH]).isEqualTo(LimitAnswer.YES)
        assertThat(limits[LimitTag.POWER_PLAY]).isEqualTo(LimitAnswer.NO)
    }

    @Test
    fun `answering again replaces the answer rather than adding a second one`() = runTest {
        store.setLimit(LimitTag.TOUCH, LimitAnswer.YES)
        store.setLimit(LimitTag.TOUCH, LimitAnswer.MAYBE)

        val limits = store.preferences.first().limits

        assertThat(limits).hasSize(1)
        assertThat(limits[LimitTag.TOUCH]).isEqualTo(LimitAnswer.MAYBE)
    }

    @Test
    fun `unsetting one leaves the others alone`() = runTest {
        store.setLimit(LimitTag.TOUCH, LimitAnswer.YES)
        store.setLimit(LimitTag.VERBAL, LimitAnswer.MAYBE)

        store.setLimit(LimitTag.TOUCH, null)

        val limits = store.preferences.first().limits
        assertThat(limits.keys).containsExactly(LimitTag.VERBAL)
    }

    @Test
    fun `a bought pack is remembered, and buying another keeps the first`() = runTest {
        store.unlock(setOf(Intensity.SPICY))
        store.unlock(setOf(Intensity.HOT))

        assertThat(store.preferences.first().ownedLevels)
            .containsExactly(Intensity.SPICY, Intensity.HOT)
    }

    @Test
    fun `the deck comes round again once every card has been seen`() = runTest {
        store.markPlayed("a", deckSize = 2)
        store.markPlayed("b", deckSize = 2)

        // Seen all of it: the record resets to this one card rather than growing into a set that
        // can only ever exclude everything (D-124).
        assertThat(store.preferences.first().playedCardIds).containsExactly("b")
    }
}
