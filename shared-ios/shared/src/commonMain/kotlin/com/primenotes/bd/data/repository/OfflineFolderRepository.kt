package com.primenotes.bd.data.repository

import com.primenotes.bd.data.TransactionRunner
import com.primenotes.bd.data.local.dao.FolderDao
import com.primenotes.bd.data.local.dao.NoteDao
import com.primenotes.bd.data.local.mapper.toDomain
import com.primenotes.bd.data.local.mapper.toEntity
import com.primenotes.bd.domain.IdGenerator
import com.primenotes.bd.domain.model.canPurge
import com.primenotes.bd.domain.model.DuplicateNameException
import com.primenotes.bd.domain.model.Folder
import com.primenotes.bd.domain.model.FolderSummary
import com.primenotes.bd.domain.model.SyncStatus
import com.primenotes.bd.domain.repository.FolderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import com.primenotes.bd.domain.TimeSource

class OfflineFolderRepository(
    private val folderDao: FolderDao,
    private val noteDao: NoteDao,
    private val transactions: TransactionRunner,
    private val clock: TimeSource,
    private val idGenerator: IdGenerator
) : FolderRepository {

    override fun observeFolders(): Flow<List<Folder>> =
        folderDao.observeActive().map { folders -> folders.map { it.toDomain() } }

    override fun observeFolderCount(): Flow<Int> = folderDao.observeActiveCount()

    /**
     * Counts arrive as one grouped query rather than a query per folder, and a
     * folder with no notes simply has no row in the count map.
     */
    override fun observeFolderSummaries(): Flow<List<FolderSummary>> =
        combine(
            folderDao.observeActive(),
            noteDao.observeNoteCountsByFolder()
        ) { folders, counts ->
            val countById = counts.associate { count -> count.id to count.count }
            folders.map { entity ->
                FolderSummary(folder = entity.toDomain(), noteCount = countById[entity.id] ?: 0)
            }
        }

    override suspend fun getFolder(id: String): Folder? = folderDao.findById(id)?.toDomain()

    override suspend fun createFolder(name: String): Folder {
        val cleanName = name.requireValidName("Folder")
        requireNoDuplicate(cleanName, excludeId = "")
        val now = clock.nowMillis()
        val folder = Folder(
            id = idGenerator.newId(),
            name = cleanName,
            createdAt = now,
            updatedAt = now,
            syncStatus = SyncStatus.PENDING
        )
        folderDao.insert(folder.toEntity())
        return folder
    }

    override suspend fun renameFolder(id: String, name: String): Folder? {
        val existing = folderDao.findById(id)?.toDomain() ?: return null
        val cleanName = name.requireValidName("Folder")
        // Excluding the row itself means a case-only change (Work -> WORK) is allowed.
        requireNoDuplicate(cleanName, excludeId = id)
        val renamed = existing.copy(
            name = cleanName,
            updatedAt = clock.nowMillis(),
            revision = existing.revision + 1,
            syncStatus = SyncStatus.PENDING
        )
        folderDao.update(renamed.toEntity())
        return renamed
    }

    /**
     * Tombstones the folder and moves its notes out of it in one transaction.
     *
     * The foreign key's ON DELETE SET NULL cannot do this, because a soft delete
     * never removes the folder row. The notes survive untouched â€” only their
     * folder assignment is cleared.
     */
    override suspend fun deleteFolder(id: String) {
        val existing = folderDao.findById(id)?.toDomain() ?: return
        val now = clock.nowMillis()

        transactions.inTransaction {
            noteDao.clearFolder(id, updatedAt = now, syncStatus = SyncStatus.PENDING)
            folderDao.update(
                existing.copy(
                    deletedAt = now,
                    updatedAt = now,
                    revision = existing.revision + 1,
                    syncStatus = SyncStatus.PENDING
                ).toEntity()
            )
        }
    }

    override suspend fun getFoldersPendingSync(): List<Folder> =
        folderDao.findPendingSync().map { it.toDomain() }

    override fun observeTrashed(): Flow<List<Folder>> =
        folderDao.observeTrashed().map { folders -> folders.map { it.toDomain() } }

    override fun observeTrashedCount(): Flow<Int> = folderDao.observeTrashedCount()

    /**
     * The mirror of [deleteFolder] â€” and deliberately *only* that. The notes it once held are
     * not moved back: [deleteFolder] took them out so that no content is ever carried away by
     * a folder's deletion, and a restore cannot undo something that never happened.
     */
    override suspend fun restoreFolder(id: String): Folder? {
        val existing = folderDao.findByIdIncludingDeleted(id)?.toDomain() ?: return null
        if (existing.deletedAt == null) return null

        // The name may have been taken while this folder was gone, and a restore does not get
        // to break the rule that two folders cannot share one.
        requireNoDuplicate(existing.name, excludeId = id)

        val restored = existing.copy(
            deletedAt = null,
            updatedAt = clock.nowMillis(),
            revision = existing.revision + 1,
            syncStatus = SyncStatus.PENDING
        )
        folderDao.update(restored.toEntity())
        return restored
    }

    override suspend fun deleteForGood(id: String): Boolean {
        val existing = folderDao.findByIdIncludingDeleted(id)?.toDomain() ?: return false

        // See [canPurge] â€” the same rule, and the same reason.
        if (!canPurge(existing.userId, existing.syncStatus, existing.deletedAt)) return false

        folderDao.deleteById(id)
        return true
    }

    override suspend fun claimUnowned(userId: String): Int = folderDao.claimUnowned(userId)

    /**
     * Folder names are unique per device, ignoring case, so two folders cannot be
     * told apart only by capitalisation. This is enforced here rather than by a
     * unique index because a case-insensitive index would require a schema
     * migration, and Prime Notes never rewrites the schema destructively.
     */
    private suspend fun requireNoDuplicate(name: String, excludeId: String) {
        if (folderDao.findActiveByName(name, excludeId) != null) {
            throw DuplicateNameException("folder")
        }
    }
}
