package com.primenotes.bd.domain.account

/** How much a password is likely to resist guessing. Guidance, not a guarantee. */
enum class PasswordStrength { WEAK, FAIR, STRONG }

/**
 * What the app asks of a password, and how it helps someone pick a better one.
 *
 * The minimum is enforced before anything is sent — the app never asks the server to
 * reject a password it already knows is unacceptable. The strength read is advice:
 * length and variety, with the two shapes that make a password worthless however long
 * it is (a single repeated character, and digits only) called out regardless of size.
 *
 * The workspace's own password policy in the Supabase dashboard should be at least as
 * strict as [MIN_LENGTH], so the server is never the weaker of the two.
 *
 * This file is common code. Its randomness is the one thing about it that is not: it comes from
 * [secureRandom], which each platform answers with its own cryptographic generator. Taking the
 * source as a parameter instead was the other option and was rejected — this is an `object` whose
 * `isAcceptable` is read on every sign-in and password screen, so a parameter would have made all
 * of those instance calls to buy nothing.
 */
object PasswordPolicy {

    const val MIN_LENGTH = 8

    private const val GENERATED_LENGTH = 16

    // Look-alike characters are left out, so a generated password can be read off the
    // screen and typed again without confusing O with 0 or l with 1.
    private const val DIGITS = "23456789"
    private const val LOWER = "abcdefghijkmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val SYMBOLS = "!@#\$%^&*-_=+?"
    private const val LOOK_ALIKES = "0O1lI"

    private val random = secureRandom()

    /** Whether a password may be sent at all. */
    fun isAcceptable(password: String): Boolean = password.length >= MIN_LENGTH

    fun strengthOf(password: String): PasswordStrength {
        if (!isAcceptable(password)) return PasswordStrength.WEAK
        if (password.all { character -> character == password.first() }) return PasswordStrength.WEAK
        if (password.all { character -> character.isDigit() }) return PasswordStrength.WEAK

        val classes = characterClasses(password)
        return when {
            password.length >= 16 && classes >= 3 -> PasswordStrength.STRONG
            password.length >= 12 && classes >= 2 -> PasswordStrength.STRONG
            classes >= 3 -> PasswordStrength.FAIR
            password.length >= 12 -> PasswordStrength.FAIR
            else -> PasswordStrength.WEAK
        }
    }

    /**
     * A password worth using: long, varied, and guaranteed to contain at least one
     * character from every class so it cannot be dismissed as one-note.
     */
    fun suggest(): String {
        val required = listOf(
            DIGITS.randomChar(),
            LOWER.randomChar(),
            UPPER.randomChar(),
            SYMBOLS.randomChar()
        )
        val filler = (0 until GENERATED_LENGTH - required.size)
            .map { (DIGITS + LOWER + UPPER + SYMBOLS).randomChar() }

        return (required + filler).shuffled(random).joinToString(separator = "")
    }

    /** How many of the four character classes appear, from zero to four. */
    private fun characterClasses(password: String): Int = listOf(
        password.any { character -> character.isDigit() },
        password.any { character -> character.isLowerCase() },
        password.any { character -> character.isUpperCase() },
        password.any { character -> !character.isLetterOrDigit() }
    ).count { present -> present }

    private fun String.randomChar(): Char = this[random.nextInt(length)]

    /** Exposed so a test can assert nothing unreadable is ever generated. */
    internal fun containsLookAlike(candidate: String): Boolean =
        candidate.any { character -> character in LOOK_ALIKES }
}
