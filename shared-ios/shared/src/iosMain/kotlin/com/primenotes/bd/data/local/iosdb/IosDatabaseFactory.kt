package com.primenotes.bd.data.local.iosdb

import androidx.room3.Room
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL

/**
 * Opens the iOS database.
 *
 * The only genuinely platform-specific part of constructing this database is the *path*: iOS has no
 * `Context`, and a shipping database has to live somewhere that survives app launches and is visible
 * to the backup system. Everything else — the builder, the driver interface, the generated
 * implementation — is common code, which is why this is a small file and the rest of the database is
 * not in `iosMain` at all.
 *
 * The builder is `Room.databaseBuilder(path) { … }.setDriver(driver).build()`. That is read off
 * `androidx.room3.RoomDatabase$Builder`, which takes an `SQLiteDriver` rather than resolving a path
 * itself.
 *
 * `fallbackToDestructiveMigration` is deliberately **not** called, for the same reason `:app` does not
 * call it: silently dropping a user's notes is the one failure Prime Notes must never have. A database
 * that cannot be upgraded must fail loudly. The iOS store registers the same five migrations before it
 * ships, and the v6 → v7 auto-migration was verified on an iOS simulator in Phase 3.5.
 *
 * `BundledSQLiteDriver` is `androidx.sqlite:sqlite-bundled` — the driver whose FTS4 external-content
 * support Phase 3.5 asserted on an actual iOS simulator, which Apple's own SQLite also provides.
 */
object IosDatabaseFactory {

    /** Opens the shipping database, in the app's Documents directory. */
    @OptIn(ExperimentalForeignApi::class)
    fun openInDocuments(): IosDatabase {
        val documents = NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = 1uL, // NSUserDomainMask
            appropriateForURL = null,
            create = true,
            error = null
        )
        val path = requireNotNull(documents as NSURL?).path + "/" + IosDatabase.NAME
        return openAt(path, BundledSQLiteDriver())
    }

    /**
     * Opens a database at an explicit path with an explicit driver.
     *
     * This is the single entry point, and the one tests use. A test **must** pass a path in a temporary
     * directory rather than letting it call [openInDocuments]: `NSDocumentDirectory` is sandboxed per
     * test bundle, and a path that does not exist yields a connection that cannot open. That cost two
     * failed CI runs in Phase 3.5, where every test failed at its first statement.
     *
     * Every migration is registered, exactly as `:app`'s `PrimeNotesDatabase.create` registers its five.
     * The auto-migration from 6 to 7 needs no registration — it is declared on `IosDatabase` itself and
     * Room generates it.
     */
    fun openAt(path: String, driver: SQLiteDriver): IosDatabase =
        Room.databaseBuilder(path) { IosDatabaseConstructor.initialize() }
            .setDriver(driver)
            .addMigrations(*IOS_MIGRATIONS)
            .build()
}
