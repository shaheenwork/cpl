package com.shnapps.couple.feature.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.component.LocalBarExtra
import com.shnapps.couple.core.designsystem.component.raised
import com.shnapps.couple.core.designsystem.icon.AfterhoursIcons
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.model.MediaKind
import com.shnapps.couple.core.model.MessageMedia
import com.shnapps.couple.core.ui.ErrorBanner
import com.shnapps.couple.core.ui.SecureScreen
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
fun ChatLayer(
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        // The way in is **offered**, not placed. Floating it in a corner put it exactly on top of
        // whatever that screen already had there — on home, the settings button, which simply
        // vanished underneath it. Each bar now puts it where that bar's own controls go.
        CompositionLocalProvider(
            LocalBarExtra provides if (state.paired) {
                { ChatAffordance(unread = state.unread, onOpen = viewModel::open) }
            } else {
                null
            },
            content = content,
        )

        AnimatedVisibility(
            visible = state.open,
            enter = fadeIn() + slideInVertically { it / SLIDE },
            exit = fadeOut() + slideOutVertically { it / SLIDE },
        ) {
            SecureScreen()
            // Back closes the chat and goes no further. Two backs mean panic (M8), and the one
            // that shut a chat must not count as the first of them.
            BackHandler(enabled = true) { viewModel.close() }
            ChatContent(state = state, actions = viewModel.actions())
        }
    }
}

/** Everything the overlay can do, gathered so the content stays easy to preview and to test. */
internal data class ChatActions(
    val onClose: () -> Unit = {},
    val onDraft: (String) -> Unit = {},
    val onSay: () -> Unit = {},
    val onSend: (MediaKind, File) -> Unit = { _, _ -> },
    val onRecord: (Boolean) -> Unit = {},
    val onFinishRecording: () -> Unit = {},
    val onCancelRecording: () -> Unit = {},
    val onLook: (MessageMedia) -> Unit = {},
    val onPlay: (MessageMedia) -> Unit = {},
    val onView: (String?) -> Unit = {},
    val onUnsay: (String) -> Unit = {},
    val onDismissError: () -> Unit = {},
    val onStop: () -> Unit = {},
)

private fun ChatViewModel.actions() = ChatActions(
    onClose = ::close,
    onDraft = ::draft,
    onSay = ::say,
    onSend = { kind, file -> send(kind, file) },
    onRecord = ::record,
    onFinishRecording = ::finishRecording,
    onCancelRecording = ::cancelRecording,
    onLook = ::look,
    onPlay = ::play,
    onView = ::view,
    onUnsay = ::unsay,
    onDismissError = ::dismissError,
    onStop = ::stop,
)

/**
 * The panel.
 *
 * It does not cover the screen. What is underneath stays dimly visible through the scrim, because
 * that is the promise the feature makes: the evening did not stop to let them talk.
 */
@Composable
internal fun ChatContent(state: ChatUiState, actions: ChatActions, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM))
                .tap(actions.onClose),
        )
        Panel(
            state = state,
            actions = actions,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(maxHeight * TALL),
        )
        state.viewing?.let { id ->
            (state.shown[id] as? Shown.Photo)?.let { photo ->
                FullPhoto(photo = photo, onClose = { actions.onView(null) })
            }
        }
    }
}

@Composable
private fun Panel(state: ChatUiState, actions: ChatActions, modifier: Modifier = Modifier) {
    val spacing = AfterhoursTheme.spacing
    // Top corners only: it rises from the bottom edge, so the bottom two have nothing to round.
    val shape = RoundedCornerShape(topStart = PANEL_CORNER, topEnd = PANEL_CORNER)
    Column(
        modifier = modifier
            // A sheet, and it has to look like one. Drawn on `surface` it was the same black as
            // everything else, so the chat opened and nothing appeared to have happened: no edge,
            // no ground, nothing to say which part of the screen you were now in. It is lifted a
            // step off the scene, with the rim every other raised object in this app has.
            .raised(color = MaterialTheme.colorScheme.surfaceContainer, shape = shape)
            .clip(shape)
            .padding(horizontal = spacing.md),
    ) {
        Handle()
        ChatBar(onClose = actions.onClose, onStop = actions.onStop)
        // The bar sits on the conversation; a hairline says where one ends and the other begins.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(spacing.hairline)
                .background(AfterhoursTheme.colors.edgeFaint),
        )
        Conversation(state = state, actions = actions, modifier = Modifier.weight(1f))
        state.error?.let { ErrorBanner(message = it.message(), onDismiss = actions.onDismissError) }
        Composer(state = state, actions = actions)
    }
}

/** The grab handle. Decorative — the close button is the control, and it is labelled. */
@Composable
private fun Handle() {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = AfterhoursTheme.spacing.sm)) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(HANDLE_WIDE)
                .height(HANDLE_TALL)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(AfterhoursTheme.colors.edgeFaint),
        )
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

@Composable
private fun ChatBar(onClose: () -> Unit, onStop: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = AfterhoursTheme.spacing.sm),
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
            text = "The two of you",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        // The safe word is on every screen that covers one that is playing (M7).
        GlowButton(text = "Stop", onClick = onStop, style = GlowButtonStyle.Quiet)
    }
}

/** A photo, filling the screen over the conversation. Nothing is saved, and there is no share. */
@Composable
private fun FullPhoto(photo: Shown.Photo, onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim)
            .tap(onClose),
        contentAlignment = Alignment.Center,
    ) {
        BackHandler(enabled = true, onBack = onClose)
        Image(
            painter = BitmapPainter(photo.image),
            contentDescription = "The photo, full screen",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(FIT),
        )
        Row(
            modifier = Modifier.fillMaxSize().padding(AfterhoursTheme.spacing.sm),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.Top,
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = AfterhoursIcons.Close,
                    contentDescription = "Close the photo",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private fun ChatError.message(): String = when (this) {
    ChatError.OFFLINE -> "You're offline. It'll send when you're back."
    ChatError.MIC_REFUSED -> "The microphone isn't allowed, so there's no voice note."
    ChatError.OTHER -> "That didn't send. Try again."
}

/** How far the panel slides in from: a fraction of its own height, not the screen's. */
private const val SLIDE = 6

/** The panel leaves the top of the screen showing, because the evening is still up there. */
private const val TALL = 0.86f
private const val SCRIM = 0.62f
private const val FIT = 0.9f

private val ICON = 24.dp

/** The unread dot: a mark, not a count — the number is for the screen reader. */
private val DOT = 8.dp
private val HANDLE_WIDE = 36.dp
private val HANDLE_TALL = 4.dp
private val PANEL_CORNER = 20.dp
