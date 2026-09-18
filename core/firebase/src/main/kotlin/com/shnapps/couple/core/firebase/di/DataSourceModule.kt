package com.shnapps.couple.core.firebase.di

import com.shnapps.couple.core.firebase.content.ContentRemoteDataSource
import com.shnapps.couple.core.firebase.content.FirebaseContentRemoteDataSource
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
}
