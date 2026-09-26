package com.primenotes.bd.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.primenotes.bd.data.local.entity.NoteEntity
import com.primenotes.bd.data.local.entity.NoteTagCrossRef
import com.primenotes.bd.data.local.entity.TagEntity
import com.primenotes.bd.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * Note reads all exclude tombstoned rows: a soft-deleted note must disappear from
 * the UI the moment it is deleted, long before it is purged after syncing.
 */
@Dao
interface NoteDao {

    @Query(
        """
        SELECT * FROM notes
        WHERE deleted_at IS NULL
        ORDER BY is_pinned DESC, updated_at DESC
        """
    )
    fun observeActive(): Flow<List<NoteEntity>>

    /**
     * How many live notes there are, for the drawer's counter.
     *
     * Counted in SQL rather than by reading the rows: the drawer wants a number, not a note, and
     * `COUNT(*)` answers without materialising anything. One table per query, which is the rule
     * [SyncDao] documents — a query across several tables leaves Room guessing at which ones it
     * has to watch.
     */
    @Query("SELECT COUNT(*) FROM notes WHERE deleted_at IS NULL")
    fun observeActiveCount(): Flow<Int>

    /** How many live notes are favourites, for the drawer's counter. See [observeActiveCount]. */
    @Query("SELECT COUNT(*) FROM notes WHERE deleted_at IS NULL AND is_favorite = 1")
    fun observeFavoriteCount(): Flow<Int>

    /** How many live notes are locked, for the drawer's counter. See [observeActiveCount]. */
    @Query("SELECT COUNT(*) FROM notes WHERE deleted_at IS NULL AND is_locked = 1")
    fun observeLockedCount(): Flow<Int>

    @Query(
        """
        SELECT * FROM notes
        WHERE deleted_at IS NULL AND folder_id = :folderId
        ORDER BY is_pinned DESC, updated_at DESC
        """
    )
    fun observeInFolder(folderId: String): Flow<List<NoteEntity>>

    @Query(
        """
        SELECT notes.* FROM notes
        INNER JOIN note_tags ON note_tags.note_id = notes.id
        WHERE note_tags.tag_id = :tagId AND notes.deleted_at IS NULL
        ORDER BY notes.is_pinned DESC, notes.updated_at DESC
        """
    )
    fun observeInTag(tagId: String): Flow<List<NoteEntity>>

    /**
     * How many live notes each folder holds.
     *
     * Notes in no folder are excluded by the `IS NOT NULL` guard, and tombstoned
     * notes never count towards a folder.
     */
    @Query(
        """
        SELECT folder_id AS id, COUNT(*) AS count FROM notes
        WHERE deleted_at IS NULL AND folder_id IS NOT NULL
        GROUP BY folder_id
        """
    )
    fun observeNoteCountsByFolder(): Flow<List<IdCount>>

    /**
     * How many live notes each tag is attached to. The join to `notes` is what
     * excludes a tag whose only notes have been tombstoned.
     */
    @Query(
        """
        SELECT note_tags.tag_id AS id, COUNT(*) AS count FROM note_tags
        INNER JOIN notes ON notes.id = note_tags.note_id
        WHERE notes.deleted_at IS NULL
        GROUP BY note_tags.tag_id
        """
    )
    fun observeNoteCountsByTag(): Flow<List<IdCount>>

    @Query("SELECT * FROM notes WHERE id = :id AND deleted_at IS NULL")
    fun observeById(id: String): Flow<NoteEntity?>

    /**
     * Every note search may read: live, and not locked.
     *
     * The two exclusions are here, in SQL, rather than wherever the results are drawn — a locked
     * note is never read at all, so its words cannot reach a count, a snippet, a log or a screen.
     * That is the same rule the full-text query used to carry, kept where it was.
     *
     * What matches is decided by the caller, not by SQLite: a case-insensitive search *inside* a
     * word is not something SQL can do beyond ASCII, and this app is used in Bengali as well as
     * English. The set is the device's own notes, so this reads exactly what the notes list already
     * reads — no more.
     */
    @Query(
        """
        SELECT * FROM notes
        WHERE deleted_at IS NULL AND is_locked = 0
        ORDER BY is_pinned DESC, updated_at DESC
        """
    )
    fun observeSearchable(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun findById(id: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE sync_status != 'SYNCED' ORDER BY updated_at ASC")
    suspend fun findPendingSync(): List<NoteEntity>

    /**
     * Everything tombstoned, most recently deleted first.
     *
     * The only read in the app that does not filter `deleted_at IS NULL`: every other one
     * deliberately hides a deleted note, and this is the one screen whose subject is the
     * deleted ones.
     *
     * A **purged** row is excluded, and that is the whole difference between the trash and a
     * permanent deletion: the row is still here — it has to be, until a pass has told the cloud —
     * but it is gone as far as anyone can see, and this screen is where that has to be true first.
     */
    @Query(
        """
        SELECT * FROM notes
        WHERE deleted_at IS NOT NULL AND purged_at IS NULL
        ORDER BY deleted_at DESC
        """
    )
    fun observeTrashed(): Flow<List<NoteEntity>>

    /** How many notes are in the trash. See [observeActiveCount] and [observeTrashed]. */
    @Query("SELECT COUNT(*) FROM notes WHERE deleted_at IS NOT NULL AND purged_at IS NULL")
    fun observeTrashedCount(): Flow<Int>

    /**
     * Trash that has reached the end of its thirty days, oldest first.
     *
     * Read once when the app starts, so a deletion cannot outlive its month just because the
     * device was never synced — see `TrashPolicy`. Purged rows are left out: they are already
     * gone as far as the app is concerned, and their announcement is a pass's business.
     */
    @Query(
        """
        SELECT * FROM notes
        WHERE deleted_at IS NOT NULL AND purged_at IS NULL AND deleted_at < :before
        ORDER BY deleted_at ASC
        """
    )
    suspend fun findTrashedBefore(before: Long): List<NoteEntity>

    // -----------------------------------------------------------------------
    // Backups
    //
    // A backup is of what exists, so it reads the live rows; the ids are read separately and
    // include the tombstones, because a deleted row still owns its id and an import has to know
    // that before it tries to use one.
    // -----------------------------------------------------------------------

    @Query("SELECT * FROM notes WHERE deleted_at IS NULL")
    suspend fun findAllActive(): List<NoteEntity>

    @Query("SELECT id FROM notes")
    suspend fun findAllIds(): List<String>

    @Query("SELECT * FROM note_tags")
    suspend fun findAllLinks(): List<NoteTagCrossRef>

    @Query("SELECT * FROM notes WHERE folder_id = :folderId AND deleted_at IS NULL")
    suspend fun findInFolder(folderId: String): List<NoteEntity>

    @Insert
    suspend fun insert(note: NoteEntity)

    @Update
    suspend fun update(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Moves every live note out of a folder. This cannot be left to the foreign
     * key's ON DELETE SET NULL, because folders are soft-deleted and the FK only
     * fires on a real row deletion.
     */
    @Query(
        """
        UPDATE notes
        SET folder_id = NULL,
            updated_at = :updatedAt,
            revision = revision + 1,
            sync_status = :syncStatus
        WHERE folder_id = :folderId AND deleted_at IS NULL
        """
    )
    suspend fun clearFolder(folderId: String, updatedAt: Long, syncStatus: SyncStatus)

    @Query(
        """
        SELECT tags.* FROM tags
        INNER JOIN note_tags ON note_tags.tag_id = tags.id
        WHERE note_tags.note_id = :noteId AND tags.deleted_at IS NULL
        ORDER BY tags.name COLLATE NOCASE ASC
        """
    )
    fun observeTagsForNote(noteId: String): Flow<List<TagEntity>>

    @Query("SELECT note_id FROM note_tags WHERE tag_id = :tagId")
    suspend fun findNoteIdsForTag(tagId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNoteTags(links: List<NoteTagCrossRef>)

    @Query("DELETE FROM note_tags WHERE note_id = :noteId")
    suspend fun deleteNoteTagsForNote(noteId: String)

    @Query("DELETE FROM note_tags WHERE tag_id = :tagId")
    suspend fun deleteNoteTagsForTag(tagId: String)

    /**
     * Hands every unowned note to [userId] and reports how many were claimed.
     *
     * Only `user_id` is written. `updated_at`, `revision` and `sync_status` are
     * deliberately left alone — claiming is not an edit, and marking rows pending
     * would put a lie into the sync accounting.
     *
     * Tombstoned notes are claimed too: a deleted note still belongs to the account
     * that deleted it, and that deletion has to reach the cloud. Rows that already
     * have an owner are not touched at all, which is what `user_id IS NULL` protects.
     */
    @Query("UPDATE notes SET user_id = :userId WHERE user_id IS NULL")
    suspend fun claimUnowned(userId: String): Int
}
