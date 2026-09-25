package com.primenotes.bd.domain.sync

import com.primenotes.bd.domain.model.Note
import com.primenotes.bd.domain.model.SyncStatus

/**
 * The second note kept when two devices have changed the same one.
 *
 * Phase 9's rule is that this device's unsynced edit wins and becomes the cloud's copy,
 * which is right — an edit made here must never be lost to a pull. What it did not do was
 * anything about the version it replaced. That version is now written as a note of its own,
 * so both survive and the person who wrote them can decide.
 *
 * Only notes are copied. A folder or a tag that was renamed in two places has no content to
 * lose, and a second folder called "Work (conflict)" would help nobody.
 */
object ConflictCopy {

    /**
     * What is added to the copy's title.
     *
     * These words are **not** in `strings.xml`, alone among the text this app writes. A note
     * title is not interface: it is stored, it syncs, and it travels to other devices exactly
     * like anything else a person typed. Putting it in a resource would only mean the same
     * note was called something different on each device, in whichever language happened to
     * be set when it was created.
     */
    const val SUFFIX = " (conflict)"

    /** What an untitled note's copy is called, since a suffix alone says nothing. */
    const val UNTITLED = "Conflict copy"

    /**
     * The cloud's version of a note as a row in its own right, waiting to be uploaded.
     *
     * Everything but the identity and the title is the cloud's, so the copy reads like the
     * note it came from: the same folder, tags, colour and flags, and the time it was last
     * changed, which is true of it. The revision starts again at nothing, because it is a
     * new row and not a version of the note it was copied from.
     *
     * [id] comes from the caller so that this can be decided and tested without a database.
     */
    fun of(remote: Note, id: String): Note = remote.copy(
        id = id,
        title = titleFor(remote.title),
        revision = 0,
        syncStatus = SyncStatus.PENDING,
        deletedAt = null
    )

    fun titleFor(title: String): String =
        if (title.isBlank()) UNTITLED else title + SUFFIX
}
