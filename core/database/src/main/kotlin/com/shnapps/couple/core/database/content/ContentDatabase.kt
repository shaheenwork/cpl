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
    entities = [
        ContentItemEntity::class,
        ContentPromptEntity::class,
        ContentDeltaEntity::class,
        ContentBundleEntity::class,
    ],
    // 2: game prompts and games (Phase 11).
    // 3: which build's assets a shipped bundle came from (D-131).
    // Dropped and reinstalled from the bundle, as any version change here is (DatabaseModule).
    version = 3,
    exportSchema = true,
)
abstract class ContentDatabase : RoomDatabase() {
    abstract fun contentDao(): ContentDao

    companion object {
        const val NAME = "content.db"
    }
}
