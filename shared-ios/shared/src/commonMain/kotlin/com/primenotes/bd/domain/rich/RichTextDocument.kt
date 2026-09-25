package com.primenotes.bd.domain.rich

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * A note's formatting, kept beside its text.
 *
 * The note's plain text lives in the `content` column and is what every reader outside the editor
 * sees — the list preview, search, the lock mask, the trash — so nothing about the rest of the app
 * has to know this exists. What the editor needs is *this*: which words are emphasised, which
 * paragraphs are headings, and which lines are bullets, numbers or tick boxes.
 *
 * ## Why whole paragraphs rather than a flat list of ranges
 *
 * A paragraph's formatting (its heading, its alignment, whether it is a list item and whether that
 * item is ticked) belongs to the paragraph and not to a span of characters inside it. Keeping the
 * two separate means an edit in one paragraph cannot corrupt another's formatting, and it is also
 * the shape a text buffer hands over naturally when the text is split on its line breaks.
 *
 * ## The format is the field and constant names
 *
 * As with a backup file, the names here are the stored format. [version] is what makes changing
 * them safe: a document is only read when its version is one of [SUPPORTED_VERSIONS], so a body
 * written by a newer build is refused outright rather than half-understood. [version] is therefore a
 * required parameter — so it is always written, even when it holds the default.
 */
@Serializable
data class RichTextDocument(
    val version: Int,
    val paragraphs: List<RichParagraph> = emptyList()
) {

    /**
     * The note's text, exactly as a person would read it off the screen.
     *
     * Paragraphs are joined with a line break and *nothing else*: a bullet is a list item and a
     * tick box is a tick box, never a character. That is what keeps search results and list
     * previews clean prose rather than markup, and it is what makes [fromPlainText] and this the
     * two halves of a lossless pair.
     */
    fun projection(): String =
        paragraphs.joinToString(separator = LINE_BREAK) { paragraph -> paragraph.text }

    /** The document as it is stored, in the column, on the wire, and in a backup. */
    fun encode(): String = json.encodeToString(this)

    companion object {

        /** The version this build writes. */
        const val CURRENT_VERSION = 2

        /**
         * Every version this build can read.
         *
         * There is no reason to refuse a v1 document: v2 added one optional field to a span, so a v1
         * document is a v2 document whose spans carry no text colour — which is what they are. A
         * document from a *newer* version is still refused, because that one may have changed a
         * meaning rather than added a field.
         */
        val SUPPORTED_VERSIONS = 1..CURRENT_VERSION

        /** The separator between paragraphs — and nothing more elaborate than that. */
        const val LINE_BREAK = "\n"

        fun of(paragraphs: List<RichParagraph>): RichTextDocument =
            RichTextDocument(version = CURRENT_VERSION, paragraphs = paragraphs)

        /**
         * A note's text as a document with no formatting at all.
         *
         * The inverse of [projection], and deliberately so: splitting and joining on [LINE_BREAK]
         * alone leaves Windows line endings, blank lines, a trailing line break, Bengali, combining
         * marks and characters outside the basic plane all exactly as they were. A plain note can
         * become a document and come back without a character of it changing.
         */
        fun fromPlainText(text: String): RichTextDocument =
            of(text.split(LINE_BREAK).map { line -> RichParagraph(text = line) })

        /**
         * The document [raw] holds, or null when this build cannot read it.
         *
         * Null rather than a best guess, in three cases: malformed JSON, a version this build does
         * not know, and anything else that goes wrong. What matters is that a body is never
         * *partly* understood — a tolerant read would drop whatever it did not recognise and then
         * write that loss back, and the person's formatting would be gone for good. A null here
         * means the caller keeps the raw text instead, which is the only safe answer.
         */
        fun decode(raw: String): RichTextDocument? {
            val document = try {
                json.decodeFromString<RichTextDocument>(raw)
            } catch (_: Exception) {
                return null
            }

            return document.takeIf { parsed -> parsed.version in SUPPORTED_VERSIONS }
        }

        /**
         * Defaults are left out, so an ordinary paragraph costs almost nothing: a document is only
         * as large as what was actually formatted. Reading is unaffected, because every default is
         * the meaning that was in force when the version was written.
         */
        private val json = Json {
            encodeDefaults = false
            ignoreUnknownKeys = true
        }
    }
}

/**
 * One line of a note, with everything that belongs to the line rather than to words in it.
 *
 * [spans] are offsets into [text] alone, so moving a paragraph's formatting cannot be thrown off by
 * a change in the paragraph above it.
 */
@Serializable
data class RichParagraph(
    val text: String,
    val heading: HeadingLevel = HeadingLevel.Body,
    val align: TextAlign = TextAlign.Start,
    val list: ListKind = ListKind.None,
    /** Only meaningful for [ListKind.Checklist], and false everywhere else. */
    val checked: Boolean = false,
    val spans: List<InlineSpan> = emptyList()
)

/**
 * Emphasis over a range of a paragraph's characters.
 *
 * Offsets are a range in the paragraph's own text — [start] inclusive, [end] exclusive — which is
 * the same shape a text buffer reports and the same convention Kotlin's own ranges use.
 *
 * [highlight] is the colour *behind* the characters and [textColor] the colour *of* them. Two
 * fields rather than one, because they are two different things a person can do independently: a
 * note can have blue writing on a yellow highlight.
 */
@Serializable
data class InlineSpan(
    val start: Int,
    val end: Int,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val highlight: HighlightColor = HighlightColor.None,
    val textColor: TextColor = TextColor.None
)

/** A paragraph's size. Body is ordinary writing; the rest are headings, largest first. */
@Serializable
enum class HeadingLevel { Body, Large, Medium, Small }

/** Where a paragraph sits across the page. */
@Serializable
enum class TextAlign { Start, Center, End }

/** What a paragraph is: prose, a bullet, a numbered item, or something to tick off. */
@Serializable
enum class ListKind { None, Bullet, Numbered, Checklist }

/**
 * A colour behind a range of characters.
 *
 * Deliberately a small palette, and one named by meaning rather than by hex: a highlight has to
 * work in both themes, and a stored hex value would freeze one theme into the note for ever.
 */
@Serializable
enum class HighlightColor { None, Yellow, Green, Blue, Pink }

/**
 * A colour for the writing itself.
 *
 * The same reasoning as [HighlightColor] — a small palette, named by meaning, so a note is not
 * frozen into one theme — but a different palette, because a colour that reads well *behind* text
 * and a colour that reads well *as* text are not the same set of colours.
 */
@Serializable
enum class TextColor { None, Red, Orange, Green, Blue, Purple }

/**
 * A stored body, read once.
 *
 * Three fields' worth of meaning in two: either a body this build understands, or one it does not
 * and is keeping exactly as it was written, or nothing at all. Never both — which is why
 * [document] and [unreadable] are mutually exclusive rather than two independent properties.
 *
 * Here rather than beside any one reader because a body comes off a row in three places — the local
 * table, the wire, and a backup file — and all three have to give the same answer about a body this
 * build cannot read. The one answer that matters: keep it, do not guess at it, and never write back
 * a version of it that has quietly lost whatever was not understood.
 */
data class ReadBody(val document: RichTextDocument?, val unreadable: String?) {

    companion object {
        fun of(raw: String?): ReadBody {
            if (raw == null) return ReadBody(document = null, unreadable = null)

            val document = RichTextDocument.decode(raw)

            return ReadBody(
                document = document,
                unreadable = if (document == null) raw else null
            )
        }
    }
}
