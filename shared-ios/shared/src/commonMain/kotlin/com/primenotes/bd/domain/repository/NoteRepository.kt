package com.primenotes.bd.domain.repository

import com.primenotes.bd.domain.model.Note
import com.primenotes.bd.domain.model.NoteQuery
import com.primenotes.bd.domain.model.NoteSortOrder
import com.primenotes.bd.domain.model.Tag
import com.primenotes.bd.domain.rich.RichTextDocument
import kotlinx.coroutines.flow.Flow

/**
 * Notes, as the rest of the app sees them.
 *
 * Implementations are responsible for every offline-first invariant: assigning
 * identity and timestamps, bumping [Note.revision], marking mutated rows pending,
 * and turning deletes into tombstones rather than removing rows.
 */
interface NoteRepository {

    /**
     * Notes ordered and filtered by [query].
     *
     * Pinned notes are always returned first, whichever sort order is requested.
     */
    fun observeNotes(query: NoteQuery = NoteQuery.Default): Flow<List<Note>>

    /**
     * How many live notes there are.
     *
     * A number of its own rather than the length of [observeNotes]: the drawer wants a count, and
     * reading every note to produce one would load titles and bodies nobody is going to draw.
     */
    fun observeActiveCount(): Flow<Int>

    /** How many notes are favourites, for the drawer's counter. See [observeActiveCount]. */
    fun observeFavoriteCount(): Flow<Int>

    /** How many notes are locked, for the drawer's counter. See [observeActiveCount]. */
    fun observeLockedCount(): Flow<Int>

    /**
     * Notes in a folder, pinned first, in the order the person chose.
     *
     * [sortOrder] is the same stored preference the notes list reads. A folder is a list like any
     * other, and somebody who put their notes in alphabetical order should not find them in some
     * other order the moment they open a folder — so the order is handed in rather than left to
     * whatever the storage happens to return. Ordering lives here with the pinned-first rule, so a
     * folder, a tag, a favourite and the notes list cannot disagree about what "oldest first" means.
     */
    fun observeInFolder(folderId: String, sortOrder: NoteSortOrder): Flow<List<Note>>

    /** Notes carrying [tagId], pinned first, in [sortOrder]. See [observeInFolder]. */
    fun observeInTag(tagId: String, sortOrder: NoteSortOrder): Flow<List<Note>>

    /**
     * Notes whose title or body matches [query], pinned first then most recently
     * updated.
     *
     * A note matches when **every** word of the query appears in it, inside a word rather than only
     * as a whole one, and blind to case — so a half-remembered word finds the note holding it. The
     * rule is stated once, in `SearchQuery`, and this is one of its readers; the highlight on a
     * result and the find bar inside a note are the others.
     *
     * A blank — or punctuation-only — query matches nothing rather than everything, and the query
     * text is treated literally rather than as search syntax.
     *
     * Locked notes and deleted notes are never read: that exclusion belongs to the query the
     * implementation runs, not to whatever draws the result, so neither can reach a count, a
     * snippet or a log.
     */
    fun searchNotes(query: String): Flow<List<Note>>

    fun observeNote(id: String): Flow<Note?>

    suspend fun getNote(id: String): Note?

    /**
     * The identity a note created next would take.
     *
     * Asked for *before* there is anything to store, so that an editor can be opened on a note that
     * does not exist yet and still know what it will be called. That is what makes a blank draft
     * cost nothing: the note is only written once there is something to write, and until then the
     * only thing that exists is this id.
     *
     * Identity belongs to the repository — the same reason [createNote] assigns it — and this is
     * that responsibility stated one step earlier, not a second source of it.
     */
    fun newNoteId(): String

    /**
     * Creates a note, with the identity and timestamps the implementation owns.
     *
     * [document] is the formatting the note begins with, or null for a plain note. It exists for
     * one caller: the note is created empty and then opened in the editor, so the moment the style
     * a person chose can be applied is the moment it is made. Its text is empty, so [content] is
     * still the document's projection and nothing that reads the note's text is disturbed.
     *
     * [id] is for the caller that already knows the identity — an editor opened on a draft, which
     * asked for one with [newNoteId] before there was anything worth storing. Null means "choose
     * one", which is what every other caller wants.
     */
    suspend fun createNote(
        title: String,
        content: String = "",
        folderId: String? = null,
        document: RichTextDocument? = null,
        id: String? = null
    ): Note

    /** Persists an edit. The implementation owns updatedAt, revision and sync state. */
    suspend fun updateNote(note: Note): Note

    suspend fun setPinned(id: String, isPinned: Boolean)

    suspend fun setFavorite(id: String, isFavorite: Boolean)

    /**
     * Locks a note, or takes the lock off.
     *
     * A lock is a curtain, not a safe: what it hides a note from is somebody looking at the
     * screen. The row is plain text in the database and in the cloud either way, and nothing in
     * the app suggests otherwise.
     */
    suspend fun setLocked(id: String, isLocked: Boolean)

    suspend fun moveToFolder(id: String, folderId: String?)

    /** Soft-deletes the note, leaving a tombstone for sync to propagate. */
    suspend fun deleteNote(id: String)

    fun observeTagsForNote(noteId: String): Flow<List<Tag>>

    /** Replaces the note's tags atomically. */
    suspend fun setTags(noteId: String, tagIds: Collection<String>)

    suspend fun getNotesPendingSync(): List<Note>

    /** Everything deleted and not yet purged, most recently deleted first. */
    fun observeTrashed(): Flow<List<Note>>

    /** How many notes are in the trash. See [observeActiveCount]. */
    fun observeTrashedCount(): Flow<Int>

    /**
     * Brings a deleted note back.
     *
     * The mirror of [deleteNote], and deliberately the same shape as an edit: `deletedAt`
     * cleared, `updatedAt` and `revision` moved on, and the row left **pending**. Bringing a
     * note back is a change this device has to send like any other, and the null the push
     * writes is what clears the cloud's tombstone so the note returns on every device.
     *
     * @return the restored note, or null when there is nothing to restore.
     */
    suspend fun restoreNote(id: String): Note?

    /**
     * Deletes a tombstone for good — locally at once, and in the cloud on the next pass.
     *
     * A note is never hard-deleted while an account holds a copy it has not been told about: that
     * would lose the deletion, and the next pull would bring the note back as a live note. Instead
     * the row becomes a **purge marker** — `deletedAt` and `purgedAt` together, with its writing
     * cleared — which is invisible on every screen and is what a pass sends to say "remove this".
     * A note no account has ever claimed has no cloud copy to tell, so it is simply removed.
     *
     * This is why it works with no network. A permanent deletion is not something that has to wait
     * for a connection; it happens locally and is announced when there is one.
     *
     * @return true when the note is gone. False only when there was nothing to purge, or when it
     *   was not in the trash to begin with.
     */
    suspend fun deleteForGood(id: String): Boolean

    /**
     * Throws away a note that was never anything.
     *
     * For the one case the trash is not the answer to: a note created by opening a new draft,
     * written in, and then emptied again before the editor was left. Nobody has been told this note
     * exists — it was never in the notes list as a note, and its author has just deleted every word
     * of it — so putting it in the trash would be an odd thing to show them, and leaving it there
     * would be worse.
     *
     * It is the same purge [deleteForGood] performs, without the note ever having been trashed —
     * including the purge marker, so a note that reached the cloud in the seconds before it was
     * emptied is removed there too rather than left behind as an empty note.
     *
     * **The caller owns the decision.** This will remove whatever the row holds, because the editor
     * is what knows the draft is blank and it holds the truth about the note at that moment; there is
     * nothing here that could tell a draft somebody emptied from a note somebody wrote. The one
     * caller is `NoteEditorViewModel`, which asks only when it created the note itself and its own
     * fields are empty. A sheet of paper that was never anything is thrown away; a note that exists
     * is deleted, through [deleteNote], and goes to the trash where it can be found again.
     *
     * @return true when the note is gone.
     */
    suspend fun discardDraft(id: String): Boolean

    /**
     * Removes every note in the trash that was deleted before [before], and reports how many went.
     *
     * A note in the trash is recoverable for thirty days and then is not. That deadline has to be
     * enforced here as well as in the database, because the database only knows about the notes it
     * has been told about: a device with no account, or one that has been offline since before the
     * deletion, has trash the cloud has never seen and will never clean up.
     *
     * What "removed" means is [deleteForGood]'s decision — a purge marker where the cloud still has
     * to be told, and a plain removal where it does not.
     */
    suspend fun purgeExpiredTrash(before: Long): Int

    /**
     * Gives every note that has no owner to [userId], and reports how many it claimed.
     *
     * This is the one mutation here that deliberately does **not** touch `updatedAt`,
     * `revision` or `syncStatus`: claiming a note is not editing it, and marking rows
     * pending would put a lie into the sync accounting. Notes that already belong to
     * someone are never touched — only rows with no owner are claimed.
     */
    suspend fun claimUnowned(userId: String): Int
}
