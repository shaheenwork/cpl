package com.shnapps.couple.core.database.content

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The content cache (BUILD_PROMPT.md §9.2): the installed bundle and the deltas on top.
 *
 * A database of its own because everything in it can be rebuilt — from the bundle shipped
 * in the app, or by downloading again — so a schema change may simply drop it. Data that
 * cannot be rebuilt belongs in a database with real migrations, not here.
 */
@Database(
    entities = [ContentItemEntity::class, ContentDeltaEntity::class, ContentBundleEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ContentDatabase : RoomDatabase() {
    abstract fun contentDao(): ContentDao

    companion object {
        const val NAME = "content.db"
    }
}
