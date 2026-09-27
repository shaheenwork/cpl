package com.shnapps.couple.core.notifications

import com.shnapps.couple.core.model.PushChannel
import com.shnapps.couple.core.model.PushKind

/**
 * Every word this app is capable of putting on a lock screen (BUILD_PROMPT.md §16.2).
 *
 * It is a fixed table on purpose. A payload carries ids only, so there is nothing to interpolate
 * and nothing that can be made to say something it should not: "Your partner left you something
 * 👀", never "Your partner selected 'blindfold'". `NotificationTextTest` reads this table and
 * fails if a line ever grows a placeholder.
 */
object NotificationText {

    /** What the notification says when previews are hidden — the default (§16.2). */
    const val HIDDEN_TITLE = "Afterhours"
    const val HIDDEN_BODY = "Something's waiting for you."

    /** The category as a person would name it, for the notification settings screen. */
    fun channelName(channel: PushChannel): String = when (channel) {
        PushChannel.PAIRING -> "Pairing"
        PushChannel.SURPRISES -> "Surprises"
        PushChannel.TOGETHER -> "Your evenings"
        PushChannel.MESSAGES -> "Voice notes and photos"
    }

    fun channelDescription(channel: PushChannel): String = when (channel) {
        PushChannel.PAIRING -> "Pairing with your partner."
        PushChannel.SURPRISES -> "Something left for you, and days that open."
        PushChannel.TOGETHER -> "An evening the two of you are in."
        PushChannel.MESSAGES -> "Something they sent you."
    }

    /** The app's own name, whatever happened: a title is the most public part of a notification. */
    const val TITLE = "Afterhours"

    /** Every line this app can put on a lock screen, for a test to read back. */
    fun entries(): List<String> = PushKind.entries.map(::body) + HIDDEN_BODY

    /** The most anyone will ever read: which kind of thing happened. */
    fun body(kind: PushKind): String = when (kind) {
        PushKind.PARTNER_INVITED -> "Someone is asking to pair with you."
        PushKind.PARTNER_JOINED -> "You're paired."
        PushKind.SURPRISE_LEFT -> "They left you something."
        PushKind.SURPRISE_UNLOCKED -> "Something is ready for you."
        PushKind.NIGHT_STARTING -> "An evening is waiting for you."
        PushKind.DAY_UNLOCKED -> "Today's day is open."
        PushKind.PARTNER_FINISHED -> "They've done their part."
        PushKind.BOTH_READY -> "You're both in."
        PushKind.SESSION_EXPIRING -> "An evening is about to time out."
        PushKind.THEY_SENT_SOMETHING -> "They sent you something."
    }
}
