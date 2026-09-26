package com.primenotes.bd.data.local.iosdb

import androidx.room3.AutoMigration
import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor

/**
 * The iOS database, on `androidx.room3`.
 *
 * It is a *separate* database from `:app`'s `PrimeNotesDatabase`, and that is the point. Android keeps
 * `androidx.room` 2.8.5 with its five migrations, its generated implementations and its shipped
 * `prime_notes.db` version 7, all untouched. room3 is a new package and a new maven group, so both
 * major versions live in this project without either one having to give way.
 *
 * The schema is version 7, with the same seven tables, and `IosSchemaParityTest` compares what Room
 * generates from these declarations against the committed Android `7.json` — so the two databases
 * cannot describe different tables without the build failing.
 *
 * The version number is Android's current one, not a fresh start at 1: an iOS store is expected to
 * open a database shaped like the one Android writes. The v6 → v7 auto-migration below is the one the
 * Phase 3.5 probe executed on an iOS simulator, and the five hand-written migrations in
 * [IosMigrations] carry the same SQL as `:app`'s, statement for statement.
 */
@ConstructedBy(IosDatabaseConstructor::class)
@Database(
    entities = [
        IosNoteEntity::class,
        IosFolderEntity::class,
        IosTagEntity::class,
        IosNoteTagCrossRef::class,
        IosNoteFtsEntity::class,
        IosSyncCursorEntity::class,
        IosSyncAccountStateEntity::class
    ],
    version = 7,
    exportSchema = true,
    // The v6 → v7 drop of the note colour column, the one change in this app's history that *removes*
    // something. Same declaration as `:app`'s `AutoMigration(from = 6, to = 7, …)`, and the spec
    // (`IosNoteColourColumnRemoval`) does the same work: SQLite has no `DROP COLUMN` on iOS, so Room
    // rebuilds the table, and the external-content FTS index beside it is rebuilt in `onPostMigrate`
    // because the rebuild renumbers the rows the index is keyed by.
    autoMigrations = [AutoMigration(from = 6, to = 7, spec = IosNoteColourColumnRemoval::class)]
)
/**
 * The iOS database has **no** `@TypeConverters`, unlike Android's `Converters` in `:app`.
 *
 * Android stores the `SyncStatus` enum and maps it with a `@TypeConverter`, because
 * `androidx.room` 2.8.5 supports that. room3's processor does not: attaching `@TypeConverters` to
 * this database makes the iOS KSP pass fail with
 *   [MissingType]: Element 'com.primenotes.bd.data.local.iosdb.IosDatabase' references a type that is
 *   not present
 * naming no missing type, because the type it cannot resolve is the converter method's `SyncStatus`
 * parameter — declared in `commonMain`, which this pass does not follow.
 *
 * So `IosNoteEntity.syncStatus` holds the `SyncStatus.wireValue` String directly and nothing is
 * converted at the database boundary. The DDL is unchanged (`sync_status TEXT NOT NULL`, per the
 * committed Android `7.json`) and the persisted value is the same string Android writes, so the two
 * databases remain interchangeable at the storage layer. `SyncStatus.fromWireValue` does the reading,
 * and it is the same function the Android converter called.
 *
 * The fix is a deliberate, recorded workaround for an alpha compiler, not a preference. If a later
 * room3 supports converters over common types, this can go back to holding the enum.
 */
abstract class IosDatabase : RoomDatabase() {
    abstract fun noteDao(): IosNoteDao

    abstract fun folderDao(): IosFolderDao

    abstract fun tagDao(): IosTagDao

    abstract fun syncDao(): IosSyncDao

    companion object {
        /** The same file name Android uses, so the two stores are recognisably the same database. */
        const val NAME = "prime_notes.db"
    }
}

/**
 * Required by [ConstructedBy]. room3 generates the `actual object` for each target from this
 * `expect` — read off the Phase 3.5 probe's generated `iosSimulatorArm64` output:
 *
 * ```kotlin
 * public actual object ProbeDatabaseConstructor : RoomDatabaseConstructor<ProbeDatabase> {
 *   actual override fun initialize(): ProbeDatabase = ProbeDatabase_Impl()
 * }
 * ```
 *
 * So the `actual` is **generated, not hand-written** — but it can only be generated because the
 * `expect` is declared. Declaring a plain `object … : RoomDatabaseConstructor` instead fails, because
 * Room's processor looks for the `expect` declaration to attach the generated `actual` to and finds
 * nothing to generate against.
 *
 * Declared under `iosMain` rather than `commonMain` because of what that fixes. `:shared`'s
 * `commonMain` also feeds the Android target, and `androidx.room3` is not on the Android compile
 * classpath at all, so a room3 `expect` there has no possible `actual` — which is what produced
 *   [MissingType]: Element 'com.primenotes.bd.data.local.iosdb.IosDatabase' references a type that is
 *   not present
 * Room's processor was reporting the unresolved `expect` as a missing type. Under `iosMain` the only
 * compilations that see this `expect` are the two iOS targets, and room3 generates an `actual` for
 * each. No Android stub is needed, because the Android compilation never reads this file.
 */
@Suppress("NO_ACTUAL_FOR_EXPECT", "KotlinNoActualForExpect")
expect object IosDatabaseConstructor : RoomDatabaseConstructor<IosDatabase> {
    override fun initialize(): IosDatabase
}

