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
) {
    /** Waiting to be opened: sent by the partner, and never looked at. */
    val unopened: Boolean get() = !mine && sentAtMillis != null && viewedAtMillis == null
}
