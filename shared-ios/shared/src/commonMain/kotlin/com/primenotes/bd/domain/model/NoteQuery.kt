package com.primenotes.bd.domain.model

/**
 * How the notes list should be ordered and filtered.
 *
 * Ordering is a presentation concern the repository owns, so the "pinned notes
 * always come first" rule lives in exactly one place no matter which sort order is
 * chosen. [Default] is what the app opens with.
 *
 * Every narrowing there is lives here too: the favourites and pinned chips on the notes list, the
 * Favorites and Locked notes destinations in the drawer, and an ordinary unfiltered read are all
 * this one query with different flags. That is what stops two of them coming to different
 * conclusions about the same collection — "favourites" means one thing in this app, and this is
 * where it is written down.
 */
data class NoteQuery(
    val sortOrder: NoteSortOrder = NoteSortOrder.LAST_MODIFIED,
    val favoritesOnly: Boolean = false,
    val pinnedOnly: Boolean = false,
    val lockedOnly: Boolean = false
) {
    companion object {
        val Default = NoteQuery()
    }
}

/**
 * Whether [note] belongs in the list [query] describes.
 *
 * A rule rather than an implementation detail, which is why it is here and not inside the
 * repository: the real read and the fake used in tests both ask this, so they cannot quietly
 * disagree about what a filter means. Every flag is "only if asked for", so the default query
 * matches everything.
 */
fun NoteQuery.matches(note: Note): Boolean =
    (!favoritesOnly || note.isFavorite) &&
        (!pinnedOnly || note.isPinned) &&
        (!lockedOnly || note.isLocked)
