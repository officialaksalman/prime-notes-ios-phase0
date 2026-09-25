package com.primenotes.bd.domain.model

/**
 * A tag together with how many live notes carry it.
 *
 * The count is produced by the repository from a single grouped query, so the
 * tags list never has to issue a query per row.
 */
data class TagSummary(
    val tag: Tag,
    val noteCount: Int
)
