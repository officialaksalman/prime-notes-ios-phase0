package com.primenotes.bd.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.primenotes.bd.data.local.entity.FolderEntity
import com.primenotes.bd.data.local.entity.NoteEntity
import com.primenotes.bd.data.local.entity.NoteTagCrossRef
import com.primenotes.bd.data.local.entity.SyncAccountStateEntity
import com.primenotes.bd.data.local.entity.SyncCursorEntity
import com.primenotes.bd.data.local.entity.TagEntity
import com.primenotes.bd.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * The local half of sync.
 *
 * This is the only place in the app that writes `SYNCED`. Every other writer — the
 * three offline repositories — marks the rows it touches `PENDING`, which is what
 * makes `sync_status` an honest answer to "has this device uploaded the row yet?".
 *
 * Status values are passed in rather than written into the SQL, so the persisted
 * contract stays in [SyncStatus] alone.
 */
@Dao
interface SyncDao {

    // -----------------------------------------------------------------------
    // Cursors and the account's own state
    // -----------------------------------------------------------------------

    @Query("SELECT * FROM sync_cursor WHERE account_id = :accountId")
    suspend fun cursors(accountId: String): List<SyncCursorEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putCursor(cursor: SyncCursorEntity)

    @Query("SELECT * FROM sync_account_state")
    fun observeAccountStates(): Flow<List<SyncAccountStateEntity>>

    @Query("SELECT * FROM sync_account_state WHERE account_id = :accountId")
    suspend fun accountState(accountId: String): SyncAccountStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAccountState(state: SyncAccountStateEntity)

    // -----------------------------------------------------------------------
    // What is waiting, for the Sync card
    //
    // One table per query so Room registers exactly the tables it must watch; a
    // single query across all three would have to guess at invalidation.
    // -----------------------------------------------------------------------

    @Query(
        """
        SELECT COUNT(CASE WHEN sync_status = :pending THEN 1 END) AS pending,
               COUNT(CASE WHEN sync_status = :failed THEN 1 END) AS failed
        FROM notes
        """
    )
    fun observeNoteCounts(pending: SyncStatus, failed: SyncStatus): Flow<SyncCounts>

    @Query(
        """
        SELECT COUNT(CASE WHEN sync_status = :pending THEN 1 END) AS pending,
               COUNT(CASE WHEN sync_status = :failed THEN 1 END) AS failed
        FROM folders
        """
    )
    fun observeFolderCounts(pending: SyncStatus, failed: SyncStatus): Flow<SyncCounts>

    @Query(
        """
        SELECT COUNT(CASE WHEN sync_status = :pending THEN 1 END) AS pending,
               COUNT(CASE WHEN sync_status = :failed THEN 1 END) AS failed
        FROM tags
        """
    )
    fun observeTagCounts(pending: SyncStatus, failed: SyncStatus): Flow<SyncCounts>

    // -----------------------------------------------------------------------
    // What this device has to say about the rows a pull returned
    // -----------------------------------------------------------------------

    @Query(
        """
        SELECT id, updated_at AS updatedAt, revision, sync_status AS syncStatus
        FROM notes WHERE id IN (:ids)
        """
    )
    suspend fun noteHeads(ids: List<String>): List<LocalSyncHead>

    /** One note as this device holds it, tombstone or not. */
    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun note(id: String): NoteEntity?

    @Query(
        """
        SELECT id, updated_at AS updatedAt, revision, sync_status AS syncStatus
        FROM folders WHERE id IN (:ids)
        """
    )
    suspend fun folderHeads(ids: List<String>): List<LocalSyncHead>

    @Query(
        """
        SELECT id, updated_at AS updatedAt, revision, sync_status AS syncStatus
        FROM tags WHERE id IN (:ids)
        """
    )
    suspend fun tagHeads(ids: List<String>): List<LocalSyncHead>

    // -----------------------------------------------------------------------
    // Applying what came down
    //
    // `@Upsert` rather than `@Insert(REPLACE)`: SQLite implements REPLACE by
    // deleting the old row and inserting a new one, and a deleted note would take
    // its `note_tags` links with it through the foreign key's cascade. Upsert
    // updates in place and leaves the links alone.
    // -----------------------------------------------------------------------

    @Upsert
    suspend fun upsertNotes(rows: List<NoteEntity>)

    @Upsert
    suspend fun upsertFolders(rows: List<FolderEntity>)

    @Upsert
    suspend fun upsertTags(rows: List<TagEntity>)

    @Query("SELECT * FROM note_tags WHERE note_id = :noteId")
    suspend fun linksForNote(noteId: String): List<NoteTagCrossRef>

    @Query("SELECT * FROM note_tags WHERE note_id IN (:noteIds)")
    suspend fun linksForNotes(noteIds: List<String>): List<NoteTagCrossRef>

    @Query("DELETE FROM note_tags WHERE note_id = :noteId")
    suspend fun deleteLinksForNote(noteId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLinks(links: List<NoteTagCrossRef>)

    // -----------------------------------------------------------------------
    // What this device has to say to the cloud
    // -----------------------------------------------------------------------

    /**
     * Rows to upload: everything not already up there, belonging to [accountId].
     *
     * The owner filter matters on a device that has been used by more than one
     * account — rows belonging to someone else are not this account's to upload,
     * and row-level security would refuse them anyway.
     *
     * `purged_at IS NULL` is what keeps a note its owner threw away out of the upload queue. That
     * row is not a row to send — sending it is what used to leave a table full of notes that are not
     * notes — it is a **deletion** to perform, and [notesToDelete] is where it is looked for.
     */
    @Query(
        """
        SELECT * FROM notes
        WHERE sync_status != :synced AND user_id = :accountId AND purged_at IS NULL
        ORDER BY updated_at ASC
        """
    )
    suspend fun notesToPush(accountId: String, synced: SyncStatus): List<NoteEntity>

    /**
     * The notes a device still has to tell the cloud to delete, oldest first.
     *
     * The mirror of [notesToPush], and the only place a purged row is still a row. Anything not
     * already confirmed gone is here — including a `FAILED` one, because a server that refused says
     * something about the request and nothing about the data, and the deletion is still owed.
     */
    @Query(
        """
        SELECT id FROM notes
        WHERE purged_at IS NOT NULL AND user_id = :accountId AND sync_status != :synced
        ORDER BY purged_at ASC
        """
    )
    suspend fun notesAwaitingDeletion(accountId: String, synced: SyncStatus): List<String>

    /**
     * Removes rows the cloud has confirmed deleted.
     *
     * A real delete, not a tombstone: the deletion is done, so there is nothing left to remember.
     * `note_tags` goes with the row through the foreign key's cascade.
     */
    @Query("DELETE FROM notes WHERE id IN (:ids)")
    suspend fun deleteNotesByIds(ids: List<String>)

    @Query(
        """
        SELECT * FROM folders
        WHERE sync_status != :synced AND user_id = :accountId
        ORDER BY updated_at ASC
        """
    )
    suspend fun foldersToPush(accountId: String, synced: SyncStatus): List<FolderEntity>

    @Query(
        """
        SELECT * FROM tags
        WHERE sync_status != :synced AND user_id = :accountId
        ORDER BY updated_at ASC
        """
    )
    suspend fun tagsToPush(accountId: String, synced: SyncStatus): List<TagEntity>

    /**
     * Marks a row uploaded — but only if it still holds the revision that was
     * uploaded.
     *
     * A row edited while its own upload was in flight has a newer revision, so this
     * deliberately does nothing to it: the edit stays `PENDING` and goes out on the
     * next pass instead of being silently recorded as uploaded.
     */
    @Query("UPDATE notes SET sync_status = :synced WHERE id = :id AND revision = :revision")
    suspend fun markNoteSynced(id: String, revision: Long, synced: SyncStatus): Int

    @Query("UPDATE folders SET sync_status = :synced WHERE id = :id AND revision = :revision")
    suspend fun markFolderSynced(id: String, revision: Long, synced: SyncStatus): Int

    @Query("UPDATE tags SET sync_status = :synced WHERE id = :id AND revision = :revision")
    suspend fun markTagSynced(id: String, revision: Long, synced: SyncStatus): Int

    /**
     * Records that the server refused a row.
     *
     * Only a `PENDING` row is marked, so a row that has since been uploaded — or
     * edited, and is waiting again — is never called failed. A failed row is still
     * picked up by the queries above, so it is retried rather than abandoned.
     */
    @Query("UPDATE notes SET sync_status = :failed WHERE id = :id AND sync_status = :pending")
    suspend fun markNoteFailed(id: String, pending: SyncStatus, failed: SyncStatus): Int

    @Query("UPDATE folders SET sync_status = :failed WHERE id = :id AND sync_status = :pending")
    suspend fun markFolderFailed(id: String, pending: SyncStatus, failed: SyncStatus): Int

    @Query("UPDATE tags SET sync_status = :failed WHERE id = :id AND sync_status = :pending")
    suspend fun markTagFailed(id: String, pending: SyncStatus, failed: SyncStatus): Int

    // -----------------------------------------------------------------------
    // Deleting what has been dealt with
    //
    // A tombstone is only ever written: every read of these tables filters `deleted_at IS
    // NULL`, so removing one is invisible to the whole app. It may only go when
    // `sync_status` says its deletion actually reached the cloud — deleting a tombstone the
    // cloud has not seen would lose the deletion itself and leave the note alive on every
    // other device, which is the one thing a purge must never do.
    // -----------------------------------------------------------------------

    @Query(
        """
        DELETE FROM notes
        WHERE deleted_at IS NOT NULL AND deleted_at < :before AND sync_status = :synced
        """
    )
    suspend fun purgeNotes(before: Long, synced: SyncStatus): Int

    @Query(
        """
        DELETE FROM folders
        WHERE deleted_at IS NOT NULL AND deleted_at < :before AND sync_status = :synced
        """
    )
    suspend fun purgeFolders(before: Long, synced: SyncStatus): Int

    @Query(
        """
        DELETE FROM tags
        WHERE deleted_at IS NOT NULL AND deleted_at < :before AND sync_status = :synced
        """
    )
    suspend fun purgeTags(before: Long, synced: SyncStatus): Int
}
