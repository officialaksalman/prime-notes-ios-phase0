package com.primenotes.bd.domain.repository

import com.primenotes.bd.domain.model.Folder
import com.primenotes.bd.domain.model.FolderSummary
import kotlinx.coroutines.flow.Flow

interface FolderRepository {

    fun observeFolders(): Flow<List<Folder>>

    /** How many live folders there are. See [NoteRepository.observeActiveCount]. */
    fun observeFolderCount(): Flow<Int>

    /** Folders with their live-note counts, listed alphabetically. */
    fun observeFolderSummaries(): Flow<List<FolderSummary>>

    suspend fun getFolder(id: String): Folder?

    /**
     * @throws IllegalArgumentException if [name] is blank.
     * @throws com.primenotes.bd.domain.model.DuplicateNameException if a live folder
     * already has this name, ignoring case.
     */
    suspend fun createFolder(name: String): Folder

    /**
     * Returns null when the folder does not exist or is already deleted.
     *
     * @throws IllegalArgumentException if [name] is blank.
     * @throws com.primenotes.bd.domain.model.DuplicateNameException if another live
     * folder already has this name, ignoring case.
     */
    suspend fun renameFolder(id: String, name: String): Folder?

    /**
     * Soft-deletes the folder and moves its notes out of it. The notes themselves
     * are never deleted.
     */
    suspend fun deleteFolder(id: String)

    suspend fun getFoldersPendingSync(): List<Folder>

    /** Everything deleted and not yet purged, most recently deleted first. */
    fun observeTrashed(): Flow<List<Folder>>

    /** How many folders are in the trash. See [observeFolderCount]. */
    fun observeTrashedCount(): Flow<Int>

    /**
     * Brings a deleted folder back, empty.
     *
     * Its notes are **not** in it: [deleteFolder] moved them out deliberately, so that no
     * content is ever carried away by a folder's deletion, and putting the name back cannot
     * undo something that never happened.
     *
     * @return the restored folder, or null when there is nothing to restore.
     * @throws com.primenotes.bd.domain.model.DuplicateNameException if a live folder now has
     * this name. Two folders cannot be told apart only by capitalisation, and restoring one
     * does not get to break that — the live one has to be renamed or deleted first.
     */
    suspend fun restoreFolder(id: String): Folder?

    /**
     * Deletes a tombstone for good, once its deletion has reached the cloud.
     *
     * See [NoteRepository.deleteForGood] — the same rule, and the same reason.
     *
     * @return true when the row was deleted.
     */
    suspend fun deleteForGood(id: String): Boolean

    /**
     * Gives every folder that has no owner to [userId]. See
     * [NoteRepository.claimUnowned] — the same rules apply: only `userId` changes.
     */
    suspend fun claimUnowned(userId: String): Int
}
