package com.shnapps.couple.feature.boundaries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.auth.AuthRepository
import com.shnapps.couple.core.data.boundaries.BoundaryRepository
import com.shnapps.couple.core.data.taxonomy.TaxonomyRepository
import com.shnapps.couple.core.model.Boundary
import com.shnapps.couple.core.model.BoundaryLevel
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.Taxonomy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class BoundariesStage { Loading, List, Editing }

/** One theme in the list, with the user's own boundary on it, if any. */
data class ThemeRow(
    val themeId: String,
    val title: String,
    /** What the theme covers, in the taxonomy's own words: "Massage · Texture". */
    val covers: String,
    val boundary: Boundary?,
)

data class BoundarySection(val categoryTitle: String, val themes: List<ThemeRow>)

/** The theme being edited. [level] is what is saved; [note] is the draft on screen. */
data class BoundaryEditor(
    val themeId: String,
    val categoryTitle: String,
    val title: String,
    val covers: String,
    val level: BoundaryLevel?,
    val note: String,
    val noteChanged: Boolean,
) {
    val hasBoundary: Boolean get() = level != null

    /** A note hangs off a level; with none chosen there is nothing to attach it to. */
    val canSaveNote: Boolean get() = noteChanged && level != null
}

data class BoundariesUiState(
    val stage: BoundariesStage = BoundariesStage.Loading,
    val contentLevel: Intensity = Intensity.FLIRTY,
    val sections: List<BoundarySection> = emptyList(),
    /** How many themes carry a boundary. */
    val setCount: Int = 0,
    val editor: BoundaryEditor? = null,
    val errorMessage: String? = null,
) {
    /** Back closes the editor first, and only leaves the screen from the list. */
    val handlesBack: Boolean get() = stage == BoundariesStage.Editing
}

/**
 * The user's private boundaries and their own content level (BUILD_PROMPT.md §3.2, §14.9).
 *
 * Everything here is the user's alone (§5.1). The server combines both partners' settings
 * into filters no client can read (§5.4); this screen never learns anything about a partner's.
 *
 * A level is saved the moment it is tapped — boundaries protect, so a chosen one should take
 * effect without a second step. A note is typed, so it is saved on demand, and on leaving.
 */
@HiltViewModel
class BoundariesViewModel @Inject constructor(
    taxonomyRepository: TaxonomyRepository,
    private val boundaries: BoundaryRepository,
    private val auth: AuthRepository,
) : ViewModel() {

    private data class Inputs(
        val taxonomy: Taxonomy,
        val boundaries: Map<String, Boundary>,
        val contentLevel: Intensity,
    )

    private data class Session(
        val editing: String? = null,
        /** Null until the user types; then the draft, which may differ from what is saved. */
        val noteDraft: String? = null,
        val error: String? = null,
    )

    private val session = MutableStateFlow(Session())

    private val contentLevel = auth.currentProfile
        .map { it?.contentLevel ?: Intensity.FLIRTY }
        .distinctUntilChanged()

    private val inputs: StateFlow<Inputs?> =
        combine(taxonomyRepository.taxonomy, boundaries.boundaries, contentLevel, ::Inputs)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val uiState: StateFlow<BoundariesUiState> =
        combine(inputs, session, ::render)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), BoundariesUiState())

    /** The user's own ceiling. The couple goes as far as the more careful partner, never further. */
    fun setContentLevel(level: Intensity) {
        if (level == inputs.value?.contentLevel) return
        viewModelScope.launch {
            if (auth.setContentLevel(level) is Outcome.Failure) fail(NOT_SAVED)
        }
    }

    fun edit(themeId: String) = session.update { Session(editing = themeId) }

    /** Saves at once, keeping whatever note is on screen. */
    fun selectLevel(level: BoundaryLevel) {
        val themeId = session.value.editing ?: return
        save(themeId, Boundary(level, currentNote(themeId)))
        session.update { it.copy(noteDraft = null) }
    }

    fun onNoteChange(text: String) = session.update { it.copy(noteDraft = text.take(Boundary.MAX_NOTE_LENGTH)) }

    fun saveNote() {
        val themeId = session.value.editing ?: return
        val level = inputs.value?.boundaries?.get(themeId)?.level ?: return
        save(themeId, Boundary(level, currentNote(themeId)))
        session.update { it.copy(noteDraft = null) }
    }

    /** Removes the boundary, and its note with it. */
    fun clearBoundary() {
        val themeId = session.value.editing ?: return
        session.update { Session() }
        viewModelScope.launch {
            if (boundaries.clearBoundary(themeId) is Outcome.Failure) fail(NOT_SAVED)
        }
    }

    /** Closes the editor. A typed note is kept rather than silently thrown away. */
    fun back() {
        val editing = session.value.editing ?: return
        val saved = inputs.value?.boundaries?.get(editing)
        if (saved != null && session.value.noteDraft != null && currentNote(editing) != saved.note) saveNote()
        session.update { Session(error = it.error) }
    }

    fun dismissError() = session.update { it.copy(error = null) }

    private fun currentNote(themeId: String): String? {
        val draft = session.value.noteDraft ?: return inputs.value?.boundaries?.get(themeId)?.note
        return draft.trim().ifEmpty { null }
    }

    private fun save(themeId: String, boundary: Boundary) {
        if (inputs.value?.boundaries?.get(themeId) == boundary) return
        viewModelScope.launch {
            if (boundaries.setBoundary(themeId, boundary) is Outcome.Failure) fail(NOT_SAVED)
        }
    }

    private fun fail(message: String) = session.update { it.copy(error = message) }

    private fun render(inputs: Inputs?, session: Session): BoundariesUiState {
        if (inputs == null) return BoundariesUiState()
        val (taxonomy, saved, level) = inputs

        val sections = taxonomy.categories.map { category ->
            BoundarySection(
                categoryTitle = category.title,
                themes = category.themes.map { theme ->
                    ThemeRow(theme.id, theme.title, theme.items.joinToString(" · ") { it.prompt }, saved[theme.id])
                },
            )
        }

        val editor = session.editing?.let(taxonomy::theme)?.let { theme ->
            val boundary = saved[theme.id]
            val draft = session.noteDraft
            BoundaryEditor(
                themeId = theme.id,
                categoryTitle = taxonomy.categories.first { category -> theme in category.themes }.title,
                title = theme.title,
                covers = theme.items.joinToString(" · ") { it.prompt },
                level = boundary?.level,
                note = draft ?: boundary?.note.orEmpty(),
                noteChanged = draft != null && draft.trim().ifEmpty { null } != boundary?.note,
            )
        }

        return BoundariesUiState(
            stage = if (editor != null) BoundariesStage.Editing else BoundariesStage.List,
            contentLevel = level,
            sections = sections,
            setCount = saved.keys.count { taxonomy.theme(it) != null },
            editor = editor,
            errorMessage = session.error,
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val NOT_SAVED = "That change didn't save. Try again in a moment."
    }
}
