package phase35.probe

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

/** CRUD plus the two behaviours that must survive on iOS: soft delete, and full-text search. */
@Dao
interface NoteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity)

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun findById(id: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE deleted_at IS NULL AND purged_at IS NULL ORDER BY is_pinned DESC, updated_at DESC")
    fun observeActive(): Flow<List<NoteEntity>>

    @Query("UPDATE notes SET deleted_at = :at, updated_at = :at, sync_status = :status WHERE id = :id")
    suspend fun softDelete(id: String, at: Long, status: String)

    @Query("SELECT * FROM notes WHERE purged_at IS NOT NULL")
    suspend fun purged(): List<NoteEntity>

    /**
     * FTS4 over the external-content table, joining on `docid` — the column an FTS4 external-content
     * table exposes. FTS5 has no `docid` and uses `rowid` instead, so this line is one of the places
     * FTS4 and FTS5 differ, should the index ever move.
     */
    @Query("SELECT notes.* FROM notes JOIN notes_fts ON notes.rowid = notes_fts.docid WHERE notes_fts MATCH :query AND notes.deleted_at IS NULL")
    fun ftsSearch(query: String): Flow<List<NoteEntity>>

    @Query("UPDATE notes SET title = :title, content = :content, updated_at = :at WHERE id = :id")
    suspend fun updateText(id: String, title: String, content: String, at: Long)
}

@Dao
interface FolderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: FolderEntity)

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun findById(id: String): FolderEntity?
}

@Dao
interface SyncDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCursor(cursor: SyncCursorEntity)

    @Query("SELECT * FROM sync_cursor WHERE account_id = :accountId AND entity = :entity")
    suspend fun cursor(accountId: String, entity: String): SyncCursorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertState(state: SyncAccountStateEntity)

    @Query("SELECT * FROM sync_account_state WHERE account_id = :accountId")
    suspend fun state(accountId: String): SyncAccountStateEntity?
}
