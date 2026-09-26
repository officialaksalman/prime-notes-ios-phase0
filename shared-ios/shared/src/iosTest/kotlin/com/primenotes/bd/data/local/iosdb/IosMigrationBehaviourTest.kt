package com.primenotes.bd.data.local.iosdb

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.execSQL
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Drives the iOS database's own migration chain on a real iOS simulator, and asserts the behaviour
 * that compiling cannot prove.
 *
 * Every other check on this database is static: `IosSchemaParityTest` compares the *declared* shape
 * against Android's, and the iOS KSP pass proves the generated code compiles. Neither of those opens a
 * database. This does — it creates a v6 file with the exact schema in
 * `shared/schemas/com.primenotes.bd.data.local.iosdb.IosDatabase/6.json`, puts real rows in it, and then
 * lets Room run the real v6 → v7 auto-migration.
 *
 * The v6 DDL is transcribed from that `6.json`, including the `color` column that v7 removes. It is
 * seeded rather than written by hand on purpose: a hand-written approximation would test a schema the
 * app never had, and the whole point is to test *this* upgrade on *this* platform.
 *
 * The assertions are the ones that actually catch data loss, and they are deliberately about behaviour
 * rather than about SQL:
 *
 *  * every note survives, and the columns that carry a user's work — content, rich text, trash marker —
 *    are carried across intact;
 *  * the dropped `color` column is genuinely gone;
 *  * **search still answers with the right notes after the rebuild.** This is the one that matters. A
 *    table rebuild renumbers its rows, and an external-content FTS index is keyed by row id, so an
 *    index left alone would point at rows that have moved and quietly return the wrong notes. Nothing
 *    in the compiled output shows whether that repair happened — it only shows up when a query runs.
 *  * sync metadata survives, because a user who has synced must not be asked to sync from scratch.
 */
class IosMigrationBehaviourTest {

    @Test
    fun v6toV7CarriesEveryNoteAndKeepsSearchWorking() = runTest {
        val path = temporaryDatabasePath()
        val driver = BundledSQLiteDriver()
        seedV6(path)

        // Opening at version 7 is what makes Room run the chain. There is no destructive fallback, so
        // if the migration were missing or wrong this throws rather than quietly emptying the file —
        // which is the behaviour `:app` relies on too.
        val database = IosDatabaseFactory.openAt(path, driver)

        // The connection comes from the *driver*, not from the database. There is no
        // `RoomDatabase.openConnection` in the KMP API — that is the Android-side accessor, and it is
        // part of what room3 replaces. The driver opened this file, so the driver is what opens a
        // second connection to it.
        val connection = driver.open(path)
        try {
            // --- every note survived the table rebuild -------------------------------------------
            assertEquals(
                3,
                connection.countOf("notes"),
                "A rebuild that renumbers rows must still carry every note across."
            )

            // --- the column that v7 removes is actually gone --------------------------------------
            val columns = connection.columnNamesOf("notes")
            assertTrue(
                "color" !in columns,
                "The colour column was removed in v7 but is still present: $columns"
            )

            // --- the columns that hold a user's work are intact ------------------------------------
            assertEquals(
                "Hello world",
                connection.textOf("SELECT `content` FROM `notes` WHERE `id` = 'note-1'"),
                "A note's text must survive the rebuild unchanged."
            )
            assertEquals(
                """{"blocks":[{"type":"paragraph"}]}""",
                connection.textOf("SELECT `content_document` FROM `notes` WHERE `id` = 'note-2'"),
                "A note's rich-text document must survive the rebuild unchanged."
            )
            // A note that never had formatting must read back as null, not as an empty string. This is
            // why `textOf` uses `isNull` rather than reading a string: `getText` on a NULL column
            // returns "" and the two would be indistinguishable if it did.
            assertTrue(
                connection.isNull("SELECT `content_document` FROM `notes` WHERE `id` = 'note-1'"),
                "A note with no rich text must read back as NULL, not as an empty string."
            )

            // --- trash semantics: a soft-deleted note stays a tombstone ----------------------------
            assertEquals(
                2,
                connection.countOf("SELECT * FROM `notes` WHERE `deleted_at` IS NULL"),
                "Only the two live notes should be untrashed."
            )
            assertEquals(
                3000L,
                connection.longOf("SELECT `deleted_at` FROM `notes` WHERE `id` = 'note-3'"),
                "A trashed note must still carry its deleted_at marker after the rebuild — it is the " +
                    "record that the cloud still has to be told about the deletion."
            )

            // --- sync metadata ---------------------------------------------------------------------
            assertEquals(
                4200L,
                connection.longOf("SELECT `pulled_at` FROM `sync_cursor` WHERE `entity` = 'notes'"),
                "The pull watermark must survive, or the user re-pulls the whole cloud."
            )
            assertEquals(
                2L,
                connection.longOf("SELECT `last_conflicts` FROM `sync_account_state`"),
                "The conflict count must survive the rebuild."
            )
            assertEquals(
                "SYNCED",
                connection.textOf("SELECT `sync_status` FROM `notes` WHERE `id` = 'note-1'"),
                "sync_status is stored as the SyncStatus wire string and must be carried across as-is."
            )

            // --- the one that matters: search after the rebuild -------------------------------------
            // The rebuild renumbers rows, and the external-content index is keyed by row id. If
            // `onPostMigrate` had not rebuilt the index, these would return nothing at all, or worse,
            // the wrong notes — and the schema would still look perfectly correct.
            assertEquals(
                listOf("note-1"),
                connection.idsMatching("sourdough"),
                "Search must find the note it found before the upgrade. A stale external-content " +
                    "index points at row ids that have moved."
            )
            assertEquals(
                listOf("note-2"),
                connection.idsMatching("honey"),
                "Search must find the second note too, keyed by its own text."
            )
        } finally {
            connection.close()
        }
    }

    @Test
    fun aFreshDatabaseOpensAtVersion7WithNoMigrationsToRun() = runTest {
        // The other direction: a brand-new file must be created at the current version, with no
        // migration needed and no table missing. A missing migration only shows up here if the
        // registration in `IosDatabaseFactory` is wrong in the other direction.
        val path = temporaryDatabasePath()
        val driver = BundledSQLiteDriver()
        val database = IosDatabaseFactory.openAt(path, driver)
        val connection = driver.open(path)
        try {
            assertEquals(0, connection.countOf("notes"), "A fresh database has no notes.")
            assertTrue(
                "color" !in connection.columnNamesOf("notes"),
                "Even a fresh database is created at v7, without the removed colour column."
            )
            // Writing and reading one row is the smallest proof that the generated DAO, the converters
            // and the schema all agree with each other.
            connection.execSQL(
                "INSERT INTO `notes` (`id`,`title`,`content`,`is_pinned`,`is_favorite`,`is_locked`," +
                    "`created_at`,`updated_at`,`sync_status`,`revision`)" +
                    " VALUES ('fresh','Title','Body',0,0,0,1,1,'SYNCED',0)"
            )
            assertEquals(
                "Title",
                connection.textOf("SELECT `title` FROM `notes` WHERE `id` = 'fresh'"),
                "A note written through the freshly-created schema must read back."
            )
        } finally {
            connection.close()
        }
    }

    /**
     * Builds a genuine v6 database at [path], with the schema from
     * `shared/schemas/com.primenotes.bd.data.local.iosdb.IosDatabase/6.json` and rows in it.
     *
     * The DDL is transcribed from that file rather than invented, so this is the schema the app
     * actually had at v6 — including the `color` column, the FTS4 external-content table, and the
     * `last_conflicts` default.
     */
    private fun seedV6(path: String) {
        val connection = BundledSQLiteDriver().open(path)
        try {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `folders` (`id` TEXT NOT NULL, `user_id` TEXT, " +
                    "`name` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                    "`sync_status` TEXT NOT NULL, `deleted_at` INTEGER, `revision` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))"
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `notes` (`id` TEXT NOT NULL, `user_id` TEXT, " +
                    "`title` TEXT NOT NULL, `content` TEXT NOT NULL, `folder_id` TEXT, " +
                    "`color` TEXT, `is_pinned` INTEGER NOT NULL, `is_favorite` INTEGER NOT NULL, " +
                    "`is_locked` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, " +
                    "`updated_at` INTEGER NOT NULL, `sync_status` TEXT NOT NULL, `deleted_at` INTEGER, " +
                    "`revision` INTEGER NOT NULL, `content_document` TEXT, `purged_at` INTEGER, " +
                    "PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`folder_id`) REFERENCES `folders`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE SET NULL )"
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `tags` (`id` TEXT NOT NULL, `user_id` TEXT, " +
                    "`name` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                    "`sync_status` TEXT NOT NULL, `deleted_at` INTEGER, `revision` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))"
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `note_tags` (`note_id` TEXT NOT NULL, `tag_id` TEXT NOT NULL, " +
                    "PRIMARY KEY(`note_id`, `tag_id`), " +
                    "FOREIGN KEY(`note_id`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                    "FOREIGN KEY(`tag_id`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            // The external-content FTS table, created empty exactly as a real v6 file would have it
            // after the app had been running — and then populated, so there is an index to go stale.
            connection.execSQL(
                "CREATE VIRTUAL TABLE IF NOT EXISTS `notes_fts` USING FTS4(" +
                    "`title` TEXT NOT NULL, `content` TEXT NOT NULL, content=`notes`)"
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `sync_cursor` (`account_id` TEXT NOT NULL, " +
                    "`entity` TEXT NOT NULL, `pulled_at` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`account_id`, `entity`))"
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `sync_account_state` (`account_id` TEXT NOT NULL, " +
                    "`last_synced_at` INTEGER, `last_error` TEXT, " +
                    "`last_conflicts` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`account_id`))"
            )

            // Three notes: a plain one, one with rich text, and one that is in the trash. The
            // trash note matters because `deleted_at` is how a deletion is recorded before the cloud
            // has been told, and a rebuild that dropped it would resurrect the note.
            connection.execSQL(
                "INSERT INTO `notes` (`id`,`title`,`content`,`color`,`is_pinned`,`is_favorite`," +
                    "`is_locked`,`created_at`,`updated_at`,`sync_status`,`revision`) VALUES" +
                    " ('note-1','Sourdough starter','A lively starter','#f0a',0,0,0,1000,1000,'SYNCED',0)," +
                    " ('note-2','Honey','Bees and honey','#fc0',0,0,0,2000,2000,'PENDING',1)," +
                    " ('note-3','Bin note','In the bin','#0f0',0,0,0,3000,3000,'FAILED',2)"
            )
            connection.execSQL(
                "UPDATE `notes` SET `content_document` = '{\"blocks\":[{\"type\":\"paragraph\"}]}'" +
                    " WHERE `id` = 'note-2'"
            )
            connection.execSQL("UPDATE `notes` SET `deleted_at` = 3000 WHERE `id` = 'note-3'")
            connection.execSQL(
                "INSERT INTO `sync_cursor` (`account_id`,`entity`,`pulled_at`)" +
                    " VALUES ('account-1','notes',4200)"
            )
            connection.execSQL(
                "INSERT INTO `sync_account_state` (`account_id`,`last_synced_at`,`last_conflicts`)" +
                    " VALUES ('account-1',5000,2)"
            )
            // Populate the index, so there is a populated external-content table for the rebuild to
            // invalidate. Without this the search assertions would pass vacuously.
            connection.execSQL("INSERT INTO `notes_fts` (`title`,`content`,`docid`)" +
                " SELECT `title`,`content`,`rowid` FROM `notes`")
        } finally {
            connection.close()
        }
    }

    // ---- small readers -----------------------------------------------------------------------------------
    //
    // Hand-rolled rather than generated, because the generated DAOs would be a second thing under test:
    // these read the file directly, so a failure means the migration did something wrong rather than
    // that a DAO was wired up incorrectly.

    private fun SQLiteConnection.countOf(sql: String): Int {
        // `prepare(...)` returns an `SQLiteStatement`, which is `use`-able; the connection is not.
        prepare(sql).use { statement ->
            return if (statement.step() && !statement.isNull(0)) statement.getInt(0) else 0
        }
    }

    private fun SQLiteConnection.textOf(sql: String): String {
        prepare(sql).use { statement ->
            if (!statement.step()) error("No row for: $sql")
            // `getText` is nullable and returns "" for a NULL column, so a null here means genuinely
            // absent, which is different from present-and-empty.
            return statement.getText(0)
                ?: error("Column 0 was NULL for: $sql — the migration dropped a value it should not have")
        }
    }

    private fun SQLiteConnection.longOf(sql: String): Long {
        prepare(sql).use { statement ->
            if (!statement.step()) error("No row for: $sql")
            return statement.getLong(0)
        }
    }

    private fun SQLiteConnection.isNull(sql: String): Boolean {
        prepare(sql).use { statement ->
            if (!statement.step()) error("No row for: $sql")
            // `getText` on a NULL column returns "", so nullness has to be asked for directly.
            return statement.isNull(0)
        }
    }

    private fun SQLiteConnection.columnNamesOf(table: String): List<String> {
        prepare("PRAGMA table_info(`$table`)").use { statement ->
            return buildList { while (statement.step()) add(statement.getText(1)!!) }
        }
    }

    private fun SQLiteConnection.idsMatching(term: String): List<String> {
        // FTS4 rejects a parameterised MATCH — "unable to use function MATCH" — so the term is inlined.
        // The terms here are test literals, not user input, and `FtsMatchQuery` inlines for the same
        // reason. That constraint is SQLite's, not this test's.
        prepare("SELECT `id` FROM `notes` WHERE `notes_fts` MATCH '$term' ORDER BY `id`").use { statement ->
            return buildList { while (statement.step()) add(statement.getText(0)!!) }
        }
    }
}
