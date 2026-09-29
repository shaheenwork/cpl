package com.shnapps.couple.core.data.instant

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.datastore.AppPreferences
import com.shnapps.couple.core.model.Boundary
import com.shnapps.couple.core.model.BoundaryLevel
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.testing.FakeAppPreferencesStore
import com.shnapps.couple.core.testing.FakeBoundaryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * What a phone learned before anyone signed in, carried into the account (REDESIGN_PROMPT.md §4.2).
 *
 * Someone can play for a week before they make an account, saying "never show me these again" to
 * things they meant. Two properties keep that honest when it finally reaches a server:
 *
 * - it only ever **tightens** — a limit the person set themselves is never overwritten, even by a
 *   stricter one, because a deliberate choice on a limits screen outranks a tap during play;
 * - it carries **no content** — a refusal is a theme, never the card that prompted it.
 */
class LocalPlayReconcilerTest {

    private val preferences = FakeAppPreferencesStore()
    private var boundaries = FakeBoundaryRepository()
    private val level = RecordingContentLevel()

    @Test
    fun `a refusal made before signing up becomes a real limit`() = runTest {
        preferences.set(refusedThemes = setOf("power_yielding", "sensory_blindfold"))

        val carried = reconciler().reconcile()

        assertThat(carried.themes).isEqualTo(2)
        assertThat(boundaries.writes.map { it.first })
            .containsExactly("power_yielding", "sensory_blindfold")
        assertThat(boundaries.writes.map { it.second.level }.toSet())
            .containsExactly(BoundaryLevel.NEVER)
    }

    @Test
    fun `a limit they set themselves is never overwritten`() = runTest {
        // They said ASK_FIRST on a limits screen. A tap during play must not silently harden it,
        // and must not loosen it either: what they chose deliberately stands.
        boundaries = FakeBoundaryRepository(mapOf("power_yielding" to Boundary(BoundaryLevel.ASK_FIRST)))
        preferences.set(refusedThemes = setOf("power_yielding"))

        val carried = reconciler().reconcile()

        assertThat(carried.themes).isEqualTo(0)
        assertThat(boundaries.writes).isEmpty()
        assertThat(boundaries.boundaries.value["power_yielding"]?.level).isEqualTo(BoundaryLevel.ASK_FIRST)
    }

    @Test
    fun `the tier this phone was set to becomes their own content level`() = runTest {
        preferences.set(tier = Intensity.HOT)

        val carried = reconciler().reconcile()

        assertThat(level.written).isEqualTo(Intensity.HOT)
        assertThat(carried.contentLevel).isEqualTo(Intensity.HOT)
    }

    @Test
    fun `nothing about what they were shown goes with it`() = runTest {
        // The cards they refused are on the phone by id. Only the themes travel: a content id on a
        // server-readable path is exactly what §5 forbids.
        preferences.set(
            refusedThemes = setOf("teasing_anticipation"),
            refusedCardIds = setOf("afterdark_one_layer_one_word", "tease_opposite_corners"),
        )

        reconciler().reconcile()

        val written = boundaries.writes.map { it.first } + boundaries.writes.mapNotNull { it.second.note }
        assertThat(written).containsExactly("teasing_anticipation")
    }

    @Test
    fun `running it twice writes nothing the second time`() = runTest {
        preferences.set(refusedThemes = setOf("power_yielding"))
        val reconciler = reconciler()

        val first = reconciler.reconcile()
        val second = reconciler.reconcile()

        assertThat(first.themes).isEqualTo(1)
        assertThat(second.themes).isEqualTo(0)
        assertThat(boundaries.writes).hasSize(1)
    }

    @Test
    fun `a phone that refused nothing still carries its tier`() = runTest {
        preferences.set(tier = Intensity.MILD)

        val carried = reconciler().reconcile()

        assertThat(carried.themes).isEqualTo(0)
        // A tier is the engine's own unit now, so it carries across as itself rather than
        // through the ceiling `Heat` used to name (M2).
        assertThat(carried.contentLevel).isEqualTo(Intensity.MILD)
    }

    @Test
    fun `a write that fails is not counted as carried`() = runTest {
        preferences.set(refusedThemes = setOf("power_yielding"))
        boundaries.setResult = Outcome.Failure(AppError.Network())

        val carried = reconciler().reconcile()

        assertThat(carried.themes).isEqualTo(0)
    }

    private fun reconciler() = LocalPlayReconciler(preferences, boundaries, level)

    private fun FakeAppPreferencesStore.set(
        tier: Intensity = Intensity.DEFAULT,
        refusedThemes: Set<String> = emptySet(),
        refusedCardIds: Set<String> = emptySet(),
    ) {
        state.value = AppPreferences(
            tier = tier,
            refusedThemes = refusedThemes,
            refusedCardIds = refusedCardIds,
        )
    }

    private class RecordingContentLevel : ContentLevelWriter {
        var written: Intensity? = null
            private set

        override suspend fun setContentLevel(level: Intensity): Outcome<Unit> {
            written = level
            return Outcome.Success(Unit)
        }
    }
}
