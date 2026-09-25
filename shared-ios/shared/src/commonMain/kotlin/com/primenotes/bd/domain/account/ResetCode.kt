package com.primenotes.bd.domain.account

/**
 * What a reset code looks like.
 *
 * Six digits and nothing else — the shape the project sends and the shape the field accepts. Checked
 * here so a half-typed or mistyped code is refused before it is ever sent, and the app never asks the
 * project whether something that is obviously not a code is a code.
 *
 * Deliberately only the *shape*. Whether a well-formed code is the right one is the auth service's
 * answer to give, and it is the only thing that can give it.
 */
object ResetCode {

    const val LENGTH = 6

    fun isWellFormed(raw: String): Boolean =
        raw.length == LENGTH && raw.all { character -> character.isDigit() }

    /**
     * Just the digits, and no more than six of them.
     *
     * So a code pasted with a space in it, or typed with one, still works — the alternative is
     * refusing something that is plainly the code somebody was sent.
     */
    fun cleaned(raw: String): String =
        raw.filter { character -> character.isDigit() }.take(LENGTH)
}
