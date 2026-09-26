package com.primenotes.bd.data.repository

import com.primenotes.bd.data.TransactionRunner
import com.primenotes.bd.data.local.dao.NoteDao
import com.primenotes.bd.data.local.dao.TagDao
import com.primenotes.bd.data.local.mapper.toDomain
import com.primenotes.bd.data.local.mapper.toEntity
import com.primenotes.bd.domain.IdGenerator
import com.primenotes.bd.domain.model.canPurge
import com.primenotes.bd.domain.model.DuplicateNameException
import com.primenotes.bd.domain.model.SyncStatus
import com.primenotes.bd.domain.model.Tag
import com.primenotes.bd.domain.model.TagSummary
import com.primenotes.bd.domain.repository.TagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import com.primenotes.bd.domain.TimeSource

class OfflineTagRepository(
    private val tagDao: TagDao,
    private val noteDao: NoteDao,
    private val transactions: TransactionRunner,
    private val clock: TimeSource,
    private val idGenerator: IdGenerator
) : TagRepository {

    override fun observeTags(): Flow<List<Tag>> =
        tagDao.observeActive().map { tags -> tags.map { it.toDomain() } }

    override fun observeTagCount(): Flow<Int> = tagDao.observeActiveCount()

    /**
     * Counts arrive as one grouped query rather than a query per tag, and a tag
     * attached to nothing simply has no row in the count map.
     */
    override fun observeTagSummaries(): Flow<List<TagSummary>> =
        combine(
            tagDao.observeActive(),
            noteDao.observeNoteCountsByTag()
        ) { tags, counts ->
            val countById = counts.associate { count -> count.id to count.count }
            tags.map { entity ->
                TagSummary(tag = entity.toDomain(), noteCount = countById[entity.id] ?: 0)
            }
        }

    override suspend fun getTag(id: String): Tag? = tagDao.findById(id)?.toDomain()

    override suspend fun createTag(name: String): Tag {
        val cleanName = name.requireValidName("Tag")
        requireNoDuplicate(cleanName, excludeId = "")
        val now = clock.nowMillis()
        val tag = Tag(
            id = idGenerator.newId(),
            name = cleanName,
            createdAt = now,
            updatedAt = now,
            syncStatus = SyncStatus.PENDING
        )
        tagDao.insert(tag.toEntity())
        return tag
    }

    override suspend fun renameTag(id: String, name: String): Tag? {
        val existing = tagDao.findById(id)?.toDomain() ?: return null
        val cleanName = name.requireValidName("Tag")
        // Excluding the row itself means a case-only change (ideas -> Ideas) is allowed.
        requireNoDuplicate(cleanName, excludeId = id)
        val renamed = existing.copy(
            name = cleanName,
            updatedAt = clock.nowMillis(),
            revision = existing.revision + 1,
            syncStatus = SyncStatus.PENDING
        )
        tagDao.update(renamed.toEntity())
        return renamed
    }

    /**
     * Tombstones the tag and detaches it from every note in one transaction, so a
     * crash mid-way cannot leave a tag that is gone from the tag list but still
     * attached to notes.
     */
    override suspend fun deleteTag(id: String) {
        val existing = tagDao.findById(id)?.toDomain() ?: return
        val now = clock.nowMillis()

        transactions.inTransaction {
            noteDao.deleteNoteTagsForTag(id)
            tagDao.update(
                existing.copy(
                    deletedAt = now,
                    updatedAt = now,
                    revision = existing.revision + 1,
                    syncStatus = SyncStatus.PENDING
                ).toEntity()
            )
        }
    }

    override suspend fun getTagsPendingSync(): List<Tag> =
        tagDao.findPendingSync().map { it.toDomain() }

    override fun observeTrashed(): Flow<List<Tag>> =
        tagDao.observeTrashed().map { tags -> tags.map { it.toDomain() } }

    override fun observeTrashedCount(): Flow<Int> = tagDao.observeTrashedCount()

    /**
     * The mirror of [deleteTag], and only that: the links it was detached from are not
     * re-attached. A tag that came back attached to notes nobody put it on would be a worse
     * surprise than an empty one.
     */
    override suspend fun restoreTag(id: String): Tag? {
        val existing = tagDao.findByIdIncludingDeleted(id)?.toDomain() ?: return null
        if (existing.deletedAt == null) return null

        // See [OfflineFolderRepository.restoreFolder] â€” a live tag may have taken the name.
        requireNoDuplicate(existing.name, excludeId = id)

        val restored = existing.copy(
            deletedAt = null,
            updatedAt = clock.nowMillis(),
            revision = existing.revision + 1,
            syncStatus = SyncStatus.PENDING
        )
        tagDao.update(restored.toEntity())
        return restored
    }

    override suspend fun deleteForGood(id: String): Boolean {
        val existing = tagDao.findByIdIncludingDeleted(id)?.toDomain() ?: return false

        // See [canPurge] â€” the same rule, and the same reason.
        if (!canPurge(existing.userId, existing.syncStatus, existing.deletedAt)) return false

        tagDao.deleteById(id)
        return true
    }

    override suspend fun claimUnowned(userId: String): Int = tagDao.claimUnowned(userId)

    /**
     * Tag names are unique per device, ignoring case, so two tags cannot be told
     * apart only by capitalisation. Enforced here rather than by a unique index
     * because a case-insensitive index would require a schema migration.
     */
    private suspend fun requireNoDuplicate(name: String, excludeId: String) {
        if (tagDao.findActiveByName(name, excludeId) != null) {
            throw DuplicateNameException("tag")
        }
    }
}
