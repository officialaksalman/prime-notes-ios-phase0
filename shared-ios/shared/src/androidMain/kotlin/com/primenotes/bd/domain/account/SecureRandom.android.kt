package com.primenotes.bd.domain.account

import kotlin.random.Random
import kotlin.random.asKotlinRandom

/**
 * `java.security.SecureRandom`, which is exactly what this file used before it moved to common
 * code.
 *
 * `asKotlinRandom()` is the stdlib's adapter over a `java.util.Random`: a view rather than a copy,
 * so every draw still goes to the platform generator.
 */
actual fun secureRandom(): Random = java.security.SecureRandom().asKotlinRandom()
