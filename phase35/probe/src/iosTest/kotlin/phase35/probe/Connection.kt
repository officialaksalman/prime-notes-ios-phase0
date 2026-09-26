package phase35.probe

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID

/**
 * The iOS actual, and the reason this test is worth running on a simulator rather than only on a
 * host: it opens a real file through the bundled driver, which is the same path a shipping iOS
 * build takes.
 *
 * TWO BUGS FOUND HERE, both mine, each costing a run.
 *
 * 1. The file was opened in `NSDocumentDirectory` with `create = false`. A bare unit-test bundle may
 *    not have that directory, and a path that does not exist yields a connection that cannot open.
 *    Every test then failed at its first `execSQL`, with a stack trace pointing at `null:-1` and no
 *    line of this file in it. `NSTemporaryDirectory()` is guaranteed to exist, and is where a
 *    temporary file belongs in the first place.
 *
 * 2. The name was `phase35-<pid>.db`, which is **constant for the lifetime of the process**. The host
 *    actual uses a fresh temp file per call, so the host tests each got a clean database and the iOS
 *    tests did not. The first migration test took its file to v7, and the second then opened that
 *    already-migrated file and tried to create the v6 `notes_fts` over it. That is why one migration
 *    test passed while its sibling failed on the same code — which reads like a platform quirk and
 *    is not one.
 *
 *    `NSUUID` gives every call a distinct file, which is what the host was already doing by
 *    accident. Two tests sharing a database is never what these tests mean.
 *
 * A file rather than an in-memory database on purpose: the behaviour under test is a table rebuild, a
 * virtual table and a foreign-key check, and an in-memory database would not exercise the same code.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun probeConnection(): SQLiteConnection {
    val path = NSTemporaryDirectory() + "/phase35-${NSUUID().UUIDString}.db"
    return BundledSQLiteDriver().open(path)
}
