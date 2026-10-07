package com.shnapps.couple.core.model

/**
 * One thing one of them said to the other (CHAT_PROMPT.md).
 *
 * The only collection under a couple a client writes outright, because there is nothing to
 * compute: a message is their own words to the one other person who can read them. It is never
 * edited — [at] is the server's clock, set once — and the only thing that happens to it afterwards
 * is that its author may take it back, for both of them.
 */
data class Message(
    val id: String,
    /** True when this phone's user said it. The uid itself never reaches a screen. */
    val mine: Boolean,
    val kind: MessageKind,
    /** The words: a message of its own, or the line that came with a photo or a voice note. */
    val text: String?,
    /** The media record this points at, for [MessageKind.VOICE] and [MessageKind.PHOTO]. */
    val mediaId: String?,
    val atMillis: Long,
) {
    /** Whether it is still on its way: Firestore has it locally but the server has not confirmed. */
    val pending: Boolean get() = atMillis == 0L
}

enum class MessageKind {
    TEXT,
    VOICE,
    PHOTO,
    ;

    companion object {
        /** The kind a stored message names, or null for one a newer app wrote and this cannot read. */
        fun of(name: String?): MessageKind? = entries.firstOrNull { it.name == name }
    }
}
