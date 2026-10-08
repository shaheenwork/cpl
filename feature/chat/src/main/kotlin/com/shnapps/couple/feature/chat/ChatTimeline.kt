package com.shnapps.couple.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.unit.Dp
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
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // Measured once for the whole list rather than per bubble: a bubble is at most this wide,
        // and a photo is exactly this wide, so photos line up and words do not have to.
        val wide: Dp = maxWidth * BUBBLE_SHARE

        if (state.messages.isEmpty() && state.outgoing.isEmpty()) {
            Nothing()
            return@BoxWithConstraints
        }

        val zone = remember { ZoneId.systemDefault() }
        val entries = remember(state.messages, state.newFrom, state.outgoing) {
            Timeline.of(state.messages, state.newFrom, zone) + state.outgoing.map(Entry::Sending)
        }
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        val adrift by remember { derivedAdrift(listState) }

        // Opening lands on the newest, and lands there rather than travelling to it: the panel is
        // sliding up at the same moment, and a list animating through a month of messages behind
        // it is the opposite of arriving where you left off.
        LaunchedEffect(Unit) {
            if (entries.isNotEmpty()) listState.scrollToItem(entries.lastIndex)
        }

        // Anything of this phone's own takes the view with it, wherever it had been scrolled to.
        // Having to find what you just sent is absurd, and it is the one case where moving
        // somebody who scrolled back is what they meant.
        val ours = entries.lastOrNull()?.let { it is Entry.Sending || (it as? Entry.Bubble)?.message?.mine == true }
        LaunchedEffect(entries.size) {
            if (!adrift || ours == true) listState.animateScrollToItem(entries.lastIndex)
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(vertical = AfterhoursTheme.spacing.sm),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(entries, key = ::keyOf) { entry ->
                when (entry) {
                    is Entry.Day -> Separator(entry.label)
                    is Entry.New -> Separator("NEW", accent = true)
                    is Entry.Bubble -> Said(entry, state, actions, wide)
                    is Entry.Sending -> Going(entry.outgoing, state.shown[entry.outgoing.id], wide)
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

/**
 * One message, on its own side.
 *
 * The bubble wraps what is in it, up to [wide]. An earlier draft gave every bubble exactly that
 * width, which made two words read as an announcement and the column read as a form.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Said(entry: Entry.Bubble, state: ChatUiState, actions: ChatActions, wide: Dp) {
    val message = entry.message
    val mine = message.mine
    var asking by remember(message.id) { mutableStateOf(false) }

    Stacked(mine = mine, first = entry.first) {
        Bubble(
            mine = mine,
            first = entry.first,
            faint = message.pending,
            wide = wide,
            modifier = Modifier
                // Only your own can be taken back, and only through a menu: a long press that
                // deleted something outright is a gesture nobody can undo.
                .combinedClickable(
                    enabled = mine && !message.pending,
                    onClick = {},
                    onLongClick = { asking = true },
                )
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
            message.media?.let { record -> MediaBody(record, message, state, actions, wide) }
            // Sent from this phone and the record has not come back yet: it exists, it is just not
            // here. Never the word "photo" in place of one — that is what this screen used to be.
            if (message.media == null && message.kind != MessageKind.TEXT) Waiting(wide)
            message.text?.takeIf { it.isNotBlank() }?.let { Words(it, mine) }
        }
        if (entry.last) Stamp(if (message.pending) "Sending…" else at(message.atMillis))
    }
}

/**
 * Media on its way up: the photo itself, dimmed, and the word that says so.
 *
 * There is no message yet — the upload comes first (D-083) — so this is drawn from what the phone
 * is holding. Without it, sending a photo on a slow connection looks exactly like sending nothing.
 */
@Composable
private fun Going(outgoing: Outgoing, shown: Shown?, wide: Dp) {
    Stacked(mine = true, first = true) {
        Bubble(mine = true, first = true, faint = true, wide = wide) {
            when (outgoing.kind) {
                MediaKind.PHOTO -> PhotoTile(
                    shown = shown,
                    wide = wide,
                    burnAfterView = false,
                    gone = false,
                    onLook = {},
                    onOpen = {},
                )

                MediaKind.VOICE -> VoiceStrip(
                    levels = outgoing.levels,
                    seconds = outgoing.seconds ?: 0,
                    fraction = 0f,
                    playing = false,
                    mine = true,
                    wide = wide,
                    onPlay = null,
                )
            }
            outgoing.text?.takeIf { it.isNotBlank() }?.let { Words(it, mine = true) }
        }
        Stamp("Sending…")
    }
}

/** A bubble and, under it, whatever is said about it. Both on the same side. */
@Composable
private fun Stacked(mine: Boolean, first: Boolean, content: @Composable () -> Unit) {
    val spacing = AfterhoursTheme.spacing
    Column(
        // Inside a run the gap is almost nothing; between runs it is a real one. Equal gaps are
        // what made a conversation read as a list of unrelated statements.
        modifier = Modifier.fillMaxWidth().padding(top = if (first) spacing.item else spacing.xxs),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
        content = { content() },
    )
}

/** The bubble itself: it wraps its content, up to the width the list measured. */
@Composable
private fun Bubble(
    mine: Boolean,
    first: Boolean,
    faint: Boolean,
    wide: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = bubbleShape(mine = mine, first = first)
    Column(
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
        modifier = modifier
            .widthIn(max = wide)
            .alpha(if (faint) FAINT else 1f)
            .clip(shape)
            .raised(
                color = if (mine) {
                    AfterhoursTheme.tier.container
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                shape = shape,
            ),
        content = { content() },
    )
}

/**
 * The words, with their own padding.
 *
 * The padding belongs to the text and not to the bubble, so a photo sits flush to the bubble's
 * edges instead of floating in a frame of the bubble's own colour.
 */
@Composable
private fun Words(words: String, mine: Boolean) {
    val spacing = AfterhoursTheme.spacing
    Text(
        text = words,
        style = MaterialTheme.typography.bodyLarge,
        color = if (mine) AfterhoursTheme.tier.onContainer else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = spacing.item, vertical = spacing.sm),
    )
}

@Composable
private fun MediaBody(
    record: MessageMedia,
    message: Message,
    state: ChatUiState,
    actions: ChatActions,
    wide: Dp,
) {
    when (record.kind) {
        MediaKind.PHOTO -> PhotoTile(
            shown = state.shown[record.id],
            wide = wide,
            burnAfterView = record.burnAfterView,
            gone = record.gone,
            onLook = { actions.onLook(record) },
            onOpen = { actions.onView(record.id) },
        )

        MediaKind.VOICE -> {
            val playing = state.playing?.takeIf { it.id == record.id }
            VoiceStrip(
                levels = record.levels,
                seconds = playing?.seconds ?: record.seconds ?: 0,
                fraction = playing?.fraction ?: 0f,
                playing = playing != null,
                mine = message.mine,
                wide = wide,
                onPlay = { actions.onPlay(record) },
            )
        }
    }
}

/** The time, or that it has not got there yet. One per run, under its last message. */
@Composable
private fun Stamp(words: String) {
    val spacing = AfterhoursTheme.spacing
    Text(
        text = words,
        style = MaterialTheme.typography.bodySmall,
        color = AfterhoursTheme.colors.textFaint,
        modifier = Modifier.padding(start = spacing.sm, end = spacing.sm, top = spacing.xxs),
    )
}

/** Something was sent, and the record describing it has not arrived on this phone yet. */
@Composable
private fun Waiting(wide: Dp) {
    Box(
        modifier = Modifier.width(wide).padding(AfterhoursTheme.spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "On its way…", style = EyebrowTextStyle, color = AfterhoursTheme.colors.textFaint)
    }
}

/** A day boundary, or the unread mark: a hairline with a word sitting on it. */
@Composable
private fun Separator(label: String, accent: Boolean = false) {
    val spacing = AfterhoursTheme.spacing
    val ink = if (accent) AfterhoursTheme.tier.onDark else AfterhoursTheme.colors.textFaint
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = spacing.group, bottom = spacing.sm),
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
    is Entry.Sending -> entry.outgoing.id
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

private const val FAINT = 0.6f
private const val SLACK = 2
