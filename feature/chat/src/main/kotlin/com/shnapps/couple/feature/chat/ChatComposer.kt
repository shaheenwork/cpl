package com.shnapps.couple.feature.chat

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.shnapps.couple.core.designsystem.icon.AfterhoursIcons
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.model.MediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * What they type, and the three ways to send something that is not words.
 *
 * One **+** rather than a row of icons: a camera, a gallery and a microphone side by side is three
 * decisions to make before saying anything, and the rest of this app does not ask for that.
 *
 * No `imePadding()` anywhere here. The nav host is already inset including the keyboard (D-028),
 * so the composer rides up on its own; adding it again pads twice.
 */
@Composable
internal fun Composer(state: ChatUiState, actions: ChatActions) {
    if (state.recording != null) {
        RecordingBar(recording = state.recording, actions = actions)
        return
    }

    val spacing = AfterhoursTheme.spacing
    var attaching by remember { mutableStateOf(false) }
    val attach = rememberAttachments(actions) { attaching = false }

    Column(modifier = Modifier.fillMaxWidth()) {
        AnimatedVisibility(visible = attaching, enter = fadeIn(), exit = fadeOut()) {
            Attachments(attach)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalAlignment = Alignment.Bottom,
        ) {
            IconButton(
                onClick = { attaching = !attaching },
                enabled = !state.sending,
                modifier = Modifier.size(spacing.touchTarget),
            ) {
                Icon(
                    imageVector = AfterhoursIcons.Plus,
                    contentDescription = if (attaching) "Close the attach menu" else "Attach something",
                    tint = AfterhoursTheme.colors.textMuted,
                    modifier = Modifier.size(ICON),
                )
            }
            Field(
                value = state.draft,
                onValueChange = actions.onDraft,
                modifier = Modifier.weight(1f),
            )
            // Only there when there is something to send: an always-on send button that does
            // nothing most of the time is a control that teaches people to ignore it.
            AnimatedVisibility(
                visible = state.canSend,
                enter = fadeIn() + scaleIn(initialScale = SMALL),
                exit = fadeOut() + scaleOut(targetScale = SMALL),
            ) {
                IconButton(onClick = actions.onSay, modifier = Modifier.size(spacing.touchTarget)) {
                    Icon(
                        imageVector = AfterhoursIcons.Send,
                        contentDescription = "Send",
                        tint = AfterhoursTheme.tier.accent,
                        modifier = Modifier.size(ICON),
                    )
                }
            }
        }
    }
}

/** The recording, in the composer's place: how long, how loud, and the two ways out of it. */
@Composable
private fun RecordingBar(recording: Recording, actions: ChatActions) {
    val spacing = AfterhoursTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = actions.onCancelRecording,
            modifier = Modifier.size(spacing.touchTarget),
        ) {
            Icon(
                imageVector = AfterhoursIcons.Close,
                contentDescription = "Throw the recording away",
                tint = AfterhoursTheme.colors.textMuted,
                modifier = Modifier.size(ICON),
            )
        }
        Box(
            modifier = Modifier
                .size(DOT)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(AfterhoursTheme.tier.accent),
        )
        Text(
            text = clock(recording.seconds),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        // The live meter: one bar that moves, so it is obvious the microphone is hearing something.
        Meter(level = recording.level, modifier = Modifier.weight(1f))
        IconButton(
            onClick = actions.onFinishRecording,
            modifier = Modifier.size(spacing.touchTarget),
        ) {
            Icon(
                imageVector = AfterhoursIcons.Send,
                contentDescription = "Send the voice note",
                tint = AfterhoursTheme.tier.accent,
                modifier = Modifier.size(ICON),
            )
        }
    }
}

/** How loud it is right now. Decorative — the running clock beside it says what is happening. */
@Composable
private fun Meter(level: Float, modifier: Modifier = Modifier) {
    Waveform(
        levels = List(BARS) { index -> meterBar(index, level) },
        fraction = 0f,
        played = AfterhoursTheme.tier.accent,
        rest = AfterhoursTheme.tier.accent,
        modifier = modifier.height(WAVE).clearAndSetSemantics { },
    )
}

/** A bar's height: tallest in the middle, so a voice reads as a pulse rather than a block. */
private fun meterBar(index: Int, level: Float): Int {
    val middle = (BARS - 1) / 2f
    val away = 1f - (kotlin.math.abs(index - middle) / middle)
    return (level * FULL * (NEAR + away * (1f - NEAR))).toInt().coerceIn(FLOOR, FULL)
}

/**
 * What they are typing.
 *
 * A bare field rather than `OutlinedTextField`: the outlined one draws Material's own container
 * and label, which on this theme reads as a form rather than a place to talk.
 */
@Composable
private fun Field(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val spacing = AfterhoursTheme.spacing
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraLarge)
            // A step above the panel it sits on. On `surfaceContainerLow` it was the panel's own
            // colour, so the field had no edges and the placeholder floated in mid-air.
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = spacing.md, vertical = spacing.item),
    ) {
        if (value.isEmpty()) {
            Text(
                text = "Say something",
                style = MaterialTheme.typography.bodyLarge,
                color = AfterhoursTheme.colors.textFaint,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            maxLines = LINES,
            textStyle = LocalTextStyle.current.merge(
                MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            ),
            cursorBrush = SolidColor(AfterhoursTheme.tier.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Take one / Choose one / Record, as three plain rows. */
@Composable
private fun Attachments(attach: Attachments) {
    val spacing = AfterhoursTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(vertical = spacing.sm),
    ) {
        Choice(AfterhoursIcons.Camera, "Take one", attach.takeOne)
        Choice(AfterhoursIcons.Eye, "Choose one", attach.chooseOne)
        Choice(AfterhoursIcons.Mic, "Record", attach.record)
    }
}

@Composable
private fun Choice(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    words: String,
    onClick: () -> Unit,
) {
    val spacing = AfterhoursTheme.spacing
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = spacing.touchTarget)
            .tap(onClick)
            .padding(horizontal = spacing.md),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AfterhoursTheme.colors.textMuted,
            modifier = Modifier.size(ICON),
        )
        Text(
            text = words,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** The three ways in, each already wired to its launcher. */
private data class Attachments(
    val takeOne: () -> Unit,
    val chooseOne: () -> Unit,
    val record: () -> Unit,
)

/**
 * The launchers, and the files they write into.
 *
 * The camera is given a `content://` URI from this app's own `FileProvider` — there is no other
 * way to get a photo back from `ACTION_IMAGE_CAPTURE`, which is why the chat had no camera at all
 * until the provider existed. The CAMERA permission is deliberately not declared: without it, the
 * camera app takes the picture on this app's behalf and nothing has to be asked for.
 */
@Composable
private fun rememberAttachments(actions: ChatActions, onPicked: () -> Unit): Attachments {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var taking by remember { mutableStateOf<File?>(null) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val file = taking
        taking = null
        if (taken && file != null && file.length() > 0) actions.onSend(MediaKind.PHOTO, file)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch { copyIn(context, uri)?.let { actions.onSend(MediaKind.PHOTO, it) } }
    }

    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), actions.onRecord)

    return remember(camera, picker, mic, context) {
        Attachments(
            takeOne = {
                onPicked()
                val file = File.createTempFile("shot_", ".jpg", context.cacheDir)
                taking = file
                camera.launch(shareable(context, file))
            },
            chooseOne = {
                onPicked()
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            record = {
                onPicked()
                mic.launch(Manifest.permission.RECORD_AUDIO)
            },
        )
    }
}

/** A cache file the camera app may write into, and nothing else may read. */
private fun shareable(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.files", file)

/**
 * The chosen picture, copied into this app's own cache.
 *
 * The picker hands back a URI this app may read once; everything downstream — the upload, the
 * retry, the record — needs a file it owns. Nothing is written to the gallery, and nothing of the
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

private const val LINES = 4
private const val SMALL = 0.7f
private const val BARS = 24
private const val FULL = 100
private const val FLOOR = 14
private const val NEAR = 0.5f

private val ICON = 22.dp
private val DOT = 8.dp
private val WAVE = 28.dp
