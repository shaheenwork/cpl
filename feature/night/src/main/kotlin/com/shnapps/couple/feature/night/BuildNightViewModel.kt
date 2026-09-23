package com.shnapps.couple.feature.night

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.night.DefaultNightRepository
import com.shnapps.couple.core.data.night.NightRepository
import com.shnapps.couple.core.data.taxonomy.TaxonomyRepository
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.Leadership
import com.shnapps.couple.core.model.Mood
import com.shnapps.couple.core.model.NightSession
import com.shnapps.couple.core.model.NightSettings
import com.shnapps.couple.core.model.Novelty
import com.shnapps.couple.core.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Why a night could not be built, in words the screen can say. */
enum class BuildError { NOT_ENOUGH_CONTENT, RATE_LIMITED, OFFLINE, OUT_OF_DATE, OTHER }

data class BuildNightUiState(
    val settings: NightSettings = NightSettings(),
    /** The couple's shared level: the most the dial can reach. Null until known. */
    val sharedLevel: Intensity? = null,
    /** The match the night is built around, in the taxonomy's words (§14.7). */
    val focusPrompt: String? = null,
    /** "We have 15 minutes" (§14.5): one tap, one choice, and nothing else to set. */
    val express: Boolean = false,
    /** A night already under way for this couple: there is one at a time (D-059). */
    val activeNight: NightSession? = null,
    val building: Boolean = false,
    /** Set once a night is built: the screen moves on to it and calls [BuildNightViewModel.onShown]. */
    val builtExperienceId: String? = null,
    val error: BuildError? = null,
)

/**
 * Build Our Night (BUILD_PROMPT.md §14.5): time, mood, intensity, who leads and novelty —
 * then the server builds the night behind both partners' boundaries (§10). Nothing here
 * decides what the couple may see; the dial only shows how far their shared level reaches.
 */
@HiltViewModel
class BuildNightViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val nights: NightRepository,
    taxonomy: TaxonomyRepository,
) : ViewModel() {

    // Type-safe routes keep each argument under its property's name (Route.BuildNight).
    private val focus: String? = savedStateHandle.get<String>(Route.BuildNight::focus.name)
    private val express: Boolean = savedStateHandle.get<Boolean>(Route.BuildNight::express.name) ?: false

    private val local = MutableStateFlow(
        BuildNightUiState(
            express = express,
            // Fifteen minutes, nobody leading, and a mix: the only thing left to say is the mood.
            settings = if (express) {
                NightSettings(durationMin = EXPRESS_MIN, leadership = Leadership.RANDOM, focus = focus)
            } else {
                NightSettings(focus = focus)
            },
        ),
    )

    val uiState: StateFlow<BuildNightUiState> = combine(
        local,
        nights.sharedLevel,
        nights.activeSession,
        taxonomy.taxonomy.map { focus?.let(it::item)?.prompt },
    ) { state, shared, active, prompt ->
        // Express never asks how far: it goes as far as the couple's own level allows.
        val settings = if (express && shared != null) state.settings.copy(intensity = shared) else state.settings
        state.copy(sharedLevel = shared, activeNight = active, focusPrompt = prompt, settings = settings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), local.value)

    fun setDuration(minutes: Int) = edit { copy(durationMin = minutes) }

    fun toggleMood(mood: Mood) = edit { copy(moods = if (mood in moods) moods - mood else moods + mood) }

    fun setIntensity(intensity: Intensity) = edit { copy(intensity = intensity) }

    fun setLeadership(leadership: Leadership) = edit { copy(leadership = leadership) }

    fun setNovelty(novelty: Novelty) = edit { copy(novelty = novelty) }

    fun build() {
        if (local.value.building) return
        local.update { it.copy(building = true, error = null) }
        viewModelScope.launch {
            val settings = local.value.settings
            var result = nights.build(settings)
            // A night that finds nothing gets one more try, with a fresh seed from the server,
            // before the couple is asked to change anything (DECISIONS.md D-058).
            if (result.isNotEnoughContent()) result = nights.build(settings)
            local.update { state ->
                when (result) {
                    is Outcome.Success -> state.copy(building = false, builtExperienceId = result.value.experienceId)
                    is Outcome.Failure -> state.copy(building = false, error = result.error.toBuildError())
                }
            }
        }
    }

    /**
     * "We have 15 minutes" (§14.5): the one choice express asks for, and the night at once.
     * [mood] is null for "surprise me", which leaves the engine to rank on everything else.
     */
    fun buildExpress(mood: Mood?) {
        // The couple's own level, not a dial: express asks for a mood and nothing else.
        val ceiling = uiState.value.sharedLevel
        local.update { state ->
            val settings = state.settings.copy(moods = setOfNotNull(mood))
            state.copy(settings = if (ceiling != null) settings.copy(intensity = ceiling) else settings)
        }
        build()
    }

    /** The screen has moved on to the built night. */
    fun onShown() = local.update { it.copy(builtExperienceId = null) }

    fun dismissError() = local.update { it.copy(error = null) }

    private fun edit(change: NightSettings.() -> NightSettings) =
        local.update { it.copy(settings = it.settings.change()) }

    private fun Outcome<*>.isNotEnoughContent(): Boolean =
        this is Outcome.Failure && (error as? AppError.Validation)?.code == NOT_ENOUGH_CONTENT

    private fun AppError.toBuildError(): BuildError = when {
        this is AppError.Validation && code == NOT_ENOUGH_CONTENT -> BuildError.NOT_ENOUGH_CONTENT
        this is AppError.Validation && code == DefaultNightRepository.CONTENT_OUT_OF_DATE -> BuildError.OUT_OF_DATE
        this is AppError.RateLimited -> BuildError.RATE_LIMITED
        this is AppError.Network -> BuildError.OFFLINE
        else -> BuildError.OTHER
    }

    private companion object {
        /** "We have 15 minutes" means fifteen minutes. */
        const val EXPRESS_MIN = 15
        const val STOP_TIMEOUT_MS = 5_000L
        const val NOT_ENOUGH_CONTENT = "not-enough-content"
    }
}
