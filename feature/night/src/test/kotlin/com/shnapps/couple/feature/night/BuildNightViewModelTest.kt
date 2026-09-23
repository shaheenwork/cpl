package com.shnapps.couple.feature.night

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.Leadership
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

    private fun TestScope.builder(focus: String? = null, express: Boolean = false) = BuildNightViewModel(
        savedStateHandle = SavedStateHandle(
            buildMap<String, Any> {
                if (focus != null) put("focus", focus)
                if (express) put("express", true)
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
        vm.setIntensity(Intensity.NAUGHTY)
        vm.setLeadership(Leadership.SWITCH)
        vm.setNovelty(Novelty.SURPRISE_ME)
        vm.build()
        advanceUntilIdle()

        val asked = nights.built.single()
        assertThat(asked.durationMin).isEqualTo(45)
        assertThat(asked.moods).containsExactly(Mood.MYSTERIOUS)
        assertThat(asked.intensity).isEqualTo(Intensity.NAUGHTY)
        assertThat(asked.leadership).isEqualTo(Leadership.SWITCH)
        assertThat(asked.novelty).isEqualTo(Novelty.SURPRISE_ME)
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
}
