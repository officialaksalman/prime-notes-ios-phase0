package com.primenotes.bd.domain.account

/**
 * What a username may be, and the identity behind it.
 *
 * A Supabase account must be an email, so a username becomes one at a domain that can
 * never receive mail. That keeps the username the thing people know and type, while
 * the account underneath stays a normal email account as far as the auth service is
 * concerned.
 *
 * The rules here mirror the database's own constraints. The database is the
 * authority — these exist so a mistake is caught before the request is made, not so
 * the client can be trusted.
 */
object Username {

    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 30

    /** RFC 2606 reserves `.invalid`, so mail to these addresses can never arrive. */
    const val IDENTITY_DOMAIN = "users.primenotes.invalid"

    private val ALLOWED = Regex("^[a-z0-9_]{$MIN_LENGTH,$MAX_LENGTH}$")

    /** Trimmed and lower-cased — what gets stored, compared, and sent. */
    fun normalise(raw: String): String = raw.trim().lowercase()

    fun isValid(raw: String): Boolean = ALLOWED.matches(normalise(raw))

    /** The address the account is actually created with. */
    fun identity(raw: String): String = "${normalise(raw)}@$IDENTITY_DOMAIN"

    /** The username inside an identity address, or null if it is some other address. */
    fun fromIdentity(identity: String): String? {
        val parts = identity.split('@')
        if (parts.size != 2 || !parts[1].equals(IDENTITY_DOMAIN, ignoreCase = true)) return null
        return parts[0].takeIf { it.isNotEmpty() }
    }

    /**
     * Whether what someone typed into sign-in should be treated as an email.
     *
     * Only an `@` distinguishes them, which is exactly how it works at sign-up: a
     * username may never contain one.
     */
    fun isEmail(identifier: String): Boolean = identifier.contains('@')
}
