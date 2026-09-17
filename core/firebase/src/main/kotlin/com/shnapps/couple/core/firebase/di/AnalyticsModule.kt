package com.shnapps.couple.core.firebase.di

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import com.shnapps.couple.core.analytics.AnalyticsLogger
import com.shnapps.couple.core.firebase.analytics.FirebaseAnalyticsLogger
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AnalyticsProvidesModule {

    @Provides
    @Singleton
    fun provideFirebaseAnalytics(@ApplicationContext context: Context): FirebaseAnalytics =
        FirebaseAnalytics.getInstance(context)
}

/**
 * Binds the app's only analytics entry point. Everything upstream depends on the
 * [AnalyticsLogger] interface from :core:analytics and never on Firebase.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AnalyticsBindsModule {

    @Binds
    @Singleton
    abstract fun bindAnalyticsLogger(impl: FirebaseAnalyticsLogger): AnalyticsLogger
}
