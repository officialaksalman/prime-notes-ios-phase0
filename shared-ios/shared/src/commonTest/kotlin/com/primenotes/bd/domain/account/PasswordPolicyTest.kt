package com.primenotes.bd.domain.account

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The password rules and the generator.
 *
 * The strength read is advice rather than a guarantee, so what matters is that the
 * two shapes that make a password worthless however long it is are called out, and
 * that a suggested password is genuinely a good one.
 *
 * It lives in `commonTest` rather than the Android unit tests so that it also runs on the iOS
 * simulator, which is the only place the `SecRandomCopyBytes` generator behind
 * [PasswordPolicy.suggest] is exercised at all.
 */
class PasswordPolicyTest {

    @Test
    fun `eight characters is the floor`() {
        assertFalse(PasswordPolicy.isAcceptable("1234567"))
        assertTrue(PasswordPolicy.isAcceptable("12345678"))
    }

    @Test
    fun `anything under the floor is weak`() {
        assertEquals(PasswordStrength.WEAK, PasswordPolicy.strengthOf("Short1!"))
        assertEquals(PasswordStrength.WEAK, PasswordPolicy.strengthOf("abcd"))
    }

    @Test
    fun `one repeated character is weak however long it is`() {
        assertEquals(PasswordStrength.WEAK, PasswordPolicy.strengthOf("aaaaaaaaaaaaaaaaaaaa"))
    }

    @Test
    fun `digits alone are weak however long they are`() {
        assertEquals(PasswordStrength.WEAK, PasswordPolicy.strengthOf("12345678"))
        assertEquals(PasswordStrength.WEAK, PasswordPolicy.strengthOf("123456789012345678"))
    }

    @Test
    fun `a single short lower-case word is weak`() {
        assertEquals(PasswordStrength.WEAK, PasswordPolicy.strengthOf("password"))
        assertEquals(PasswordStrength.WEAK, PasswordPolicy.strengthOf("sunshine"))
    }

    @Test
    fun `a long single-class passphrase earns fair on length alone`() {
        // Length is worth something on its own — "correcthorse" resists guessing far
        // better than an eight-character word, even with only one character class.
        assertEquals(PasswordStrength.FAIR, PasswordPolicy.strengthOf("correcthorse"))
        assertEquals(PasswordStrength.FAIR, PasswordPolicy.strengthOf("correcthorsebatterystaple"))
    }

    @Test
    fun `short but varied is fair and long and varied is strong`() {
        assertEquals(PasswordStrength.FAIR, PasswordPolicy.strengthOf("Password1"))
        assertEquals(PasswordStrength.FAIR, PasswordPolicy.strengthOf("Tr0ub4dor"))
        assertEquals(PasswordStrength.STRONG, PasswordPolicy.strengthOf("Password1!xyz"))
        assertEquals(PasswordStrength.STRONG, PasswordPolicy.strengthOf("correct-horse-battery"))
        assertEquals(PasswordStrength.STRONG, PasswordPolicy.strengthOf("Tr0ub4dor&3-and-more"))
    }

    @Test
    fun `a suggested password is long enough and varied`() {
        val suggestion = PasswordPolicy.suggest()

        assertTrue(suggestion.length >= PasswordPolicy.MIN_LENGTH)
        assertTrue(suggestion.any { character -> character.isDigit() }, "needs a digit")
        assertTrue(suggestion.any { character -> character.isLowerCase() }, "needs a lower-case letter")
        assertTrue(suggestion.any { character -> character.isUpperCase() }, "needs an upper-case letter")
        assertTrue(suggestion.any { character -> !character.isLetterOrDigit() }, "needs a symbol")
        assertEquals(PasswordStrength.STRONG, PasswordPolicy.strengthOf(suggestion))
    }

    @Test
    fun `a suggested password can be read off the screen and typed again`() {
        repeat(20) {
            assertFalse(
                PasswordPolicy.containsLookAlike(PasswordPolicy.suggest()),
                "a suggestion contained a look-alike character"
            )
        }
    }

    @Test
    fun `two suggestions are never the same`() {
        val suggestions = (1..20).map { PasswordPolicy.suggest() }.toSet()

        assertEquals(20, suggestions.size)
    }
}
