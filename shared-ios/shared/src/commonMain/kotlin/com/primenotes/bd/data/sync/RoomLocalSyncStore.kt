package com.primenotes.bd.data.sync

import com.primenotes.bd.data.TransactionRunner
import com.primenotes.bd.data.local.dao.SyncDao
import com.primenotes.bd.data.local.entity.NoteTagCrossRef
import com.primenotes.bd.data.local.entity.SyncAccountStateEntity
import com.primenotes.bd.data.local.entity.SyncCursorEntity
import com.primenotes.bd.data.local.mapper.toDomain
import com.primenotes.bd.data.local.mapper.toEntity
import com.primenotes.bd.domain.model.Folder
import com.primenotes.bd.domain.model.Note
import com.primenotes.bd.domain.model.SyncStatus
import com.primenotes.bd.domain.model.Tag
import com.primenotes.bd.domain.sync.LastRun
import com.primenotes.bd.domain.sync.LocalCopy
import com.primenotes.bd.domain.sync.LocalSyncStore
import com.primenotes.bd.domain.sync.OutstandingWork
import com.primenotes.bd.domain.sync.RemoteRow
import com.primenotes.bd.domain.sync.SyncCursor
import com.primenotes.bd.domain.sync.SyncEntity
import com.primenotes.bd.domain.sync.SyncHead
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * The device's half of sync, against the real database.
 *
 * Everything here is about being able to say, precisely, what this device has and what
 * the cloud said. The two places that matter most:
 *
 *  * [applyRemote] replaces a note's links wholesale, because the note is the unit that
 *    syncs â€” the cloud does not track a link's age, so the only way the two sides can
 *    agree about a note's tags is for one of them to state the whole set;
 *  * [markSynced] will not mark a row that has been edited since it was sent.
 */
class RoomLocalSyncStore(
    private val dao: SyncDao,
    private val transactions: TransactionRunner
) : LocalSyncStore {

    override suspend fun cursor(accountId: String, entity: SyncEntity): Long? =
        dao.cursors(accountId)
            .firstOrNull { cursor -> cursor.entity == entity.cursorName }
            ?.pulledAt

    override suspend fun advanceCursor(accountId: String, entity: SyncEntity, finishedAt: Long) {
        val next = SyncCursor.advance(cursor(accountId, entity), finishedAt)

        dao.putCursor(
            SyncCursorEntity(accountId = accountId, entity = entity.cursorName, pulledAt = next)
        )
    }

    override suspend fun recordSuccess(accountId: String, at: Long, conflicts: Int) {
        dao.putAccountState(
            SyncAccountStateEntity(
                accountId = accountId,
                lastSyncedAt = at,
                lastError = null,
                lastConflicts = conflicts
            )
        )
    }

    override suspend fun recordFailure(accountId: String, reason: String) {
        // The last time it *did* work is still worth showing, and so is what it kept, so both
        // are carried over rather than blanked by a pass that did not get far enough to know.
        val previous = dao.accountState(accountId)

        dao.putAccountState(
            SyncAccountStateEntity(
                accountId = accountId,
                lastSyncedAt = previous?.lastSyncedAt,
                lastError = reason,
                lastConflicts = previous?.lastConflicts ?: 0
            )
        )
    }

    override fun lastRuns(): Flow<Map<String, LastRun>> =
        dao.observeAccountStates().map { states ->
            states.associate { state ->
                state.accountId to LastRun(
                    lastSyncedAt = state.lastSyncedAt,
                    lastError = state.lastError,
                    conflicts = state.lastConflicts
                )
            }
        }

    override fun outstanding(): Flow<OutstandingWork> = combine(
        dao.observeNoteCounts(SyncStatus.PENDING, SyncStatus.FAILED),
        dao.observeFolderCounts(SyncStatus.PENDING, SyncStatus.FAILED),
        dao.observeTagCounts(SyncStatus.PENDING, SyncStatus.FAILED)
    ) { notes, folders, tags ->
        OutstandingWork(
            pending = notes.pending + folders.pending + tags.pending,
            failed = notes.failed + folders.failed + tags.failed
        )
    }

    override suspend fun copies(entity: SyncEntity, ids: List<String>): Map<String, LocalCopy> {
        if (ids.isEmpty()) return emptyMap()

        return ids.chunked(ID_CHUNK)
            .flatMap { chunk -> heads(entity, chunk) }
            .associate { head ->
                head.id to LocalCopy(
                    head = SyncHead(
                        id = head.id,
                        updatedAt = head.updatedAt,
                        revision = head.revision
                    ),
                    isSynced = head.syncStatus == SyncStatus.SYNCED
                )
            }
    }

    /**
     * One transaction, so a page either lands whole or not at all: a note without the
     * tags that came with it would be worse than a note that has not arrived yet.
     *
     * Every row is written recorded as uploaded, whatever it arrived as. A row that came
     * from the cloud is by definition already up there, and one left waiting would be
     * sent straight back.
     */
    override suspend fun applyRemote(rows: List<RemoteRow>) {
        if (rows.isEmpty()) return

        transactions.inTransaction {
            rows.filterIsInstance<RemoteRow.FolderRow>()
                .map { row -> row.folder.copy(syncStatus = SyncStatus.SYNCED).toEntity() }
                .takeIf { folders -> folders.isNotEmpty() }
                ?.let { folders -> dao.upsertFolders(folders) }

            rows.filterIsInstance<RemoteRow.TagRow>()
                .map { row -> row.tag.copy(syncStatus = SyncStatus.SYNCED).toEntity() }
                .takeIf { tags -> tags.isNotEmpty() }
                ?.let { tags -> dao.upsertTags(tags) }

            val notes = rows.filterIsInstance<RemoteRow.NoteRow>()
            if (notes.isNotEmpty()) {
                dao.upsertNotes(
                    notes.map { row -> row.note.copy(syncStatus = SyncStatus.SYNCED).toEntity() }
                )
                notes.forEach { row -> replaceLinks(row) }
            }
        }
    }

    private suspend fun replaceLinks(row: RemoteRow.NoteRow) {
        dao.deleteLinksForNote(row.note.id)

        if (row.tagIds.isNotEmpty()) {
            dao.insertLinks(
                row.tagIds.map { tagId -> NoteTagCrossRef(noteId = row.note.id, tagId = tagId) }
            )
        }
    }

    /**
     * A conflict copy is written as what it is â€” a row written here, never uploaded â€” so it
     * goes in unsettled, unlike everything else a pull writes.
     *
     * Its links go with it in the same transaction, so a copy can never appear without the tags
     * it was copied with.
     */
    override suspend fun keepConflictCopy(note: Note, tagIds: List<String>) {
        transactions.inTransaction {
            dao.upsertNotes(listOf(note.toEntity()))
            dao.deleteLinksForNote(note.id)

            if (tagIds.isNotEmpty()) {
                dao.insertLinks(
                    tagIds.map { tagId -> NoteTagCrossRef(noteId = note.id, tagId = tagId) }
                )
            }
        }
    }

    override suspend fun note(id: String): Note? = dao.note(id)?.toDomain()

    override suspend fun purgeTombstones(before: Long): Int {
        var purged = 0

        transactions.inTransaction {
            purged += dao.purgeNotes(before, SyncStatus.SYNCED)
            purged += dao.purgeFolders(before, SyncStatus.SYNCED)
            purged += dao.purgeTags(before, SyncStatus.SYNCED)
        }

        return purged
    }

    override suspend fun foldersToPush(accountId: String): List<Folder> =
        dao.foldersToPush(accountId, SyncStatus.SYNCED).map { entity -> entity.toDomain() }

    override suspend fun tagsToPush(accountId: String): List<Tag> =
        dao.tagsToPush(accountId, SyncStatus.SYNCED).map { entity -> entity.toDomain() }

    override suspend fun notesToPush(accountId: String): List<Note> =
        dao.notesToPush(accountId, SyncStatus.SYNCED).map { entity -> entity.toDomain() }

    override suspend fun notesToDelete(accountId: String): List<String> =
        dao.notesAwaitingDeletion(accountId, SyncStatus.SYNCED)

    override suspend fun forgetNotes(ids: List<String>) {
        if (ids.isEmpty()) return

        transactions.inTransaction {
            ids.chunked(ID_CHUNK).forEach { chunk -> dao.deleteNotesByIds(chunk) }
        }
    }

    override suspend fun tagIdsForNotes(noteIds: Collection<String>): Map<String, List<String>> {
        if (noteIds.isEmpty()) return emptyMap()

        return noteIds.toList().chunked(ID_CHUNK)
            .flatMap { chunk -> dao.linksForNotes(chunk) }
            .groupBy({ link -> link.noteId }, { link -> link.tagId })
    }

    override suspend fun markSynced(entity: SyncEntity, rows: List<SyncHead>) {
        if (rows.isEmpty()) return

        transactions.inTransaction {
            rows.forEach { row -> markSynced(entity, row) }
        }
    }

    private suspend fun markSynced(entity: SyncEntity, row: SyncHead) {
        when (entity) {
            SyncEntity.Folders -> dao.markFolderSynced(row.id, row.revision, SyncStatus.SYNCED)
            SyncEntity.Tags -> dao.markTagSynced(row.id, row.revision, SyncStatus.SYNCED)
            SyncEntity.Notes -> dao.markNoteSynced(row.id, row.revision, SyncStatus.SYNCED)
        }
    }

    override suspend fun markFailed(entity: SyncEntity, ids: List<String>) {
        if (ids.isEmpty()) return

        transactions.inTransaction {
            ids.forEach { id ->
                when (entity) {
                    SyncEntity.Folders ->
                        dao.markFolderFailed(id, SyncStatus.PENDING, SyncStatus.FAILED)

                    SyncEntity.Tags -> dao.markTagFailed(id, SyncStatus.PENDING, SyncStatus.FAILED)

                    SyncEntity.Notes -> dao.markNoteFailed(id, SyncStatus.PENDING, SyncStatus.FAILED)
                }
            }
        }
    }

    private suspend fun heads(entity: SyncEntity, ids: List<String>) = when (entity) {
        SyncEntity.Folders -> dao.folderHeads(ids)
        SyncEntity.Tags -> dao.tagHeads(ids)
        SyncEntity.Notes -> dao.noteHeads(ids)
    }

    private companion object {
        /**
         * Ids per `IN (...)` clause.
         *
         * SQLite caps how many values one statement may bind, and the cap is lower on
         * older devices, so a page of ids is split into groups that are safe anywhere.
         */
        const val ID_CHUNK = 300
    }
}
