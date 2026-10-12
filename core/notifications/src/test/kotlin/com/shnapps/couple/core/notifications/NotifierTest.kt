package com.shnapps.couple.core.notifications

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.model.PushChannel
import com.shnapps.couple.core.model.PushKind
import com.shnapps.couple.core.model.PushMessage
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The other half of the payload inspection (BUILD_PROMPT.md §16.2): what actually lands on the
 * lock screen. The server sends ids; this proves the phone turns them into the app's own words and
 * nothing else — whichever way the preview switch is set.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class NotifierTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val visibility = FakeVisibility()
    private val notifier = Notifier(context, visibility)
    private val manager = context.getSystemService(NotificationManager::class.java)

    @Before
    fun granted() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun posted(): List<Notification> = shadowOf(manager).allNotifications

    private fun Notification.title(): String = extras.getString(Notification.EXTRA_TITLE).orEmpty()

    private fun Notification.body(): String = extras.getString(Notification.EXTRA_TEXT).orEmpty()

    /** Whether the app is on the screen, set by hand. */
    private class FakeVisibility(var open: Boolean = false) : AppVisibility {
        override val inForeground: Boolean get() = open
    }

    @Test
    fun `an open app is not told what its screen already says`() {
        visibility.open = true

        // A step in an evening, a game starting, a pairing: each is on the screen the phone is
        // looking at, and a banner over it is the same news twice (D-151).
        for (kind in PushKind.entries.filter { !it.speaksWhileOpen }) {
            notifier.show(PushMessage(kind), showContent = true)
        }

        assertThat(posted()).isEmpty()
    }

    @Test
    fun `the same steps are told to a phone that is not looking`() {
        visibility.open = false

        for (kind in PushKind.entries.filter { !it.speaksWhileOpen }) {
            notifier.show(PushMessage(kind), showContent = true)
        }

        // One per kind: a second of the same kind replaces the first, it does not stack.
        assertThat(posted()).hasSize(PushKind.entries.count { !it.speaksWhileOpen })
    }

    @Test
    fun `a message from them is still told to an open app`() {
        visibility.open = true

        notifier.show(PushMessage(PushKind.THEY_SENT_SOMETHING), showContent = false)

        assertThat(posted()).hasSize(1)
    }

    @Test
    fun `only a message from them speaks while the app is open`() {
        // Held as a list so a new kind has to be decided about rather than defaulting into either.
        assertThat(PushKind.entries.filter { it.speaksWhileOpen }).containsExactly(PushKind.THEY_SENT_SOMETHING)
    }

    @Test
    fun `hides the kind of thing it was, until asked not to`() {
        notifier.show(PushMessage(PushKind.SURPRISE_LEFT, ref = "HDjiwnxR8u9xqPsTMwuW"), showContent = false)

        val hidden = posted().single()
        assertThat(hidden.title()).isEqualTo("Afterhours")
        assertThat(hidden.body()).isEqualTo("Something's waiting for you.")
        // Not even which category: the channel is how a phone silences one, not something read.
        assertThat(hidden.body()).doesNotContain("surprise")
    }

    @Test
    fun `says which kind of thing when the user asked for that, and no more`() {
        notifier.show(PushMessage(PushKind.SURPRISE_LEFT, ref = "HDjiwnxR8u9xqPsTMwuW"), showContent = true)

        val shown = posted().single()
        assertThat(shown.title()).isEqualTo("Afterhours")
        assertThat(shown.body()).isEqualTo("They left you something.")
    }

    @Test
    fun `never carries the id it was sent, or anything else from the payload`() {
        val ref = "HDjiwnxR8u9xqPsTMwuW"
        for (kind in PushKind.entries) {
            notifier.show(PushMessage(kind, ref = ref), showContent = true)
        }

        assertThat(posted()).hasSize(PushKind.entries.size)
        for (notification in posted()) {
            assertThat(notification.title() + notification.body()).doesNotContain(ref)
            // Every word on the screen is one of this app's own.
            assertThat(NotificationText.entries()).contains(notification.body())
            assertThat(notification.visibility).isEqualTo(Notification.VISIBILITY_PRIVATE)
        }
    }

    @Test
    fun `shows nothing at all to a phone that said no`() {
        shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        notifier.show(PushMessage(PushKind.BOTH_READY), showContent = true)

        assertThat(posted()).isEmpty()
    }

    @Test
    fun `puts each category on its own channel, so one can be silenced and another kept`() {
        notifier.ensureChannels()

        val channels = shadowOf(manager).notificationChannels.map { (it as android.app.NotificationChannel).id }
        assertThat(channels).containsAtLeastElementsIn(PushChannel.entries.map { it.id })
        for (kind in PushKind.entries) {
            notifier.show(PushMessage(kind), showContent = true)
        }
        assertThat(posted().map { it.channelId }.toSet())
            .isEqualTo(PushKind.entries.map { it.channel.id }.toSet())
    }
}
