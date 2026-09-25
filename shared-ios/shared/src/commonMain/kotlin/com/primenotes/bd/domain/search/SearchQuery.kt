package com.primenotes.bd.domain.search

/**
 * What somebody typed, and what it means.
 *
 * One definition, used by the search itself, by the result card that highlights what matched, and by
 * the find bar inside a note. The three have to agree about what "a match" is — a card that says
 * `3 matches` above a bar that counts two would be the app contradicting itself — so the rule lives
 * here rather than in each of them.
 *
 * ## The rules
 *
 *  - **Every term has to be there.** `"oat milk"` finds a note holding both words, anywhere in it.
 *    That is what the app already did, and what the highlight already assumed.
 *  - **Inside a word, not whole words.** `sha` finds `shaon`, and so does `haon`. This is the change
 *    this phase exists for: a person searching a half-remembered word gets their note.
 *  - **Blind to case**, both ways round.
 *  - **Whitespace is whitespace.** Terms are split on any run of spaces, tabs or newlines, so the
 *    query never has to be typed exactly; punctuation-only fragments are dropped, because
 *    highlighting a stray comma is noise.
 *  - **Nothing typed, or nothing but punctuation, matches nothing** — never everything.
 */
data class SearchQuery(val raw: String) {

    /** The words worth looking for, in the order they were typed. */
    val terms: List<String> = raw
        .split(' ', '\t', '\n', '\r')
        .filter { term -> term.any { character -> character.isLetterOrDigit() } }

    /** True when there is nothing here to look for. */
    val isEmpty: Boolean
        get() = terms.isEmpty()

    /** Whether [text] holds every term. */
    fun matches(text: String): Boolean =
        !isEmpty && terms.all { term -> text.contains(term, ignoreCase = true) }

    /**
     * Every place any term appears in [text], in order.
     *
     * This is the count *and* the list of places to step through: the number of matches a note has
     * is the length of this, and the find bar walks it. Occurrences of different terms are listed
     * together, earliest first, so stepping forward moves down the page rather than jumping about.
     */
    fun rangesIn(text: String): List<IntRange> {
        if (text.isEmpty() || isEmpty) return emptyList()

        val ranges = mutableListOf<IntRange>()

        terms.forEach { term ->
            var index = text.indexOf(term, ignoreCase = true)
            while (index >= 0) {
                ranges += index until (index + term.length)
                index = text.indexOf(term, startIndex = index + term.length, ignoreCase = true)
            }
        }

        return ranges.sortedBy { range -> range.first }
    }

    /** How many times this query appears in [text]. */
    fun countIn(text: String): Int = rangesIn(text).size
}
