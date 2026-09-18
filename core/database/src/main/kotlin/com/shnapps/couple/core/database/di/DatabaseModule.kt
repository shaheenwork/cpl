package com.shnapps.couple.core.database.di

import android.content.Context
import androidx.room.Room
import com.shnapps.couple.core.database.content.ContentDao
import com.shnapps.couple.core.database.content.ContentDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideContentDatabase(@ApplicationContext context: Context): ContentDatabase =
        Room.databaseBuilder(context, ContentDatabase::class.java, ContentDatabase.NAME)
            // Safe only because this database is a rebuildable cache (see ContentDatabase):
            // after a schema change the shipped bundle is simply installed again.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideContentDao(database: ContentDatabase): ContentDao = database.contentDao()
}
