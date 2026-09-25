package phase0

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Spike A. Answers the one question that decides the database approach:
 *
 *   Can the SQLite the app would ship on iOS create, populate and query the
 *   external-content FTS4 index that Prime Notes' schema contains?
 *
 * Each check is its own @Test so a failure in one does not hide the others.
 */
class SpikeATest {

    // ------------------------------------------------------------------
    // Raw driver probes — no Room involved, so a failure here is purely
    // about the SQLite build, not about Room's KMP support.
    // ------------------------------------------------------------------

    private fun rawProbe(vararg statements: String): String? =
        try {
            withRawConnection { connection -> statements.forEach { connection.execSQL(it) } }
            null
        } catch (t: Throwable) {
            t.message ?: t.toString()
        }

    private inline fun withRawConnection(block: (SQLiteConnection) -> Unit) {
        val connection = BundledSQLiteDriver().open(":memory:")
        try {
            block(connection)
        } finally {
            connection.close()
        }
    }

    private fun rawQuery(sql: String): List<String> = withRawConnection { connection ->
        val statement = connection.prepare(sql)
        try {
            buildList {
                while (statement.step()) add(statement.getText(0))
            }
        } finally {
            statement.close()
        }
    }

    @Test
    fun bundledSqliteSupportsFts4() {
        val failure = rawProbe(
            "CREATE TABLE t (id TEXT NOT NULL PRIMARY KEY, body TEXT NOT NULL)",
            "CREATE VIRTUAL TABLE t_fts USING FTS4(body, content=t)"
        )
        println("PHASE0 SPIKE A: FTS4 available in BundledSQLiteDriver = ${failure == null}")
        if (failure != null) println("PHASE0 SPIKE A: FTS4 failure = $failure")
        assertTrue(failure == null, "FTS4 is NOT supported by the bundled SQLite: $failure")
    }

    @Test
    fun bundledSqliteSupportsFts5() {
        val failure = rawProbe(
            "CREATE TABLE t (id TEXT NOT NULL PRIMARY KEY, body TEXT NOT NULL)",
            "CREATE VIRTUAL TABLE t_fts USING FTS5(body, content=t)"
        )
        println("PHASE0 SPIKE A: FTS5 available in BundledSQLiteDriver = ${failure == null}")
        if (failure != null) println("PHASE0 SPIKE A: FTS5 failure = $failure")
    }

    @Test
    fun externalContentFts4RebuildAndMatch() {
        withRawConnection { connection ->
            // The exact statements the production migration runs.
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `notes` (" +
                    "`id` TEXT NOT NULL PRIMARY KEY, `title` TEXT NOT NULL, " +
                    "`content` TEXT NOT NULL, `updated_at` INTEGER NOT NULL, `revision` INTEGER NOT NULL)"
            )
            connection.execSQL(
                "INSERT INTO `notes` (`id`,`title`,`content`,`updated_at`,`revision`) " +
                    "VALUES ('n1','Oat milk','buy oat milk and bread',1,1)"
            )
            connection.execSQL(CREATE_NOTES_FTS)
            connection.execSQL(REBUILD_NOTES_FTS)

            val statement = connection.prepare(
                "SELECT notes.id FROM notes " +
                    "INNER JOIN notes_fts ON notes_fts.rowid = notes.rowid " +
                    "WHERE notes_fts MATCH 'oat'"
            )
            val ids = try {
                buildList { while (statement.step()) add(statement.getText(0)) }
            } finally {
                statement.close()
            }
            println("PHASE0 SPIKE A: external-content MATCH 'oat' -> $ids")
            assertEquals(listOf("n1"), ids, "external-content FTS4 rebuild + MATCH did not work")
        }
    }

    // ------------------------------------------------------------------
    // Room KMP probes — does Room itself create and maintain the index?
    // ------------------------------------------------------------------

    @Test
    fun roomCreatesFtsSchemaAndTriggersKeepItInStep() = runTest {
        val path = phase0TempPath("phase0-room-${Random.nextLong()}.db")
        val database = openV2(path)
        try {
            // Nothing is written to notes_fts by the test: if the search finds this row,
            // Room's generated sync triggers did it.
            database.noteDao().insert(NoteEntity("a", "Oat milk", "buy oat milk and bread", 1L, 1L))
            database.noteDao().insert(NoteEntity("b", "Groceries", "bread and cheese", 2L, 1L))

            assertEquals(2, database.noteDao().all().size)
            val hits = database.noteDao().search("bread")
            println("PHASE0 SPIKE A: Room trigger-driven MATCH 'bread' -> ${hits.map { it.id }}")
            assertEquals(setOf("a", "b"), hits.map { it.id }.toSet())
        } finally {
            database.close()
        }
    }

    @Test
    fun migrationV1ToV2PreservesRowsAndBuildsTheIndex() = runTest {
        val path = phase0TempPath("phase0-migration-${Random.nextLong()}.db")

        // v1 is created by Room itself so the file is a genuine Room database (identity
        // hash included) rather than a hand-built one that Room would refuse to open.
        run {
            val v1 = openV1(path)
            v1.noteDao().insert(NoteEntity("n1", "Oat milk", "buy oat milk and bread", 1L, 1L))
            v1.noteDao().insert(NoteEntity("n2", "Groceries", "bread and cheese", 2L, 1L))
            assertEquals(2, v1.noteDao().all().size)
            v1.close()
        }

        val v2 = openV2(path)
        try {
            assertEquals(2, v2.noteDao().all().size, "the migration lost rows")

            val hits = v2.noteDao().search("oat")
            println("PHASE0 SPIKE A: post-migration MATCH 'oat' -> ${hits.map { it.id }}")
            assertEquals(listOf("n1"), hits.map { it.id })
        } finally {
            v2.close()
        }
    }
}
