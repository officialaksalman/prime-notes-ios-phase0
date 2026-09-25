package com.primenotes.bd.domain.repository

import com.primenotes.bd.domain.model.Tag
import com.primenotes.bd.domain.model.TagSummary
import kotlinx.coroutines.flow.Flow

interface TagRepository {

    fun observeTags(): Flow<List<Tag>>

    /** How many live tags there are. See [NoteRepository.observeActiveCount]. */
    fun observeTagCount(): Flow<Int>

    /** Tags with the number of live notes each is attached to, listed alphabetically. */
    fun observeTagSummaries(): Flow<List<TagSummary>>

    suspend fun getTag(id: String): Tag?

    /**
     * @throws IllegalArgumentException if [name] is blank.
     * @throws com.primenotes.bd.domain.model.DuplicateNameException if a live tag
     * already has this name, ignoring case.
     */
    suspend fun createTag(name: String): Tag

    /**
     * Returns null when the tag does not exist or is already deleted.
     *
     * @throws IllegalArgumentException if [name] is blank.
     * @throws com.primenotes.bd.domain.model.DuplicateNameException if another live
     * tag already has this name, ignoring case.
     */
    suspend fun renameTag(id: String, name: String): Tag?

    /**
     * Soft-deletes the tag and detaches it from every note atomically.
     *
     * The notes are left untouched; only the associations disappear.
     */
    suspend fun deleteTag(id: String)

    suspend fun getTagsPendingSync(): List<Tag>

    /** Everything deleted and not yet purged, most recently deleted first. */
    fun observeTrashed(): Flow<List<Tag>>

    /** How many tags are in the trash. See [observeTagCount]. */
    fun observeTrashedCount(): Flow<Int>

    /**
     * Brings a deleted tag back, attached to nothing.
     *
     * Its links are **not** restored: [deleteTag] dropped them deliberately, and a tag that
     * came back attached to notes nobody re-attached it to would be a worse surprise than an
     * empty one.
     *
     * @return the restored tag, or null when there is nothing to restore.
     * @throws com.primenotes.bd.domain.model.DuplicateNameException if a live tag now has this
     * name. See [FolderRepository.restoreFolder].
     */
    suspend fun restoreTag(id: String): Tag?

    /**
     * Deletes a tombstone for good, once its deletion has reached the cloud.
     *
     * See [NoteRepository.deleteForGood] — the same rule, and the same reason.
     *
     * @return true when the row was deleted.
     */
    suspend fun deleteForGood(id: String): Boolean

    /**
     * Gives every tag that has no owner to [userId]. See
     * [NoteRepository.claimUnowned] — the same rules apply: only `userId` changes.
     */
    suspend fun claimUnowned(userId: String): Int
}
