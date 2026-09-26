package com.primenotes.bd.data.local.dao

/**
 * A row count for one id, produced by a `GROUP BY` query.
 *
 * Counting in SQL rather than in memory keeps the folders and tags lists from
 * issuing a query per row, and lets the existing indexes on `folder_id` and
 * `note_tags.tag_id` do the work.
 */
data class IdCount(
    val id: String,
    val count: Int
)
