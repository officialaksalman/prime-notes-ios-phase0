package com.primenotes.bd.domain.model

/**
 * A folder together with how many live notes it holds.
 *
 * The count is produced by the repository from a single grouped query, so the
 * folders list never has to issue a query per row.
 */
data class FolderSummary(
    val folder: Folder,
    val noteCount: Int
)
