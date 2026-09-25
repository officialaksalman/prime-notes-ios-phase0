package com.primenotes.bd.domain.account

import kotlin.random.Random

/**
 * A random source good enough to make a password with.
 *
 * This is the only platform-specific thing about [PasswordPolicy]: where its randomness comes from.
 * It is a top-level `expect`/`actual` rather than an injected `Random` on purpose — `PasswordPolicy`
 * is an `object` read from every sign-in and password screen, and a parameter would turn each of
 * those into an instance call to buy nothing.
 *
 * Both actuals are the platform's *cryptographic* generator rather than a seeded `Random`. A
 * suggested password is a credential: it has to resist guessing, and two suggestions made in the
 * same session must not be predictable from one another.
 */
expect fun secureRandom(): Random
