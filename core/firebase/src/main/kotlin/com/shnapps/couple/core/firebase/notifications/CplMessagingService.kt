package com.shnapps.couple.core.firebase.notifications

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.shnapps.couple.core.datastore.AppPreferencesStore
import com.shnapps.couple.core.model.PushKind
import com.shnapps.couple.core.model.PushMessage
import com.shnapps.couple.core.notifications.Notifier
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Messages from the server (BUILD_PROMPT.md §16.2).
 *
 * Every message is data-only — the server sends no `notification` block at all — so nothing is
 * ever displayed by the system on this app's behalf, and the words come from the app's own table.
 * A payload carries a kind, a channel, a time and at most a document id; anything else in it, or a
 * kind this version does not know, is dropped rather than guessed at.
 */
@AndroidEntryPoint
class CplMessagingService : FirebaseMessagingService() {

    @Inject lateinit var notifier: Notifier

    @Inject lateinit var preferences: AppPreferencesStore

    @Inject lateinit var push: PushDataSource

    @Inject lateinit var auth: FirebaseAuth

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        val kind = PushKind.of(message.data[KIND]) ?: return
        val ref = message.data[REF]?.takeIf { REF_SHAPE.matches(it) }
        scope.launch {
            val showContent = preferences.preferences.first().showNotificationContent
            notifier.show(PushMessage(kind = kind, ref = ref), showContent)
        }
    }

    /** A new token for this phone, which the server needs before it can reach it again. */
    override fun onNewToken(token: String) {
        val uid = auth.currentUser?.uid ?: return
        scope.launch { push.register(uid, token) }
    }

    private companion object {
        const val KIND = "kind"
        const val REF = "ref"

        /** What the server promises a reference is. Anything else is not read (§16.2). */
        val REF_SHAPE = Regex("^[A-Za-z0-9_-]{1,64}$")
    }
}
