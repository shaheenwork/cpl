package com.shnapps.couple.core.engine.game

import com.shnapps.couple.core.model.ChapterReaction

/**
 * An answer as the one short string a phone sends about a round (BUILD_PROMPT.md §12.2), and
 * back again. The other phone never reads it: the server puts both into the reveal, and this is
 * how each side writes and reads that.
 *
 * Kept next to the answers themselves so the two halves cannot drift: a new kind of answer that
 * two phones can exchange is encoded here or it is not exchanged at all.
 */
private const val PICKED = "P"
private const val TEXT = "T"
private const val STATEMENTS = "S"
private const val REACTED = "R"
private const val DIALED = "D"

/** Separates the parts of an answer: a control character no one types. */
private const val SEPARATOR = ""

/** What this answer says, or null when it says nothing the other phone could not know. */
fun Answer.encode(): String? = when (this) {
    is Answer.Picked -> "$PICKED$SEPARATOR$optionId"
    is Answer.Text -> "$TEXT$SEPARATOR${text.clean()}"
    is Answer.Statements -> (listOf(STATEMENTS, lie.toString()) + statements.map { it.clean() })
        .joinToString(SEPARATOR)
    is Answer.Reacted -> "$REACTED$SEPARATOR${reaction.name}"
    is Answer.Dialed -> "$DIALED$SEPARATOR$value"
    // A recording is a file on this phone. There is nothing to send, and sending a path would
    // say where it is without letting anyone hear it (D-069).
    Answer.Done, is Answer.Judged, is Answer.Recorded -> null
}

/** [text] as an answer again, or null if it is not one this version can read. */
fun decodeAnswer(text: String): Answer? {
    val parts = text.split(SEPARATOR)
    val rest = parts.drop(1)
    return when (parts.firstOrNull()) {
        PICKED -> rest.firstOrNull()?.let { Answer.Picked(it) }
        DIALED -> rest.firstOrNull()?.toIntOrNull()?.takeIf { it in RoundPrimitive.RANGE }?.let { Answer.Dialed(it) }
        TEXT -> rest.firstOrNull()?.let { Answer.Text(it) }
        STATEMENTS -> {
            val lie = rest.firstOrNull()?.toIntOrNull()
            val statements = rest.drop(1)
            if (lie == null || statements.size != RoundPrimitive.STATEMENTS) {
                null
            } else {
                Answer.Statements(statements, lie)
            }
        }
        REACTED -> ChapterReaction.entries.firstOrNull { it.name == rest.firstOrNull() }?.let { Answer.Reacted(it) }
        else -> null
    }
}

private fun String.clean(): String = replace(SEPARATOR, " ")
