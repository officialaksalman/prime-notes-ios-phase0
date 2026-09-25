package com.primenotes.bd.domain.account

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlin.random.Random
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault

/**
 * `SecRandomCopyBytes`, the same generator iOS uses for keys and tokens.
 *
 * `Random`'s one abstract member is `nextBits`, so a `Random` over the platform generator is a
 * four-byte draw per call with the requested number of bits taken from the top. Nothing else needs
 * writing: `nextInt`, `nextInt(until)` and `shuffled` are all derived from it, which is what lets
 * [PasswordPolicy] keep its two existing uses unchanged.
 *
 * A failure to draw is not recoverable here — the alternative would be to hand back a value that is
 * not random, and this exists precisely to avoid that — so it throws rather than degrades.
 */
actual fun secureRandom(): Random = CryptoRandom()

@OptIn(ExperimentalForeignApi::class)
private class CryptoRandom : Random() {

    override fun nextBits(bitCount: Int): Int {
        require(bitCount in 0..BITS) { "bitCount must be in 0..$BITS, was $bitCount" }
        if (bitCount == 0) return 0

        val bytes = ByteArray(BYTES)
        val status = bytes.usePinned { pinned ->
            SecRandomCopyBytes(kSecRandomDefault, bytes.size.convert(), pinned.addressOf(0))
        }
        check(status == errSecSuccess) { "SecRandomCopyBytes failed with status $status" }

        val value = ((bytes[0].toInt() and 0xFF) shl 24) or
            ((bytes[1].toInt() and 0xFF) shl 16) or
            ((bytes[2].toInt() and 0xFF) shl 8) or
            (bytes[3].toInt() and 0xFF)

        return value ushr (BITS - bitCount)
    }

    private companion object {
        const val BITS = 32
        const val BYTES = BITS / 8
    }
}
