package com.shnapps.couple.feature.send

import android.Manifest
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shnapps.couple.core.designsystem.component.CinematicCard
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.model.MediaItem
import com.shnapps.couple.core.model.MediaKind
import com.shnapps.couple.core.ui.BackRow
import com.shnapps.couple.core.ui.ErrorBanner
import com.shnapps.couple.core.ui.ScreenColumn
import com.shnapps.couple.core.ui.ScreenHeading
import com.shnapps.couple.core.ui.SecureScreen
import java.io.File

/**
 * Send something (BUILD_PROMPT.md §13): a voice note or a photo, and what they have sent back.
 *
 * It is private the whole way: what is recorded stays in this app's cache until it is sent, the
 * bytes go to the couple's own Storage under a path the server named, and nothing here ever holds
 * a URL. A burn-after-view item is deleted by the server as it is opened (§5.8, D-083).
 */
@Composable
fun SendScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SendViewModel = hiltViewModel(),
) {
    SecureScreen()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startRecording()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        // Copied into this app's own cache at once: what was picked stays where it was.
        uri?.let { picked ->
            val target = File.createTempFile("photo_", ".jpg", context.cacheDir)
            context.contentResolver.openInputStream(picked)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            viewModel.photoPicked(target)
        }
    }

    SendContent(
        state = state,
        actions = SendActions(
            onRecord = { microphone.launch(Manifest.permission.RECORD_AUDIO) },
            onStopRecording = viewModel::stopRecording,
            onPickPhoto = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onNote = viewModel::setNote,
            onBurn = viewModel::setBurnAfterView,
            onSend = viewModel::send,
            onOpen = { item -> viewModel.open(item, File.createTempFile("open_", ".bin", context.cacheDir)) },
            onClose = viewModel::close,
            onDismissSent = viewModel::dismissSent,
            onDismissError = viewModel::dismissError,
            onBack = onBack,
        ),
        modifier = modifier,
    )
}

/** Everything this screen can do, gathered so the content stays easy to preview. */
internal data class SendActions(
    val onRecord: () -> Unit = {},
    val onStopRecording: () -> Unit = {},
    val onPickPhoto: () -> Unit = {},
    val onNote: (String) -> Unit = {},
    val onBurn: (Boolean) -> Unit = {},
    val onSend: () -> Unit = {},
    val onOpen: (MediaItem) -> Unit = {},
    val onClose: () -> Unit = {},
    val onDismissSent: () -> Unit = {},
    val onDismissError: () -> Unit = {},
    val onBack: () -> Unit = {},
)

@Composable
internal fun SendContent(state: SendUiState, actions: SendActions, modifier: Modifier = Modifier) {
    val open = state.open
    if (open != null) {
        OpenContent(open, actions, modifier)
        return
    }
    Column(modifier = modifier) {
        ScreenColumn {
            BackRow(label = "Back", onBack = actions.onBack)
            ScreenHeading(
                eyebrow = "Send something",
                headline = "Say it out loud, or show them.",
                body = "It goes to the two of you and nowhere else. Nothing leaves this phone until you send it.",
            )
            state.error?.let { ErrorBanner(message = it.message(), onDismiss = actions.onDismissError) }
            if (state.sent) {
                CinematicCard(modifier = Modifier.fillMaxWidth(), glowing = true) {
                    Text(text = "SENT", style = EyebrowTextStyle, color = AfterhoursTheme.colors.brass)
                    Text(
                        text = "It's with them.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                GlowButton(
                    text = "Send another",
                    onClick = actions.onDismissSent,
                    style = GlowButtonStyle.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Maker(state, actions)
            }
            Inbox(state, actions)
        }
    }
}

/** Recording, or picking: one thing at a time, and nothing sent by accident. */
@Composable
private fun Maker(state: SendUiState, actions: SendActions) {
    Column(verticalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm)) {
        if (state.recording) {
            GlowButton(
                text = "Stop recording",
                onClick = actions.onStopRecording,
                leadingEmoji = "⏹",
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            GlowButton(
                text = if (state.ready != null && state.readyKind == MediaKind.VOICE) {
                    "Record it again"
                } else {
                    "Record a voice note"
                },
                onClick = actions.onRecord,
                leadingEmoji = "🎙",
                modifier = Modifier.fillMaxWidth(),
            )
            GlowButton(
                text = if (state.ready != null && state.readyKind == MediaKind.PHOTO) {
                    "Pick another"
                } else {
                    "Send a photo"
                },
                onClick = actions.onPickPhoto,
                style = GlowButtonStyle.Secondary,
                leadingEmoji = "📸",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (state.ready != null) {
        OutlinedTextField(
            value = state.note,
            onValueChange = actions.onNote,
            label = { Text("A line with it (optional)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.fillMaxWidth(BURN_LABEL_WIDTH)) {
                Text(
                    text = "Gone after they open it",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Deleted for good — not hidden.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AfterhoursTheme.colors.textMuted,
                )
            }
            Switch(checked = state.burnAfterView, onCheckedChange = actions.onBurn)
        }
        GlowButton(
            text = if (state.sending) "Sending…" else "Send it",
            onClick = actions.onSend,
            enabled = state.canSend,
            leadingEmoji = "💌",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** What the two of them have sent each other, newest first. */
@Composable
private fun Inbox(state: SendUiState, actions: SendActions) {
    if (state.inbox.isEmpty()) return
    Text(text = "BETWEEN YOU", style = EyebrowTextStyle, color = AfterhoursTheme.colors.textMuted)
    Column(verticalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm)) {
        for (item in state.inbox) {
            val what = if (item.kind == MediaKind.VOICE) "A voice note" else "A photo"
            val line = when {
                item.mine && item.viewedAtMillis != null -> "$what you sent. They've opened it."
                item.mine -> "$what you sent."
                item.unopened -> "$what from them. Not opened yet."
                else -> "$what from them."
            }
            CinematicCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { actions.onOpen(item) },
                glowing = item.unopened,
                contentDescription = "$line ${item.note.orEmpty()}",
            ) {
                if (item.burnAfterView) {
                    Text(text = "🔥 ONCE ONLY", style = EyebrowTextStyle, color = AfterhoursTheme.colors.brass)
                }
                Text(
                    text = line,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                item.note?.let { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AfterhoursTheme.colors.textMuted,
                    )
                }
            }
        }
    }
}

/** What is open: the photo itself, or a voice note ready to play. */
@Composable
private fun OpenContent(open: OpenItem, actions: SendActions, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        ScreenColumn {
            BackRow(label = "Back", onBack = actions.onClose)
            ScreenHeading(
                eyebrow = if (open.burned) "Once only" else "From them",
                headline = open.item.note ?: if (open.item.kind == MediaKind.VOICE) "Listen." else "Look.",
                body = if (open.burned) "This one is gone now — for both of you." else null,
            )
            when (open.item.kind) {
                MediaKind.PHOTO -> {
                    val bitmap = remember(open.file) { BitmapFactory.decodeFile(open.file.absolutePath) }
                    bitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = open.item.note ?: "The photo they sent",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                MediaKind.VOICE -> VoicePlayer(open.file)
            }
            GlowButton(
                text = "Done",
                onClick = actions.onClose,
                style = GlowButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private const val BURN_LABEL_WIDTH = 0.75f

private fun SendError.message(): String = when (this) {
    SendError.OFFLINE -> "You're offline. That one needs the network."
    SendError.TOO_BIG -> "That's too big to send."
    SendError.GONE -> "That's gone now."
    SendError.OTHER -> "That didn't work. Try again."
}
