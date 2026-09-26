package com.shnapps.couple.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shnapps.couple.core.designsystem.component.CinematicCard
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
 */
@Composable
fun ApartThread(
    entries: List<ThreadEntry>,
    onOpen: (ThreadEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.xs),
    ) {
        if (entries.isEmpty()) {
            Text(
                text = "Nothing yet. Send them something, or leave something for later.",
                style = MaterialTheme.typography.bodyMedium,
                color = AfterhoursTheme.colors.textMuted,
            )
            return@Column
        }

        entries.forEach { entry ->
            when (entry) {
                is ThreadEntry.Media -> Entry(
                    who = if (entry.mine) "YOU" else "THEM",
                    line = entry.line(),
                    detail = entry.note?.takeIf { !entry.waiting },
                    highlight = entry.waiting,
                    onClick = { onOpen(entry) },
                )

                is ThreadEntry.Left -> Entry(
                    who = if (entry.mine) "YOU LEFT" else "THEY LEFT",
                    line = entry.line(),
                    detail = entry.forMoment?.let { "For when $it" },
                    highlight = entry.readyForMe,
                    onClick = { onOpen(entry) },
                )

                is ThreadEntry.Counting -> Entry(
                    who = "COUNTING DOWN",
                    line = entry.title,
                    detail = null,
                    highlight = false,
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
    readyForMe -> "🎁 ${title.orEmpty()}"
    else -> title.orEmpty()
}

@Composable
private fun Entry(who: String, line: String, detail: String?, highlight: Boolean, onClick: () -> Unit) {
    CinematicCard(
        modifier = Modifier.fillMaxWidth(),
        glowing = highlight,
        onClick = onClick,
        contentDescription = "$who. $line",
    ) {
        Text(text = who, style = EyebrowTextStyle, color = AfterhoursTheme.colors.textFaint)
        Text(
            text = line,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = AfterhoursTheme.colors.textMuted,
            )
        }
    }
}
