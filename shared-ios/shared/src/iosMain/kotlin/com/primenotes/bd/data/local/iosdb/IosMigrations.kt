package com.primenotes.bd.data.local.iosdb

import androidx.room3.DeleteColumn
import androidx.room3.migration.AutoMigrationSpec
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * The iOS database's migrations, v1 → v7.
 *
 * Every statement here is **byte-for-byte the same SQL** as `PrimeNotesMigrations.kt` and
 * `NoteColourColumnRemoval.kt` in `:app`, which are Android's and which this must not disturb. Only
 * the API around them changes, and it is the API that is the whole reason room3 was adopted:
 *
 * |                     | Android (`androidx.room` 2.8.5)      | iOS (`androidx.room3` 3.1.0-alpha01)  |
 * |---------------------|--------------------------------------|----------------------------------------|
 * | base type           | `androidx.room.migration.Migration`   | `androidx.room3.migration.Migration`  |
 * | `migrate`           | `override fun`                        | `override suspend fun`                 |
 * | parameter           | `SupportSQLiteDatabase` (Android only) | `SQLiteConnection`                   |
 * | `onPostMigrate`     | `override fun`                        | `override suspend fun`                 |
 *
 * `SupportSQLiteDatabase` has no iOS implementation, which is the concrete reason the whole
 * migration API had to be an alpha. The `object : Migration(4, 5)` spelling is unchanged, so the
 * versions still read the way they do in `:app`.
 *
 * WHY THE DUPLICATION, since it is the same trade as the entities: `@DeleteColumn` and `Migration` are
 * annotations and types in `androidx.room` on Android and `androidx.room3` on iOS. One class cannot be
 * both. `:app`'s copies are **production** — they are what upgrades a real user's `prime_notes.db` — so
 * they are left exactly as they are and these are written alongside them. The SQL is duplicated
 * deliberately; the behaviour is not negotiable, and the parity test in `IosSchemaParityTest` checks
 * that the *schema* these produce still matches Android's committed `7.json` column by column.
 *
 * Statements are copied from the generated schemas in `app/schemas/…/2.json` … `6.json` for the reason
 * Android's comments give: Room validates the migrated schema against the exported one on open and
 * fails the upgrade if they disagree. That is as true on iOS as it is on Android.
 */

/**
 * Shared with [IosNoteColourColumnRemoval], for the same reason `:app` shares `REBUILD_NOTES_FTS`
 * between its v1 → v2 and its v6 → v7: an external-content FTS table starts **empty** even when the
 * table it mirrors is full, and after a table rebuild the rows are at new row ids while the index is
 * keyed by the old ones. One statement, so the two repairs cannot be described differently.
 */
internal const val IOS_REBUILD_NOTES_FTS =
    "INSERT INTO notes_fts(notes_fts) VALUES('rebuild')"

/** v1 → v2: adds the full-text index that search reads. Copied from `app/schemas/…/2.json`. */
private const val CREATE_NOTES_FTS =
    "CREATE VIRTUAL TABLE IF NOT EXISTS `notes_fts` USING FTS4(" +
        "`title` TEXT NOT NULL, `content` TEXT NOT NULL, content=`notes`)"

/**
 * Purely additive: no table is dropped and no existing row is touched. Room re-creates the FTS sync
 * triggers once the migration finishes.
 */
val IOS_MIGRATION_1_2 = object : Migration(1, 2) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(CREATE_NOTES_FTS)
        // An external-content FTS table is empty even when `notes` is full, so without this every note
        // written before v2 would be invisible to search — the upgrade would "succeed" while silently
        // taking away the user's ability to find their own notes.
        connection.execSQL(IOS_REBUILD_NOTES_FTS)
    }
}

/** v2 → v3: adds the two tables sync keeps its own bookkeeping in. Copied from `3.json`. */
private const val CREATE_SYNC_CURSOR =
    "CREATE TABLE IF NOT EXISTS `sync_cursor` (`account_id` TEXT NOT NULL, " +
        "`entity` TEXT NOT NULL, `pulled_at` INTEGER NOT NULL, " +
        "PRIMARY KEY(`account_id`, `entity`))"

private const val CREATE_SYNC_ACCOUNT_STATE =
    "CREATE TABLE IF NOT EXISTS `sync_account_state` (`account_id` TEXT NOT NULL, " +
        "`last_synced_at` INTEGER, `last_error` TEXT, PRIMARY KEY(`account_id`))"

/** Purely additive, and no existing row is in the path of either statement. */
val IOS_MIGRATION_2_3 = object : Migration(2, 3) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(CREATE_SYNC_CURSOR)
        connection.execSQL(CREATE_SYNC_ACCOUNT_STATE)
    }
}

/**
 * v3 → v4: records how many versions a pass had to keep as copies.
 *
 * The default is not decoration — SQLite refuses to add a `NOT NULL` column without one, because the
 * rows already there would have nothing to put in it.
 */
val IOS_MIGRATION_3_4 = object : Migration(3, 4) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE `sync_account_state` ADD COLUMN `last_conflicts` INTEGER NOT NULL DEFAULT 0"
        )
    }
}

/**
 * v4 → v5: notes gain somewhere to keep their formatting.
 *
 * One nullable column with no default, so every existing row reads NULL — which is exactly what it is,
 * a note with no formatting. Nothing about how a note's *text* is stored changes, which is why the
 * full-text index needs no migration and search behaves identically.
 */
val IOS_MIGRATION_4_5 = object : Migration(4, 5) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `notes` ADD COLUMN `content_document` TEXT")
    }
}

/**
 * v5 → v6: a note remembers when it was thrown away for good.
 *
 * It is what lets a permanent deletion be carried out with no network: the row stays, hidden from every
 * screen, as the record that the cloud still has to be told.
 */
val IOS_MIGRATION_5_6 = object : Migration(5, 6) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `notes` ADD COLUMN `purged_at` INTEGER")
    }
}

/**
 * The note colour column going, declared rather than inferred — and the search index rebuilt behind it.
 *
 * Room refuses a migration that quietly drops a column unless it is told in as many words that the drop
 * is what was meant, which is the right way round: a removed field in an entity is otherwise
 * indistinguishable from a mistake, and the mistake would be somebody's data.
 *
 * SQLite's own `DROP COLUMN` needs a version of SQLite that iOS does not ship, so the column can only
 * go by the table being rebuilt — which is what Room generates, carrying every row and every other
 * column across untouched.
 *
 * **The rebuild is why this does more than declare.** Rebuilding renumbers the rows, and the full-text
 * index beside it is keyed by row id, so an index left as it was would point at rows that have moved
 * and quietly answer with the wrong notes. This is the same statement Android's
 * `NoteColourColumnRemoval` runs, and the same reason.
 *
 * `onPostMigrate` is `suspend` in room3. `:app`'s copy overrides it as a plain function — that is a
 * required change on adoption, and the signpost that the whole migration API moved to coroutines.
 *
 * `@DeleteColumn` is unchanged in shape: a plain annotation with `tableName` and `columnName`, applied
 * to the spec class. It is *not* nested under `.Entries` — that spelling is text the compiler prints in
 * an error message and does not compile.
 */
@DeleteColumn(tableName = "notes", columnName = "color")
class IosNoteColourColumnRemoval : AutoMigrationSpec {
    override suspend fun onPostMigrate(connection: SQLiteConnection) {
        connection.execSQL(IOS_REBUILD_NOTES_FTS)
    }
}

/**
 * Every migration, in the order Room walks them, ready to hand to the builder.
 *
 * The iOS store registers this list in `IosDatabaseFactory`. It is `internal` because the factory is the
 * only thing that should hand migrations to a builder — a caller that could add its own would be able
 * to skip one, and a skipped migration is a database that opens at the wrong version.
 */
internal val IOS_MIGRATIONS: Array<Migration> = arrayOf(
    IOS_MIGRATION_1_2,
    IOS_MIGRATION_2_3,
    IOS_MIGRATION_3_4,
    IOS_MIGRATION_4_5,
    IOS_MIGRATION_5_6
)
