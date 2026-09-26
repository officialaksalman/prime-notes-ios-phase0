package com.primenotes.bd.data.sync

import com.primenotes.bd.domain.model.Folder
import com.primenotes.bd.domain.model.Note
import com.primenotes.bd.domain.model.SyncStatus
import com.primenotes.bd.domain.model.Tag
import com.primenotes.bd.domain.rich.ReadBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The wire shape of a row: the cloud's columns, exactly.
 *
 * Deliberately separate from the domain models, and deliberately snake_case, so a
 * change to either side of the wire has to be made on purpose rather than falling out
 * of a rename.
 *
 * There is no `sync_status` here. The cloud does not have that column, because "has
 * *this device* uploaded the row yet?" is not a fact about the cloud's copy.
 *
 * The nullable fields carry **no default**, and this app's Postgrest serializer encodes
 * nulls explicitly. Both matter: moving a note out of a folder is `folder_id: null`, and
 * a field the encoder quietly skipped would leave that note in its old folder on every
 * other device.
 */

@Serializable
internal data class FolderRecord(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("deleted_at") val deletedAt: Long?,
    val revision: Long
)

@Serializable
internal data class TagRecord(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("deleted_at") val deletedAt: Long?,
    val revision: Long
)

@Serializable
internal data class NoteRecord(
    val id: String,
    @SerialName("user_id") val userId: String,
    val title: String,
    val content: String,
    @SerialName("folder_id") val folderId: String?,
    @SerialName("is_pinned") val isPinned: Boolean,
    @SerialName("is_favorite") val isFavorite: Boolean,
    @SerialName("is_locked") val isLocked: Boolean,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("deleted_at") val deletedAt: Long?,
    val revision: Long,
    /**
     * The note's formatting, NULL for plain text — the device's `content_document` column.
     *
     * Defaulted, and this is the one field here that is. The others carry no default so that a
     * cleared value is always stated; this one is defaulted so that a device on this build can still
     * read a project where `0004` has not been applied yet, where the column does not exist and the
     * key is simply absent. `encodeDefaults = true` on the Postgrest serializer means it is written
     * on every push regardless, so clearing a note's formatting to NULL does travel.
     */
    @SerialName("content_document") val contentDocument: String? = null,
    /**
     * When the note was thrown away for good, NULL for one that was not — the device's `purged_at`
     * column, and the pending permanent deletion this app announces.
     *
     * Defaulted for exactly the same reason as [contentDocument], and here it earns its keep twice
     * over: a project where `0005` has not been applied has no such column, and a device on this
     * build has to be able to read it anyway. It is written on every push, so clearing it travels —
     * though nothing in the app ever clears one, because there is nothing on the other side of a
     * permanent deletion.
     */
    @SerialName("purged_at") val purgedAt: Long? = null
)

@Serializable
internal data class LinkRecord(
    @SerialName("note_id") val noteId: String,
    @SerialName("tag_id") val tagId: String
)

// ---------------------------------------------------------------------------
// Cloud -> device
//
// A row that came from the cloud is by definition already up there, so it lands
// recorded as uploaded. This is the only place that decides that, which is why the
// store does not have to.
// ---------------------------------------------------------------------------

internal fun FolderRecord.toDomain(): Folder = Folder(
    id = id,
    name = name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    userId = userId,
    syncStatus = SyncStatus.SYNCED,
    deletedAt = deletedAt,
    revision = revision
)

internal fun TagRecord.toDomain(): Tag = Tag(
    id = id,
    name = name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    userId = userId,
    syncStatus = SyncStatus.SYNCED,
    deletedAt = deletedAt,
    revision = revision
)

internal fun NoteRecord.toDomain(): Note {
    // The same rule the local table uses, from the same place — see [ReadBody]. A row that came
    // down carrying formatting this build cannot read is kept as it arrived rather than being
    // flattened to plain text and pushed back that way.
    val body = ReadBody.of(contentDocument)

    return Note(
        id = id,
        title = title,
        content = content,
        createdAt = createdAt,
        updatedAt = updatedAt,
        userId = userId,
        folderId = folderId,
        isPinned = isPinned,
        isFavorite = isFavorite,
        isLocked = isLocked,
        syncStatus = SyncStatus.SYNCED,
        deletedAt = deletedAt,
        revision = revision,
        purgedAt = purgedAt,
        document = body.document,
        unreadableDocument = body.unreadable
    )
}

// ---------------------------------------------------------------------------
// Device -> cloud
//
// [userId] is stated rather than left to the column's default, so the cloud's
// row-level security has a claim to check: an insert or update only passes when the
// owner sent is the account making the request.
// ---------------------------------------------------------------------------

internal fun Folder.toRecord(userId: String): FolderRecord = FolderRecord(
    id = id,
    userId = userId,
    name = name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    revision = revision
)

internal fun Tag.toRecord(userId: String): TagRecord = TagRecord(
    id = id,
    userId = userId,
    name = name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    revision = revision
)

internal fun Note.toRecord(userId: String): NoteRecord = NoteRecord(
    id = id,
    userId = userId,
    title = title,
    content = content,
    folderId = folderId,
    isPinned = isPinned,
    isFavorite = isFavorite,
    isLocked = isLocked,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    revision = revision,
    // What is here, whether this build wrote it or merely kept it.
    contentDocument = document?.encode() ?: unreadableDocument,
    // The pending permanent deletion, when there is one. The row it travels with has already had
    // its writing cleared, so this is the whole of what is sent to say "remove this".
    purgedAt = purgedAt
)
