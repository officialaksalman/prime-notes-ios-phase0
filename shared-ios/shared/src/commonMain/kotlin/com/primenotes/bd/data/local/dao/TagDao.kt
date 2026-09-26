package com.primenotes.bd.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.primenotes.bd.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Query("SELECT * FROM tags WHERE deleted_at IS NULL ORDER BY name COLLATE NOCASE ASC")
    fun observeActive(): Flow<List<TagEntity>>

    /** How many live tags there are, for the drawer's counter. See [NoteDao.observeActiveCount]. */
    @Query("SELECT COUNT(*) FROM tags WHERE deleted_at IS NULL")
    fun observeActiveCount(): Flow<Int>

    @Query("SELECT * FROM tags WHERE id = :id AND deleted_at IS NULL")
    suspend fun findById(id: String): TagEntity?

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun findByIdIncludingDeleted(id: String): TagEntity?

    /**
     * A live tag with the same name, ignoring case.
     *
     * [excludeId] lets a rename ignore the row being renamed, so changing only the
     * capitalisation of a tag's own name is not treated as a duplicate.
     */
    @Query(
        """
        SELECT * FROM tags
        WHERE deleted_at IS NULL AND name = :name COLLATE NOCASE AND id <> :excludeId
        LIMIT 1
        """
    )
    suspend fun findActiveByName(name: String, excludeId: String): TagEntity?

    @Query("SELECT * FROM tags WHERE sync_status != 'SYNCED' ORDER BY updated_at ASC")
    suspend fun findPendingSync(): List<TagEntity>

    /** Everything tombstoned, most recently deleted first. See [NoteDao.observeTrashed]. */
    @Query("SELECT * FROM tags WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC")
    fun observeTrashed(): Flow<List<TagEntity>>

    /** How many tags are in the trash. See [NoteDao.observeActiveCount]. */
    @Query("SELECT COUNT(*) FROM tags WHERE deleted_at IS NOT NULL")
    fun observeTrashedCount(): Flow<Int>

    /** Every live tag, for a backup. See [NoteDao.findAllActive]. */
    @Query("SELECT * FROM tags WHERE deleted_at IS NULL")
    suspend fun findAllActive(): List<TagEntity>

    @Query("SELECT id FROM tags")
    suspend fun findAllIds(): List<String>

    @Insert
    suspend fun insert(tag: TagEntity)

    @Update
    suspend fun update(tag: TagEntity)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Hands every unowned tag to [userId]. See [NoteDao.claimUnowned]. */
    @Query("UPDATE tags SET user_id = :userId WHERE user_id IS NULL")
    suspend fun claimUnowned(userId: String): Int
}
