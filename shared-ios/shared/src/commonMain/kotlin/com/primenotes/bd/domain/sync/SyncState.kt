package com.primenotes.bd.domain.sync

/**
 * Everything the Sync card is drawn from.
 *
 * [pending] and [failed] are counted separately so the card can say "3 waiting" and "2 could
 * not be uploaded" without either number hiding the other. [lastError] is human prose from
 * the cloud layer, kept until a pass succeeds — a failure that only existed in memory would
 * be forgotten the moment the app was killed.
 *
 * [conflicts] is how many versions the last pass had to keep a second copy of. It is kept
 * beside the rest of the pass's record for the same reason: the app should never make a
 * decision about someone's writing without being able to say that it did.
 */
data class SyncState(
    val configured: Boolean = false,
    val signedIn: Boolean = false,
    val running: Boolean = false,
    val lastSyncedAt: Long? = null,
    val pending: Int = 0,
    val failed: Int = 0,
    val conflicts: Int = 0,
    val lastError: String? = null
) {

    /** Every row on this device that has not reached the cloud yet. */
    val outstanding: Int get() = pending + failed
}

/** How many rows are waiting, split by whether the server has already refused them. */
data class OutstandingWork(val pending: Int, val failed: Int) {

    companion object {
        val None = OutstandingWork(pending = 0, failed = 0)
    }
}

/** What the last finished pass for one account did. */
data class LastRun(
    val lastSyncedAt: Long?,
    val lastError: String?,
    /** How many versions that pass had to keep as copies. */
    val conflicts: Int = 0
)
