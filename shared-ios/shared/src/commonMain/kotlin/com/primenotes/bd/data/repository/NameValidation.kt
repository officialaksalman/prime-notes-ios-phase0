package com.primenotes.bd.data.repository

/**
 * Folder and tag names come straight from the user, so they are the one place in
 * the data layer that needs input validation. Names are trimmed and must not be
 * blank. Uniqueness (case-insensitive, per kind) is enforced by the repositories
 * through `DuplicateNameException`.
 */
internal fun String.requireValidName(subject: String): String {
    val trimmed = trim()
    require(trimmed.isNotEmpty()) { "$subject name must not be blank" }
    return trimmed
}
