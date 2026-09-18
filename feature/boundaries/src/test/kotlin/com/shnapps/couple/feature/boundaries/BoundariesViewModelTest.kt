package com.shnapps.couple.feature.boundaries

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.model.Boundary
import com.shnapps.couple.core.model.BoundaryLevel
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.UserProfile
import com.shnapps.couple.core.testing.FakeAuthRepository
import com.shnapps.couple.core.testing.FakeBoundaryRepository
import com.shnapps.couple.core.testing.FakeTaxonomyRepository
import com.shnapps.couple.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * The boundaries screen (BUILD_PROMPT.md §3.2). The fixture taxonomy has four themes:
 * mood_gentle and mood_charged under Mood, power_leading and power_yielding under Power.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BoundariesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository().apply {
        user.value = FakeAuthRepository.DEFAULT_USER
        profile.value = UserProfile(uid = FakeAuthRepository.DEFAULT_USER.uid, contentLevel = Intensity.FLIRTY)
    }
    private var boundaries = FakeBoundaryRepository()

    private fun TestScope.viewModel(): BoundariesViewModel =
        BoundariesViewModel(FakeTaxonomyRepository(), boundaries, auth).also { vm ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
            advanceUntilIdle()
        }

    private fun TestScope.act(action: () -> Unit) {
        action()
        advanceUntilIdle()
    }

    private val BoundariesViewModel.state get() = uiState.value

    @Test
    fun `lists every theme by category, unset until the user chooses`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()

        assertThat(vm.state.stage).isEqualTo(BoundariesStage.List)
        assertThat(vm.state.sections.map { it.categoryTitle }).containsExactly("Mood", "Power & dynamics").inOrder()
        assertThat(vm.state.sections.flatMap { it.themes }.map { it.themeId })
            .containsExactly("mood_gentle", "mood_charged", "power_leading", "power_yielding").inOrder()
        assertThat(vm.state.sections.flatMap { it.themes }.all { it.boundary == null }).isTrue()
        assertThat(vm.state.setCount).isEqualTo(0)
    }

    @Test
    fun `says what each theme covers, in the taxonomy's words`() = runTest(mainDispatcherRule.testDispatcher) {
        val gentle = viewModel().state.sections.first().themes.first()

        assertThat(gentle.covers).isEqualTo("Romantic nights · Playful")
    }

    @Test
    fun `a chosen level is saved at once`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        act { vm.edit("power_yielding") }
        act { vm.selectLevel(BoundaryLevel.NEVER) }

        assertThat(boundaries.writes).containsExactly("power_yielding" to Boundary(BoundaryLevel.NEVER))
        assertThat(vm.state.editor?.level).isEqualTo(BoundaryLevel.NEVER)
        assertThat(vm.state.setCount).isEqualTo(1)
    }

    @Test
    fun `an editor opens on the saved level and note`() = runTest(mainDispatcherRule.testDispatcher) {
        boundaries = FakeBoundaryRepository(mapOf("mood_charged" to Boundary(BoundaryLevel.ASK_FIRST, "Talk first")))
        val vm = viewModel()
        act { vm.edit("mood_charged") }

        val editor = vm.state.editor!!
        assertThat(vm.state.stage).isEqualTo(BoundariesStage.Editing)
        assertThat(editor.categoryTitle).isEqualTo("Mood")
        assertThat(editor.level).isEqualTo(BoundaryLevel.ASK_FIRST)
        assertThat(editor.note).isEqualTo("Talk first")
        assertThat(editor.noteChanged).isFalse()
    }

    @Test
    fun `a note is saved with its level, trimmed`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        act { vm.edit("mood_charged") }
        act { vm.selectLevel(BoundaryLevel.ASK_FIRST) }
        act { vm.onNoteChange("  Only after we have talked.  ") }

        assertThat(vm.state.editor?.canSaveNote).isTrue()
        act { vm.saveNote() }

        assertThat(boundaries.boundaries.value["mood_charged"])
            .isEqualTo(Boundary(BoundaryLevel.ASK_FIRST, "Only after we have talked."))
        assertThat(vm.state.editor?.noteChanged).isFalse()
    }

    @Test
    fun `a note cannot be saved without a level to hang it on`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        act { vm.edit("mood_charged") }
        act { vm.onNoteChange("A thought") }

        assertThat(vm.state.editor?.canSaveNote).isFalse()
        act { vm.saveNote() }
        assertThat(boundaries.writes).isEmpty()
    }

    @Test
    fun `changing the level keeps the note that is on screen`() = runTest(mainDispatcherRule.testDispatcher) {
        boundaries = FakeBoundaryRepository(mapOf("mood_charged" to Boundary(BoundaryLevel.CURIOUS, "Slowly")))
        val vm = viewModel()
        act { vm.edit("mood_charged") }
        act { vm.selectLevel(BoundaryLevel.ASK_FIRST) }

        assertThat(boundaries.boundaries.value["mood_charged"]).isEqualTo(Boundary(BoundaryLevel.ASK_FIRST, "Slowly"))
    }

    @Test
    fun `leaving the editor keeps a typed note rather than losing it`() = runTest(mainDispatcherRule.testDispatcher) {
        boundaries = FakeBoundaryRepository(mapOf("mood_charged" to Boundary(BoundaryLevel.CURIOUS)))
        val vm = viewModel()
        act { vm.edit("mood_charged") }
        act { vm.onNoteChange("Weekends only") }
        act { vm.back() }

        assertThat(vm.state.stage).isEqualTo(BoundariesStage.List)
        assertThat(boundaries.boundaries.value["mood_charged"]?.note).isEqualTo("Weekends only")
    }

    @Test
    fun `picking the level already saved writes nothing`() = runTest(mainDispatcherRule.testDispatcher) {
        boundaries = FakeBoundaryRepository(mapOf("mood_charged" to Boundary(BoundaryLevel.NEVER)))
        val vm = viewModel()
        act { vm.edit("mood_charged") }
        act { vm.selectLevel(BoundaryLevel.NEVER) }

        assertThat(boundaries.writes).isEmpty()
    }

    @Test
    fun `clearing removes the boundary and returns to the list`() = runTest(mainDispatcherRule.testDispatcher) {
        boundaries = FakeBoundaryRepository(mapOf("power_leading" to Boundary(BoundaryLevel.NOT_TONIGHT, "tired")))
        val vm = viewModel()
        act { vm.edit("power_leading") }
        act { vm.clearBoundary() }

        assertThat(boundaries.clears).containsExactly("power_leading")
        assertThat(vm.state.stage).isEqualTo(BoundariesStage.List)
        assertThat(vm.state.setCount).isEqualTo(0)
    }

    @Test
    fun `the content level is the user's own, and changes on request`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        assertThat(vm.state.contentLevel).isEqualTo(Intensity.FLIRTY)

        act { vm.setContentLevel(Intensity.BOLD) }

        assertThat(vm.state.contentLevel).isEqualTo(Intensity.BOLD)
    }

    @Test
    fun `a refused save says so`() = runTest(mainDispatcherRule.testDispatcher) {
        boundaries.setResult = Outcome.Failure(AppError.PermissionDenied())
        val vm = viewModel()
        act { vm.edit("mood_gentle") }
        act { vm.selectLevel(BoundaryLevel.CURIOUS) }

        assertThat(vm.state.errorMessage).isNotNull()
        act { vm.dismissError() }
        assertThat(vm.state.errorMessage).isNull()
    }

    @Test
    fun `back is handled inside the screen only while editing`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        assertThat(vm.state.handlesBack).isFalse()

        act { vm.edit("mood_gentle") }
        assertThat(vm.state.handlesBack).isTrue()
    }
}
