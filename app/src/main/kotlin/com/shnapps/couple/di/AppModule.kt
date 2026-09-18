package com.shnapps.couple.di

import com.shnapps.couple.BuildConfig
import com.shnapps.couple.core.common.ApplicationScope
import com.shnapps.couple.core.common.Clock
import com.shnapps.couple.core.common.DefaultDispatcherProvider
import com.shnapps.couple.core.common.DispatcherProvider
import com.shnapps.couple.core.common.SystemClock
import com.shnapps.couple.core.firebase.FirebaseEnvironment
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * :app is the only module that reads BuildConfig. Everything downstream receives plain
 * data (here, [FirebaseEnvironment]) so that no core or feature module has to know which
 * build variant it is running in.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideFirebaseEnvironment(): FirebaseEnvironment = FirebaseEnvironment(
        name = BuildConfig.ENVIRONMENT,
        useEmulator = BuildConfig.USE_FIREBASE_EMULATOR,
    )

    @Provides
    @Singleton
    fun provideDispatcherProvider(): DispatcherProvider = DefaultDispatcherProvider()

    @Provides
    @Singleton
    fun provideClock(): Clock = SystemClock

    /**
     * Application-lifetime scope for work that must outlive any screen, such as the
     * initial Remote Config fetch. SupervisorJob so one failure does not cancel the rest.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(dispatchers: DispatcherProvider): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatchers.default)
}
