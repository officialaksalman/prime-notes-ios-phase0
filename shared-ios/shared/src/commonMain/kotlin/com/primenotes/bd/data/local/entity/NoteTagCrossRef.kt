package com.primenotes.bd.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Join table between notes and tags.
 *
 * Both foreign keys cascade, so hard-deleting a note or a tag cannot leave an
 * orphaned link behind. The primary key already covers `note_id` lookups, so only
 * `tag_id` needs its own index.
 */
@Entity(
    tableName = "note_tags",
    primaryKeys = ["note_id", "tag_id"],
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["note_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["tag_id"])]
)
data class NoteTagCrossRef(
    @ColumnInfo(name = "note_id")
    val noteId: String,
    @ColumnInfo(name = "tag_id")
    val tagId: String
)
