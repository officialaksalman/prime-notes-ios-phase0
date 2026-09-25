package com.primenotes.bd.domain.backup

/**
 * What an export or an import did, or why it did not.
 *
 * Results rather than exceptions, like the cloud probe and the account results: every way this
 * can end has a shape the screen must deal with, and none of them can quietly look like
 * success.
 */
sealed interface BackupOutcome {

    /** [locked] is how many of the notes went out in the clear. */
    data class Exported(
        val notes: Int,
        val folders: Int,
        val tags: Int,
        val locked: Int
    ) : BackupOutcome

    /**
     * [added] rows came in, [kept] versions were kept alongside what was here, and [unchanged]
     * notes the file held were already here word for word.
     */
    data class Imported(val added: Int, val kept: Int, val unchanged: Int) : BackupOutcome

    /** [reason] is a sentence for a person, not a stack trace. */
    data class Failed(val reason: String) : BackupOutcome
}

/**
 * Writing the collection out, and reading one back in.
 *
 * [handle] is whatever the system file picker returned, passed on untouched: keeping it opaque
 * is what lets everything above this line be tested without a device.
 */
interface NoteBackup {

    suspend fun export(handle: String): BackupOutcome

    suspend fun import(handle: String): BackupOutcome
}
