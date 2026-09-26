package com.primenotes.bd.data.local.iosdb

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import com.primenotes.bd.domain.model.SyncStatus

/**
 * The iOS database's entities — `androidx.room3`, mirroring the Android database's shape exactly.
 *
 * This whole package is in `iosMain`, not `commonMain`, because the iOS database is only ever built for
 * iOS. That is also what keeps the `androidx.room3` types out of `:shared`'s Android compilation, where
 * they have no meaning.
 *
 * WHY THIS DUPLICATES `com.primenotes.bd.data.local.entity`, which is the real Android one and lives in
 * `commonMain`:
 *
 *  * `@Entity` lives in `androidx.room` for Android and in `androidx.room3` for iOS. One Kotlin class
 *    cannot carry both, so the duplication is a consequence of running two Room major versions in one
 *    project — not a shortcut. room3 is a new package *and* a new maven group precisely so both can
 *    coexist.
 *  * The Android entities are **production**. `:app` generates every DAO implementation and the
 *    `AutoMigration_6_7_Impl` from them, and `prime_notes.db` version 7 is what ships. Touching them
 *    risks Android's data, so they are left exactly as they are — duplication here is a deliberate
 *    Android-regression-safety measure, not something to be tidied away.
 *
 * These copies exist only for the iOS store. `IosSchemaParityTest` is what stops the two from drifting:
 * it compares the schema Room generates from this file against the committed `app/schemas/…/7.json`, so a
 * change to one without the other fails the build rather than producing two different databases.
 *
 * Every column name, affinity, nullability, index and foreign key below is transcribed from
 * `app/schemas/com.primenotes.bd.data.local.PrimeNotesDatabase/7.json` — the authoritative record of
 * what Android actually creates.
 */

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = IosFolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folder_id"],
            onUpdate = ForeignKey.NO_ACTION,
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
data class IosNoteEntity(
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
    /**
     * Stored as TEXT, exactly as Android stores it: `sync_status TEXT NOT NULL` in `7.json`.
     *
     * This is the `SyncStatus.wireValue` String, held directly, where the Android entity holds the
     * enum and lets `:app`'s `Converters` map it. The DDL is identical — Room maps a `String` field to
     * `TEXT NOT NULL` just as the converter did — and the value written is the same string, because
     * `wireValue` is what the converter called anyway.
     *
     * The reason is concrete, not stylistic: room3's processor fails on this database with
     *   [MissingType]: Element '…iosdb.IosDatabase' references a type that is not present
     * the moment `@TypeConverters` is attached, naming no missing type. The trigger is a converter
     * method whose parameter is `SyncStatus`, a type declared in `commonMain`, which the iOS KSP pass
     * does not resolve. Removing the converter is what makes the iOS processor run. Verified by
     * bisection: with `@TypeConverters` detached the same sources compile clean, and with it attached
     * they fail identically every time.
     *
     * `SyncStatus.fromWireValue` is the reader, and it is the same function the Android side uses, so
     * the two platforms still cannot disagree about the stored string.
     */
    @ColumnInfo(name = "sync_status")
    val syncStatus: String,
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long?,
    @ColumnInfo(name = "revision")
    val revision: Long,
    @ColumnInfo(name = "content_document")
    val contentDocument: String?,
    @ColumnInfo(name = "purged_at")
    val purgedAt: Long?
)

@Entity(
    tableName = "folders",
    indices = [
        Index(value = ["deleted_at", "name"]),
        Index(value = ["sync_status"])
    ]
)
data class IosFolderEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "user_id")
    val userId: String?,
    val name: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus,
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long?,
    @ColumnInfo(name = "revision")
    val revision: Long
)

@Entity(
    tableName = "tags",
    indices = [
        Index(value = ["deleted_at", "name"]),
        Index(value = ["sync_status"])
    ]
)
data class IosTagEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "user_id")
    val userId: String?,
    val name: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus,
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long?,
    @ColumnInfo(name = "revision")
    val revision: Long
)

@Entity(
    tableName = "note_tags",
    primaryKeys = ["note_id", "tag_id"],
    foreignKeys = [
        ForeignKey(
            entity = IosNoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["note_id"],
            onUpdate = ForeignKey.NO_ACTION,
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = IosTagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onUpdate = ForeignKey.NO_ACTION,
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["tag_id"])]
)
data class IosNoteTagCrossRef(
    @ColumnInfo(name = "note_id")
    val noteId: String,
    @ColumnInfo(name = "tag_id")
    val tagId: String
)

/**
 * External-content FTS4, configured exactly as Android's is: `tokenizer=simple`,
 * `contentTable=notes`, `matchInfo=FTS4`, with the four content-sync triggers Room generates.
 *
 * FTS4 rather than FTS5 is deliberate and was measured. Apple's SQLite has the FTS4 external-content
 * support this needs — the Phase 3.5 probe asserted it on an iOS simulator — whereas FTS5 availability
 * is not uniform even between SQLite builds, so it cannot be assumed.
 */
@androidx.room3.Fts4(contentEntity = IosNoteEntity::class)
@Entity(tableName = "notes_fts")
data class IosNoteFtsEntity(
    val title: String,
    val content: String
)

@Entity(tableName = "sync_cursor")
data class IosSyncCursorEntity(
    @PrimaryKey
    @ColumnInfo(name = "account_id")
    val accountId: String,
    val entity: String,
    @ColumnInfo(name = "pulled_at")
    val pulledAt: Long
)

@Entity(tableName = "sync_account_state")
data class IosSyncAccountStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "account_id")
    val accountId: String,
    @ColumnInfo(name = "last_synced_at")
    val lastSyncedAt: Long?,
    @ColumnInfo(name = "last_error")
    val lastError: String?,
    /**
     * Mirrors Android's `last_conflicts`, which is `INTEGER NOT NULL DEFAULT 0`.
     *
     * Both halves matter. The `defaultValue` is what keeps SQLite able to add a `NOT NULL` column
     * without a table rebuild, and the Kotlin default is what a fresh row gets before it is written.
     * An earlier draft of this file had the Kotlin default but not the `defaultValue`, and
     * `IosSchemaParityTest` failed on exactly that — the committed `7.json` records `DEFAULT 0` and
     * the generated iOS schema did not. That is the whole reason the parity test exists.
     */
    @ColumnInfo(name = "last_conflicts", defaultValue = "0")
    val lastConflicts: Int = 0
)
