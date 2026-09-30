package com.shnapps.couple.feature.night

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.model.Heat
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.Leadership
import com.shnapps.couple.core.model.Mode
import com.shnapps.couple.core.model.Mood
import com.shnapps.couple.core.model.Novelty
import com.shnapps.couple.core.testing.FakeNightRepository
import com.shnapps.couple.core.testing.FakeTaxonomyRepository
import com.shnapps.couple.core.testing.MainDispatcherRule
import com.shnapps.couple.core.testing.TestNights
import com.shnapps.couple.core.testing.TestTaxonomy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** Build Our Night (BUILD_PROMPT.md §14.5, §14.7). */
@OptIn(ExperimentalCoroutinesApi::class)
class BuildNightViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun test(body: suspend TestScope.() -> Unit) = runTest(mainDispatcherRule.testDispatcher) { body() }

    private val nights = FakeNightRepository()

    private fun TestScope.builder(
        focus: String? = null,
        express: Boolean = false,
        forPartner: Boolean = false,
        apart: Boolean = false,
    ) = BuildNightViewModel(
        savedStateHandle = SavedStateHandle(
            buildMap<String, Any> {
                if (focus != null) put("focus", focus)
                if (express) put("express", true)
                if (forPartner) put("forPartner", true)
                if (apart) put("apart", true)
            },
        ),
        nights = nights,
        taxonomy = FakeTaxonomyRepository(),
    ).also { vm ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
    }

    private val BuildNightViewModel.state get() = uiState.value

    private fun TestScope.act(action: () -> Unit) {
        action()
        advanceUntilIdle()
    }

    @Test
    fun `asks the server for exactly what was chosen`() = test {
        val vm = builder()
        vm.setDuration(45)
        vm.toggleMood(Mood.NAUGHTY)
        vm.toggleMood(Mood.MYSTERIOUS)
        vm.toggleMood(Mood.NAUGHTY)
        vm.setHeat(Heat.WILD)
        vm.build()
        advanceUntilIdle()

        val asked = nights.built.single()
        assertThat(asked.durationMin).isEqualTo(45)
        assertThat(asked.moods).containsExactly(Mood.MYSTERIOUS)
        // A heat is a ceiling: Wild asks for the top of the range, and the server caps it again.
        assertThat(asked.intensity).isEqualTo(Heat.WILD.ceiling)
        // The two dials that used to ask keep the model's defaults, which is what they mostly gave.
        assertThat(asked.leadership).isEqualTo(Leadership.RANDOM)
        assertThat(asked.novelty).isEqualTo(Novelty.MIXED)
        assertThat(vm.state.builtExperienceId).isEqualTo(TestNights.gentle.experienceId)

        vm.onShown()
        advanceUntilIdle()
        assertThat(vm.state.builtExperienceId).isNull()
    }

    @Test
    fun `builds around a revealed match, named in the taxonomy's words`() = test {
        val vm = builder(focus = TestTaxonomy.flirty.id)
        assertThat(vm.state.settings.focus).isEqualTo(TestTaxonomy.flirty.id)
        assertThat(vm.state.focusPrompt).isEqualTo(TestTaxonomy.flirty.prompt)
        vm.build()
        advanceUntilIdle()
        assertThat(nights.built.single().focus).isEqualTo(TestTaxonomy.flirty.id)
    }

    @Test
    fun `tries once more with a fresh seed before saying nothing fits`() = test {
        val none = Outcome.Failure(AppError.Validation("not-enough-content"))
        nights.buildResults = mutableListOf(none, Outcome.Success(TestNights.gentle))
        val vm = builder()
        vm.build()
        advanceUntilIdle()
        assertThat(nights.built).hasSize(2)
        assertThat(vm.state.builtExperienceId).isEqualTo(TestNights.gentle.experienceId)

        nights.buildResults = mutableListOf(none, none)
        vm.build()
        advanceUntilIdle()
        assertThat(vm.state.error).isEqualTo(BuildError.NOT_ENOUGH_CONTENT)
        vm.dismissError()
        advanceUntilIdle()
        assertThat(vm.state.error).isNull()
    }

    @Test
    fun `says plainly when the server is out of reach or asked too often`() = test {
        val vm = builder()
        nights.buildResults = mutableListOf(Outcome.Failure(AppError.Network()))
        vm.build()
        advanceUntilIdle()
        assertThat(vm.state.error).isEqualTo(BuildError.OFFLINE)
        assertThat(nights.built).hasSize(1)

        nights.buildResults = mutableListOf(Outcome.Failure(AppError.RateLimited()))
        vm.build()
        advanceUntilIdle()
        assertThat(vm.state.error).isEqualTo(BuildError.RATE_LIMITED)
    }

    @Test
    fun `knows the couple's shared level, and any night already under way`() = test {
        nights.level.value = Intensity.NAUGHTY
        val vm = builder()
        assertThat(vm.state.sharedLevel).isEqualTo(Intensity.NAUGHTY)
        assertThat(vm.state.activeNight).isNull()
        nights.start(TestNights.gentle.experienceId)
        advanceUntilIdle()
        assertThat(vm.state.activeNight?.experienceId).isEqualTo(TestNights.gentle.experienceId)
    }

    @Test
    fun `how far never goes past what the couple agreed to`() = test {
        nights.level.value = Intensity.NAUGHTY
        val vm = builder()

        act { vm.setHeat(Heat.WILD) }

        // The picker says Wild; what is asked for stops where they said it would (§3.2).
        assertThat(vm.state.settings.intensity).isEqualTo(Intensity.NAUGHTY)
        assertThat(vm.state.heat).isEqualTo(Heat.covering(Intensity.NAUGHTY))
    }

    @Test
    fun `fifteen minutes takes one tap and one choice, and nothing else is asked`() = test {
        nights.level.value = Intensity.NAUGHTY
        val vm = builder(express = true)

        assertThat(vm.state.express).isTrue()
        act { vm.buildExpress(Mood.ROMANTIC) }

        val asked = nights.built.single()
        assertThat(asked.durationMin).isEqualTo(15)
        assertThat(asked.moods).containsExactly(Mood.ROMANTIC)
        // As far as the couple's own level allows, with nobody leading: nothing left to set.
        assertThat(asked.intensity).isEqualTo(Intensity.NAUGHTY)
        assertThat(asked.leadership).isEqualTo(Leadership.RANDOM)
        assertThat(vm.state.builtExperienceId).isEqualTo(nights.night.experienceId)
    }

    @Test
    fun `express with no mood at all leaves the choosing to the engine`() = test {
        val vm = builder(express = true)
        act { vm.buildExpress(null) }
        assertThat(nights.built.single().moods).isEmpty()
    }

    @Test
    fun `planning for the partner asks the server to build it for them, and shows only its shape`() = test {
        val vm = builder(forPartner = true)
        assertThat(vm.state.forPartner).isTrue()

        act { vm.setDuration(60) }
        act(vm::build)

        assertThat(nights.calls).containsExactly("plan")
        assertThat(nights.built.single().durationMin).isEqualTo(60)
        assertThat(vm.state.plannedChapters).isEqualTo(nights.night.chapters.size)
        // Nothing to open here: what was planned is the partner's to find (§2.5 #2).
        assertThat(vm.state.builtExperienceId).isNull()
    }

    @Test
    fun `a night for being apart is asked for as one`() = test {
        val vm = builder(apart = true)
        act(vm::build)
        assertThat(nights.built.single().mode).isEqualTo(Mode.APART)
    }
}
