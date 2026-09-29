package com.shnapps.couple.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.model.MediaKind
import com.shnapps.couple.core.model.SurpriseState
import com.shnapps.couple.core.model.ThreadEntry

/**
 * The thread, as a couple apart reads it (REDESIGN_PROMPT.md §5.4).
 *
 * One rule runs through every line here: it says what happened, never what is inside. A sealed
 * surprise is "something is coming" even to the person it is for; a burned voice note says it is
 * gone rather than quietly disappearing.
 *
 * It is a timeline rather than a stack of cards (DESIGN_PROMPT.md §6.5): a rail down the gutter
 * with a node per entry, and yours told from theirs by the surface it sits on rather than by a
 * label. A conversation across a distance has an order, and a list of identical cards hides it.
 */
@Composable
fun ApartThread(
    entries: List<ThreadEntry>,
    onOpen: (ThreadEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = AfterhoursTheme.spacing
    val rail = AfterhoursTheme.colors.edgeFaint

    if (entries.isEmpty()) {
        Text(
            text = "Nothing yet. Send them something, or leave something for later.",
            style = MaterialTheme.typography.bodyMedium,
            color = AfterhoursTheme.colors.textMuted,
            modifier = modifier,
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            // The rail: one line down the gutter, behind every node, drawn once.
            .drawBehind {
                val x = RAIL_X.toPx()
                drawLine(
                    color = rail,
                    start = Offset(x, RAIL_INSET.toPx()),
                    end = Offset(x, size.height - RAIL_INSET.toPx()),
                    strokeWidth = RAIL_WIDTH.toPx(),
                )
            }
            .padding(start = RAIL_GUTTER),
        verticalArrangement = Arrangement.spacedBy(spacing.item),
    ) {
        entries.forEach { entry ->
            when (entry) {
                is ThreadEntry.Media -> Entry(
                    who = if (entry.mine) "YOU" else "THEM",
                    line = entry.line(),
                    detail = entry.note?.takeIf { !entry.waiting },
                    highlight = entry.waiting,
                    mine = entry.mine,
                    onClick = { onOpen(entry) },
                )

                is ThreadEntry.Left -> Entry(
                    who = if (entry.mine) "YOU LEFT" else "THEY LEFT",
                    line = entry.line(),
                    detail = entry.forMoment?.let { "For when $it" },
                    highlight = entry.readyForMe,
                    mine = entry.mine,
                    onClick = { onOpen(entry) },
                )

                is ThreadEntry.Counting -> Entry(
                    who = "COUNTING DOWN",
                    line = entry.title,
                    detail = null,
                    highlight = false,
                    mine = false,
                    onClick = { onOpen(entry) },
                )
            }
        }
    }
}

/** What a voice note or photo says in the thread — never the note itself while it is unopened. */
private fun ThreadEntry.Media.line(): String {
    val what = if (kind == MediaKind.VOICE) "A voice note" else "A photo"
    return when {
        waiting && burnAfterView -> "$what, waiting. It goes when you open it."
        waiting -> "$what, waiting."
        mine && opened -> "$what — they opened it."
        mine -> "$what, sent."
        burnAfterView -> "$what — opened, and gone."
        else -> what
    }
}

/** What something left for later says — which, before it is delivered, is only that it exists. */
private fun ThreadEntry.Left.line(): String = when {
    sealed && forMoment != null -> "Something, kept for a moment."
    sealed -> "Something is coming."
    state == SurpriseState.OPENED -> title.orEmpty()
    readyForMe -> title.orEmpty()
    else -> title.orEmpty()
}

/**
 * One node on the rail.
 *
 * [mine] changes the surface rather than the alignment: a right-aligned bubble would make this a
 * messaging app, which it is not — nothing here is a message, and half of it is not even readable
 * yet. [highlight] is the one thing actually waiting for this person, and it is the only node that
 * catches any light.
 */
@Composable
private fun Entry(
    who: String,
    line: String,
    detail: String?,
    highlight: Boolean,
    mine: Boolean,
    onClick: () -> Unit,
) {
    val spacing = AfterhoursTheme.spacing
    val tier = AfterhoursTheme.tier
    val node = if (highlight) tier.accent else AfterhoursTheme.colors.edgeFaint

    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = RAIL_X - RAIL_GUTTER - NODE / 2, y = NODE_DROP)
                .size(NODE)
                .background(node, CircleShape),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = if (mine) tier.container else MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.small,
                )
                .clickable(onClick = onClick)
                .semantics { contentDescription = "$who. $line" }
                .padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            Text(text = who, style = EyebrowTextStyle, color = AfterhoursTheme.colors.textFaint)
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium,
                color = if (mine) tier.onContainer else MaterialTheme.colorScheme.onSurface,
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = AfterhoursTheme.colors.textFaint,
                )
            }
        }
    }
}

private val RAIL_GUTTER = 22.dp
private val RAIL_X = 4.dp
private val RAIL_WIDTH = 1.dp
private val RAIL_INSET = 8.dp
private val NODE = 7.dp

/** Where a node sits against its bubble: level with the eyebrow, not the bubble's top corner. */
private val NODE_DROP = 16.dp
