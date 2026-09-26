package com.primenotes.bd.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * How far a pull has already read, for one account and one table.
 *
 * [pulledAt] is the newest `updated_at` seen on the cloud for that table, in epoch
 * milliseconds. It is a high-water mark, not a promise: a row can always be pulled
 * again, because applying a row this device already has is a no-op.
 *
 * One row per table rather than a single watermark across all three, because a
 * number shared between tables can be advanced by one table and then skip a row in
 * another. [entity] names the table it belongs to.
 */
@Entity(
    tableName = "sync_cursor",
    primaryKeys = ["account_id", "entity"]
)
data class SyncCursorEntity(
    @ColumnInfo(name = "account_id")
    val accountId: String,
    val entity: String,
    @ColumnInfo(name = "pulled_at")
    val pulledAt: Long
)
