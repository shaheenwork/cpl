package com.shnapps.couple.core.datastore.di

import com.shnapps.couple.core.datastore.AppPreferencesDataSource
import com.shnapps.couple.core.datastore.AppPreferencesStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataStoreModule {

    @Binds
    @Singleton
    abstract fun bindAppPreferencesStore(impl: AppPreferencesDataSource): AppPreferencesStore
}
