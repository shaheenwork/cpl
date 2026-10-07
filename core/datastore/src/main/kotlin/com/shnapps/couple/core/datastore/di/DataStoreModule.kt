package com.shnapps.couple.core.datastore.di

import com.shnapps.couple.core.datastore.AppPreferencesDataSource
import com.shnapps.couple.core.datastore.AppPreferencesStore
import com.shnapps.couple.core.datastore.PlaySettingsStore
import com.shnapps.couple.core.datastore.PurchaseStore
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

    /** The same object, under the name the play screens ask for. */
    @Binds
    @Singleton
    abstract fun bindPlaySettingsStore(impl: AppPreferencesDataSource): PlaySettingsStore

    /** And again, under the name the store asks for. */
    @Binds
    @Singleton
    abstract fun bindPurchaseStore(impl: AppPreferencesDataSource): PurchaseStore
}
