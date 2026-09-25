package com.primenotes.bd.domain.sync

import com.primenotes.bd.domain.model.Folder
import com.primenotes.bd.domain.model.Note
import com.primenotes.bd.domain.model.Tag
import kotlinx.coroutines.flow.Flow

/**
 * The device half of sync.
 *
 * This is the only part of the app that writes a row as uploaded. Everything else
 * marks the rows it touches as waiting, which is what keeps that column an honest
 * answer to "has this device sent the row yet?".
 *
 * An interface so the whole engine — the ordering, the merge rule, the revision
 * guard and every failure path — can be tested without a database.
 */
interface LocalSyncStore {

    // -----------------------------------------------------------------------
    // Where a pull picks up, and what the last pass did
    // -----------------------------------------------------------------------

    suspend fun cursor(accountId: String, entity: SyncEntity): Long?

    /**
     * Records how far this table has been read.
     *
     * [finishedAt] is the **server's** time when the pass began, and it is the only value
     * worth recording: the cloud stamps its rows from the same clock, so a cursor is
     * comparable with them and with nothing else.
     */
    suspend fun advanceCursor(accountId: String, entity: SyncEntity, finishedAt: Long)

    suspend fun recordSuccess(accountId: String, at: Long, conflicts: Int)

    suspend fun recordFailure(accountId: String, reason: String)

    /** The last pass for every account this device has synced, keyed by account id. */
    fun lastRuns(): Flow<Map<String, LastRun>>

    /** Rows waiting to be uploaded, across all three tables. */
    fun outstanding(): Flow<OutstandingWork>

    // -----------------------------------------------------------------------
    // Applying what came down
    // -----------------------------------------------------------------------

    /**
     * This device's copy of each row, reduced to what the decision needs.
     *
     * Read for a whole page at once rather than a row at a time.
     */
    suspend fun copies(entity: SyncEntity, ids: List<String>): Map<String, LocalCopy>

    /**
     * Writes the rows a pull decided to take, each recorded as uploaded.
     *
     * One transaction: a page either lands whole or not at all, so a failure part way
     * through cannot leave a note without its links.
     */
    suspend fun applyRemote(rows: List<RemoteRow>)

    /**
     * Writes [note] as a row of its own, with [tagIds], **waiting to be uploaded**.
     *
     * This is the one place a row from the cloud is written as unsettled rather than settled,
     * because a conflict copy is not the cloud's version of anything — it is a new note that
     * happens to hold what the cloud had, and it has to travel like any other local row.
     */
    suspend fun keepConflictCopy(note: Note, tagIds: List<String>)

    /** The note with [id] as this device holds it, tombstone or not, or null if it is gone. */
    suspend fun note(id: String): Note?

    /**
     * Deletes tombstones whose deletion has reached the cloud and that are older than
     * [before].
     *
     * @return how many rows were deleted.
     */
    suspend fun purgeTombstones(before: Long): Int

    // -----------------------------------------------------------------------
    // Sending what is here
    // -----------------------------------------------------------------------

    /**
     * Rows to upload: everything not already up there, owned by [accountId].
     *
     * The owner filter matters on a device used by more than one account — another
     * account's rows are not this account's to send, and the cloud would refuse them.
     */
    suspend fun foldersToPush(accountId: String): List<Folder>

    suspend fun tagsToPush(accountId: String): List<Tag>

    /**
     * Rows to upload: everything not already up there, owned by [accountId].
     *
     * The owner filter matters on a device used by more than one account — another
     * account's rows are not this account's to send, and the cloud would refuse them.
     *
     * A note its owner threw away is **not** in here, and that is the point: it is not a row to
     * send but a row to delete, and [notesToDelete] is where it is.
     */
    suspend fun notesToPush(accountId: String): List<Note>

    /**
     * The notes this device has to tell the cloud to delete, by id.
     *
     * These are the leftovers of a permanent deletion: a row the owner threw away, with its writing
     * already cleared and its links already gone, kept only until the cloud has been told. Ids
     * rather than notes, because there is nothing left worth reading — the row is an announcement,
     * and the whole of it is *which note*.
     *
     * One that has been refused is still here: a server that said no says nothing about the data,
     * and the deletion is not something to give up on.
     */
    suspend fun notesToDelete(accountId: String): List<String>

    /**
     * Forgets notes this device has finished telling the cloud about.
     *
     * Called on exactly one condition — the cloud confirmed the deletion — so a row removed here is
     * a row that no longer exists anywhere. Forgetting one before that would lose the deletion, and
     * the note would be back on the next pull.
     */
    suspend fun forgetNotes(ids: List<String>)

    /** The tags each of [noteIds] carries on this device. */
    suspend fun tagIdsForNotes(noteIds: Collection<String>): Map<String, List<String>>

    // -----------------------------------------------------------------------
    // What the upload did
    // -----------------------------------------------------------------------

    /**
     * Records rows as uploaded — but only those still holding the revision that was
     * sent. Anything edited while its own upload was in flight stays waiting, so the
     * edit goes out next pass rather than being lost.
     */
    suspend fun markSynced(entity: SyncEntity, rows: List<SyncHead>)

    /** Records that the server refused these rows. They stay in the queue for a retry. */
    suspend fun markFailed(entity: SyncEntity, ids: List<String>)
}
