package com.shnapps.couple.feature.boundaries

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.shnapps.couple.core.designsystem.theme.AfterhoursSurface
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.model.Boundary
import com.shnapps.couple.core.model.BoundaryLevel
import com.shnapps.couple.core.model.Intensity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h840dp-xhdpi")
class BoundariesSnapshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val list = BoundariesUiState(
        stage = BoundariesStage.List,
        contentLevel = Intensity.NAUGHTY,
        setCount = 3,
        sections = listOf(
            BoundarySection(
                categoryTitle = "Power & dynamics",
                themes = listOf(
                    ThemeRow("power_leading", "Leading", "Taking the lead · Directing the evening", null),
                    ThemeRow(
                        "power_yielding",
                        "Letting go",
                        "Letting them decide · Following instructions · Giving up control",
                        Boundary(BoundaryLevel.ASK_FIRST),
                    ),
                    ThemeRow(
                        "power_negotiated",
                        "Negotiated power play",
                        "Negotiated power play",
                        Boundary(BoundaryLevel.NEVER),
                    ),
                ),
            ),
            BoundarySection(
                categoryTitle = "Senses",
                themes = listOf(
                    ThemeRow("sensory_touch", "Touch", "Massage · Texture", Boundary(BoundaryLevel.CURIOUS)),
                    ThemeRow("sensory_blindfold", "Blindfolds", "Blindfolded", Boundary(BoundaryLevel.NOT_TONIGHT)),
                ),
            ),
        ),
    )

    private val editor = BoundariesUiState(
        stage = BoundariesStage.Editing,
        editor = BoundaryEditor(
            themeId = "power_yielding",
            categoryTitle = "Power & dynamics",
            title = "Letting go",
            covers = "Letting them decide · Following instructions · Giving up control",
            level = BoundaryLevel.ASK_FIRST,
            note = "Only after we have talked it through.",
            noteChanged = true,
        ),
    )

    @Test fun list() = capture("boundaries-list", list)

    @Test fun editing() = capture("boundaries-editing", editor)

    @Test fun editingUnset() = capture(
        "boundaries-editing-unset",
        editor.copy(editor = editor.editor!!.copy(level = null, note = "", noteChanged = false)),
    )

    @Test
    fun `each level is announced once, with its consequence, and the saved one is selected`() {
        show(editor)

        composeRule.onNodeWithContentDescription("Ask first", substring = true).assertIsSelected()
        // Cleared and set: in the tree a screen reader walks (the merged one), the visible
        // lines are not read out on top of the description.
        composeRule.onAllNodesWithText(NEVER_CONSEQUENCE).assertCountEquals(0)
        composeRule.onNode(hasContentDescription("Never. Removed completely", substring = true)).assertExists()
    }

    private companion object {
        const val NEVER_CONSEQUENCE = "Removed completely, for both of you. Nothing overrides this."
    }

    private fun capture(name: String, state: BoundariesUiState) {
        show(state)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun show(state: BoundariesUiState) {
        composeRule.setContent {
            AfterhoursTheme {
                AfterhoursSurface {
                    BoundariesContent(
                        state = state,
                        onLevelChange = {}, onEdit = {}, onSelectLevel = {}, onNoteChange = {},
                        onSaveNote = {}, onClear = {}, onCloseEditor = {}, onDismissError = {}, onBack = {},
                    )
                }
            }
        }
    }
}
