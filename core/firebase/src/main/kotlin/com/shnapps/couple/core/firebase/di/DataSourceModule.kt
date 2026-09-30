package com.shnapps.couple.core.firebase.di

import com.shnapps.couple.core.firebase.account.AccountRemoteDataSource
import com.shnapps.couple.core.firebase.account.FirebaseAccountRemoteDataSource
import com.shnapps.couple.core.firebase.billing.BillingRemoteDataSource
import com.shnapps.couple.core.firebase.billing.FirebaseBillingRemoteDataSource
import com.shnapps.couple.core.firebase.content.ContentRemoteDataSource
import com.shnapps.couple.core.firebase.content.FirebaseContentRemoteDataSource
import com.shnapps.couple.core.firebase.game.FirebaseGameRemoteDataSource
import com.shnapps.couple.core.firebase.game.GameRemoteDataSource
import com.shnapps.couple.core.firebase.media.FirebaseMediaRemoteDataSource
import com.shnapps.couple.core.firebase.media.MediaRemoteDataSource
import com.shnapps.couple.core.firebase.night.FirebaseNightRemoteDataSource
import com.shnapps.couple.core.firebase.night.NightRemoteDataSource
import com.shnapps.couple.core.firebase.notifications.FirebasePushDataSource
import com.shnapps.couple.core.firebase.notifications.PushDataSource
import com.shnapps.couple.core.firebase.surprise.ArcRemoteDataSource
import com.shnapps.couple.core.firebase.surprise.FirebaseArcRemoteDataSource
import com.shnapps.couple.core.firebase.surprise.FirebaseSurpriseRemoteDataSource
import com.shnapps.couple.core.firebase.surprise.SurpriseRemoteDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Data sources that sit behind an interface, so the repositories using them can be tested with fakes. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataSourceModule {

    @Binds
    abstract fun bindContentRemoteDataSource(impl: FirebaseContentRemoteDataSource): ContentRemoteDataSource

    @Binds
    abstract fun bindNightRemoteDataSource(impl: FirebaseNightRemoteDataSource): NightRemoteDataSource

    @Binds
    abstract fun bindMediaRemoteDataSource(impl: FirebaseMediaRemoteDataSource): MediaRemoteDataSource

    @Binds
    abstract fun bindSurpriseRemoteDataSource(impl: FirebaseSurpriseRemoteDataSource): SurpriseRemoteDataSource

    @Binds
    abstract fun bindArcRemoteDataSource(impl: FirebaseArcRemoteDataSource): ArcRemoteDataSource

    @Binds
    abstract fun bindPushDataSource(impl: FirebasePushDataSource): PushDataSource

    @Binds
    abstract fun bindBillingRemoteDataSource(impl: FirebaseBillingRemoteDataSource): BillingRemoteDataSource

    @Binds
    abstract fun bindAccountRemoteDataSource(impl: FirebaseAccountRemoteDataSource): AccountRemoteDataSource

    @Binds
    abstract fun bindGameRemoteDataSource(impl: FirebaseGameRemoteDataSource): GameRemoteDataSource
}
