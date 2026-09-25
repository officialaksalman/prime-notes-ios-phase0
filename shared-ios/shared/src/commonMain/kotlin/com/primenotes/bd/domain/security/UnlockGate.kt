package com.primenotes.bd.domain.security

/** How asking someone to prove who they are ended. */
sealed interface UnlockResult {

    data object Unlocked : UnlockResult

    /** Dismissed, or the attempt failed. Nothing was unlocked. */
    data object Cancelled : UnlockResult

    /**
     * There was nothing to ask with — no fingerprint, no face, no device credential set up, or
     * no activity to show it in.
     *
     * [reason] is a sentence for a person. A locked note stays shut rather than opening because
     * the gate could not be raised: a lock with no way to answer it is still a lock.
     */
    data class Unavailable(val reason: String) : UnlockResult
}

/**
 * Asking the person holding the device to prove it is them.
 *
 * An interface so the screens that need a yes or a no can be tested without one, and so the one
 * part of this app that cannot be exercised from a unit test is confined to a single
 * implementation.
 */
interface UnlockGate {

    suspend fun request(title: String, subtitle: String, cancel: String): UnlockResult
}
