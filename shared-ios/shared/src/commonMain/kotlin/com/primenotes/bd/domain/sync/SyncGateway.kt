package com.primenotes.bd.domain.sync

import com.primenotes.bd.domain.model.Folder
import com.primenotes.bd.domain.model.Note
import com.primenotes.bd.domain.model.Tag

/**
 * The tables sync moves.
 *
 * The order they are declared in is the order they have to be moved, in both directions: a
 * note's `folder_id` is a real foreign key, and a tag has to exist before a note can be
 * linked to it.
 *
 * [cursorName] is what a cursor is filed under, and it carries the meaning of the number
 * stored beside it. Phase 9's cursors were the writing device's `updated_at`; the pull now
 * reads the server's `server_at`, and a number is only comparable with another from the same
 * clock. Renaming the key is what stops an old cursor being read as a server time and
 * stepping over everything between the two — the first pass after upgrading re-reads the
 * collection instead, which costs a pass and loses nothing.
 */
enum class SyncEntity(val wireName: String, val cursorName: String) {
    Folders("folders", "folders@server-time"),
    Tags("tags", "tags@server-time"),
    Notes("notes", "notes@server-time")
}

/** A row as it travels, with the tags the cloud says are attached to a note. */
sealed interface RemoteRow {

    val head: SyncHead

    data class FolderRow(val folder: Folder) : RemoteRow {
        override val head: SyncHead get() = folder.syncHead
    }

    data class TagRow(val tag: Tag) : RemoteRow {
        override val head: SyncHead get() = tag.syncHead
    }

    data class NoteRow(val note: Note, val tagIds: List<String>) : RemoteRow {
        override val head: SyncHead get() = note.syncHead
    }
}

/** One note/tag link, as the cloud holds it. */
data class RemoteLink(val noteId: String, val tagId: String)

/**
 * What a cloud call returned.
 *
 * [Failed] keeps "the request never got an answer" apart from "the server answered
 * and refused", because the two say different things about the rows involved: a
 * network failure says nothing about the data, while a refusal is about the rows
 * themselves and is worth showing to the person who wrote them.
 */
sealed interface GatewayResult<out T> {

    data class Ok<T>(val value: T) : GatewayResult<T>

    /**
     * [refused] is true only when the server answered and rejected the request — a
     * constraint, a policy, a row it will not accept. An absent or expired session is
     * deliberately *not* a refusal: it says nothing about the rows, so it is reported
     * with [refused] false and they are simply tried again.
     */
    data class Failed(val reason: String, val refused: Boolean) : GatewayResult<Nothing>
}

/**
 * The cloud half of sync, as the engine needs it.
 *
 * **Contract:** implementations do not throw. Everything that can go wrong comes back
 * as [GatewayResult.Failed], so the engine has one shape to handle and no failure can
 * be mistaken for success.
 *
 * Every read is bounded by the account's own rows, and the cloud enforces that with
 * row-level security rather than trusting this side to filter.
 */
interface SyncGateway {

    /**
     * What the server's clock says now, in epoch milliseconds.
     *
     * Read once at the start of a pass and recorded as every table's cursor. The cloud
     * stamps each row with its own reading of the same clock, so this is the only value a
     * cursor can be meaningfully compared with.
     */
    suspend fun serverTime(): GatewayResult<Long>

    /**
     * A window of the rows whose `server_at` is newer than [since], oldest first and ordered
     * by id within the same instant.
     *
     * A window rather than everything, so a large collection is read in pages instead of one
     * very large response. The order has to be stable for the pages to line up, which is why
     * the id is part of it.
     */
    suspend fun pull(
        entity: SyncEntity,
        since: Long,
        offset: Int,
        limit: Int
    ): GatewayResult<List<RemoteRow>>

    /** The tags attached to each of [noteIds] on the cloud. */
    suspend fun pullLinks(noteIds: Collection<String>): GatewayResult<List<RemoteLink>>

    suspend fun pushFolders(rows: List<Folder>): GatewayResult<Unit>

    suspend fun pushTags(rows: List<Tag>): GatewayResult<Unit>

    suspend fun pushNotes(rows: List<Note>): GatewayResult<Unit>

    /**
     * Deletes the notes named, for good, from the cloud.
     *
     * The one operation that is a **delete** and not an upsert: a note its owner threw away must
     * leave no row behind, so there is nothing to send. Sending the row with a marker on it instead
     * — which is what this used to do — leaves a table full of notes that are not notes.
     *
     * Ownership is the cloud's to enforce, not this side's: the delete runs under row-level
     * security, so it can only ever remove rows the signed-in account owns. A note that is not
     * there, or is not this account's, is not an error — the caller asked for the row to be gone
     * and it is.
     *
     * Failure comes back as [GatewayResult.Failed], and the distinction the engine acts on is the
     * usual one: a server that *refused* says something about the request, while a request that
     * never arrived says nothing and is simply tried again. Either way the caller keeps its record
     * of the deletion — this returning `Ok` is the only thing that may discard one.
     */
    suspend fun deleteNotes(ids: List<String>): GatewayResult<Unit>

    /**
     * Makes the cloud's links match [linksByNote] for every note named in it,
     * including the notes with no tags at all — those have to lose the links they had.
     */
    suspend fun replaceLinks(linksByNote: Map<String, Collection<String>>): GatewayResult<Unit>
}
