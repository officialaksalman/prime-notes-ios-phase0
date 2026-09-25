package com.primenotes.bd.domain.sync

/**
 * What happened when a sync pass was asked to run.
 *
 * Results rather than exceptions, like the cloud probe and the account results: every
 * outcome has a shape the caller has to deal with, and none of them can quietly look
 * like success.
 */
sealed interface SyncResult {

    /** This build has no cloud project. Nothing to do, and nothing broken. */
    data object NotConfigured : SyncResult

    /** Nobody is signed in, so there is nothing to sync against. */
    data object NotSignedIn : SyncResult

    /** A pass was already running. The one in flight is doing the work. */
    data object AlreadyRunning : SyncResult

    /** The pass finished. [pushed] rows went up, [pulled] rows came down. */
    data class Completed(val pushed: Int, val pulled: Int) : SyncResult

    /** The pass did not finish. [reason] is prose for a person; nothing was discarded. */
    data class Failed(val reason: String) : SyncResult
}
