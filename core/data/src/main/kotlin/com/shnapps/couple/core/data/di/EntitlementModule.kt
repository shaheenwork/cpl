package com.shnapps.couple.core.data.di

import com.shnapps.couple.core.data.entitlement.DefaultLevelAccessRepository
import com.shnapps.couple.core.data.entitlement.LaunchOffer
import com.shnapps.couple.core.data.entitlement.LevelAccessRepository
import com.shnapps.couple.core.data.entitlement.RemoteLaunchOffer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * What this phone may play, and what it had to pay for (V2 rule 5).
 *
 * Its own module rather than three more lines in [DataModule], which is at the point where one
 * more binding of anything is one too many to read.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class EntitlementModule {

    @Binds
    @Singleton
    abstract fun bindLevelAccessRepository(impl: DefaultLevelAccessRepository): LevelAccessRepository

    @Binds
    abstract fun bindLaunchOffer(impl: RemoteLaunchOffer): LaunchOffer
}
