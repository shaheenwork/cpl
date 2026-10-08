package com.shnapps.couple.core.ui.voice

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The microphone and the speaker behind the screen.
 *
 * Both are interfaces so a test can record and play without either: the fakes in `:core:testing`
 * stand in, and a screenshot test draws a note that is playing without a file existing.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class VoiceModule {

    @Binds
    @Singleton
    abstract fun bindVoiceRecorder(impl: AndroidVoiceRecorder): VoiceRecorder

    @Binds
    @Singleton
    abstract fun bindNotePlayer(impl: AndroidNotePlayer): NotePlayer
}
