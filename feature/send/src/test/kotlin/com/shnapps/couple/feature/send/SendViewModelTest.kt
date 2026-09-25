package com.shnapps.couple.feature.send

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.model.MediaKind
import com.shnapps.couple.core.testing.FakeAccountRepository
import com.shnapps.couple.core.testing.FakeMediaRepository
import com.shnapps.couple.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Sending a voice note or a photo, and opening one (BUILD_PROMPT.md §5.8, §13): what is recorded
 * is not sent until it is sent, a burn-after-view item leaves for good as it is opened, and a
 * failure says so without losing what was made.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SendViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun test(body: suspend TestScope.() -> Unit) = runTest(mainDispatcherRule.testDispatcher) { body() }

    private val media = FakeMediaRepository()
    private val recorder = FakeVoiceRecorder()

    private val account = FakeAccountRepository()

    private fun TestScope.screen() = SendViewModel(media, recorder, account).also { vm ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
    }

    private fun TestScope.act(action: () -> Unit) {
        action()
        advanceUntilIdle()
    }

    @Test
    fun `a voice note is recorded, then sent — and not before`() = test {
        val vm = screen()

        act(vm::startRecording)
        assertThat(vm.uiState.value.recording).isTrue()
        assertThat(media.sent).isEmpty()

        act(vm::stopRecording)
        assertThat(vm.uiState.value.recording).isFalse()
        assertThat(vm.uiState.value.canSend).isTrue()
        assertThat(media.sent).isEmpty()

        act { vm.setNote("thinking about you") }
        act { vm.setBurnAfterView(true) }
        act(vm::send)

        assertThat(media.sent).containsExactly(Triple(MediaKind.VOICE, "thinking about you", true))
        assertThat(vm.uiState.value.sent).isTrue()
        assertThat(vm.uiState.value.ready).isNull()
    }

    @Test
    fun `a photo picked on this phone is sent the same way`() = test {
        val vm = screen()
        val photo = File.createTempFile("photo", ".jpg")

        act { vm.photoPicked(photo) }
        act(vm::send)

        assertThat(media.sent).containsExactly(Triple(MediaKind.PHOTO, null, false))
    }

    @Test
    fun `opening what they sent burns the one that was sent to burn`() = test {
        val once = media.theySend(MediaKind.VOICE, note = "just this once", burnAfterView = true)
        val vm = screen()
        assertThat(vm.uiState.value.waiting?.id).isEqualTo(once.id)

        act { vm.open(once, File.createTempFile("open", ".bin")) }

        assertThat(media.opened).containsExactly(once.id)
        assertThat(vm.uiState.value.open?.burned).isTrue()
        // Gone from both of them: nothing is left in the inbox to open again.
        assertThat(vm.uiState.value.inbox).isEmpty()
        act(vm::close)
        assertThat(vm.uiState.value.open).isNull()
    }

    @Test
    fun `one that does not burn stays, and shows as opened`() = test {
        val kept = media.theySend(MediaKind.PHOTO)
        val vm = screen()

        act { vm.open(kept, File.createTempFile("open", ".bin")) }

        assertThat(vm.uiState.value.open?.burned).isFalse()
        assertThat(vm.uiState.value.inbox.single().viewedAtMillis).isNotNull()
        assertThat(vm.uiState.value.waiting).isNull()
    }

    @Test
    fun `a send that fails says so, and what was made is still here`() = test {
        val vm = screen()
        act(vm::startRecording)
        act(vm::stopRecording)
        media.failNext = AppError.Network()

        act(vm::send)

        assertThat(vm.uiState.value.error).isEqualTo(SendError.OFFLINE)
        assertThat(vm.uiState.value.ready).isNotNull()
        assertThat(vm.uiState.value.sent).isFalse()
        act(vm::dismissError)
        act(vm::send)
        assertThat(media.sent).hasSize(1)
    }

    @Test
    fun `a microphone that refuses is said plainly, and nothing is left recording`() = test {
        val vm = screen()
        recorder.failStart = AppError.PermissionDenied()

        act(vm::startRecording)

        assertThat(vm.uiState.value.recording).isFalse()
        assertThat(vm.uiState.value.error).isEqualTo(SendError.GONE)
        assertThat(vm.uiState.value.ready).isNull()
    }

    /** A recorder without a microphone: a file appears, and nothing touches the platform. */
    private class FakeVoiceRecorder : VoiceRecorder {
        var failStart: AppError? = null
        var cancelled = false

        override fun start(): Outcome<Unit> =
            failStart?.let { Outcome.Failure(it) } ?: Outcome.Success(Unit)

        override fun stop(): Outcome<File> = Outcome.Success(File.createTempFile("voice", ".m4a"))

        override fun cancel() {
            cancelled = true
        }
    }
}
