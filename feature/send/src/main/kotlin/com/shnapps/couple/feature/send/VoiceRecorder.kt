package com.shnapps.couple.feature.send

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Recording a voice note (BUILD_PROMPT.md §13). An interface because a test must be able to
 * record without a microphone — and because a ViewModel that owns a MediaRecorder is a ViewModel
 * that cannot be tested at all.
 *
 * The file lands in this app's own cache. It leaves the phone only when it is sent, and the copy
 * that stays is deleted with the cache.
 */
interface VoiceRecorder {
    /** Starts recording into a new file. */
    fun start(): Outcome<Unit>

    /** Stops, and returns what was recorded. */
    fun stop(): Outcome<File>

    /** Throws away whatever is in progress: the screen left, or the ViewModel was cleared. */
    fun cancel()
}

@Singleton
class AndroidVoiceRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
) : VoiceRecorder {

    private var recorder: MediaRecorder? = null
    private var file: File? = null

    @Suppress("TooGenericExceptionCaught") // The platform boundary: a refusal is an AppError.
    override fun start(): Outcome<Unit> = try {
        cancel()
        val target = File.createTempFile("voice_", ".m4a", context.cacheDir)
        val recording = newRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(BIT_RATE)
            setAudioSamplingRate(SAMPLE_RATE)
            setMaxDuration(MAX_MILLIS)
            setOutputFile(target.absolutePath)
            prepare()
            start()
        }
        recorder = recording
        file = target
        Outcome.Success(Unit)
    } catch (error: Throwable) {
        cancel()
        Outcome.Failure(AppError.Unknown(error))
    }

    @Suppress("TooGenericExceptionCaught")
    override fun stop(): Outcome<File> {
        val recording = recorder ?: return Outcome.Failure(AppError.NotFound())
        val target = file
        return try {
            recording.stop()
            recording.release()
            recorder = null
            file = null
            if (target == null || target.length() == 0L) {
                Outcome.Failure(AppError.NotFound())
            } else {
                Outcome.Success(target)
            }
        } catch (error: Throwable) {
            cancel()
            Outcome.Failure(AppError.Unknown(error))
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override fun cancel() {
        try {
            recorder?.release()
        } catch (error: Throwable) {
            // Nothing to do: the recorder was never started, or is already gone.
        }
        recorder = null
        file?.delete()
        file = null
    }

    @Suppress("DEPRECATION") // The pre-S constructor, for the phones that still need it.
    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()

    private companion object {
        const val BIT_RATE = 64_000
        const val SAMPLE_RATE = 44_100

        /** Two minutes: a voice note, not a podcast — and well under the 20 MB the rules allow. */
        const val MAX_MILLIS = 2 * 60 * 1000
    }
}
