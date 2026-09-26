package com.primenotes.bd.data.local.iosdb

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

/**
 * The iOS database's DAOs — `androidx.room3` copies of the four in
 * `com.primenotes.bd.data.local.dao`, which are the Android ones and stay untouched.
 *
 * Only the annotations differ. The queries, their column names and their semantics are Android's,
 * transcribed, because the point of the duplication is that the two databases answer the same
 * questions in the same way. `IosSchemaParityTest` guards the schema; these are the query half, and
 * a divergence here would be a behavioural one rather than a structural one, which is why the SQL is
 * kept line-for-line comparable with the Android DAOs.
 *
 * The SQLite behaviour that constrains this file, both established by measurement in Phase 3.5:
 *
 *  * A parameterised `MATCH` is rejected outright — FTS4 refuses a `?` in the MATCH position, so the
 *    search term is passed in already quoted, the way `FtsMatchQuery` does on Android.
 *  * `getText` on a NULL column yields `""`, not `null`, so anything that must distinguish "no
 *    formatting" from "an empty document" has to check `isNull`.
 */

@Dao
interface IosNoteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: IosNoteEntity)

    @Update
    suspend fun update(note: IosNoteEntity)

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun findById(id: String): IosNoteEntity?

    /** The live list: neither trashed nor permanently gone, pinned first. */
    @Query(
        "SELECT * FROM notes WHERE deleted_at IS NULL AND purged_at IS NULL " +
            "ORDER BY is_pinned DESC, updated_at DESC"
    )
    fun observeActive(): Flow<List<IosNoteEntity>>

    @Query("SELECT * FROM notes WHERE deleted_at IS NOT NULL AND purged_at IS NULL ORDER BY deleted_at DESC")
    fun observeTrashed(): Flow<List<IosNoteEntity>>

    /**
     * Full-text search over the external-content FTS4 table, joined on `docid` — the column an FTS4
     * external-content table exposes. FTS5 would use `rowid` here instead, which is one of the places
     * the two would differ.
     *
     * `FtsMatchQuery` is Android's; this copies its quoting rules rather than depending on it, because
     * the term has to be inlined into the SQL and a shared helper would be one more thing that could
     * drift between the two databases.
     */
    @Query(
        "SELECT notes.* FROM notes JOIN notes_fts ON notes.rowid = notes_fts.docid " +
            "WHERE notes_fts MATCH :query AND notes.deleted_at IS NULL AND notes.purged_at IS NULL " +
            "ORDER BY notes.is_pinned DESC, notes.updated_at DESC"
    )
    fun search(query: String): Flow<List<IosNoteEntity>>

    /** Soft delete — the tombstone that lets the deletion be told to other devices. */
    @Query("UPDATE notes SET deleted_at = :at, updated_at = :at, sync_status = :status WHERE id = :id")
    suspend fun softDelete(id: String, at: Long, status: String)

    /** Restored from the trash. */
    @Query("UPDATE notes SET deleted_at = NULL, updated_at = :at, sync_status = :status WHERE id = :id")
    suspend fun restore(id: String, at: Long, status: String)

    /** Purged for good; the row stays so the cloud can still be told. */
    @Query("UPDATE notes SET purged_at = :at WHERE id = :id")
    suspend fun purge(id: String, at: Long)

    @Query("SELECT * FROM notes WHERE purged_at IS NOT NULL")
    suspend fun purged(): List<IosNoteEntity>

    @Query("UPDATE notes SET title = :title, content = :content, updated_at = :at, sync_status = :status WHERE id = :id")
    suspend fun updateText(id: String, title: String, content: String, at: Long, status: String)

    @Query("SELECT COUNT(*) FROM notes")
    suspend fun count(): Int
}

@Dao
interface IosFolderDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: IosFolderEntity)

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun findById(id: String): IosFolderEntity?

    @Query("SELECT * FROM folders WHERE deleted_at IS NULL ORDER BY name COLLATE NOCASE ASC")
    fun observeActive(): Flow<List<IosFolderEntity>>
}

@Dao
interface IosTagDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tag: IosTagEntity)

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun findById(id: String): IosTagEntity?

    @Query("SELECT * FROM tags WHERE deleted_at IS NULL ORDER BY name COLLATE NOCASE ASC")
    fun observeActive(): Flow<List<IosTagEntity>>

    @Query("DELETE FROM note_tags WHERE tag_id = :tagId")
    suspend fun clearLinks(tagId: String)
}

@Dao
interface IosSyncDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCursor(cursor: IosSyncCursorEntity)

    @Query("SELECT * FROM sync_cursor WHERE account_id = :accountId AND entity = :entity")
    suspend fun cursor(accountId: String, entity: String): IosSyncCursorEntity?

    @Query("SELECT * FROM sync_cursor WHERE account_id = :accountId ORDER BY entity ASC")
    suspend fun allCursors(accountId: String): List<IosSyncCursorEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertState(state: IosSyncAccountStateEntity)

    @Query("SELECT * FROM sync_account_state WHERE account_id = :accountId")
    suspend fun state(accountId: String): IosSyncAccountStateEntity?
}
