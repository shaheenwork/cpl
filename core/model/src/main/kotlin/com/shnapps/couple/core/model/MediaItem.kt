package com.shnapps.couple.core.model

/**
 * A voice note or a photo, as a phone sees it (§5.8, §13). Never the content itself: the bytes
 * live in Storage, are fetched with the user's own credentials, and are never given a URL that
 * could outlive the rules.
 */
data class MediaItem(
    val id: String,
    val kind: MediaKind,
    /** True when this phone's user sent it. */
    val mine: Boolean,
    /** A short line sent with it, or null. Never the media itself. */
    val note: String?,
    /** It is deleted — really deleted — the moment the other partner opens it. */
    val burnAfterView: Boolean,
    val sentAtMillis: Long?,
    /** When the partner opened it, for one that does not burn. */
    val viewedAtMillis: Long?,
    val expiresAtMillis: Long?,
    /** A voice note's length. Null for a photo, and for a note an older app sent. */
    val seconds: Int? = null,
    /**
     * A voice note's waveform: [WAVEFORM_BARS] bars, each 0..100, sampled while it was recorded.
     *
     * It travels with the record rather than being measured from the audio, so both phones draw
     * the same shape without either of them decoding the file to find out what it looks like.
     */
    val levels: List<Int> = emptyList(),
) {
    /** Waiting to be opened: sent by the partner, and never looked at. */
    val unopened: Boolean get() = !mine && sentAtMillis != null && viewedAtMillis == null

    companion object {
        /** How many bars a waveform has, here and on the server. */
        const val WAVEFORM_BARS = 40
    }
}
