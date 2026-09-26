package com.primenotes.bd.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

/**
 * The full-text index behind search.
 *
 * This is an external-content FTS4 table: it keeps no copy of the text and instead
 * points at `notes`, letting SQLite read `title` and `content` from there. Room
 * creates the triggers that keep it in step with every write to [NoteEntity], so no
 * repository has to remember to update the index — writing to `notes` is enough.
 *
 * [rowId] mirrors the content table's implicit rowid, which is what the search
 * query joins back on (notes are identified by a text id, but every ordinary SQLite
 * table still has a rowid).
 *
 * Notes are soft-deleted, so a tombstoned note legitimately stays in the index; the
 * search query filters those out through the join rather than by removing rows.
 */
@Fts4(contentEntity = NoteEntity::class)
@Entity(tableName = "notes_fts")
data class NoteFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Int,
    val title: String,
    val content: String
)
