package com.primenotes.bd.domain.sync

/**
 * How long a deletion is kept on the device after it has been uploaded.
 *
 * A tombstone exists so a deletion can reach the account's other devices. Once it has been
 * uploaded, the cloud holds the copy that matters — a device that last synced before the
 * deletion learns about it from there, not from here — so what is left on this device is
 * bookkeeping. Keeping it for ever would mean a row for every note ever deleted.
 *
 * The cloud's tombstones are deliberately *not* purged. A device that has not synced since
 * before a deletion still has to be told about it, and a row of text costs nothing.
 */
object SyncRetention {

    /** Thirty days. Long enough that nothing about this is a race. */
    val TOMBSTONE_RETENTION_MILLIS: Long = 30L * 24L * 60L * 60L * 1000L

    /**
     * The instant before which a tombstone may be deleted for good.
     *
     * Compared against the **server's** clock, because a tombstone's `deletedAt` is whatever
     * the device's clock said at the time. Thirty days is far wider than any disagreement
     * between two clocks; what would not be acceptable is a device's own clock deciding how
     * old its own rows are.
     */
    fun cutoff(serverNow: Long): Long = serverNow - TOMBSTONE_RETENTION_MILLIS
}
