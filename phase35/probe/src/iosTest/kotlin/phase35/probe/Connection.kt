package phase35.probe

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.posix.getpid

/**
 * The iOS actual, and the reason this test is worth running on a simulator rather than only on a
 * host: it opens a real file in the app's Documents directory through the bundled driver, which is
 * the same path a shipping iOS build takes.
 *
 * A file rather than an in-memory database on purpose — the behaviour under test is a table rebuild,
 * a virtual table and a foreign-key check, and an in-memory database would not exercise the same
 * code. The process id keeps concurrent runs from colliding on one filename.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun probeConnection(): SQLiteConnection {
    val documents = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = 1uL, // NSUserDomainMask
        appropriateForURL = null,
        create = false,
        error = null
    )
    val path = requireNotNull(documents as NSURL?).path + "/phase35-${getpid()}.db"
    NSFileManager.defaultManager.removeItemAtPath(path, error = null)
    return BundledSQLiteDriver().open(path)
}
