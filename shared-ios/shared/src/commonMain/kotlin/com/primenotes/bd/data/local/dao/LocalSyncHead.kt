package com.primenotes.bd.data.local.dao

import com.primenotes.bd.domain.model.SyncStatus

/**
 * The only part of a local row a merge decision needs: its identity, its version,
 * and whether it has changes that have not been uploaded yet.
 *
 * Read in one query per page of pulled rows rather than one query per row, and kept
 * deliberately small so a pull over a whole collection is cheap.
 */
data class LocalSyncHead(
    val id: String,
    val updatedAt: Long,
    val revision: Long,
    val syncStatus: SyncStatus
)
