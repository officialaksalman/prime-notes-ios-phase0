package com.primenotes.bd.data.local

/**
 * Turns what the user typed into a safe FTS4 `MATCH` expression.
 *
 * Text from a search box is not FTS syntax, but `MATCH` parses it as syntax anyway:
 * a bare `"`, `-`, `:`, `*`, `(` or the words AND/OR/NOT either quietly change the
 * meaning of a query or make SQLite throw. Each word is therefore wrapped in a
 * phrase with any embedded quote doubled, which makes the whole input literal — no
 * character the user types can ever be read as an operator or a wildcard.
 *
 * ## Every word must match a whole token
 *
 * The expression deliberately does **not** use FTS prefix syntax (`oat*`). FTS4
 * prefix queries return nothing at all on Android's SQLite for a table like this one
 * — measured on a device, and independently of the migration: a freshly created
 * database with a trigger-built index behaved the same way, while the identical
 * query answered correctly under desktop SQLite. Rather than ship a search that
 * silently finds nothing for a partially typed word, Prime Notes asks for the whole
 * word. The consequence is stated in the UI's own copy and in the phase notes.
 *
 * Returns null when nothing searchable is left — an empty box, or nothing but
 * punctuation — so the caller can skip the query instead of matching nothing.
 *
 * Public rather than `internal` because it now lives in `:shared` while a Robolectric test
 * helper (which needs an Android database) still lives in `:app` and calls it. `internal` stops
 * at the module boundary; this does not need to be hidden from anything, it is one pure function.
 */
fun toFtsMatchExpression(input: String): String? {
    val terms = input
        .split(' ', '\t', '\n', '\r')
        .filter { term -> term.any { character -> character.isWordCharacter() } }

    if (terms.isEmpty()) return null

    return terms.joinToString(separator = " ") { term ->
        "\"" + term.replace("\"", "\"\"") + "\""
    }
}

/**
 * Whether a character makes a fragment worth searching for.
 *
 * Combining marks count. They are not letters on their own, but a fragment written
 * only in marks is still text — and [Char.isLetterOrDigit] alone would call a word
 * like the Bengali "নোট" wordless if the vowels were marks. Nothing is stripped from
 * the query, so marks inside a term always survive into the expression.
 */
private fun Char.isWordCharacter(): Boolean =
    isLetterOrDigit() || when (category) {
        CharCategory.NON_SPACING_MARK,
        CharCategory.COMBINING_SPACING_MARK,
        CharCategory.ENCLOSING_MARK -> true

        else -> false
    }
