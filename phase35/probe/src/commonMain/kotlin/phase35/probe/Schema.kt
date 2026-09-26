package phase35.probe

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Fts4
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * A schema-accurate mirror of the Prime Notes tables, at the current v7 shape. Column names, types,
 * nullability, indices and foreign keys are copied from what the app's own `app/schemas/…/7.json`
 * records, because a probe against a different schema would prove nothing about the real upgrade.
 *
 * The search index is FTS4, which is what that schema declares and therefore what the generated
 * v6 -> v7 migration produces. FTS5 was tried here and reverted for two measured reasons, recorded
 * on the entity below.
 */
@Entity(
    tableName = "folders",
    indices = [Index(value = ["deleted_at", "name"]), Index(value = ["sync_status"])]
)
data class FolderEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String?,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "sync_status") val syncStatus: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long?,
    val revision: Int
)

@Entity(
    tableName = "tags",
    indices = [Index(value = ["deleted_at", "name"]), Index(value = ["sync_status"])]
)
data class TagEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String?,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "sync_status") val syncStatus: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long?,
    val revision: Int
)

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folder_id"],
            onUpdate = ForeignKey.NO_ACTION,
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
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String?,
    val title: String,
    val content: String,
    @ColumnInfo(name = "folder_id") val folderId: String?,
    @ColumnInfo(name = "is_pinned") val isPinned: Boolean,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean,
    @ColumnInfo(name = "is_locked") val isLocked: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "sync_status") val syncStatus: String,
    /** Trash semantics: a soft delete, null while the note is live. */
    @ColumnInfo(name = "deleted_at") val deletedAt: Long?,
    val revision: Int,
    @ColumnInfo(name = "content_document") val contentDocument: String?,
    /** Permanent deletion, kept so the cloud can still be told without a network. */
    @ColumnInfo(name = "purged_at") val purgedAt: Long?
)

@Entity(
    tableName = "note_tags",
    primaryKeys = ["note_id", "tag_id"],
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["note_id"],
            onUpdate = ForeignKey.NO_ACTION,
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onUpdate = ForeignKey.NO_ACTION,
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["tag_id"])]
)
data class NoteTagCrossRef(
    @ColumnInfo(name = "note_id") val noteId: String,
    @ColumnInfo(name = "tag_id") val tagId: String
)

/**
 * External-content full-text index, FTS4 — the shape Prime Notes' own v6 schema declares, and the
 * shape the generated v6 -> v7 migration therefore produces.
 *
 * FTS5 was tried here and reverted. Two reasons, both measured rather than assumed:
 *
 *  * Declaring FTS5 makes the entity disagree with the committed v6 schema, which is FTS4, so Room
 *    generates a migration from one shape to a shape the upgrade never had.
 *  * FTS5 availability is not uniform even between SQLite builds on the same machine: a
 *    Windows `sqlite3.exe` on this workstation reports `no such module: FTS5` while the bundled
 *    driver has both FTS4 and FTS5. So FTS5 cannot be assumed anywhere without checking that build.
 *
 * The FTS4 statement failed on the iOS runner at the very first
 * `CREATE VIRTUAL TABLE ... USING FTS4`, with no message attached by the driver. `PlatformSupportTest`
 * now prints what the iOS build actually supports — version, FTS4, FTS5, external content, and the
 * app's exact table shape — so the next run states the cause instead of leaving it to be guessed at.
 */
@Fts4(contentEntity = NoteEntity::class)
@Entity(tableName = "notes_fts")
data class NoteFtsEntity(
    val title: String,
    val content: String
)

@Entity(tableName = "sync_cursor")
data class SyncCursorEntity(
    @PrimaryKey @ColumnInfo(name = "account_id") val accountId: String,
    val entity: String,
    @ColumnInfo(name = "pulled_at") val pulledAt: Long
)

@Entity(tableName = "sync_account_state")
data class SyncAccountStateEntity(
    @PrimaryKey @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "last_synced_at") val lastSyncedAt: Long?,
    @ColumnInfo(name = "last_error") val lastError: String?,
    @ColumnInfo(name = "last_conflicts") val lastConflicts: Int = 0
)
