package phase0

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Fts4
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/*
 * Spike A. The shape of the production `notes` table + its external-content FTS4 index,
 * reduced to the parts that matter for the iOS question.
 *
 * NOT production code and NOT wired into the app.
 *
 * The production definitions this mirrors:
 *   data/local/entity/NoteEntity.kt      (String primary key, soft delete, revision)
 *   data/local/entity/NoteFtsEntity.kt   (@Fts4(contentEntity = NoteEntity::class))
 *   data/local/PrimeNotesMigrations.kt   (CREATE_VIRTUAL_TABLE ... USING FTS4(..., content=`notes`))
 */

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val revision: Long,
)

@Fts4(contentEntity = NoteEntity::class)
@Entity(tableName = "notes_fts")
data class NoteFtsEntity(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Int,
    val title: String,
    val content: String,
)

@Dao
interface NoteDao {
    @Insert
    suspend fun insert(note: NoteEntity)

    @Query("SELECT * FROM notes ORDER BY updated_at DESC")
    suspend fun all(): List<NoteEntity>

    /** The query the app used before search moved into Kotlin. Proves MATCH reaches real rows. */
    @Query(
        """
        SELECT notes.* FROM notes
        INNER JOIN notes_fts ON notes_fts.rowid = notes.rowid
        WHERE notes_fts MATCH :query
        """
    )
    suspend fun search(query: String): List<NoteEntity>
}

/** v1 has no FTS index, so it must not declare a DAO that queries one. */
@Dao
interface NoteDaoV1 {
    @Insert
    suspend fun insert(note: NoteEntity)

    @Query("SELECT * FROM notes")
    suspend fun all(): List<NoteEntity>
}

// Copied from the production migration so the spike tests the real DDL, not an approximation.
const val CREATE_NOTES_FTS =
    "CREATE VIRTUAL TABLE IF NOT EXISTS `notes_fts` USING FTS4(" +
        "`title` TEXT NOT NULL, `content` TEXT NOT NULL, content=`notes`)"

/** An external-content FTS table starts empty; this is what fills it from the content table. */
const val REBUILD_NOTES_FTS = "INSERT INTO notes_fts(notes_fts) VALUES('rebuild')"

/** v1 -> v2 introduces the search index, exactly as the production migration does. */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(CREATE_NOTES_FTS)
        connection.execSQL(REBUILD_NOTES_FTS)
    }
}

/** The schema before the search index existed. */
@Database(entities = [NoteEntity::class], version = 1, exportSchema = false)
abstract class Phase0DatabaseV1 : RoomDatabase() {
    abstract fun noteDao(): NoteDaoV1
}

/** The schema after the search index was added. */
@Database(entities = [NoteEntity::class, NoteFtsEntity::class], version = 2, exportSchema = false)
abstract class Phase0Database : RoomDatabase() {
    abstract fun noteDao(): NoteDao
}

@Suppress("KotlinNoActualForExpect")
expect object Phase0DatabaseV1Constructor : RoomDatabaseConstructor<Phase0DatabaseV1> {
    override fun initialize(): Phase0DatabaseV1
}

@Suppress("KotlinNoActualForExpect")
expect object Phase0DatabaseConstructor : RoomDatabaseConstructor<Phase0Database> {
    override fun initialize(): Phase0Database
}
