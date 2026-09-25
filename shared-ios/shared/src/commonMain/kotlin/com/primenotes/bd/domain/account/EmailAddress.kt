package com.primenotes.bd.domain.account

/**
 * Whether something typed into a form is shaped like an email address.
 *
 * Deliberately loose — enough to catch a typo before it is stored, not an attempt to
 * implement the specification. The profile holds this address for recovery, so an
 * obvious mistake is worth catching, and anything stricter only rejects valid
 * addresses.
 */
object EmailAddress {

    fun isPlausible(raw: String): Boolean {
        val trimmed = raw.trim()
        if (trimmed.any { character -> character.isWhitespace() }) return false
        if (trimmed.count { character -> character == '@' } != 1) return false

        val local = trimmed.substringBefore('@')
        val domain = trimmed.substringAfter('@')

        return local.isNotEmpty() &&
            domain.contains('.') &&
            !domain.startsWith('.') &&
            !domain.endsWith('.')
    }
}
