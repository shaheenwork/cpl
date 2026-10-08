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
    /**
     * What the media record says about itself, once it has arrived.
     *
     * Null while it has not. The message and the record come down two listeners and either may be
     * first, so a null here means *not yet* and draws as loading — never as broken.
     */
    val media: MessageMedia? = null,
) {
    /** Whether it is still on its way: Firestore has it locally but the server has not confirmed. */
    val pending: Boolean get() = atMillis == 0L
}

/**
 * The facts a screen needs about a message's photo or voice note.
 *
 * Not the bytes, and not a URL to them — there is no such thing here (D-083). What this carries is
 * everything needed to decide *how to draw it before fetching it*, which matters because fetching
 * a once-only item is what destroys it.
 */
data class MessageMedia(
    val id: String,
    val kind: MediaKind,
    /** Opening it deletes it, really — so it is never fetched until it is asked for. */
    val burnAfterView: Boolean,
    /** Already looked at: a once-only one is gone, an ordinary one is merely read. */
    val opened: Boolean,
    /** A voice note's length, or null. */
    val seconds: Int?,
    /** A voice note's waveform, or empty. [MediaItem.WAVEFORM_BARS] bars, each 0..100. */
    val levels: List<Int>,
) {
    /** Nothing left to fetch: it burned as it was read, and saying so is all that is left. */
    val gone: Boolean get() = burnAfterView && opened
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
