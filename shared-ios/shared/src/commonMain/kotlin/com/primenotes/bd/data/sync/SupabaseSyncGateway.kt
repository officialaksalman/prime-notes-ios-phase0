package com.primenotes.bd.data.sync

import com.primenotes.bd.data.cloud.toCloudReason
import com.primenotes.bd.domain.model.Folder
import com.primenotes.bd.domain.model.Note
import com.primenotes.bd.domain.model.Tag
import com.primenotes.bd.domain.sync.GatewayResult
import com.primenotes.bd.domain.sync.RemoteLink
import com.primenotes.bd.domain.sync.RemoteRow
import com.primenotes.bd.domain.sync.SyncEntity
import com.primenotes.bd.domain.sync.SyncGateway
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.PostgrestFilterBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Sync against the real project.
 *
 * Every read is ordered `(updated_at, id)` and windowed by offset. That order is what
 * makes the windows line up across pages, and the id is part of it because several
 * rows can share a millisecond â€” without it, which rows fell either side of a page
 * boundary would be up to the server.
 *
 * Writes are upserts on the primary key, which is what makes "this device's copy wins"
 * true: the row being sent is the one this device just decided is newer.
 */
class SupabaseSyncGateway(private val client: SupabaseClient) : SyncGateway {

    override suspend fun serverTime(): GatewayResult<Long> = call {
        val answer = client.postgrest.rpc(SERVER_TIME).decodeAs<JsonElement>()

        (answer as? JsonPrimitive)?.longOrNull ?: error(NO_CLOCK)
    }

    override suspend fun pull(
        entity: SyncEntity,
        since: Long,
        offset: Int,
        limit: Int
    ): GatewayResult<List<RemoteRow>> = call {
        when (entity) {
            SyncEntity.Folders -> window<FolderRecord>(entity, offset, limit) {
                gt(SERVER_AT, since)
            }.map { record -> RemoteRow.FolderRow(record.toDomain()) }

            SyncEntity.Tags -> window<TagRecord>(entity, offset, limit) {
                gt(SERVER_AT, since)
            }.map { record -> RemoteRow.TagRow(record.toDomain()) }

            SyncEntity.Notes -> window<NoteRecord>(entity, offset, limit) {
                gt(SERVER_AT, since)
            }.map { record -> RemoteRow.NoteRow(record.toDomain(), tagIds = emptyList()) }
        }
    }

    override suspend fun pullLinks(noteIds: Collection<String>): GatewayResult<List<RemoteLink>> = call {
        val ids = noteIds.toList()

        ids.chunked(NOTE_ID_CHUNK).flatMap { chunk -> linksFor(chunk) }
            .map { record -> RemoteLink(noteId = record.noteId, tagId = record.tagId) }
    }

    override suspend fun pushFolders(rows: List<Folder>): GatewayResult<Unit> = call<Unit> {
        if (rows.isEmpty()) return@call
        val userId = currentUserId()
        client.postgrest.from(SyncEntity.Folders.wireName)
            .upsert(rows.map { folder -> folder.toRecord(userId) })
    }

    override suspend fun pushTags(rows: List<Tag>): GatewayResult<Unit> = call<Unit> {
        if (rows.isEmpty()) return@call
        val userId = currentUserId()
        client.postgrest.from(SyncEntity.Tags.wireName)
            .upsert(rows.map { tag -> tag.toRecord(userId) })
    }

    override suspend fun pushNotes(rows: List<Note>): GatewayResult<Unit> = call<Unit> {
        if (rows.isEmpty()) return@call
        val userId = currentUserId()
        client.postgrest.from(SyncEntity.Notes.wireName)
            .upsert(rows.map { note -> note.toRecord(userId) })
    }

    /**
     * The real thing: `delete from notes where id in (â€¦)`, under row-level security.
     *
     * Ids travel in the query string, so they are chunked for the same reason the link reads are.
     * The filter is not optional â€” PostgREST refuses a delete with no filter at all, which is the
     * behaviour that keeps a mistaken empty list from emptying a table â€” and it is also what makes
     * this a delete *of these notes* rather than of whatever the policy happens to allow.
     *
     * An id that is not there, or is not this account's, simply matches nothing: no error, and the
     * outcome is the one the caller wanted. `note_tags` goes with the row through the foreign key's
     * cascade, which is the database's job and not this one's to reproduce.
     */
    override suspend fun deleteNotes(ids: List<String>): GatewayResult<Unit> = call<Unit> {
        if (ids.isEmpty()) return@call

        ids.chunked(NOTE_ID_CHUNK).forEach { chunk ->
            client.postgrest.from(SyncEntity.Notes.wireName)
                .delete { filter { isIn(ID, chunk) } }
        }
    }

    /**
     * Clears the links for every note named, then writes the ones it should have.
     *
     * A note with no tags at all is still named here â€” those are exactly the notes
     * whose links have to disappear. Deleting first is what makes the two sides agree
     * rather than accumulate.
     */
    override suspend fun replaceLinks(
        linksByNote: Map<String, Collection<String>>
    ): GatewayResult<Unit> = call<Unit> {
        if (linksByNote.isEmpty()) return@call

        linksByNote.keys.toList().chunked(NOTE_ID_CHUNK).forEach { chunk ->
            client.postgrest.from(LINKS_TABLE)
                .delete { filter { isIn(NOTE_ID, chunk) } }

            val additions = chunk.flatMap { noteId ->
                linksByNote.getValue(noteId).map { tagId ->
                    LinkRecord(noteId = noteId, tagId = tagId)
                }
            }
            if (additions.isNotEmpty()) {
                client.postgrest.from(LINKS_TABLE).insert(additions)
            }
        }
    }

    /**
     * One window of a table, oldest first.
     *
     * Ordered by the server's own `server_at` rather than the writing device's `updated_at`:
     * that column is stamped by the database, so the order a page comes back in and the
     * cursor a pass records are readings of one clock.
     *
     * The range is inclusive at both ends, which is what PostgREST expects.
     */
    private suspend inline fun <reified T : Any> window(
        entity: SyncEntity,
        offset: Int,
        limit: Int,
        noinline bound: PostgrestFilterBuilder.() -> Unit
    ): List<T> =
        client.postgrest.from(entity.wireName)
            .select(Columns.ALL) {
                filter(bound)
                order(SERVER_AT, Order.ASCENDING)
                order(ID, Order.ASCENDING)
                range(offset.toLong(), (offset + limit - 1).toLong())
            }
            .decodeList<T>()

    /**
     * Every link the given notes have, read in pages.
     *
     * The id list is kept short because it travels in the query string, and the read is
     * paged because one note can carry several tags â€” so the number of rows is not the
     * number of notes.
     */
    private suspend fun linksFor(noteIds: List<String>): List<LinkRecord> {
        var from = 0L
        val found = mutableListOf<LinkRecord>()

        while (true) {
            val page = client.postgrest.from(LINKS_TABLE)
                .select(Columns.ALL) {
                    filter { isIn(NOTE_ID, noteIds) }
                    order(NOTE_ID, Order.ASCENDING)
                    order(TAG_ID, Order.ASCENDING)
                    range(from, from + LINK_PAGE - 1)
                }
                .decodeList<LinkRecord>()

            found += page
            if (page.size < LINK_PAGE) return found
            from += LINK_PAGE
        }
    }

    /**
     * Who the rows are being sent as.
     *
     * Read from the session rather than passed down, so a pass that outlives a sign-out
     * cannot upload rows under the wrong account. Only ever used for a write: the cloud
     * decides what is visible, and row-level security refuses anything sent as someone
     * else.
     */
    private fun currentUserId(): String =
        client.auth.currentUserOrNull()?.id ?: error(NO_SESSION)

    /**
     * Turns the ways a call can go wrong into one shape, without letting a failure look
     * like a success.
     *
     * A refusal is the server answering and saying no â€” a constraint, a policy, a row it
     * will not accept â€” which is worth reporting against the rows involved. An absent or
     * expired session is *not* a refusal: it says nothing about the rows, so they simply
     * wait and are tried again.
     */
    private suspend fun <T> call(block: suspend () -> T): GatewayResult<T> =
        try {
            GatewayResult.Ok(block())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (rest: RestException) {
            GatewayResult.Failed(
                reason = rest.toCloudReason(),
                refused = rest.statusCode in CLIENT_ERRORS &&
                    rest.statusCode != UNAUTHORIZED &&
                    rest.statusCode != FORBIDDEN
            )
        } catch (error: Exception) {
            GatewayResult.Failed(reason = error.toCloudReason(), refused = false)
        }

    private companion object {
        const val SERVER_AT = "server_at"
        const val SERVER_TIME = "server_time"
        const val ID = "id"
        const val NOTE_ID = "note_id"
        const val TAG_ID = "tag_id"
        const val LINKS_TABLE = "note_tags"

        const val NO_SESSION = "no signed-in account"

        /** The clock is the one thing a pass cannot proceed without. */
        const val NO_CLOCK = "the project did not say what the time is"

        /** Note ids per link query â€” they travel in the query string. */
        const val NOTE_ID_CHUNK = 40

        const val LINK_PAGE = 500L

        const val UNAUTHORIZED = 401
        const val FORBIDDEN = 403
        val CLIENT_ERRORS = 400..499
    }
}
