package phase0

import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

/*
 * The only part of the database API that must differ per platform, mirroring what the
 * production plan says: the builder is platform-specific, everything else is shared.
 */

expect fun phase0RoomBuilder(path: String): RoomDatabase.Builder<Phase0Database>

expect fun phase0RoomBuilderV1(path: String): RoomDatabase.Builder<Phase0DatabaseV1>

expect fun platformEnv(name: String): String?

expect fun phase0TempPath(fileName: String): String

/**
 * The production plan's chosen driver: SQLite compiled from source and bundled, so both
 * platforms run the *same* SQLite rather than whatever the OS happens to ship.
 */
fun openV1(path: String): Phase0DatabaseV1 =
    phase0RoomBuilderV1(path)
        .setDriver(BundledSQLiteDriver())
        .build()

fun openV2(path: String): Phase0Database =
    phase0RoomBuilder(path)
        .setDriver(BundledSQLiteDriver())
        .addMigrations(MIGRATION_1_2)
        .build()
