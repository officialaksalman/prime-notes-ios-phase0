package com.primenotes.bd.domain.settings

import com.primenotes.bd.domain.rich.HeadingLevel
import com.primenotes.bd.domain.rich.ListKind
import com.primenotes.bd.domain.rich.RichParagraph
import com.primenotes.bd.domain.rich.RichTextDocument

/**
 * The style a new note starts in.
 *
 * A note is empty when it is created, so there is nothing to format yet — this is the *shape* it
 * begins in, which is the one moment a style can be chosen for somebody rather than by them. It is
 * the same idea as a notebook that opens on a lined page: picking "checklist" means the first thing
 * you type is already a tick box.
 *
 * [BODY] is the default and is deliberately the same as having no preference at all: a note created
 * with it carries **no** formatting document, exactly as every note did before this setting
 * existed. Nothing changes for anyone who never opens it.
 */
enum class DefaultNoteStyle {

    /** Ordinary writing. The note is stored as plain text, with no document at all. */
    BODY,

    /** A heading, for a note that is one title and little else. */
    HEADING,

    BULLETED,

    NUMBERED,

    /** A tick box, for a note that is a list of things to do. */
    CHECKLIST;

    companion object {
        /**
         * The style [name] means, or [BODY] for anything this build does not know.
         *
         * Stored by name, never by ordinal, so a file written by a newer build still reads as a
         * usable file rather than as the wrong style.
         */
        fun fromName(name: String?): DefaultNoteStyle =
            entries.firstOrNull { style -> style.name == name } ?: BODY
    }
}

/**
 * The formatting a note created with this style begins with, or null for [DefaultNoteStyle.BODY].
 *
 * One empty paragraph carrying the style, and nothing else: its text is empty, so the note's plain
 * text is still `""` and the invariant that `content` is the document's projection holds without a
 * special case. Null rather than an empty document for [DefaultNoteStyle.BODY], because a plain
 * note is one with no document and a body-styled note must be indistinguishable from one.
 */
fun DefaultNoteStyle.firstDocument(): RichTextDocument? {
    val paragraph = when (this) {
        DefaultNoteStyle.BODY -> return null

        DefaultNoteStyle.HEADING -> RichParagraph(text = "", heading = HeadingLevel.Large)

        DefaultNoteStyle.BULLETED -> RichParagraph(text = "", list = ListKind.Bullet)

        DefaultNoteStyle.NUMBERED -> RichParagraph(text = "", list = ListKind.Numbered)

        DefaultNoteStyle.CHECKLIST -> RichParagraph(text = "", list = ListKind.Checklist)
    }

    return RichTextDocument.of(listOf(paragraph))
}
