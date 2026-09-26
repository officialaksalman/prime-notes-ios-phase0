package phase35.probe

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File

/**
 * The host actual. The same bundled driver an iOS build uses, and the one that ships its own
 * SQLite — which is what matters, because the behaviour under test is FTS4 and a table rebuild.
 *
 * The database file is temporary. Nothing here touches Prime Notes.
 */
actual fun probeConnection(): SQLiteConnection {
    val file = File.createTempFile("phase35", ".db")
    file.delete()
    return BundledSQLiteDriver().open(file.absolutePath)
}
