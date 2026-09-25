package com.primenotes.bd.domain.sync

/**
 * Where a pull picks up from, and how far it may claim to have got.
 *
 * Both ends are the **server's** clock. A cursor records what `server_time()` said when the
 * pass began, and the cloud stamps every row with its own `server_at`, so the two agree by
 * construction. Phase 9 read by the writing device's `updated_at` instead and covered the
 * disagreement between two clocks with a five-minute guess; with one clock the margin below
 * is an allowance for a write committing in the moment a pass asked for the time, not a hedge
 * against a device being wrong.
 */
object SyncCursor {

    /**
     * How far back before the last finished point a pull reaches.
     *
     * Ten seconds is room for a transaction that was already running when the pass asked for
     * the time and committed just after it. Reading a row twice costs nothing — an identical
     * row is never applied a second time.
     */
    const val SAFETY_MARGIN_MILLIS: Long = 10 * 1000L

    /**
     * The point to read from, given what a previous pass finished at.
     *
     * No cursor at all means everything: the first sync on a device fetches the whole
     * collection, which is what makes a fresh install catch up.
     */
    fun since(pulledAt: Long?): Long = ((pulledAt ?: 0L) - SAFETY_MARGIN_MILLIS).coerceAtLeast(0L)

    /**
     * The point a pass may record, having finished reading.
     *
     * Never moves backwards: a pass that found nothing must not undo the progress of one that
     * found everything. Nor does it move forwards on a guess — the value handed in is the
     * server's own answer, not the newest row that happened to be seen.
     */
    fun advance(pulledAt: Long?, finishedAt: Long?): Long =
        maxOf(pulledAt ?: 0L, finishedAt ?: 0L)
}
