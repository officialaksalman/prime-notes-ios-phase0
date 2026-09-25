package com.primenotes.bd.domain

/**
 * The current time, as the domain reads it.
 *
 * Replaces `java.time.Clock`, which cannot exist in common code. It is a plain interface rather
 * than an `expect`/`actual` pair on purpose: nothing about *asking* the time is platform-specific,
 * only *supplying* it. So the platform seam sits at the edge of the graph — `:app` supplies a
 * system-backed source, tests supply one they can drive, and iOS supplies its own at its composition
 * root — instead of being spread through the domain as `expect` declarations every platform must
 * satisfy before anything compiles.
 *
 * Milliseconds since the Unix epoch, deliberately: that is the unit every timestamp here is kept in
 * — the `created_at` and `updated_at` columns, sync cursors, and the archive format. Staying on the
 * stored unit avoids a conversion at every call site.
 */
fun interface TimeSource {
    fun nowMillis(): Long
}
