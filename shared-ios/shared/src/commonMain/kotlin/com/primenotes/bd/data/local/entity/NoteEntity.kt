package com.primenotes.bd.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.primenotes.bd.domain.model.SyncStatus

/**
 * A note.
 *
 * [content] is the note's text and nothing else, exactly as before — everything that reads a note
 * except the editor reads this. [contentDocument] holds the formatting beside it, as JSON, and is
 * NULL for a note without any; that is the whole of how the two are told apart.
 *
 * [deletedAt] is a tombstone rather than a real delete, so a deletion can be
 * propagated to other devices in Phase 10 instead of disappearing silently.
 * [revision] is bumped on every local edit and gives Phase 10 something firmer
 * than a timestamp to detect a concurrent remote change.
 */
@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folder_id"],
            // Deleting a folder must never delete the notes inside it.
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["folder_id"]),
        Index(value = ["deleted_at", "updated_at"]),
        Index(value = ["sync_status"])
    ]
)
data class NoteEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "user_id")
    val userId: String?,
    val title: String,
    val content: String,
    @ColumnInfo(name = "folder_id")
    val folderId: String?,
    @ColumnInfo(name = "is_pinned")
    val isPinned: Boolean,
    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean,
    @ColumnInfo(name = "is_locked")
    val isLocked: Boolean,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus,
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long?,
    val revision: Long,
    /**
     * The note's formatting, as `RichTextDocument` wrote it, or NULL for plain text.
     *
     * Declared last, after [revision], so that the table this entity creates on a fresh install is
     * column-for-column the one `MIGRATION_4_5` produces by appending to the v4 table. Room
     * validates a migrated schema by column name rather than by order, so this is not required —
     * it is here so the two paths cannot describe the same table differently.
     */
    @ColumnInfo(name = "content_document")
    val contentDocument: String?,
    /**
     * When the note was thrown away for good, or NULL. Always set on a row that also has a
     * [deletedAt] — a note is purged from the trash, never straight from the notes list.
     *
     * Together they are the whole of the deletion lifecycle: `deleted_at` alone is a note in the
     * trash, and both together is a note that is gone. Nothing reads a row with either set except
     * the trash (which filters [purgedAt] out) and the sync push (which is how the cloud is told),
     * so a purge disappears from the app the moment it happens while its announcement is still
     * waiting to be sent.
     *
     * Last again, for the same reason as [contentDocument]: so that `MIGRATION_5_6`'s appended
     * column and this one are the same table.
     */
    @ColumnInfo(name = "purged_at")
    val purgedAt: Long?
)
