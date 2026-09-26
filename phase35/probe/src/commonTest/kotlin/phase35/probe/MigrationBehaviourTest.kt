package phase35.probe

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The behaviour the app depends on, run against the *generated* iOS-target migration.
 *
 * `ProbeDatabase_AutoMigration_6_7_Impl` is the exact source Kotlin/Native compiles for iOS — it
 * comes out of the KSP output for the `iosSimulatorArm64` target — so this is what iOS runs. The
 * migration is driven directly against a `SQLiteConnection`, which is how it is driven on iOS;
 * there is no Room builder involved, because that is precisely what 2.8.5 cannot provide.
 *
 * These tests run twice: on the Android host (a Windows developer machine, via the bundled driver)
 * and on the iOS simulator (this workflow, against a real file in the app's Documents directory).
 */
class MigrationBehaviourTest {

    @Test
    fun `v6 to v7 drops colour and keeps every note intact`() = runTest {
        val conn = probeConnection()

        // A v6 database, exactly as a device upgrading carries it.
        execV6Schema(conn)
        conn.execSQL(
            "INSERT INTO notes (id, user_id, title, content, folder_id, color, is_pinned, is_favorite, is_locked, created_at, updated_at, sync_status, deleted_at, revision, content_document, purged_at) " +
                "VALUES ('note-1', NULL, 'Groceries', 'Oat milk and sourdough', NULL, 'amber', 0, 0, 0, 1000, 2000, 'SYNCED', NULL, 3, '{\"version\":1,\"paragraphs\":[]}', NULL)"
        )
        conn.execSQL(
            "INSERT INTO notes (id, user_id, title, content, folder_id, color, is_pinned, is_favorite, is_locked, created_at, updated_at, sync_status, deleted_at, revision, content_document, purged_at) " +
                "VALUES ('note-2', NULL, 'Bakery', 'Bread and honey', NULL, 'red', 1, 1, 0, 1000, 2500, 'SYNCED', NULL, 4, NULL, NULL)"
        )
        // A trashed note — the tombstone semantics must survive the table rebuild.
        conn.execSQL(
            "INSERT INTO notes (id, user_id, title, content, folder_id, color, is_pinned, is_favorite, is_locked, created_at, updated_at, sync_status, deleted_at, revision, content_document, purged_at) " +
                "VALUES ('note-3', NULL, 'Old list', 'Bread', NULL, 'blue', 0, 0, 0, 1000, 3000, 'SYNCED', 3000, 1, NULL, NULL)"
        )
        // Sync metadata, which the migration also rebuilds.
        conn.execSQL(
            "INSERT INTO sync_cursor (account_id, entity, pulled_at) VALUES ('acct', 'notes@server-time', 4200)"
        )
        conn.execSQL(
            "INSERT INTO sync_account_state (account_id, last_synced_at, last_error, last_conflicts) VALUES ('acct', 4100, NULL, 2)"
        )

        // The upgrade, as the app would run it.
        ProbeDatabase_AutoMigration_6_7_Impl().migrate(conn)
        NoteColourColumnRemoval().onPostMigrate(conn)

        // Nothing lost.
        assertEquals(3L, count(conn, "notes"), "not one row may be lost on the way across")

        // The column that was removed is gone; the ones that stayed are still described.
        val columns = columnsOf(conn, "notes")
        assertTrue("color" !in columns, "the feature was removed, so its column has to be too")
        assertTrue("content_document" in columns, "every other column is still described")
        assertTrue("purged_at" in columns, "every other column is still described")

        // The body a note carried is carried across the rebuild.
        assertEquals(
            """{"version":1,"paragraphs":[]}""",
            text(conn, "SELECT content_document FROM notes WHERE id = 'note-1'"),
            "the formatting column is part of the table that was rebuilt"
        )
        assertNull(
            text(conn, "SELECT content_document FROM notes WHERE id = 'note-2'"),
            "and a note that had none still has none"
        )

        // Trash semantics survive: the tombstone is still a tombstone.
        assertEquals(
            "3000",
            text(conn, "SELECT deleted_at FROM notes WHERE id = 'note-3'"),
            "a deleted note is still a tombstone afterwards"
        )
        assertEquals(
            2L,
            long(conn, "SELECT COUNT(*) FROM notes WHERE deleted_at IS NULL"),
            "the live list is exactly the notes that were never trashed"
        )

        // Sync metadata survives — the watermark and the conflict count.
        assertEquals(
            "4200",
            text(conn, "SELECT pulled_at FROM sync_cursor WHERE account_id = 'acct'"),
            "the pull watermark is still there"
        )
        assertEquals(
            "2",
            text(conn, "SELECT last_conflicts FROM sync_account_state WHERE account_id = 'acct'"),
            "and so is the record of how many copies the last pass kept"
        )
    }

    /**
     * The dangerous half. Rebuilding `notes` renumbers its rows, and the external-content index is
     * keyed by row id — so an index left as it was answers with notes that no longer match. This is
     * the assertion the app's own migration comment is written about, and the one that would catch
     * a silent regression.
     */
    @Test
    fun `search still answers with the right notes after the rebuild`() = runTest {
        val conn = probeConnection()
        execV6Schema(conn)
        conn.execSQL(
            "INSERT INTO notes (id, user_id, title, content, folder_id, color, is_pinned, is_favorite, is_locked, created_at, updated_at, sync_status, deleted_at, revision, content_document, purged_at) " +
                "VALUES ('note-1', NULL, 'Groceries', 'Oat milk and sourdough', NULL, 'amber', 0, 0, 0, 1000, 2000, 'SYNCED', NULL, 3, NULL, NULL)"
        )
        conn.execSQL(
            "INSERT INTO notes (id, user_id, title, content, folder_id, color, is_pinned, is_favorite, is_locked, created_at, updated_at, sync_status, deleted_at, revision, content_document, purged_at) " +
                "VALUES ('note-2', NULL, 'Bakery', 'Bread and honey', NULL, 'red', 1, 1, 0, 1000, 2500, 'SYNCED', NULL, 4, NULL, NULL)"
        )
        // A v6 device's index is current, because the triggers kept it so.
        conn.execSQL("INSERT INTO notes_fts(notes_fts) VALUES('rebuild')")

        ProbeDatabase_AutoMigration_6_7_Impl().migrate(conn)
        NoteColourColumnRemoval().onPostMigrate(conn)

        assertEquals(
            listOf("note-1"),
            match(conn, "sourdough"),
            "the index has to point at the row that holds the word"
        )
        assertEquals(
            listOf("note-2"),
            match(conn, "honey"),
            "and a word only the other note holds"
        )
    }

    private fun execV6Schema(conn: SQLiteConnection) {
        listOf(
            "CREATE TABLE IF NOT EXISTS `folders` (`id` TEXT NOT NULL, `user_id` TEXT, `name` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `sync_status` TEXT NOT NULL, `deleted_at` INTEGER, `revision` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `tags` (`id` TEXT NOT NULL, `user_id` TEXT, `name` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `sync_status` TEXT NOT NULL, `deleted_at` INTEGER, `revision` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `notes` (`id` TEXT NOT NULL, `user_id` TEXT, `title` TEXT NOT NULL, `content` TEXT NOT NULL, `folder_id` TEXT, `color` TEXT, `is_pinned` INTEGER NOT NULL, `is_favorite` INTEGER NOT NULL, `is_locked` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `sync_status` TEXT NOT NULL, `deleted_at` INTEGER, `revision` INTEGER NOT NULL, `content_document` TEXT, `purged_at` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`folder_id`) REFERENCES `folders`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            "CREATE TABLE IF NOT EXISTS `note_tags` (`note_id` TEXT NOT NULL, `tag_id` TEXT NOT NULL, PRIMARY KEY(`note_id`, `tag_id`))",
            "CREATE VIRTUAL TABLE IF NOT EXISTS `notes_fts` USING FTS4(`title` TEXT NOT NULL, `content` TEXT NOT NULL, content=`notes`)",
            "CREATE TABLE IF NOT EXISTS `sync_cursor` (`account_id` TEXT NOT NULL, `entity` TEXT NOT NULL, `pulled_at` INTEGER NOT NULL, PRIMARY KEY(`account_id`, `entity`))",
            "CREATE TABLE IF NOT EXISTS `sync_account_state` (`account_id` TEXT NOT NULL, `last_synced_at` INTEGER, `last_error` TEXT, `last_conflicts` INTEGER NOT NULL, PRIMARY KEY(`account_id`))"
        ).forEach(conn::execSQL)
    }

    private fun count(conn: SQLiteConnection, table: String): Long =
        long(conn, "SELECT COUNT(*) FROM $table")

    private fun columnsOf(conn: SQLiteConnection, table: String): List<String> =
        conn.prepare("PRAGMA table_info(`$table`)").use { statement ->
            buildList { while (statement.step()) add(statement.getText(1)!!) }
        }

    /**
     * Reads a single text value, or null when the column is SQL NULL.
     *
     * `getText` alone is not enough: on a NULL column it hands back an empty string, which would
     * make a note that had no formatting indistinguishable from one whose body was the empty
     * document. `isNull` is the only thing that tells the two apart.
     */
    private fun text(conn: SQLiteConnection, sql: String): String? =
        conn.prepare(sql).use { statement ->
            if (!statement.step()) return@use null
            if (statement.isNull(0)) null else statement.getText(0)
        }

    private fun long(conn: SQLiteConnection, sql: String): Long =
        conn.prepare(sql).use { statement ->
            statement.step()
            statement.getLong(0)
        }

    /**
     * The FTS query, with the term written into the SQL rather than bound.
     *
     * FINDING: FTS4 rejects a parameterised `MATCH` outright — `android.database.SQLException` on a
     * `?` in the MATCH position. This is a SQLite behaviour, not a Room one, and it is the same on
     * every platform, so the app's own DAO must keep inlining the term the way it already does
     * (through `FtsMatchQuery`, which quotes it). Binding it is tempting and does not work.
     */
    private fun match(conn: SQLiteConnection, term: String): List<String> {
        val escaped = term.replace("\"", "\"\"")
        return conn.prepare(
            "SELECT notes.id FROM notes JOIN notes_fts ON notes.rowid = notes_fts.docid " +
                "WHERE notes_fts MATCH \"$escaped\" ORDER BY notes.id"
        ).use { statement ->
            buildList { while (statement.step()) add(statement.getText(0)!!) }
        }
    }
}
