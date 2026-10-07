package com.shnapps.couple.feature.chat

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.component.raised
import com.shnapps.couple.core.designsystem.icon.AfterhoursIcons
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.model.MediaKind
import com.shnapps.couple.core.model.Message
import com.shnapps.couple.core.model.MessageKind
import com.shnapps.couple.core.ui.ErrorBanner
import com.shnapps.couple.core.ui.SecureScreen
import com.shnapps.couple.core.ui.voice.LocalVoiceRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The conversation, over whatever else is on screen (CHAT_PROMPT.md).
 *
 * Hosted by `:app` **above** the nav host rather than as a route. A route would move the back
 * stack and take the player's ViewModel out of the composition with it — the run, the round, the
 * answer in flight — and the whole point of the chat is that the game does not notice it.
 *
 * It takes the back press while it is open, which also resets the panic counter: two backs mean
 * panic, and the first of them closing a chat must not count as the first of two (M8).
 */
@Composable
fun ChatLayer(modifier: Modifier = Modifier, viewModel: ChatViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (!state.paired) return

    Box(modifier = modifier.fillMaxSize()) {
        // Closed: one small thing in the corner, with a dot when they have said something. It is
        // never the primary action on any screen and never takes the heat accent.
        AnimatedVisibility(
            visible = !state.open,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd).padding(AfterhoursTheme.spacing.sm),
        ) {
            ChatAffordance(unread = state.unread, onOpen = viewModel::open)
        }

        AnimatedVisibility(
            visible = state.open,
            enter = fadeIn() + slideInVertically { it / SLIDE },
            exit = fadeOut() + slideOutVertically { it / SLIDE },
        ) {
            SecureScreen()
            // Back closes the chat and goes no further. Two backs mean panic (M8), and the one
            // that shut a chat must not count as the first of them.
            BackHandler(enabled = true) { viewModel.close() }
            ChatContent(
                state = state,
                actions = ChatActions(
                    onClose = viewModel::close,
                    onDraft = viewModel::draft,
                    onSay = viewModel::say,
                    onSend = { kind, file -> viewModel.send(kind, file) },
                    onRecording = viewModel::recording,
                    onDismissError = viewModel::dismissError,
                    onStop = viewModel::stop,
                ),
            )
        }
    }
}

/** The way in: a quiet pill, and a dot when there is something to read. */
@Composable
private fun ChatAffordance(unread: Int, onOpen: () -> Unit) {
    Box {
        IconButton(
            onClick = onOpen,
            modifier = Modifier
                .clip(MaterialTheme.shapes.extraLarge)
                .raised(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.extraLarge,
                ),
        ) {
            Icon(
                imageVector = AfterhoursIcons.Message,
                contentDescription = if (unread > 0) "Chat, $unread unread" else "Chat",
                tint = AfterhoursTheme.colors.textMuted,
                modifier = Modifier.size(ICON),
            )
        }
        if (unread > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(AfterhoursTheme.spacing.xs)
                    .size(DOT)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(AfterhoursTheme.tier.accent),
            )
        }
    }
}

/** Everything the overlay can do, gathered so the content stays easy to preview. */
internal data class ChatActions(
    val onClose: () -> Unit = {},
    val onDraft: (String) -> Unit = {},
    val onSay: () -> Unit = {},
    val onSend: (MediaKind, File) -> Unit = { _, _ -> },
    val onRecording: (Boolean) -> Unit = {},
    val onDismissError: () -> Unit = {},
    val onStop: () -> Unit = {},
)

@Composable
internal fun ChatContent(state: ChatUiState, actions: ChatActions, modifier: Modifier = Modifier) {
    val spacing = AfterhoursTheme.spacing
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = spacing.gutter),
    ) {
        ChatBar(onClose = actions.onClose, onStop = actions.onStop)

        val listState = rememberLazyListState()
        // Always on the newest: a conversation opens where it left off, not at its beginning.
        LaunchedEffect(state.messages.size) {
            if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (state.messages.isEmpty()) {
                Nothing()
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.messages, key = { it.id }) { message -> Said(message) }
                }
            }
        }

        state.error?.let { ErrorBanner(message = it.message(), onDismiss = actions.onDismissError) }
        Composer(state = state, actions = actions)
    }
}

@Composable
private fun ChatBar(onClose: () -> Unit, onStop: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = AfterhoursTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = AfterhoursIcons.Close,
                contentDescription = "Close the chat",
                tint = AfterhoursTheme.colors.textMuted,
            )
        }
        Text(
            text = "THE TWO OF YOU",
            style = EyebrowTextStyle,
            color = AfterhoursTheme.colors.textFaint,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        // The safe word is on every screen that covers one that is playing (M7).
        GlowButton(text = "Stop", onClick = onStop, style = GlowButtonStyle.Quiet)
    }
}

/** One thing that was said: theirs on the left, yours on the right, and nothing else said about it. */
@Composable
private fun Said(message: Message) {
    val spacing = AfterhoursTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.mine) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(BUBBLE)
                .clip(MaterialTheme.shapes.medium)
                .raised(
                    color = if (message.mine) {
                        AfterhoursTheme.tier.container
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    shape = MaterialTheme.shapes.medium,
                )
                .padding(spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            if (message.kind != MessageKind.TEXT) {
                Text(
                    text = if (message.kind == MessageKind.VOICE) "A voice note" else "A photo",
                    style = EyebrowTextStyle,
                    color = AfterhoursTheme.colors.brass,
                )
            }
            message.text?.takeIf { it.isNotBlank() }?.let { words ->
                Text(
                    text = words,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (message.pending) {
                Text(
                    text = "Sending…",
                    style = MaterialTheme.typography.bodySmall,
                    color = AfterhoursTheme.colors.textFaint,
                )
            }
        }
    }
}

@Composable
private fun Nothing() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Nothing here yet.",
            style = MaterialTheme.typography.bodyLarge,
            color = AfterhoursTheme.colors.textMuted,
        )
    }
}

@Composable
private fun Composer(state: ChatUiState, actions: ChatActions) {
    val spacing = AfterhoursTheme.spacing
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = LocalVoiceRecorder.current

    // The phone's own picker: no permission, and nothing of the gallery is read but the one file
    // they chose, which is copied into this app's cache on the way to being sent.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            copyIn(context, uri)?.let { actions.onSend(MediaKind.PHOTO, it) }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            enabled = !state.recording && !state.sending,
        ) {
            Icon(
                imageVector = AfterhoursIcons.Camera,
                contentDescription = "Send a photo",
                tint = AfterhoursTheme.colors.textMuted,
                modifier = Modifier.size(ICON),
            )
        }
        // Press to start, press again to send: a voice note here is a message, not a round's
        // answer, so there is no "done" step between the two.
        IconButton(
            onClick = {
                if (state.recording) {
                    actions.onRecording(false)
                    (recorder?.stop() as? Outcome.Success)?.let { actions.onSend(MediaKind.VOICE, it.value) }
                } else if (recorder?.start() is Outcome.Success) {
                    actions.onRecording(true)
                }
            },
            enabled = !state.sending,
        ) {
            Icon(
                imageVector = AfterhoursIcons.Mic,
                contentDescription = if (state.recording) "Send the voice note" else "Record a voice note",
                tint = if (state.recording) AfterhoursTheme.tier.accent else AfterhoursTheme.colors.textMuted,
                modifier = Modifier.size(ICON),
            )
        }
        OutlinedTextField(
            value = state.draft,
            onValueChange = actions.onDraft,
            placeholder = { Text("Say something") },
            maxLines = COMPOSER_LINES,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = actions.onSay, enabled = state.canSend) {
            Icon(
                imageVector = AfterhoursIcons.Send,
                contentDescription = "Send",
                tint = if (state.canSend) AfterhoursTheme.tier.accent else AfterhoursTheme.colors.textFaint,
                modifier = Modifier.size(ICON),
            )
        }
    }
}

/**
 * The chosen picture, copied into this app's own cache.
 *
 * The picker hands back a URI this app may read once; everything downstream — the upload, the
 * retry, the record — needs a file it owns. Nothing is written to the gallery and nothing of the
 * gallery is read but this.
 */
private suspend fun copyIn(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
    runCatching {
        val file = File.createTempFile("chat_", ".jpg", context.cacheDir)
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use(input::copyTo)
        } ?: return@runCatching null
        file
    }.getOrNull()
}

private fun ChatError.message(): String = when (this) {
    ChatError.OFFLINE -> "You're offline. It'll send when you're back."
    ChatError.OTHER -> "That didn't send. Try again."
}

/** How far the panel slides in from: a fraction of its own height, not the screen's. */
private const val SLIDE = 6

/** A bubble never runs the full width: the side it is on is how you tell whose it is. */
private const val BUBBLE = 0.82f

private const val COMPOSER_LINES = 4
private val ICON = 24.dp

/** The unread dot: a mark, not a count — the number is for the screen reader. */
private val DOT = 8.dp
