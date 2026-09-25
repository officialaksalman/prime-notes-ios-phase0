package com.primenotes.bd.domain.model

/**
 * The profile row behind the signed-in account.
 *
 * [Account] is what the session alone can say — the username and the address it carries — and it is
 * available with no network at all. This is the same account as the database holds it, which is the
 * only place [createdAt] exists: the session does not carry the account's age, and nothing about the
 * app needs it to.
 *
 * It is read on demand rather than watched: an account's creation date does not change, so there is
 * nothing to watch for.
 */
data class AccountProfile(
    val id: String,
    val username: String,
    val email: String?,
    /** When the account was created, in epoch milliseconds, or null if the row did not say. */
    val createdAt: Long?
)
