package com.shnapps.couple.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.content.ContentRepository
import com.shnapps.couple.core.designsystem.component.CinematicCard
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.model.ContentItem
import com.shnapps.couple.core.model.ContentPack
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.Mode
import com.shnapps.couple.core.ui.BackRow
import com.shnapps.couple.core.ui.ScreenHeading
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The content inspector: debug builds only (DECISIONS.md D-042).
 *
 * Shows every published item on the device, straight from the cache, so a developer can
 * check that content installed, updated and was disabled as expected — offline included.
 * A product build must never list content like this: what a couple sees is chosen by the
 * engine behind their boundaries (BUILD_PROMPT.md §3.2), so this screen has no release twin.
 */
const val CONTENT_INSPECTOR_AVAILABLE = true

data class ContentInspectorUiState(
    val loading: Boolean = true,
    val contentVersion: Int = 0,
    val totalItems: Int = 0,
    val deltaCount: Int = 0,
    val lastSyncMillis: Long? = null,
    val packs: List<ContentPack> = emptyList(),
    val pack: String? = null,
    val mode: Mode? = null,
    val intensity: Intensity? = null,
    val items: List<ContentItem> = emptyList(),
    val syncing: Boolean = false,
    val syncResult: String? = null,
)

private data class InspectorFilters(val pack: String? = null, val mode: Mode? = null, val intensity: Intensity? = null)

private data class SyncStatus(val running: Boolean = false, val result: String? = null)

@HiltViewModel
class ContentInspectorViewModel @Inject constructor(
    private val repository: ContentRepository,
) : ViewModel() {

    private val filters = MutableStateFlow(InspectorFilters())
    private val sync = MutableStateFlow(SyncStatus())

    val uiState: StateFlow<ContentInspectorUiState> = combine(
        combine(repository.items, repository.packs, repository.state, ::Triple),
        filters,
        sync,
    ) { (items, packs, state), filter, status ->
        ContentInspectorUiState(
            loading = false,
            contentVersion = state.contentVersion,
            totalItems = items.size,
            deltaCount = state.deltaCount,
            lastSyncMillis = state.lastSyncMillis,
            packs = packs,
            pack = filter.pack,
            mode = filter.mode,
            intensity = filter.intensity,
            items = items.filter { item ->
                (filter.pack == null || item.pack == filter.pack) &&
                    (filter.mode == null || filter.mode in item.modes) &&
                    (filter.intensity == null || item.intensity == filter.intensity)
            },
            syncing = status.running,
            syncResult = status.result,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ContentInspectorUiState())

    /** Selecting the active filter again clears it. */
    fun selectPack(pack: String?) = filters.update { current ->
        current.copy(pack = if (current.pack == pack) null else pack)
    }

    fun selectMode(mode: Mode?) = filters.update { current ->
        current.copy(mode = if (current.mode == mode) null else mode)
    }

    fun selectIntensity(intensity: Intensity?) = filters.update { current ->
        current.copy(intensity = if (current.intensity == intensity) null else intensity)
    }

    fun syncNow() {
        if (sync.value.running) return
        sync.value = SyncStatus(running = true)
        viewModelScope.launch {
            val result = when (val outcome = repository.sync()) {
                is Outcome.Success -> "installed ${outcome.value.installedVersion ?: "nothing new"}, " +
                    "${outcome.value.deltasReceived} delta(s)"
                is Outcome.Failure -> "failed: ${outcome.error::class.simpleName}"
            }
            sync.value = SyncStatus(running = false, result = result)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

@Composable
fun ContentInspectorScreen(onBack: () -> Unit, viewModel: ContentInspectorViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ContentInspectorContent(
        state = state,
        onBack = onBack,
        onPack = viewModel::selectPack,
        onMode = viewModel::selectMode,
        onIntensity = viewModel::selectIntensity,
        onSync = viewModel::syncNow,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("LongParameterList") // One callback per filter keeps the content stateless.
internal fun ContentInspectorContent(
    state: ContentInspectorUiState,
    onBack: () -> Unit,
    onPack: (String?) -> Unit,
    onMode: (Mode?) -> Unit,
    onIntensity: (Intensity?) -> Unit,
    onSync: () -> Unit,
) {
    val spacing = AfterhoursTheme.spacing
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = spacing.gutter, vertical = spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        item { BackRow(label = "Back", onBack = onBack) }
        item {
            ScreenHeading(
                eyebrow = "Debug",
                headline = "Content",
                body = "v${state.contentVersion} · ${state.totalItems} items · ${state.deltaCount} deltas · " +
                    (state.lastSyncMillis?.let { "synced ${java.util.Date(it)}" } ?: "never synced"),
            )
        }
        item {
            GlowButton(
                text = if (state.syncing) "Syncing…" else "Sync now",
                onClick = onSync,
                style = GlowButtonStyle.Secondary,
                enabled = !state.syncing,
            )
        }
        state.syncResult?.let { result ->
            item { Text(result, style = MaterialTheme.typography.bodySmall, color = AfterhoursTheme.colors.textMuted) }
        }
        item {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                state.packs.forEach { pack ->
                    FilterChip(
                        selected = state.pack == pack.id,
                        onClick = { onPack(pack.id) },
                        label = { Text(pack.title) },
                    )
                }
                Mode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.mode == mode,
                        onClick = { onMode(mode) },
                        label = { Text(mode.name.lowercase()) },
                    )
                }
                Intensity.entries.forEach { level ->
                    FilterChip(
                        selected = state.intensity == level,
                        onClick = { onIntensity(level) },
                        label = { Text("${level.level}") },
                    )
                }
            }
        }
        item {
            Text(
                text = "${state.items.size} shown",
                style = MaterialTheme.typography.labelLarge,
                color = AfterhoursTheme.colors.textMuted,
            )
        }
        items(state.items, key = { it.id }) { item -> InspectorItem(item) }
    }
}

@Composable
private fun InspectorItem(item: ContentItem) {
    CinematicCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.xs)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium)
            Text(item.subtitle, style = MaterialTheme.typography.bodyMedium, color = AfterhoursTheme.colors.textMuted)
            Text(item.body, style = MaterialTheme.typography.bodyMedium)
            val modes = item.modes.joinToString("/") { it.name.lowercase() }
            Text(
                text = "${item.id} · ${item.intensity.level} · $modes · " +
                    "${item.interactionType.name.lowercase()} · ${item.durationMin} min · " +
                    item.chapterKinds.joinToString { it.name.lowercase() },
                style = MaterialTheme.typography.labelSmall,
                color = AfterhoursTheme.colors.textFaint,
            )
        }
    }
}
