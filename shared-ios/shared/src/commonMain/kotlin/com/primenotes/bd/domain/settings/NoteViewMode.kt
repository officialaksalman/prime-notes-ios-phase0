package com.primenotes.bd.domain.settings

/**
 * How the notes list draws its notes.
 *
 * A preference like the sort order: somebody picks the way they like to see their notes once and
 * expects it still to be that way tomorrow. It changes nothing about *which* notes are read or in
 * what order — only how each one is drawn — so it is a setting and not part of a query.
 *
 * Four modes rather than a size slider, because these are the four things a person actually wants:
 * roomy cards to read a preview, a compact list to scan titles, and two grids of tiles.
 */
enum class NoteViewMode {
    /** A card per note: title, a few lines of the writing, and when it changed. */
    LIST,

    /** One line per note: the title and when it changed, nothing else. */
    SIMPLE,

    /** Tiles, a couple across, each with a short preview. */
    GRID_LARGE,

    /** Smaller tiles, more across, for a wider look at the collection. */
    GRID_SMALL;

    companion object {
        /**
         * The mode [name] means, or [LIST] for anything this build does not know.
         *
         * Stored by name, never by ordinal, so a file written by a newer build still reads as a
         * usable file rather than as the wrong layout.
         */
        fun fromName(name: String?): NoteViewMode = entries.firstOrNull { it.name == name } ?: LIST
    }
}
