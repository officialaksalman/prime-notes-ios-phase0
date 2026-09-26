package com.primenotes.bd.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.primenotes.bd.domain.model.SyncStatus

/**
 * A tag. Mirrors [FolderEntity]'s shape so both can be synchronised identically.
 */
@Entity(
    tableName = "tags",
    indices = [
        Index(value = ["deleted_at", "name"]),
        Index(value = ["sync_status"])
    ]
)
data class TagEntity(
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
