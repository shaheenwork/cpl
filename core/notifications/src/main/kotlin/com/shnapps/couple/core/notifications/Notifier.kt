package com.shnapps.couple.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.shnapps.couple.core.model.PushChannel
import com.shnapps.couple.core.model.PushKind
import com.shnapps.couple.core.model.PushMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Puts a notification on the screen (BUILD_PROMPT.md §16.2).
 *
 * The payload it is built from carries no words at all, so every line here comes from
 * [NotificationText]. With previews hidden — the default — even the kind is left out and the
 * notification says only that something is waiting; the app says the rest once it is unlocked.
 */
@Singleton
class Notifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val manager = NotificationManagerCompat.from(context)

    /** Creates the four categories, so a phone can silence one and keep another (§16.2). */
    fun ensureChannels() {
        for (channel in PushChannel.entries) {
            manager.createNotificationChannel(
                NotificationChannel(
                    channel.id,
                    NotificationText.channelName(channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = NotificationText.channelDescription(channel)
                    // Nothing private is in a notification, but a preview of one on a lock screen
                    // is still a thing a flatmate reads over a shoulder (§16.2).
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
                    setShowBadge(false)
                },
            )
        }
    }

    /**
     * Shows [message]. [showContent] chooses between naming the kind of thing that happened and
     * saying only the app's name — never between showing content and hiding it, because there is
     * no content here to show.
     */
    fun show(message: PushMessage, showContent: Boolean) {
        // Android 13+ asks before the first notification, and a phone can refuse them wholesale.
        // Either answer is respected here rather than left to the system to swallow.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (!manager.areNotificationsEnabled()) return
        ensureChannels()
        val notification = NotificationCompat.Builder(context, message.kind.channel.id)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(if (showContent) NotificationText.TITLE else NotificationText.HIDDEN_TITLE)
            .setContentText(if (showContent) NotificationText.body(message.kind) else NotificationText.HIDDEN_BODY)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(openApp(message.kind))
            .build()
        manager.notify(message.kind.name, ID, notification)
    }

    /**
     * Opens the app, and nothing more specific than that (D-088). The intent is explicit, so
     * nothing outside this app can be made to handle it, and it carries no extras: whatever the
     * notification was about is on the home surface once the app is unlocked, fetched there as
     * the signed-in user rather than carried here.
     */
    private fun openApp(kind: PushKind): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            ?: return null
        return PendingIntent.getActivity(
            context,
            kind.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        /** One per kind, so a second surprise replaces the first rather than stacking up. */
        const val ID = 1
    }
}
