package com.primenotes.bd.domain.model

/**
 * How the notes list is ordered.
 *
 * Pinning always wins regardless of this choice — see [NoteQuery]. These orders
 * apply *within* the pinned and unpinned groups.
 *
 * A direction is part of the order rather than a second toggle beside it, because
 * that is what somebody chooses: "newest first" is one thing, and having to set a
 * direction for each of three fields would be two controls where one will do.
 *
 * The first three are the orders this app has always had, and their **names are the
 * stored format** — a saved preference is read back by name, so renaming one would
 * silently reset it to the default. The three below them were added later and follow
 * the same shape.
 */
enum class NoteSortOrder {
    /** Most recently changed first. The default. */
    LAST_MODIFIED,

    /** Least recently changed first. */
    LAST_MODIFIED_ASC,

    /** Most recently created first. */
    DATE_CREATED,

    /** Oldest first. */
    DATE_CREATED_ASC,

    /** By title, A to Z, with untitled notes last. */
    TITLE,

    /** By title, Z to A, with untitled notes still last. */
    TITLE_DESC
}
