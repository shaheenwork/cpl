package com.shnapps.couple.core.database.content

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ContentDao {

    @Query("SELECT * FROM content_items ORDER BY pack, id")
    fun observeItems(): Flow<List<ContentItemEntity>>

    @Query("SELECT * FROM content_deltas")
    fun observeDeltas(): Flow<List<ContentDeltaEntity>>

    @Query("SELECT * FROM content_bundle WHERE id = 0")
    fun observeBundle(): Flow<ContentBundleEntity?>

    @Query("SELECT * FROM content_bundle WHERE id = 0")
    suspend fun bundle(): ContentBundleEntity?

    /**
     * Swaps in a whole bundle at once: a reader sees the old bundle or the new one, never a
     * mixture. Deltas are kept; they carry their own versions.
     */
    @Transaction
    suspend fun installBundle(bundle: ContentBundleEntity, items: List<ContentItemEntity>) {
        deleteItems()
        insertItems(items)
        upsertBundle(bundle)
    }

    @Upsert
    suspend fun upsertDeltas(deltas: List<ContentDeltaEntity>)

    @Query(
        "UPDATE content_bundle SET lastSyncMillis = :syncedAtMillis, deltaCursorMillis = :deltaCursorMillis " +
            "WHERE id = 0",
    )
    suspend fun markSynced(syncedAtMillis: Long, deltaCursorMillis: Long)

    @Query("DELETE FROM content_items")
    suspend fun deleteItems()

    @Insert
    suspend fun insertItems(items: List<ContentItemEntity>)

    @Upsert
    suspend fun upsertBundle(bundle: ContentBundleEntity)
}
