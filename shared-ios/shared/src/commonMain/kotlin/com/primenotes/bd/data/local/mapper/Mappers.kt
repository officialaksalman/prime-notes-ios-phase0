package com.primenotes.bd.data.local.mapper

import com.primenotes.bd.data.local.entity.FolderEntity
import com.primenotes.bd.data.local.entity.NoteEntity
import com.primenotes.bd.data.local.entity.TagEntity
import com.primenotes.bd.domain.model.Folder
import com.primenotes.bd.domain.model.Note
import com.primenotes.bd.domain.model.Tag
import com.primenotes.bd.domain.rich.ReadBody

/**
 * Entity <-> domain mapping.
 *
 * Entities never leave the data layer, so a schema change cannot ripple into the
 * UI, and the domain model stays free of persistence concerns.
 */

fun NoteEntity.toDomain(): Note {
    // One rule, in one place, for what a stored body means — see [ReadBody]. A body this build
    // cannot read is handed on exactly as it was written, so that every writer downstream (the
    // editor, a sync push, an import) carries it through rather than erasing what it never read.
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
        syncStatus = syncStatus,
        deletedAt = deletedAt,
        revision = revision,
        purgedAt = purgedAt,
        document = body.document,
        unreadableDocument = body.unreadable
    )
}

fun Note.toEntity(): NoteEntity = NoteEntity(
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
    syncStatus = syncStatus,
    deletedAt = deletedAt,
    revision = revision,
    contentDocument = document?.encode() ?: unreadableDocument,
    purgedAt = purgedAt
)

fun FolderEntity.toDomain(): Folder = Folder(
    id = id,
    name = name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    userId = userId,
    syncStatus = syncStatus,
    deletedAt = deletedAt,
    revision = revision
)

fun Folder.toEntity(): FolderEntity = FolderEntity(
    id = id,
    userId = userId,
    name = name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    syncStatus = syncStatus,
    deletedAt = deletedAt,
    revision = revision
)

fun TagEntity.toDomain(): Tag = Tag(
    id = id,
    name = name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    userId = userId,
    syncStatus = syncStatus,
    deletedAt = deletedAt,
    revision = revision
)

fun Tag.toEntity(): TagEntity = TagEntity(
    id = id,
    userId = userId,
    name = name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    syncStatus = syncStatus,
    deletedAt = deletedAt,
    revision = revision
)
