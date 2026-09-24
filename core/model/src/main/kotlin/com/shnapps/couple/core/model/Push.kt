package com.shnapps.couple.core.model

/**
 * What a push message can say (BUILD_PROMPT.md §16.1) — which is only ever *which kind of thing*
 * happened. The words a phone shows come from the app's own strings, never from the payload, and
 * whatever the notification is about is fetched afterwards with the signed-in user's credentials
 * (§16.2).
 */
enum class PushKind(val channel: PushChannel) {
    PARTNER_INVITED(PushChannel.PAIRING),
    PARTNER_JOINED(PushChannel.PAIRING),
    SURPRISE_LEFT(PushChannel.SURPRISES),
    SURPRISE_UNLOCKED(PushChannel.SURPRISES),
    NIGHT_STARTING(PushChannel.SURPRISES),
    DAY_UNLOCKED(PushChannel.SURPRISES),
    PARTNER_FINISHED(PushChannel.TOGETHER),
    BOTH_READY(PushChannel.TOGETHER),
    SESSION_EXPIRING(PushChannel.TOGETHER),
    THEY_SENT_SOMETHING(PushChannel.MESSAGES),
    ;

    companion object {
        /** The kind the server named, or null — an unknown kind is dropped, never guessed at. */
        fun of(name: String?): PushKind? = entries.firstOrNull { it.name == name }
    }
}

/** A category a phone can silence on its own (§16.2). */
enum class PushChannel(val id: String) {
    PAIRING("pairing"),
    SURPRISES("surprises"),
    TOGETHER("together"),
    MESSAGES("messages"),
}

/**
 * One message off the wire. [ref] is the document it is about, if any: an id, which is the most
 * the server will ever put in a payload.
 */
data class PushMessage(
    val kind: PushKind,
    val ref: String? = null,
    val atMillis: Long = 0L,
)
