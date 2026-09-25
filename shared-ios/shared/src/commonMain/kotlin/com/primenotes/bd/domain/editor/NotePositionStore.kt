package com.primenotes.bd.domain.editor

/**
 * Where a note was last left, as everything above the disk sees it.
 *
 * An interface for the same reason [com.primenotes.bd.domain.settings.SettingsStore] and
 * [com.primenotes.bd.domain.search.RecentSearchStore] are one: the editor can be tested without a
 * file, and the one part that has to be Android's own storage stays in a single implementation.
 *
 * A position is a **character offset into the note's text** — not a scroll offset in pixels, and not
 * a line number. That is the whole reason this is worth writing down. A pixel offset means something
 * different the moment a word is added above it, and a line number stops meaning anything when a
 * paragraph is broken in two, whereas an offset into the text is a place *in the note* and stays
 * recognisably the same place when the writing around it changes.
 *
 * Keyed by note id, so two notes cannot take each other's place.
 */
interface NotePositionStore {

    /**
     * Where this note was last left, or null when it never has been.
     *
     * Null and zero are different answers and are kept apart: a note nobody has opened yet has no
     * place to return to, and a note whose reader was at the very first character was left at the
     * very first character.
     */
    suspend fun position(noteId: String): Int?

    /** Remembers where a note was left. */
    suspend fun remember(noteId: String, offset: Int)
}
