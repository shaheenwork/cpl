package com.shnapps.couple.core.database.content

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One item of the installed content bundle (BUILD_PROMPT.md §9.2).
 *
 * The item is kept as the JSON the bundle carried, fields this version does not know
 * included, and parsed by :core:data. The columns beside it are only what queries and the
 * delta overlay need, so a new content field never needs a schema migration.
 */
@Entity(tableName = "content_items", indices = [Index("pack")])
data class ContentItemEntity(
    @PrimaryKey val id: String,
    val version: Int,
    val pack: String,
    val status: String,
    val json: String,
)

/**
 * An admin override from Firestore `content/{id}` (§9.2, §9.6): a status, and optionally an
 * edited copy of the item. It applies to bundled items of [version] and earlier, so it
 * outlives bundle updates until the edit is baked into a later item version.
 */
@Entity(tableName = "content_deltas")
data class ContentDeltaEntity(
    @PrimaryKey val id: String,
    val status: String,
    val version: Int,
    val itemJson: String?,
    val updatedAtMillis: Long,
)

/** The installed bundle's header. A single row. */
@Entity(tableName = "content_bundle")
data class ContentBundleEntity(
    @PrimaryKey val id: Int = SINGLE_ROW,
    val format: Int,
    val contentVersion: Int,
    val taxonomyVersion: Int,
    /** The taxonomy travels in the bundle, so it updates with the content (§9.5). */
    val taxonomyJson: String,
    val packsJson: String,
    /** When content was last checked against the server; null if never. */
    val lastSyncMillis: Long?,
    /** Deltas updated at or after this instant have not been fetched yet. */
    val deltaCursorMillis: Long,
) {
    companion object {
        const val SINGLE_ROW = 0
    }
}
