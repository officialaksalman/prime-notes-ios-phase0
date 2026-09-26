package com.primenotes.bd.data.sync

import com.primenotes.bd.domain.IdGenerator
import com.primenotes.bd.domain.account.AccountRepository
import com.primenotes.bd.domain.model.Note
import com.primenotes.bd.domain.sync.ConflictCopy
import com.primenotes.bd.domain.sync.GatewayResult
import com.primenotes.bd.domain.sync.LocalCopy
import com.primenotes.bd.domain.sync.LocalSyncStore
import com.primenotes.bd.domain.sync.MergeDecision
import com.primenotes.bd.domain.sync.RemoteRow
import com.primenotes.bd.domain.sync.SyncCursor
import com.primenotes.bd.domain.sync.SyncEngine
import com.primenotes.bd.domain.sync.SyncEntity
import com.primenotes.bd.domain.sync.SyncGateway
import com.primenotes.bd.domain.sync.SyncHead
import com.primenotes.bd.domain.sync.SyncResult
import com.primenotes.bd.domain.sync.SyncRetention
import com.primenotes.bd.domain.sync.SyncState
import com.primenotes.bd.domain.sync.conflictsWithLocal
import com.primenotes.bd.domain.sync.decideSync
import com.primenotes.bd.domain.sync.syncHead
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex

/**
 * One sync pass, and the state of the last one.
 *
 * A pass, in order:
 *
 *  1. **the time** â€” what the server's clock says, which is the only thing a cursor can be
 *     compared with. A pass with no answer does not start;
 *  2. **pull** â€” folders, then tags, then notes with their tags. Everything the cloud has
 *     that this device does not, or holds an older copy of, plus a second copy of anything
 *     this device is about to overrule;
 *  3. **claim** â€” anything still unowned becomes this account's (Phase 8). Until that has
 *     happened, a row written before signing in is not in the upload queue at all;
 *  4. **push** â€” folders, then tags, then notes, each with its links. Only rows this account
 *     owns, and only rows that are not already up there;
 *  5. **record and tidy** â€” when the pass finished and what it had to keep; then, and only
 *     then, the deletions that have been dealt with.
 *
 * Pulling before pushing is what makes the rule hold: a row edited here is not overwritten
 * by the pull, and is then sent up as the newer copy. Two edits of the same note are still
 * not *merged* â€” this device's version wins and becomes the cloud's â€” but the version it
 * replaced is kept as a note of its own rather than thrown away.
 *
 * Nothing here is allowed to lose anything: every failure leaves the rows it was working on
 * exactly where they were, waiting.
 */
class SyncEngineImpl(
    private val account: AccountRepository,
    private val claimUnowned: suspend (String) -> Unit,
    private val gateway: SyncGateway,
    private val store: LocalSyncStore,
    private val idGenerator: IdGenerator,
    private val configured: Boolean
) : SyncEngine {

    private val running = MutableStateFlow(false)

    /**
     * One pass at a time, for the whole process.
     *
     * Two passes over the same cursors would read and write the same rows with no way to
     * tell which of them was right, so the second is refused rather than queued: the
     * answer to "am I in sync?" is already on its way.
     */
    private val pass = Mutex()

    override val state: Flow<SyncState> = combine(
        account.account,
        store.outstanding(),
        store.lastRuns(),
        running
    ) { signedIn, work, runs, isRunning ->
        val last = signedIn?.let { session -> runs[session.id] }

        SyncState(
            configured = configured,
            signedIn = signedIn != null,
            running = isRunning,
            lastSyncedAt = last?.lastSyncedAt,
            pending = work.pending,
            failed = work.failed,
            conflicts = last?.conflicts ?: 0,
            lastError = last?.lastError
        )
    }

    override suspend fun syncNow(): SyncResult {
        if (!configured) return SyncResult.NotConfigured

        val signedIn = account.account.first() ?: return SyncResult.NotSignedIn

        if (!pass.tryLock()) return SyncResult.AlreadyRunning

        return try {
            running.value = true
            runPass(signedIn.id)
        } finally {
            running.value = false
            pass.unlock()
        }
    }

    private suspend fun runPass(accountId: String): SyncResult {
        // The one clock a cursor can be compared with. The cloud stamps every row from it and
        // a pass records what it said, so without an answer there is no honest way to say how
        // far this device has read â€” and the pass does not start.
        val asOf = when (val clock = gateway.serverTime()) {
            is GatewayResult.Failed -> return recordFailure(accountId, clock.reason)
            is GatewayResult.Ok -> clock.value
        }

        val pulled = pullEverything(accountId, asOf)
        if (pulled is Step.Failed) return recordFailure(accountId, pulled.reason)

        claimUnowned(accountId)

        val pushed = pushEverything(accountId)
        if (pushed is Step.Failed) return recordFailure(accountId, pushed.reason)

        store.recordSuccess(accountId, at = asOf, conflicts = pulled.conflicts)

        // Only now. Everything this device had to say is up there, so a deletion that has been
        // dealt with can go for good. A pass that failed never reaches this line, which is the
        // whole point of it being here rather than at the start.
        store.purgeTombstones(SyncRetention.cutoff(asOf))

        return SyncResult.Completed(pushed = pushed.count, pulled = pulled.count)
    }

    private suspend fun recordFailure(accountId: String, reason: String): SyncResult {
        store.recordFailure(accountId, reason)
        return SyncResult.Failed(reason)
    }

    // -----------------------------------------------------------------------
    // Pulling
    // -----------------------------------------------------------------------

    private suspend fun pullEverything(accountId: String, asOf: Long): Step {
        var total = 0
        var kept = 0

        // Declaration order is the order that works: a note's folder and its tags have
        // to be here before the note can be written.
        for (entity in SyncEntity.entries) {
            when (val step = pullEntity(accountId, entity, asOf)) {
                is Step.Failed -> return step
                is Step.Done -> {
                    total += step.count
                    kept += step.conflicts
                }
            }
        }

        return Step.Done(count = total, conflicts = kept)
    }

    /**
     * Reads one table, a page at a time, and applies what the merge rule accepts.
     *
     * The cursor only moves once the whole table has been read, and then only as far as
     * [asOf] â€” the server's time when the pass began. A pass that breaks half way through
     * starts again from where it began rather than from part way, which is the difference
     * between a slow sync and a skipped row.
     */
    private suspend fun pullEntity(accountId: String, entity: SyncEntity, asOf: Long): Step {
        val from = SyncCursor.since(store.cursor(accountId, entity))
        var offset = 0
        var applied = 0
        var kept = 0

        while (true) {
            val page = when (val rows = page(entity, from, offset)) {
                is GatewayResult.Failed -> return Step.Failed(rows.reason)
                is GatewayResult.Ok -> rows.value
            }
            if (page.isEmpty()) break

            val local = store.copies(entity, page.map { row -> row.head.id })
            val take = page.filter { row ->
                decideSync(local[row.head.id], row.head) == MergeDecision.ApplyRemote
            }
            if (take.isNotEmpty()) store.applyRemote(take)
            applied += take.size
            kept += keepConflicts(entity, page, local)

            offset += page.size
            if (page.size < PAGE_SIZE) break
        }

        store.advanceCursor(accountId, entity, asOf)
        return Step.Done(count = applied, conflicts = kept)
    }

    /**
     * Keeps the cloud's version of any note this device is about to overrule.
     *
     * This device's unsynced edit still wins and still becomes the cloud's copy. What is new
     * is that the version it replaces is written out as a note of its own first, so nothing
     * anyone wrote is thrown away to settle a race between two devices.
     *
     * Three things are deliberately left alone:
     *
     *  * **a tombstone** â€” there is no content to keep. The local edit wins and the note
     *    survives, which is the rule that refuses to lose writing;
     *  * **the same version twice** â€” two devices that made the same edit have not
     *    disagreed, and a second copy of identical text is only noise;
     *  * **folders and tags** â€” a rename has no content to lose, and a second folder called
     *    "Work (conflict)" would help nobody.
     */
    private suspend fun keepConflicts(
        entity: SyncEntity,
        page: List<RemoteRow>,
        local: Map<String, LocalCopy>
    ): Int {
        if (entity != SyncEntity.Notes) return 0

        var kept = 0

        for (row in page) {
            if (row !is RemoteRow.NoteRow) continue

            val remote = row.note
            if (remote.deletedAt != null) continue
            if (!conflictsWithLocal(local[remote.id], remote.syncHead)) continue

            val mine = store.note(remote.id) ?: continue
            if (sameVersion(mine, remote)) continue

            store.keepConflictCopy(ConflictCopy.of(remote, idGenerator.newId()), row.tagIds)
            kept++
        }

        return kept
    }

    /**
     * The same edit made twice is not two edits.
     *
     * The formatting counts as much as the words. Two copies whose text agrees but whose formatting
     * does not are not the same note, and calling them the same would let this device's version
     * overwrite the cloud's with no copy kept â€” the formatting would simply be gone. An unreadable
     * body is compared as it stands, for the same reason it is preserved rather than re-encoded.
     */
    private fun sameVersion(mine: Note, remote: Note): Boolean =
        mine.title == remote.title &&
            mine.content == remote.content &&
            mine.document == remote.document &&
            mine.unreadableDocument == remote.unreadableDocument

    /**
     * A page of one table, with each note's tags filled in.
     *
     * A note's tags travel with the note. The cloud keeps no timestamp on a link, so the
     * only thing that can say whether a note's tags need looking at is the note itself
     * having changed.
     */
    private suspend fun page(
        entity: SyncEntity,
        from: Long,
        offset: Int
    ): GatewayResult<List<RemoteRow>> {
        val rows = gateway.pull(entity, from, offset, PAGE_SIZE)
        if (entity != SyncEntity.Notes) return rows

        val fetched = (rows as? GatewayResult.Ok)?.value ?: return rows
        if (fetched.isEmpty()) return rows

        val links = when (val result = gateway.pullLinks(fetched.map { row -> row.head.id })) {
            is GatewayResult.Failed -> return result
            is GatewayResult.Ok -> result.value
        }
        val byNote = links.groupBy({ link -> link.noteId }, { link -> link.tagId })

        return GatewayResult.Ok(
            fetched.map { row ->
                when (row) {
                    is RemoteRow.NoteRow -> row.copy(tagIds = byNote[row.note.id].orEmpty())
                    else -> row
                }
            }
        )
    }

    // -----------------------------------------------------------------------
    // Pushing
    // -----------------------------------------------------------------------

    private suspend fun pushEverything(accountId: String): Step {
        val folders = send(
            entity = SyncEntity.Folders,
            rows = store.foldersToPush(accountId),
            id = { folder -> folder.id },
            head = { folder -> folder.syncHead },
            deliver = { rows -> gateway.pushFolders(rows) }
        )
        if (folders is Step.Failed) return folders

        val tags = send(
            entity = SyncEntity.Tags,
            rows = store.tagsToPush(accountId),
            id = { tag -> tag.id },
            head = { tag -> tag.syncHead },
            deliver = { rows -> gateway.pushTags(rows) }
        )
        if (tags is Step.Failed) return tags

        val notes = pushNotes(accountId)
        if (notes is Step.Failed) return notes

        return Step.Done(folders.count + tags.count + notes.count)
    }

    /**
     * Sends rows that have nothing hanging off them, in chunks.
     *
     * A refusal is recorded against the rows that were refused: they stay in the queue
     * and are tried again, but the app can say so rather than pretend. Anything else
     * leaves them untouched â€” a network that is not there says nothing about the data,
     * and calling those rows failed would be a lie the user would read on the card.
     */
    private suspend fun <T> send(
        entity: SyncEntity,
        rows: List<T>,
        id: (T) -> String,
        head: (T) -> SyncHead,
        deliver: suspend (List<T>) -> GatewayResult<Unit>
    ): Step {
        var sent = 0

        for (chunk in rows.chunked(PUSH_CHUNK)) {
            when (val result = deliver(chunk)) {
                is GatewayResult.Ok -> store.markSynced(entity, chunk.map(head))

                is GatewayResult.Failed -> {
                    if (result.refused) store.markFailed(entity, chunk.map(id))
                    return Step.Failed(result.reason)
                }
            }

            sent += chunk.size
        }

        return Step.Done(sent)
    }

    /**
     * Notes: the ones to say, then the ones to delete.
     *
     * A note that has been permanently deleted has nothing to send â€” the row left on this device is
     * the instruction to delete it, and it is deliberately absent from the upload queue. That is
     * what makes the collapsing in the other direction automatic: a note that was moved to the trash
     * and then deleted outright is one row whose state moved on, so there is no queued "move to
     * trash" left to run afterwards and nothing to resurrect.
     *
     * The deletions go **last**, which is not an accident. A pass stops at its first failure, so
     * putting them first would mean a deletion the server keeps refusing held up every other note
     * on the device. This way the writing goes up regardless, and a deletion that will not go is
     * retried and reported on its own.
     *
     * A note is not recorded as uploaded until its links are too. Marking it uploaded
     * with its tags still missing would mean it was never looked at again, and the links
     * would be lost â€” so when the links fail, the note stays in the queue and the whole
     * thing is sent again on the next pass. The cloud still ends up right either way,
     * because replacing links states the whole set rather than adding to it.
     */
    private suspend fun pushNotes(accountId: String): Step {
        val notes = store.notesToPush(accountId)
        var sent = 0

        for (chunk in notes.chunked(PUSH_CHUNK)) {
            when (val result = gateway.pushNotes(chunk)) {
                is GatewayResult.Ok -> Unit
                is GatewayResult.Failed -> {
                    if (result.refused) store.markFailed(SyncEntity.Notes, chunk.map { it.id })
                    return Step.Failed(result.reason)
                }
            }

            val links = store.tagIdsForNotes(chunk.map { note -> note.id })
            val byNote = chunk.associate { note -> note.id to links[note.id].orEmpty() }

            when (val result = gateway.replaceLinks(byNote)) {
                is GatewayResult.Ok -> store.markSynced(SyncEntity.Notes, chunk.map { it.syncHead })

                is GatewayResult.Failed -> {
                    if (result.refused) store.markFailed(SyncEntity.Notes, chunk.map { it.id })
                    return Step.Failed(result.reason)
                }
            }

            sent += chunk.size
        }

        val deletions = deleteNotes(accountId)
        if (deletions is Step.Failed) return deletions

        return Step.Done(sent + deletions.count)
    }

    /**
     * Carries out the permanent deletions this device is holding, and forgets each one the cloud
     * has confirmed.
     *
     * This is the whole of `PERMANENT_DELETE`: the row on the device was written the moment the user
     * asked for it â€” emptied, hidden and marked waiting â€” so there is nothing assembled here and
     * nothing that a restart or a lost connection can undo. What is left is to say it.
     *
     * A deletion is only forgotten on a `Ok`. A refusal leaves it marked failed, to be tried again
     * and to be counted on the Sync card, and a request that never arrived leaves it untouched. Both
     * mean the row stays where it is, which is the only safe answer: forgetting one before the cloud
     * has agreed would lose the deletion, and the note would come back on the next pull.
     */
    private suspend fun deleteNotes(accountId: String): Step {
        val ids = store.notesToDelete(accountId)
        var deleted = 0

        for (chunk in ids.chunked(PUSH_CHUNK)) {
            when (val result = gateway.deleteNotes(chunk)) {
                is GatewayResult.Ok -> store.forgetNotes(chunk)

                is GatewayResult.Failed -> {
                    if (result.refused) store.markFailed(SyncEntity.Notes, chunk)
                    return Step.Failed(result.reason)
                }
            }

            deleted += chunk.size
        }

        return Step.Done(deleted)
    }

    /** How one stage of a pass ended. */
    private sealed interface Step {

        /** Rows handled â€” zero when the stage stopped short. */
        val count: Int

        /** Versions kept as copies of themselves. Only a pull ever keeps any. */
        val conflicts: Int

        data class Done(override val count: Int, override val conflicts: Int = 0) : Step

        data class Failed(
            val reason: String,
            override val count: Int = 0,
            override val conflicts: Int = 0
        ) : Step
    }

    private companion object {
        /** Rows per cloud request, and the size of one page of a pull. */
        const val PAGE_SIZE = 200

        const val PUSH_CHUNK = 200
    }
}
