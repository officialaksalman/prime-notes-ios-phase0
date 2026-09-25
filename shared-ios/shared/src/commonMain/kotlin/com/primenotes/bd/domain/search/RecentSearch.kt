package com.primenotes.bd.domain.search

/**
 * What has been searched for before.
 *
 * A short list kept newest first, and the rules for keeping it are here rather than in a store or a
 * screen because they are the same rules wherever it is kept: trim what was typed, ignore a blank,
 * never hold the same search twice, and never grow past a handful.
 *
 * No Compose and no storage in this file, on purpose — these are pure functions over a list of
 * strings, so what the history does with a query can be asserted without a device.
 */

/** How many searches are kept. Long enough to be useful, short enough to stay a strip of chips. */
const val RECENT_SEARCH_LIMIT = 10

/**
 * The history with [query] put at the top.
 *
 * A blank query leaves the history alone — there is nothing to remember about an empty box — and the
 * query is trimmed first, so a stray space cannot become a chip of its own.
 *
 * An existing entry is **removed and replaced rather than skipped**, which is what makes searching
 * for the same thing again move it to the top instead of leaving a second copy. That comparison
 * ignores case, because the search itself does: `prime` and `Prime` are the same search, and two
 * chips for them would be two chips for one thing.
 */
fun List<String>.withRecentSearch(query: String): List<String> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return this

    return (listOf(trimmed) + withoutRecentSearch(trimmed)).take(RECENT_SEARCH_LIMIT)
}

/** The history without [query]. Removes only that one, and only compares it the way [withRecentSearch] does. */
fun List<String>.withoutRecentSearch(query: String): List<String> {
    val trimmed = query.trim()

    return filterNot { existing -> existing.equals(trimmed, ignoreCase = true) }
}

/**
 * The history as it should be, whatever was read.
 *
 * Applied on the way in, so a file that was hand-edited, half-written, or written by a version that
 * allowed something this one does not cannot put a blank chip or a duplicate on the screen. It is
 * the same rules as [withRecentSearch], over a whole list at once.
 */
fun List<String>.cleanedRecentSearches(): List<String> {
    val cleaned = ArrayList<String>(size)

    for (entry in this) {
        val trimmed = entry.trim()
        if (trimmed.isEmpty()) continue
        if (cleaned.any { existing -> existing.equals(trimmed, ignoreCase = true) }) continue

        cleaned += trimmed
        if (cleaned.size == RECENT_SEARCH_LIMIT) break
    }

    return cleaned
}
