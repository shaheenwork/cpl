package com.shnapps.couple.core.data.instant

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.limits.LimitsRepository
import com.shnapps.couple.core.engine.NightLength
import com.shnapps.couple.core.model.ContentItem
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.LimitAnswer
import com.shnapps.couple.core.model.LimitTag
import com.shnapps.couple.core.model.Mode
import com.shnapps.couple.core.testing.FakeAppPreferencesStore
import com.shnapps.couple.core.testing.FakeContentRepository
import com.shnapps.couple.core.testing.TestContent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * What each surface is dealt from (D-125, D-133).
 *
 * The lane is a choice made in the card player: "deal me After dark". A guided evening is the one
 * surface that needs the whole catalogue — four phases of it — which is why it is a plan over the
 * deck rather than a game with a pool of its own. The two must not share the narrowing.
 */
class InstantPlayRepositoryTest {

    private val preferences = FakeAppPreferencesStore()
    private val content = FakeContentRepository(items = catalogue)
    private val limits = object : LimitsRepository {
        override val limits: Flow<Map<LimitTag, LimitAnswer>> = MutableStateFlow(emptyMap())
        override val refusedThemes: Flow<Set<String>> = MutableStateFlow(emptySet())

        override suspend fun set(tag: LimitTag, answer: LimitAnswer): Outcome<Unit> = Outcome.Success(Unit)
    }

    private fun repository() = DefaultInstantPlayRepository(content, preferences, preferences, limits)

    @Test
    fun `the card player deals from the lane it was asked for`() = runTest {
        val repository = repository()
        repository.setLane("AFTER_DARK")

        assertThat(repository.deck.first().packs).containsExactly("AFTER_DARK")
    }

    @Test
    fun `a guided evening ignores the lane and takes the whole catalogue`() = runTest {
        val repository = repository()
        repository.setTier(Intensity.EXTREME)
        repository.setLane("FLIRT")

        val night = repository.planNight(NightLength.LONG)

        // Found on a device: a couple who once asked for one pack got guided evenings out of that
        // pack for ever, because the lane is remembered and the plan took the deck as it stood.
        assertThat(night.cards.map { it.card.pack }.toSet().size).isGreaterThan(1)
    }

    @Test
    fun `the lane still narrows the cards, so the two really are different decks`() = runTest {
        val repository = repository()
        repository.setTier(Intensity.EXTREME)
        repository.setLane("FLIRT")

        assertThat(repository.deck.first().packs).containsExactly("FLIRT")
    }

    private companion object {
        /** Enough of both packs at every level for an hour to have somewhere to go. */
        val catalogue: List<ContentItem> = Intensity.entries.flatMap { level ->
            (1..6).map { n ->
                TestContent.item("flirt_${level.name.lowercase()}_$n", "FLIRT", level, setOf(Mode.TOGETHER))
            } + (1..6).map { n ->
                TestContent.item("afterdark_${level.name.lowercase()}_$n", "AFTER_DARK", level, setOf(Mode.TOGETHER))
            }
        }
    }
}
