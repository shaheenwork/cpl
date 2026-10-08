package com.shnapps.couple.feature.chat

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The microphone and the speaker, behind an interface.
 *
 * A ViewModel depends on the interface so a test can record and play without either, and — more
 * to the point — so the ticking that drives the meter and the progress bar lives somewhere a test
 * can leave switched off (D-076).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ChatModule {

    @Binds
    @Singleton
    abstract fun bindChatVoice(impl: DefaultChatVoice): ChatVoice

    @Binds
    @Singleton
    abstract fun bindChatMedia(impl: DefaultChatMedia): ChatMedia
}
