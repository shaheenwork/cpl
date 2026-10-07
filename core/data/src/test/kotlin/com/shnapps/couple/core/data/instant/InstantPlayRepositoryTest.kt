package com.shnapps.couple.core.data.instant

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.entitlement.LevelAccess
import com.shnapps.couple.core.data.limits.LimitsRepository
import com.shnapps.couple.core.engine.NightLength
import com.shnapps.couple.core.model.ContentItem
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.LimitAnswer
import com.shnapps.couple.core.model.LimitTag
import com.shnapps.couple.core.model.Mode
import com.shnapps.couple.core.testing.FakeAppPreferencesStore
import com.shnapps.couple.core.testing.FakeContentRepository
import com.shnapps.couple.core.testing.FakeLevelAccessRepository
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

    /** Open, as it is for the first month after install. The clamp has its own tests below. */
    private val levels = FakeLevelAccessRepository(LevelAccess.OPEN)

    private fun repository() =
        DefaultInstantPlayRepository(content, preferences, preferences, limits, levels)

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

    @Test
    fun `the deck never goes past what this phone may play`() = runTest {
        val repository = repository()
        repository.setTier(Intensity.EXTREME)
        levels.access.value = LevelAccess.FREE

        // Clamped in the repository rather than on the screen, for the same reason the server
        // clamps a night: a ceiling only the UI applies is a ceiling the next caller forgets.
        assertThat(repository.tier.first()).isEqualTo(Intensity.FLIRTY)
        assertThat(repository.deck.first().cards.map { it.intensity }.toSet())
            .containsNoneOf(Intensity.SPICY, Intensity.HOT, Intensity.EXTREME)
    }

    @Test
    fun `the heat they asked for comes back the moment a pack opens it`() = runTest {
        val repository = repository()
        repository.setTier(Intensity.EXTREME)
        levels.access.value = LevelAccess.FREE
        assertThat(repository.tier.first()).isEqualTo(Intensity.FLIRTY)

        // Nothing was overwritten while it was locked, so buying the pack restores the evening
        // they had rather than making them set it again.
        levels.access.value = LevelAccess(ceiling = Intensity.EXTREME, owned = setOf(Intensity.EXTREME))

        assertThat(repository.tier.first()).isEqualTo(Intensity.EXTREME)
    }

    @Test
    fun `a guided evening is clamped too, and so is a game`() = runTest {
        val repository = repository()
        repository.setTier(Intensity.EXTREME)
        levels.access.value = LevelAccess(ceiling = Intensity.SPICY, owned = setOf(Intensity.SPICY))

        val night = repository.planNight(NightLength.LONG)

        assertThat(night.cards.map { it.card.intensity }.toSet())
            .containsNoneOf(Intensity.HOT, Intensity.EXTREME)
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
