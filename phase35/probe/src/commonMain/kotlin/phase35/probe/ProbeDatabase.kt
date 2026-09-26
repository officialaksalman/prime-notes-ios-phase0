package phase35.probe

import androidx.room3.AutoMigration
import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.DeleteColumn
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.migration.AutoMigrationSpec
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * The three declarations Room 2.8.5's KMP artifacts do not contain, written the way room3 requires.
 * Whether these compile for iOS is the question this probe exists to answer.
 *
 * FINDING 1 — `@ConstructedBy` plus a hand-written `expect object … : RoomDatabaseConstructor` is
 * required. Room 2.8.5's KMP artifacts contain annotations only, so nothing generates the companion.
 *
 * FINDING 2 — `onPostMigrate` is `suspend`. The app's `NoteColourColumnRemoval` overrides it as a
 * plain function, so this is a required change on adoption.
 *
 * FINDING 3 — `Migration` keeps its `startVersion`/`endVersion` constructor, so the app's
 * `object : Migration(4, 5)` spelling survives; only `migrate` changes, to `suspend` over a
 * `SQLiteConnection` instead of a `SupportSQLiteDatabase`.
 */
@ConstructedBy(ProbeDatabaseConstructor::class)
@Database(
    entities = [
        NoteEntity::class,
        FolderEntity::class,
        TagEntity::class,
        NoteTagCrossRef::class,
        NoteFtsEntity::class,
        SyncCursorEntity::class,
        SyncAccountStateEntity::class
    ],
    version = 7,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 6, to = 7, spec = NoteColourColumnRemoval::class)]
)
abstract class ProbeDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun folderDao(): FolderDao
    abstract fun syncDao(): SyncDao

    companion object {
        const val NAME = "prime_notes.db"
    }
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object ProbeDatabaseConstructor : RoomDatabaseConstructor<ProbeDatabase> {
    override fun initialize(): ProbeDatabase
}

/**
 * The v6 -> v7 auto-migration's hand-written half, already the shape the app uses: the table is
 * rebuilt, which renumbers its rows, so the external-content index keyed by row id has to be
 * rebuilt from the notes as they now stand.
 *
 * FINDING — `@DeleteColumn` is unchanged in shape: a plain annotation with `tableName` and
 * `columnName`, applied to the spec class, exactly as the app already writes it. It is NOT nested
 * under `.Entries`; that spelling is the processor's suggestion text and does not compile.
 */
@DeleteColumn(tableName = "notes", columnName = "color")
class NoteColourColumnRemoval : AutoMigrationSpec {
    override suspend fun onPostMigrate(connection: SQLiteConnection) {
        connection.execSQL("INSERT INTO notes_fts(notes_fts) VALUES('rebuild')")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `notes` ADD COLUMN `content_document` TEXT")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `notes` ADD COLUMN `purged_at` INTEGER")
    }
}
