package com.primenotes.bd.domain.sync

import com.primenotes.bd.domain.model.Folder
import com.primenotes.bd.domain.model.Note
import com.primenotes.bd.domain.model.Tag

/**
 * The part of a row a merge decision compares: which row it is, how new this
 * device's copy is, and how many times it has been edited.
 *
 * `revision` is the tie-breaker rather than decoration. Two devices can produce the
 * same millisecond, and a timestamp alone would leave the outcome of a tie to
 * whichever row happened to be read first.
 */
data class SyncHead(
    val id: String,
    val updatedAt: Long,
    val revision: Long
) {

    /**
     * Strictly newer — so two copies of the same row are never a reason to write
     * anything, and a pull that re-reads a row it already has does nothing at all.
     */
    fun isNewerThan(other: SyncHead): Boolean =
        updatedAt > other.updatedAt || (updatedAt == other.updatedAt && revision > other.revision)
}

/**
 * This device's copy of a row, reduced to what the decision needs.
 *
 * [isSynced] is the whole of the local-wins rule: a row with changes that have not
 * been uploaded yet is never replaced by something a pull brought back.
 */
data class LocalCopy(
    val head: SyncHead,
    val isSynced: Boolean
)

val Note.syncHead: SyncHead get() = SyncHead(id = id, updatedAt = updatedAt, revision = revision)

val Folder.syncHead: SyncHead get() = SyncHead(id = id, updatedAt = updatedAt, revision = revision)

val Tag.syncHead: SyncHead get() = SyncHead(id = id, updatedAt = updatedAt, revision = revision)
