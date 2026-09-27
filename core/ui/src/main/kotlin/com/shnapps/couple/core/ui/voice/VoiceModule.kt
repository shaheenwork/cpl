package com.shnapps.couple.core.ui.voice

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** The recorder behind the screen: an interface, so a test can record without a microphone. */
@Module
@InstallIn(SingletonComponent::class)
abstract class VoiceModule {

    @Binds
    @Singleton
    abstract fun bindVoiceRecorder(impl: AndroidVoiceRecorder): VoiceRecorder
}
