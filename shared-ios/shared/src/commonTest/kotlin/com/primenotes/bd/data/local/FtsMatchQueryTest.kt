package com.primenotes.bd.data.local

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The query sanitiser is the boundary between a user's typing and FTS query syntax,
 * so it is tested against the input that would otherwise break SQLite or silently
 * change the meaning of a search.
 *
 * Every word is expected to come out quoted: that is what makes the input literal,
 * and what stops any of it being read as an operator, a column filter or a wildcard.
 *
 * In `commonTest`, and therefore on `kotlin.test` rather than JUnit: a common test compiles for
 * every target, so it may not name a JVM-only framework. `kotlin.test` resolves to JUnit on the
 * Android host and XCTest on iOS, which is what lets this one test run in both places.
 */
class FtsMatchQueryTest {

    @Test
    fun `blank input has nothing to search for`() {
        assertNull(toFtsMatchExpression(""))
        assertNull(toFtsMatchExpression("   "))
        assertNull(toFtsMatchExpression("\t\n "))
    }

    @Test
    fun `punctuation alone has nothing to search for`() {
        assertNull(toFtsMatchExpression("\""))
        assertNull(toFtsMatchExpression("-"))
        assertNull(toFtsMatchExpression("***"))
        assertNull(toFtsMatchExpression("() :"))
        assertNull(toFtsMatchExpression("%"))
    }

    @Test
    fun `a single word becomes a quoted phrase`() {
        assertEquals("\"oat\"", toFtsMatchExpression("oat"))
    }

    @Test
    fun `every word is quoted`() {
        assertEquals("\"oat\" \"milk\"", toFtsMatchExpression("oat milk"))
        assertEquals("\"one\" \"two\" \"three\"", toFtsMatchExpression("one two three"))
    }

    @Test
    fun `surrounding and repeated whitespace is ignored`() {
        assertEquals("\"oat\" \"milk\"", toFtsMatchExpression("  oat   milk  "))
    }

    @Test
    fun `FTS operators are ordinary words`() {
        assertEquals("\"AND\" \"OR\"", toFtsMatchExpression("AND OR"))
        assertEquals("\"NOT\"", toFtsMatchExpression("NOT"))
        assertEquals("\"NEAR\"", toFtsMatchExpression("NEAR"))
    }

    @Test
    fun `a quote inside a word is doubled so it stays literal`() {
        assertEquals("\"a\"\"b\"", toFtsMatchExpression("a\"b"))
        assertEquals("\"a\"\"b\" \"c\"", toFtsMatchExpression("a\"b c"))
    }

    @Test
    fun `punctuation inside a word is kept inside the quotes`() {
        assertEquals("\"foo-bar\"", toFtsMatchExpression("foo-bar"))
        assertEquals("\"foo:bar\"", toFtsMatchExpression("foo:bar"))
        assertEquals("\"check-list\" \"now\"", toFtsMatchExpression("check-list now"))
    }

    @Test
    fun `a star cannot act as a wildcard`() {
        // FTS prefix syntax is deliberately not used, so a typed '*' is just text.
        assertEquals("\"foo*\"", toFtsMatchExpression("foo*"))
        assertEquals("\"a*b\"", toFtsMatchExpression("a*b"))
    }

    @Test
    fun `punctuation-only fragments are dropped`() {
        assertEquals("\"milk\"", toFtsMatchExpression("-- milk"))
        assertEquals("\"milk\" \"bread\"", toFtsMatchExpression("milk -- bread"))
    }

    @Test
    fun `very long input does not throw`() {
        val long = "a".repeat(10_000)

        assertEquals("\"$long\"", toFtsMatchExpression(long))
    }

    @Test
    fun `non-ascii text is preserved`() {
        assertEquals("\"আমার\" \"নোট\"", toFtsMatchExpression("আমার নোট"))
    }

    /**
     * Scripts that write vowels as combining marks must survive intact — dropping one
     * would search for a different word.
     */
    @Test
    fun `scripts with combining marks are not mangled`() {
        assertEquals("\"নোট\"", toFtsMatchExpression("নোট"))
        assertEquals("\"हिन्दी\"", toFtsMatchExpression("हिन्दी"))
        assertEquals("\"২৫\"", toFtsMatchExpression("২৫"))
    }

    @Test
    fun `quotes always come in pairs`() {
        listOf("a\"b c", "he said \"hi\"", "\"a\" b", "a\"\"b c\"d", "\"\"\"").forEach { input ->
            val expression = toFtsMatchExpression(input)
            if (expression != null) {
                val quotes = expression.count { character -> character == '"' }
                assertEquals(0, quotes % 2, "unbalanced quotes for '$input'")
            }
        }
    }

    @Test
    fun `nothing is ever emitted outside quotes`() {
        // The guarantee behind the sanitiser: no fragment reaches FTS as bare syntax.
        listOf(
            "AND", "OR", "NOT", "NEAR", "AND OR NOT", "and or",
            "foo*", "foo-bar", "foo:bar", "a\"b", "abc", "২৫"
        ).forEach { input ->
            val expression = requireNotNull(toFtsMatchExpression(input))
            expression.split(' ').forEach { part ->
                assertTrue(
                    part.startsWith("\"") && part.endsWith("\""),
                    "'$part' from '$input' was emitted unquoted"
                )
            }
        }
    }
}
