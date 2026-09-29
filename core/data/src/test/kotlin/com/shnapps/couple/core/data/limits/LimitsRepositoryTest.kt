package com.shnapps.couple.core.data.limits

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.model.Boundary
import com.shnapps.couple.core.model.BoundaryLevel
import com.shnapps.couple.core.model.LimitAnswer
import com.shnapps.couple.core.model.LimitTag
import com.shnapps.couple.core.testing.FakeAppPreferencesStore
import com.shnapps.couple.core.testing.FakeAuthRepository
import com.shnapps.couple.core.testing.FakeBoundaryRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Eight questions over twenty-seven themes (M3).
 *
 * The assertions worth having are the ones about what a no actually *does*: it has to reach every
 * theme its tag owns, because the themes are what the engine filters on, and it has to reach the
 * phone whether or not there is an account, because the profile is offered after the first session
 * and signing up is never required to play.
 */
class LimitsRepositoryTest {

    private val auth = FakeAuthRepository()
    private val boundaries = FakeBoundaryRepository()
    private val preferences = FakeAppPreferencesStore()

    private fun repository() = DefaultLimitsRepository(auth, boundaries, preferences, preferences)

    @Test
    fun `a profile can be filled in with no account at all`() = runTest {
        auth.user.value = null

        val result = repository().set(LimitTag.POWER_PLAY, LimitAnswer.NO)

        assertThat(result).isInstanceOf(Outcome.Success::class.java)
        assertThat(repository().limits.first()[LimitTag.POWER_PLAY]).isEqualTo(LimitAnswer.NO)
        // Nothing was written to an account that does not exist.
        assertThat(boundaries.writes).isEmpty()
    }

    @Test
    fun `a no reaches every theme the tag owns`() = runTest {
        auth.user.value = FakeAuthRepository.DEFAULT_USER

        repository().set(LimitTag.POWER_PLAY, LimitAnswer.NO)

        assertThat(boundaries.writes.map { it.first }.toSet()).isEqualTo(LimitTag.POWER_PLAY.themes)
        assertThat(boundaries.writes.map { it.second.level }.toSet())
            .containsExactly(BoundaryLevel.NEVER)
    }

    @Test
    fun `a no removes the tag's themes from the deck, and a maybe does not`() = runTest {
        val repository = repository()

        repository.set(LimitTag.SENSORY, LimitAnswer.NO)
        repository.set(LimitTag.TOUCH, LimitAnswer.MAYBE)

        val refused = repository.refusedThemes.first()
        assertThat(refused).containsAtLeastElementsIn(LimitTag.SENSORY.themes)
        // Maybe is a real answer that keeps the card. Treating it as a refusal would make the
        // middle answer the expensive one to pick, which is not what the brief asks for.
        assertThat(refused).containsNoneIn(LimitTag.TOUCH.themes)
    }

    @Test
    fun `the account's answer wins over the phone's, because the server filters on it`() = runTest {
        auth.user.value = FakeAuthRepository.DEFAULT_USER
        val repository = repository()
        repository.set(LimitTag.FANTASY, LimitAnswer.YES)

        // As if it had been set on the couple's other phone, or before this one was installed.
        boundaries.setBoundary("comm_fantasies", Boundary(level = BoundaryLevel.NEVER))

        assertThat(repository.limits.first()[LimitTag.FANTASY]).isEqualTo(LimitAnswer.NO)
        assertThat(repository.refusedThemes.first()).containsAtLeastElementsIn(LimitTag.FANTASY.themes)
    }

    @Test
    fun `a tag is as strict as the strictest theme under it`() = runTest {
        auth.user.value = FakeAuthRepository.DEFAULT_USER
        val repository = repository()

        boundaries.setBoundary("power_leading", Boundary(level = BoundaryLevel.ALWAYS_OK))
        boundaries.setBoundary("power_yielding", Boundary(level = BoundaryLevel.CURIOUS))

        assertThat(repository.limits.first()[LimitTag.POWER_PLAY]).isEqualTo(LimitAnswer.MAYBE)

        boundaries.setBoundary("power_switching", Boundary(level = BoundaryLevel.NEVER))

        assertThat(repository.limits.first()[LimitTag.POWER_PLAY]).isEqualTo(LimitAnswer.NO)
    }

    @Test
    fun `an unanswered tag is absent rather than defaulted`() = runTest {
        // A profile is optional: nothing about a couple who never opened it may look like a no.
        assertThat(repository().limits.first()).isEmpty()
        assertThat(repository().refusedThemes.first()).isEmpty()
    }

    @Test
    fun `a refusal made mid-card still counts alongside the profile`() = runTest {
        val repository = repository()
        preferences.refuse(cardId = "flirt_x", themes = setOf("comm_flirting"))

        repository.set(LimitTag.SENSORY, LimitAnswer.NO)

        val refused = repository.refusedThemes.first()
        assertThat(refused).contains("comm_flirting")
        assertThat(refused).containsAtLeastElementsIn(LimitTag.SENSORY.themes)
    }
}
