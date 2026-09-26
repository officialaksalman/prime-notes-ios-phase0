package com.primenotes.bd.data.local.iosdb

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID

/**
 * Opens a throwaway iOS database for the migration tests.
 *
 * Two details here were learned the hard way in Phase 3.5, where they each cost a full CI run:
 *
 *  1. `NSTemporaryDirectory()`, not `NSDocumentDirectory`. A bare unit-test bundle is not guaranteed
 *     to have a Documents directory, and a path that does not exist yields a connection that cannot
 *     open — every test then failed at its very first statement, with a stack trace pointing at
 *     `null:-1` and none of this file in it.
 *
 *  2. `NSUUID` in the file name, so **every call gets a distinct file**. An earlier version named the
 *     file after the process, which is constant for its whole lifetime. The result was that the first
 *     test migrated its file all the way to v7 and the second then opened that already-migrated file
 *     and tried to build a v6 schema on top of it. One migration test passed and its sibling failed on
 *     identical code, which reads exactly like a platform quirk and was not one. Two tests sharing a
 *     file is never what these tests mean.
 *
 * A file rather than an in-memory database on purpose: the behaviour under test is a table rebuild, a
 * virtual table, and a foreign-key check, and an in-memory database would not exercise the same code.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun temporaryDatabasePath(): String =
    NSTemporaryDirectory() + "/ios-migration-${NSUUID().UUIDString}.db"

/** A raw connection to a fresh temporary file, for building a schema to migrate *from*. */
@OptIn(ExperimentalForeignApi::class)
internal fun openTemporaryConnection(): SQLiteConnection =
    BundledSQLiteDriver().open(temporaryDatabasePath())
