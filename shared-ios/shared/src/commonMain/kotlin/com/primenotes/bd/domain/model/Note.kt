package com.primenotes.bd.domain.model

import com.primenotes.bd.domain.rich.RichTextDocument

/**
 * A note as the rest of the app sees it.
 *
 * [content] is the note's **text**, and it is what everything outside the editor reads: the list
 * preview, search, the lock mask, the trash. A note with formatting keeps its text there as the
 * formatting's projection, so no reader has to know that [document] exists — which is why one
 * column besides the text was enough to add formatting to this app.
 *
 * [document] is that formatting, when there is any and this build can read it. [unreadableDocument]
 * is a body this build *cannot* read, kept exactly as it was written.
 *
 * [deletedAt] marks a tombstone: Prime Notes never hard-deletes on the user's
 * behalf, so a deletion can still be propagated to the cloud and to other devices.
 *
 * [purgedAt] marks a tombstone the user has thrown away for good. It always sits on top of a
 * [deletedAt] — a note is only ever purged from the trash — and it means something different on
 * each side of the wire. On the device it says *this row is a purge still to be announced*: the
 * note is gone from every screen, its writing has been cleared, and what is left is the id the
 * cloud has to be told about. In the cloud it is the announcement itself. Either way the note is
 * gone as far as anyone can see, which is why every read filters it out beside [deletedAt].
 */
data class Note(
    val id: String,
    val title: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val userId: String? = null,
    val folderId: String? = null,
    val isPinned: Boolean = false,
    val isFavorite: Boolean = false,
    val isLocked: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val deletedAt: Long? = null,
    val revision: Long = 0,
    val purgedAt: Long? = null,
    val document: RichTextDocument? = null,
    /**
     * A stored body written by a version of Prime Notes this one does not understand.
     *
     * Kept verbatim so that a save can never drop formatting it never read — the editor, a sync
     * push, an import and a conflict copy all write this straight back out. Reading it as plain
     * text, which is what its [content] holds, is the best this build can do; throwing the rest
     * away would be a choice, and a bad one.
     *
     * Never set at the same time as [document]: a body is either understood or it is not.
     */
    val unreadableDocument: String? = null
)
