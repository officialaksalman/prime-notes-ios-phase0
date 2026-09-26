package com.primenotes.bd.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * What the last sync pass for an account did.
 *
 * Kept on disk rather than in a ViewModel so the Settings card can still say when the last
 * sync happened — and why it failed, and how many versions it had to keep — after the process
 * has been killed. [lastError] is the human sentence the cloud layer produced, not an
 * exception.
 */
@Entity(tableName = "sync_account_state")
data class SyncAccountStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "account_id")
    val accountId: String,
    @ColumnInfo(name = "last_synced_at")
    val lastSyncedAt: Long?,
    @ColumnInfo(name = "last_error")
    val lastError: String?,
    /**
     * How many versions that pass had to write a second copy of.
     *
     * Declared with a default because SQLite cannot add a `NOT NULL` column without one, and
     * because the column arrived after the table did.
     */
    @ColumnInfo(name = "last_conflicts", defaultValue = "0")
    val lastConflicts: Int = 0
)
