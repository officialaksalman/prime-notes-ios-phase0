package com.primenotes.bd.domain.account

import com.primenotes.bd.domain.model.AccountProfile

/**
 * What came back when the profile was asked for.
 *
 * Results rather than an exception, and deliberately four distinct answers: "this build has no
 * cloud", "nobody is signed in", "the request did not get an answer" and "here it is" are four
 * different things to say on a screen, and collapsing them into a null would make an offline device
 * look like an account with no profile.
 */
sealed interface ProfileResult {

    data class Found(val profile: AccountProfile) : ProfileResult

    /** Nobody is signed in, so there is no profile to read. */
    data object SignedOut : ProfileResult

    /** This build has no cloud project, so there is nowhere to read one from. */
    data object NotConfigured : ProfileResult

    /** The request was made and did not produce the row. [reason] is prose for a person. */
    data class Failed(val reason: String) : ProfileResult
}

/**
 * The profile row behind the signed-in account.
 *
 * Read-only, and only ever the reader's own: the database's row-level security restricts the table
 * to its owner, so there is nothing here to filter and nothing a client could send to widen it.
 */
interface ProfileRepository {

    /**
     * The profile of whoever is signed in.
     *
     * Never throws. A device with no network answers [ProfileResult.Failed], which is the honest
     * answer — the screen keeps showing what the session already knows and says nothing was fresh.
     */
    suspend fun profile(): ProfileResult
}
