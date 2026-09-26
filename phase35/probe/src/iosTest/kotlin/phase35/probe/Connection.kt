package phase35.probe

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSTemporaryDirectory
import platform.posix.getpid

/**
 * The iOS actual, and the reason this test is worth running on a simulator rather than only on a
 * host: it opens a real file through the bundled driver, which is the same path a shipping iOS
 * build takes.
 *
 * FINDING, and the reason this function is now this short: every test failed on the iOS runner at
 * the *first* `execSQL` — including `PlatformSupportTest`, which creates only a plain table and
 * never touches full-text search. The stack trace pointed at `null:-1` with no line of this file in
 * it, which means the exception came from opening the connection rather than from any statement. So
 * the fault was here, not in Room, not in the generated migration, and not in SQLite.
 *
 * The directory was the suspect. `NSDocumentDirectory` with `create = false` asks the sandbox for a
 * directory a bare unit-test bundle may not have, and a path that does not exist yields a connection
 * that cannot open. `NSTemporaryDirectory()` is where a temporary file belongs in the first place and
 * is guaranteed to exist. `getpid()` keeps concurrent runs from colliding on one filename.
 *
 * A file rather than an in-memory database on purpose: the behaviour under test is a table rebuild, a
 * virtual table and a foreign-key check, and an in-memory database would not exercise the same code.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun probeConnection(): SQLiteConnection {
    val path = NSTemporaryDirectory() + "/phase35-${getpid()}.db"
    return BundledSQLiteDriver().open(path)
}
