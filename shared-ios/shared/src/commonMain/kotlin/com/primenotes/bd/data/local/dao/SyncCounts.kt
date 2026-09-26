package com.primenotes.bd.data.local.dao

import androidx.room.ColumnInfo

/**
 * How many rows in one table are waiting to be uploaded, and how many of those the
 * server refused last time.
 *
 * Counted separately so the Sync card can say "3 waiting" and "2 could not be
 * uploaded" without either number hiding the other.
 */
data class SyncCounts(
    @ColumnInfo(name = "pending")
    val pending: Int,
    @ColumnInfo(name = "failed")
    val failed: Int
) {

    val outstanding: Int get() = pending + failed
}
