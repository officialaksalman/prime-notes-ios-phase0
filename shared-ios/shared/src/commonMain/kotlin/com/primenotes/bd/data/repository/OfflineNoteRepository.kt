package com.primenotes.bd.data.repository

import com.primenotes.bd.data.TransactionRunner
import com.primenotes.bd.data.local.dao.NoteDao
import com.primenotes.bd.data.local.entity.NoteTagCrossRef
import com.primenotes.bd.data.local.mapper.toDomain
import com.primenotes.bd.data.local.mapper.toEntity
import com.primenotes.bd.domain.IdGenerator
import com.primenotes.bd.domain.model.matches
import com.primenotes.bd.domain.model.Note
import com.primenotes.bd.domain.model.NoteQuery
import com.primenotes.bd.domain.model.NoteSortOrder
import com.primenotes.bd.domain.model.SyncStatus
import com.primenotes.bd.domain.model.Tag
import com.primenotes.bd.domain.repository.NoteRepository
import com.primenotes.bd.domain.rich.RichTextDocument
import com.primenotes.bd.domain.search.SearchQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import com.primenotes.bd.domain.TimeSource

/**
 * Ceiling on how many search hits are returned.
 *
 * Results are ordered most-recently-updated within the pinned group, so a cap keeps
 * the freshest matches. It is a guard on how much work one query can do, not a claim
 * that the list is complete â€” a personal note collection will not reach it.
 */
private const val SEARCH_LIMIT = 200

/**
 * Offline-first implementation of [NoteRepository].
 *
 * Every mutation marks the row [SyncStatus.PENDING] and bumps `updatedAt` and
 * `revision`, so Phase 9 can find exactly what still needs uploading and Phase 10
 * can detect a concurrent change. Deletes write a tombstone instead of removing
 * the row.
 */
class OfflineNoteRepository(
    private val noteDao: NoteDao,
    private val transactions: TransactionRunner,
    private val clock: TimeSource,
    private val idGenerator: IdGenerator
) : NoteRepository {

    /**
     * Ordering and filtering happen here rather than in SQL.
     *
     * One query still does the heavy lifting â€” the Phase 2 index on
     * `(deleted_at, updated_at)` drives the default order â€” while the pinned-first
     * invariant and the filters stay in one testable place instead of being spread across
     * parameterised `CASE WHEN` SQL. Sorting is O(n log n) over an already-materialised list, which
     * suits a personal note collection; if that ever stops being true, ordering moves into SQL
     * behind this same method.
     *
     * What each flag means is [NoteQuery.matches]'s to say, not this method's â€” so the favourites
     * chip and the Favorites destination are the same read with the same flag, and cannot drift
     * apart.
     */
    override fun observeNotes(query: NoteQuery): Flow<List<Note>> =
        noteDao.observeActive().map { entities ->
            entities.asSequence()
                .map { it.toDomain() }
                .filter { note -> query.matches(note) }
                .sortedWith(comparatorFor(query.sortOrder))
                .toList()
        }
            // Converting, filtering and ordering every note is real work, and Room's flow hands the
            // rows back on whichever thread is collecting. That thread is the screen's â€” a
            // `stateIn(viewModelScope)` collects on the main one â€” so without this the main thread
            // does all of it every time a row changes. Nothing downstream minds where the value came
            // from: a `StateFlow` is written from any thread and Compose reads snapshot state from
            // any thread, which is why moving it is a change of thread and not of behaviour.
            .flowOn(Dispatchers.Default)

    override fun observeActiveCount(): Flow<Int> = noteDao.observeActiveCount()

    override fun observeFavoriteCount(): Flow<Int> = noteDao.observeFavoriteCount()

    override fun observeLockedCount(): Flow<Int> = noteDao.observeLockedCount()

    /**
     * A folder's notes, in the order the person chose.
     *
     * The DAO's own `ORDER BY` is what the rows arrive in; the chosen order is applied here, beside
     * the pinned-first rule, exactly as [observeNotes] does â€” so "alphabetical" means one thing in
     * this app no matter which list is asking.
     */
    override fun observeInFolder(folderId: String, sortOrder: NoteSortOrder): Flow<List<Note>> =
        noteDao.observeInFolder(folderId).map { notes ->
            notes.asSequence()
                .map { it.toDomain() }
                .sortedWith(comparatorFor(sortOrder))
                .toList()
        }
            // The same reason as [observeNotes]: converting and ordering is real work, and the flow
            // would otherwise do it on whichever thread is collecting â€” the screen's.
            .flowOn(Dispatchers.Default)

    override fun observeInTag(tagId: String, sortOrder: NoteSortOrder): Flow<List<Note>> =
        noteDao.observeInTag(tagId).map { notes ->
            notes.asSequence()
                .map { it.toDomain() }
                .sortedWith(comparatorFor(sortOrder))
                .toList()
        }
            .flowOn(Dispatchers.Default)

    /**
     * Search, over the notes this device holds.
     *
     * The user's text never reaches SQL: it is turned into a [SearchQuery] first, and a query with
     * nothing searchable in it short-circuits to no results rather than matching everything. What
     * matches â€” whole terms, inside words, blind to case â€” is that type's to say, so the results
     * here, the highlight on a result card and the find bar inside a note cannot disagree.
     *
     * The rows come from [NoteDao.observeSearchable], which is where a deleted note and a locked
     * note are excluded â€” before anything is read, so neither can be counted, quoted or logged.
     *
     * Reading every note and matching in Kotlin is deliberate. SQLite cannot match inside a word
     * case-insensitively beyond ASCII, and this app is used in Bengali as well as English, so a
     * `LIKE` prefilter would be wrong for exactly the queries this exists to answer. It is the
     * device's own notes, the screen debounces, and it is a substring scan rather than a query per
     * keystroke.
     */
    override fun searchNotes(query: String): Flow<List<Note>> {
        val parsed = SearchQuery(query)
        if (parsed.isEmpty) return flowOf(emptyList())

        return noteDao.observeSearchable().map { entities ->
            entities.asSequence()
                .map { entity -> entity.toDomain() }
                .filter { note ->
                    parsed.matches(note.title) || parsed.matches(note.content)
                }
                .take(SEARCH_LIMIT)
                .toList()
        }
            // The same reason as [observeNotes], and it matters more here: this reads **every** note
            // and matches every word of the query against every one of them, which is the one place
            // in the app where a single query is genuinely proportional to the whole collection.
            .flowOn(Dispatchers.Default)
    }

    override fun observeNote(id: String): Flow<Note?> =
        noteDao.observeById(id).map { note -> note?.toDomain() }

    override suspend fun getNote(id: String): Note? = noteDao.findById(id)?.toDomain()

    override fun newNoteId(): String = idGenerator.newId()

    override suspend fun createNote(
        title: String,
        content: String,
        folderId: String?,
        document: RichTextDocument?,
        id: String?
    ): Note {
        val now = clock.nowMillis()
        val note = Note(
            id = id ?: idGenerator.newId(),
            title = title,
            content = content,
            createdAt = now,
            updatedAt = now,
            folderId = folderId,
            syncStatus = SyncStatus.PENDING,
            document = document
        )
        noteDao.insert(note.toEntity())
        return note
    }

    /**
     * Persists an edit, deriving the sync metadata from the *stored* row rather
     * than from the caller's object. A screen may be holding a stale copy, and
     * `revision` is a concurrency token â€” trusting the caller's value would let a
     * stale edit look newer than it is.
     *
     * `deletedAt` is taken from the stored row for the same reason: a caller
     * holding a note read before it was deleted must not be able to bring it back.
     * `purgedAt` for the same reason again â€” a copy taken before the note was thrown away for good
     * must not be able to put it back.
     *
     * `folderId` likewise. Which folder a note is filed in is not part of its writing, and the one
     * thing that changes it is [moveToFolder]; this method is the editor saving what somebody typed.
     * Taking the caller's folder would mean an editor holding a copy from before a move undid that
     * move on the next keystroke â€” the note would appear to jump back to the folder it had left.
     */
    override suspend fun updateNote(note: Note): Note {
        val existing = noteDao.findById(note.id)?.toDomain() ?: return note
        val updated = note.copy(
            createdAt = existing.createdAt,
            folderId = existing.folderId,
            deletedAt = existing.deletedAt,
            purgedAt = existing.purgedAt,
            updatedAt = clock.nowMillis(),
            revision = existing.revision + 1,
            syncStatus = SyncStatus.PENDING
        )
        noteDao.update(updated.toEntity())
        return updated
    }

    override suspend fun setPinned(id: String, isPinned: Boolean) =
        mutate(id) { note -> note.copy(isPinned = isPinned) }

    override suspend fun setFavorite(id: String, isFavorite: Boolean) =
        mutate(id) { note -> note.copy(isFavorite = isFavorite) }

    override suspend fun setLocked(id: String, isLocked: Boolean) =
        mutate(id) { note -> note.copy(isLocked = isLocked) }

    override suspend fun moveToFolder(id: String, folderId: String?) =
        mutate(id) { note -> note.copy(folderId = folderId) }

    override suspend fun deleteNote(id: String) {
        val existing = noteDao.findById(id)?.toDomain() ?: return
        val now = clock.nowMillis()
        noteDao.update(
            existing.copy(
                deletedAt = now,
                updatedAt = now,
                revision = existing.revision + 1,
                syncStatus = SyncStatus.PENDING
            ).toEntity()
        )
    }

    override fun observeTagsForNote(noteId: String): Flow<List<Tag>> =
        noteDao.observeTagsForNote(noteId).map { tags -> tags.map { it.toDomain() } }

    override suspend fun setTags(noteId: String, tagIds: Collection<String>) {
        val existing = noteDao.findById(noteId)?.toDomain() ?: return
        val now = clock.nowMillis()

        transactions.inTransaction {
            noteDao.deleteNoteTagsForNote(noteId)
            if (tagIds.isNotEmpty()) {
                noteDao.insertNoteTags(
                    tagIds.map { tagId -> NoteTagCrossRef(noteId = noteId, tagId = tagId) }
                )
            }
            // Tag membership is part of the note, so the note is what has to sync.
            noteDao.update(
                existing.copy(
                    updatedAt = now,
                    revision = existing.revision + 1,
                    syncStatus = SyncStatus.PENDING
                ).toEntity()
            )
        }
    }

    override suspend fun getNotesPendingSync(): List<Note> =
        noteDao.findPendingSync().map { it.toDomain() }

    override fun observeTrashed(): Flow<List<Note>> =
        noteDao.observeTrashed().map { notes -> notes.map { it.toDomain() } }

    override fun observeTrashedCount(): Flow<Int> = noteDao.observeTrashedCount()

    /**
     * The mirror of [deleteNote], and the same shape as any edit: the row moves on and is
     * left pending, so this device sends the change. What clears the cloud's tombstone is the
     * explicit null in the push, which is why the note comes back on every device.
     *
     * A **purged** note cannot be brought back, and says so by answering null. There is nothing
     * left to bring back â€” its writing has been cleared â€” and clearing the tombstone on a row that
     * still carried a purge would produce the one thing this whole lifecycle exists to prevent: a
     * note that is live on the device and, on the next pass, deleted in the cloud.
     */
    override suspend fun restoreNote(id: String): Note? {
        val existing = noteDao.findById(id)?.toDomain() ?: return null
        if (existing.deletedAt == null || existing.purgedAt != null) return null

        val restored = existing.copy(
            deletedAt = null,
            updatedAt = clock.nowMillis(),
            revision = existing.revision + 1,
            syncStatus = SyncStatus.PENDING
        )
        noteDao.update(restored.toEntity())
        return restored
    }

    override suspend fun deleteForGood(id: String): Boolean {
        val existing = noteDao.findById(id)?.toDomain() ?: return false

        // Nothing to purge unless it is in the trash and not already gone.
        if (existing.deletedAt == null || existing.purgedAt != null) return false

        return purge(existing)
    }

    /**
     * A draft the editor emptied before it was ever a note. See [NoteRepository.discardDraft] for who
     * decides that, and why the decision is not made here.
     */
    override suspend fun discardDraft(id: String): Boolean {
        val existing = noteDao.findById(id)?.toDomain() ?: return false
        if (existing.purgedAt != null) return false

        return purge(existing)
    }

    override suspend fun purgeExpiredTrash(before: Long): Int {
        val expired = noteDao.findTrashedBefore(before).map { entity -> entity.toDomain() }

        var purged = 0
        expired.forEach { note -> if (purge(note)) purged += 1 }
        return purged
    }

    /**
     * The purge itself â€” the one place a note is actually got rid of.
     *
     * Which of the two things happens is decided by whether a cloud copy can exist:
     *
     *  * a row **no account has ever claimed** was never uploaded â€” the push only ever sends rows
     *    the account owns â€” so there is no copy anywhere that could bring it back, and the row is
     *    simply removed;
     *  * a row an account holds is turned into a **purge marker** instead: still here, so a pass
     *    has something to send, but emptied of its writing and hidden from every screen. It is the
     *    pending `PERMANENT_DELETE` this app has, in the only shape its architecture has for one â€”
     *    a row's state, rather than an operation in a queue.
     *
     * The marker is why a permanent deletion needs no network to happen and cannot be lost by
     * closing the app: nothing is waiting in memory, and there is nothing to reconcile later
     * beyond sending the row as it now stands.
     *
     * Both paths take the note's own links with it. A tag is not deleted â€” it is the *link* that
     * belonged to this note â€” and removing them here is also what makes the push that follows state
     * the whole set as empty, so the cloud's links go too.
     */
    private suspend fun purge(note: Note): Boolean {
        // One transaction: a purge that removed the links but not the row (or the reverse) would
        // leave a note that is half gone.
        transactions.inTransaction {
            noteDao.deleteNoteTagsForNote(note.id)

            if (note.userId == null) {
                noteDao.deleteById(note.id)
                return@inTransaction
            }

            val now = clock.nowMillis()
            noteDao.update(
                note.copy(
                    // The writing goes. What has to survive is the identity the cloud is told about,
                    // and holding the text until a pass runs would keep somebody's words on disk
                    // after they asked for them to be gone.
                    title = "",
                    content = "",
                    document = null,
                    unreadableDocument = null,
                    // Purged always on top of a tombstone, never instead of one: the read that shows
                    // the trash filters on both, and the expiry counts from the deletion.
                    deletedAt = note.deletedAt ?: now,
                    purgedAt = now,
                    updatedAt = now,
                    revision = note.revision + 1,
                    syncStatus = SyncStatus.PENDING
                ).toEntity()
            )
        }

        return true
    }

    /**
     * A single statement, so it is atomic without a transaction: a row is either
     * unowned or its owner is being set, and nothing else about it changes.
     */
    override suspend fun claimUnowned(userId: String): Int = noteDao.claimUnowned(userId)

    private suspend fun mutate(id: String, transform: (Note) -> Note) {
        val existing = noteDao.findById(id)?.toDomain() ?: return
        val changed = transform(existing)
        noteDao.update(
            changed.copy(
                updatedAt = clock.nowMillis(),
                revision = existing.revision + 1,
                syncStatus = SyncStatus.PENDING
            ).toEntity()
        )
    }
}

/**
 * Pinned notes always come first; the requested order applies within each group.
 *
 * A final comparison on `id` keeps the order stable when two notes share a
 * timestamp or title, so the list never reshuffles between identical emissions.
 */
internal fun comparatorFor(sortOrder: NoteSortOrder): Comparator<Note> =
    compareByDescending<Note> { note -> note.isPinned }
        .then(primaryComparatorFor(sortOrder))
        .thenBy { note -> note.id }

private fun primaryComparatorFor(sortOrder: NoteSortOrder): Comparator<Note> =
    when (sortOrder) {
        NoteSortOrder.LAST_MODIFIED -> compareByDescending { note -> note.updatedAt }
        NoteSortOrder.LAST_MODIFIED_ASC -> compareBy { note -> note.updatedAt }
        NoteSortOrder.DATE_CREATED -> compareByDescending { note -> note.createdAt }
        NoteSortOrder.DATE_CREATED_ASC -> compareBy { note -> note.createdAt }
        NoteSortOrder.TITLE -> UNTITLED_LAST
        NoteSortOrder.TITLE_DESC -> UNTITLED_LAST_DESC
    }

/**
 * Alphabetical, case-insensitive, but untitled notes sink to the bottom instead of
 * cluttering the top of the list.
 */
private val UNTITLED_LAST: Comparator<Note> = Comparator { left, right ->
    val leftBlank = left.title.isBlank()
    val rightBlank = right.title.isBlank()
    when {
        leftBlank && rightBlank -> 0
        leftBlank -> 1
        rightBlank -> -1
        else -> left.title.compareTo(right.title, ignoreCase = true)
    }
}

/**
 * [UNTITLED_LAST] the other way round — Z to A — but with the one thing that does not
 * reverse: an untitled note has no place in an alphabetical run either way, so it stays at
 * the bottom rather than being flung to the top by the reversal.
 */
private val UNTITLED_LAST_DESC: Comparator<Note> = Comparator { left, right ->
    val leftBlank = left.title.isBlank()
    val rightBlank = right.title.isBlank()
    when {
        leftBlank && rightBlank -> 0
        leftBlank -> 1
        rightBlank -> -1
        else -> right.title.compareTo(left.title, ignoreCase = true)
    }
}
