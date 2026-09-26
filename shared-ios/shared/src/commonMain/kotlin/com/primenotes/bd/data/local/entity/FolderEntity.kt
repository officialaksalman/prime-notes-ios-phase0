package com.primenotes.bd.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.primenotes.bd.domain.model.SyncStatus

/**
 * A folder. Carries the same sync metadata as a note so that folders can be
 * synchronised and their deletions propagated without a later migration.
 */
@Entity(
    tableName = "folders",
    indices = [
        Index(value = ["deleted_at", "name"]),
        Index(value = ["sync_status"])
    ]
)
data class FolderEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "user_id")
    val userId: String?,
    val name: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus,
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long?,
    val revision: Long
)
