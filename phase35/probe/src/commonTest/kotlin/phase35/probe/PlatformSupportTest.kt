package phase35.probe

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * What this platform's SQLite can actually do, stated as a test.
 *
 * The iOS run failed with a bare `androidx.sqlite.SQLiteException` at the first
 * `CREATE VIRTUAL TABLE ... USING FTS4`, with nothing to say which extension was missing. That is a
 * poor way to find out that a platform differs, so it is written down here: the migration tests
 * depend on external-content full-text search, and this test says plainly whether the platform has
 * it, and which SQLite it is.
 *
 * The result is printed as well as asserted, because the printed line is what lands in the run log
 * — and on this project that log is the only record of what a macOS runner does.
 */
class PlatformSupportTest {

    @Test
    fun `report what this platform's full text search supports`() {
        val conn = probeConnection()

        val version = text(conn, "SELECT sqlite_version()")
        println("PHASE35 sqlite_version=$version")

        val fts4 = attempt(conn, "CREATE VIRTUAL TABLE p4 USING FTS4(body)")
        val fts5 = attempt(conn, "CREATE VIRTUAL TABLE p5 USING FTS5(body)")

        println("PHASE35 FTS4=$fts4 FTS5=$fts5")

        // External content is the arrangement the app depends on: the index holds no copy of the
        // text, it points at the notes table by row id. A build with FTS but no external-content
        // tables would still be unusable, so it is checked rather than assumed.
        runCatching { conn.execSQL("CREATE TABLE fts_probe (`id` TEXT PRIMARY KEY, `body` TEXT NOT NULL)") }
        runCatching { conn.execSQL("INSERT INTO fts_probe (`id`, `body`) VALUES ('a', 'oat milk and sourdough')") }

        val external = when {
            fts4 -> attempt(conn, "CREATE VIRTUAL TABLE p4x USING FTS4(body, content='fts_probe')")
            fts5 -> attempt(conn, "CREATE VIRTUAL TABLE p5x USING FTS5(body, content='fts_probe')")
            else -> false
        }
        println("PHASE35 external_content=$external")

        val rebuild = when {
            fts4 -> attempt(conn, "INSERT INTO p4x(p4x) VALUES('rebuild')")
            fts5 -> attempt(conn, "INSERT INTO p5x(p5x) VALUES('rebuild')")
            else -> false
        }
        println("PHASE35 rebuild=$rebuild")

        // The exact shape the app declares: two NOT NULL columns, one of them called `content`,
        // pointing at a content table that has a `content` column of its own.
        runCatching { conn.execSQL("CREATE TABLE notes_probe (`id` TEXT PRIMARY KEY, `content` TEXT NOT NULL)") }
        val appShape = when {
            fts4 -> attempt(conn, "CREATE VIRTUAL TABLE appx USING FTS4(`title` TEXT NOT NULL, `content` TEXT NOT NULL, content='notes_probe')")
            fts5 -> attempt(conn, "CREATE VIRTUAL TABLE appx USING FTS5(`title` TEXT NOT NULL, `content` TEXT NOT NULL, content='notes_probe')")
            else -> false
        }
        println("PHASE35 app_shape=$appShape")

        if (!appShape) {
            fail(
                "this platform cannot create the notes_fts table the app's schema declares. " +
                    "sqlite_version=$version FTS4=$fts4 FTS5=$fts5 external_content=$external " +
                    "— the lines above say which capability is missing"
            )
        }

        assertTrue(appShape)
        assertTrue(rebuild, "the external-content 'rebuild' statement failed, and the v6 to v7 migration depends on it")
    }

    private fun attempt(conn: SQLiteConnection, sql: String): Boolean =
        runCatching { conn.execSQL(sql) }.isSuccess

    private fun text(conn: SQLiteConnection, sql: String): String? =
        conn.prepare(sql).use { statement -> if (!statement.step()) null else statement.getText(0) }
}
