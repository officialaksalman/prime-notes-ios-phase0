package com.primenotes.bd.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.primenotes.bd.data.local.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {

    @Query("SELECT * FROM folders WHERE deleted_at IS NULL ORDER BY name COLLATE NOCASE ASC")
    fun observeActive(): Flow<List<FolderEntity>>

    /** How many live folders there are, for the drawer's counter. See [NoteDao.observeActiveCount]. */
    @Query("SELECT COUNT(*) FROM folders WHERE deleted_at IS NULL")
    fun observeActiveCount(): Flow<Int>

    @Query("SELECT * FROM folders WHERE id = :id AND deleted_at IS NULL")
    suspend fun findById(id: String): FolderEntity?

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun findByIdIncludingDeleted(id: String): FolderEntity?

    /**
     * A live folder with the same name, ignoring case.
     *
     * [excludeId] lets a rename ignore the row being renamed, so changing only the
     * capitalisation of a folder's own name is not treated as a duplicate.
     */
    @Query(
        """
        SELECT * FROM folders
        WHERE deleted_at IS NULL AND name = :name COLLATE NOCASE AND id <> :excludeId
        LIMIT 1
        """
    )
    suspend fun findActiveByName(name: String, excludeId: String): FolderEntity?

    @Query("SELECT * FROM folders WHERE sync_status != 'SYNCED' ORDER BY updated_at ASC")
    suspend fun findPendingSync(): List<FolderEntity>

    /** Everything tombstoned, most recently deleted first. See [NoteDao.observeTrashed]. */
    @Query("SELECT * FROM folders WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC")
    fun observeTrashed(): Flow<List<FolderEntity>>

    /** How many folders are in the trash. See [NoteDao.observeActiveCount]. */
    @Query("SELECT COUNT(*) FROM folders WHERE deleted_at IS NOT NULL")
    fun observeTrashedCount(): Flow<Int>

    /** Every live folder, for a backup. See [NoteDao.findAllActive]. */
    @Query("SELECT * FROM folders WHERE deleted_at IS NULL")
    suspend fun findAllActive(): List<FolderEntity>

    @Query("SELECT id FROM folders")
    suspend fun findAllIds(): List<String>

    @Insert
    suspend fun insert(folder: FolderEntity)

    @Update
    suspend fun update(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Hands every unowned folder to [userId]. See [NoteDao.claimUnowned]. */
    @Query("UPDATE folders SET user_id = :userId WHERE user_id IS NULL")
    suspend fun claimUnowned(userId: String): Int
}
