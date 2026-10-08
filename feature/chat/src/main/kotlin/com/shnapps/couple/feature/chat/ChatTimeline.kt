package com.shnapps.couple.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.shnapps.couple.core.designsystem.component.raised
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.model.MediaKind
import com.shnapps.couple.core.model.Message
import com.shnapps.couple.core.model.MessageKind
import com.shnapps.couple.core.model.MessageMedia
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The conversation itself: day separators, runs of messages, and the unread mark.
 *
 * It follows the newest message only when it is already near the newest. Jumping somebody who has
 * scrolled back to read something is the single most irritating thing a chat can do.
 */
@Composable
internal fun Conversation(state: ChatUiState, actions: ChatActions, modifier: Modifier = Modifier) {
    if (state.messages.isEmpty()) {
        Nothing(modifier)
        return
    }
    val zone = remember { ZoneId.systemDefault() }
    val entries = remember(state.messages, state.newFrom) {
        Timeline.of(state.messages, state.newFrom, zone)
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val adrift by remember { derivedAdrift(listState) }

    LaunchedEffect(entries.size) {
        if (!adrift) listState.animateScrollToItem(entries.lastIndex)
    }

    Box(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.xxs),
            contentPadding = PaddingValues(
                top = AfterhoursTheme.spacing.sm,
                bottom = AfterhoursTheme.spacing.sm,
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(entries, key = ::keyOf) { entry ->
                when (entry) {
                    is Entry.Day -> Separator(entry.label)
                    is Entry.New -> Separator("NEW", accent = true)
                    is Entry.Bubble -> Said(entry = entry, state = state, actions = actions)
                }
            }
        }
        AnimatedVisibility(
            visible = adrift,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(AfterhoursTheme.spacing.sm),
        ) {
            Latest(unread = state.unread) {
                scope.launch { listState.animateScrollToItem(entries.lastIndex) }
            }
        }
    }
}

/** One message, on its own side, with the time on the last of its run. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Said(entry: Entry.Bubble, state: ChatUiState, actions: ChatActions) {
    val spacing = AfterhoursTheme.spacing
    val message = entry.message
    val mine = message.mine
    val shape = bubbleShape(mine = mine, first = entry.first)
    var asking by remember(message.id) { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (entry.first) spacing.sm else spacing.hairline),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
            modifier = Modifier
                .fillMaxWidth(BUBBLE)
                .alpha(if (message.pending) FAINT else 1f)
                .clip(shape)
                .raised(
                    color = if (mine) {
                        AfterhoursTheme.tier.container
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    shape = shape,
                )
                // Only your own can be taken back, and only through a menu: a long press that
                // deleted something outright is a gesture nobody can undo.
                .combinedClickable(
                    enabled = mine && !message.pending,
                    onClick = {},
                    onLongClick = { asking = true },
                )
                .padding(spacing.sm)
                .semantics { contentDescription = spoken(message, entry.last) },
        ) {
            DropdownMenu(expanded = asking, onDismissRequest = { asking = false }) {
                DropdownMenuItem(
                    text = { Text("Delete for both of us") },
                    onClick = {
                        asking = false
                        actions.onUnsay(message.id)
                    },
                )
            }
            message.media?.let { record -> MediaBody(record, message, state, actions) }
            // A photo on this phone that has no record yet: it exists, it just has not landed.
            if (message.media == null && message.kind != MessageKind.TEXT) WaitingForRecord()
            message.text?.takeIf { it.isNotBlank() }?.let { words ->
                Text(
                    text = words,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (mine) AfterhoursTheme.tier.onContainer else MaterialTheme.colorScheme.onSurface,
                )
            }
            if (entry.last) Stamp(message)
        }
    }
}

@Composable
private fun MediaBody(
    record: MessageMedia,
    message: Message,
    state: ChatUiState,
    actions: ChatActions,
) {
    when (record.kind) {
        MediaKind.PHOTO -> PhotoTile(
            record = record,
            shown = state.shown[record.id],
            onLook = { actions.onLook(record) },
            onOpen = { actions.onView(record.id) },
        )

        MediaKind.VOICE -> VoiceBar(
            record = record,
            playing = state.playing?.takeIf { it.id == record.id },
            mine = message.mine,
            onPlay = { actions.onPlay(record) },
        )
    }
}

/** The time, or that it has not got there yet. One per run, on its last message. */
@Composable
private fun Stamp(message: Message) {
    Text(
        text = if (message.pending) "Sending…" else at(message.atMillis),
        style = MaterialTheme.typography.bodySmall,
        color = AfterhoursTheme.colors.textFaint,
    )
}

/** Something was sent, and the record describing it has not arrived on this phone yet. */
@Composable
private fun WaitingForRecord() {
    Text(
        text = "On its way…",
        style = EyebrowTextStyle,
        color = AfterhoursTheme.colors.textFaint,
    )
}

/** A day boundary, or the unread mark: a hairline with a word sitting on it. */
@Composable
private fun Separator(label: String, accent: Boolean = false) {
    val spacing = AfterhoursTheme.spacing
    val ink = if (accent) AfterhoursTheme.tier.onDark else AfterhoursTheme.colors.textFaint
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = spacing.item),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Hairline(Modifier.weight(1f), accent)
        Text(text = label, style = EyebrowTextStyle, color = ink, textAlign = TextAlign.Center)
        Hairline(Modifier.weight(1f), accent)
    }
}

@Composable
private fun Hairline(modifier: Modifier, accent: Boolean) {
    Box(
        modifier = modifier
            .height(AfterhoursTheme.spacing.hairline)
            .background(
                if (accent) AfterhoursTheme.tier.rim else AfterhoursTheme.colors.edgeFaint,
            ),
    )
}

/** Back to the newest, for somebody who scrolled up. */
@Composable
private fun Latest(unread: Int, onClick: () -> Unit) {
    val spacing = AfterhoursTheme.spacing
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .raised(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.extraLarge,
            )
            .tap(onClick)
            .padding(horizontal = spacing.md, vertical = spacing.sm),
    ) {
        Text(
            text = if (unread > 0) "$unread new" else "Latest",
            style = EyebrowTextStyle,
            color = AfterhoursTheme.colors.textMuted,
        )
    }
}

/** Nothing said yet. Not a grey apology — an invitation, with the field already waiting. */
@Composable
private fun Nothing(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Say something to them.",
            style = MaterialTheme.typography.bodyLarge,
            color = AfterhoursTheme.colors.textMuted,
        )
    }
}

/**
 * What a screen reader is told about a bubble: who, what, and when.
 *
 * Built rather than interpolated into a string literal, because a `$` inside a plain string is
 * exactly the bug that once announced a locked tier as `${tier.label}, locked`.
 */
private fun spoken(message: Message, last: Boolean): String = buildString {
    append(if (message.mine) "You" else "Them")
    append(": ")
    when (message.kind) {
        MessageKind.TEXT -> append(message.text.orEmpty())
        MessageKind.PHOTO -> {
            append(if (message.media?.gone == true) "a photo, already seen and gone" else "a photo")
            message.text?.takeIf { it.isNotBlank() }?.let { append(", ").append(it) }
        }
        MessageKind.VOICE -> {
            append("a voice note")
            message.media?.seconds?.let { append(", ").append(it).append(" seconds") }
        }
    }
    if (!last) return@buildString
    append(", ")
    append(if (message.pending) "sending" else at(message.atMillis))
}

/** `9:42 pm`, in this phone's own zone. */
private fun at(millis: Long): String =
    TIME.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

/** A stable key per row, so a new day separator does not restate every bubble under it. */
private fun keyOf(entry: Entry): String = when (entry) {
    is Entry.Day -> "day_${entry.label}"
    is Entry.New -> "new"
    is Entry.Bubble -> entry.message.id
}

/**
 * Whether they have scrolled away from the newest.
 *
 * Two items of slack: a conversation that follows only when it is exactly at the bottom stops
 * following the moment somebody nudges it.
 */
private fun derivedAdrift(listState: LazyListState) = derivedStateOf {
    val visible = listState.layoutInfo.visibleItemsInfo
    val total = listState.layoutInfo.totalItemsCount
    if (visible.isEmpty() || total == 0) false else visible.last().index < total - 1 - SLACK
}

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

/** A bubble never runs the full width: the side it is on is how you tell whose it is. */
private const val BUBBLE = 0.82f
private const val FAINT = 0.6f
private const val SLACK = 2
