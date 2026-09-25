package com.primenotes.bd.domain.model

/**
 * Whether a deleted folder or tag may be removed for good.
 *
 * A tombstone may be purged once its deletion has reached the cloud, because the cloud then holds
 * the deletion and no device can bring the row back. It may **also** be purged when no account
 * has ever claimed it: a row that was never uploaded cannot be sitting in anybody's cloud, so
 * there is no copy anywhere that could resurrect it.
 *
 * That second half is what makes the trash usable on a device with no account. Its deletions can
 * never reach a cloud that does not exist, so with only the first half the row could never be
 * removed — **Delete now** would be a button that is permanently dead and the trash would fill up
 * for ever. A control that refuses until a cloud nobody configured is told about it is a worse
 * lie than the one the guard exists to prevent.
 *
 * The case it still refuses is the one it was written for: a row an account *has* claimed, whose
 * deletion has not been uploaded yet. Removing that locally would push nothing, and the next pull
 * would bring the note back as a live note on every device — the opposite of what was asked for.
 *
 * Notes are **not** held to this rule; see [canPurgeNote], which can complete what this one has to
 * refuse.
 */
fun canPurge(userId: String?, syncStatus: SyncStatus, deletedAt: Long?): Boolean =
    deletedAt != null && (syncStatus == SyncStatus.SYNCED || userId == null)

/**
 * Whether a deleted note may be removed for good.
 *
 * Always, once it is in the trash — which is the difference between a note and a folder or a tag.
 * A folder or a tag whose deletion has not been uploaded yet can only be *left* in the trash,
 * because [canPurge] has nothing else it can do with it. A note has somewhere to go: it becomes a
 * purge marker ([Note.purgedAt]), which disappears from every screen at once and carries the
 * deletion to the cloud on the next pass.
 *
 * That is what makes **Delete permanently** work on a signed-in device with no network — the case
 * that used to be refused, leaving a button on screen that did nothing.
 */
fun canPurgeNote(deletedAt: Long?): Boolean = deletedAt != null

/**
 * How long a deleted note is kept, on the device and in the cloud.
 *
 * Thirty days, counted from the moment the note was deleted. The same number is written down on
 * both sides of the wire, because each has to be able to enforce it alone: the device when it is
 * next opened, and the database on its own schedule, without the app being involved at all.
 *
 * It is a *duration*, not a date, so it can be applied to a timestamp that came from another
 * device's clock without either side having to agree about the time.
 */
object TrashPolicy {

    /** Thirty days. Long enough that nothing about this is a race. */
    val RETENTION_MILLIS: Long = 30L * 24L * 60L * 60L * 1000L

    /**
     * The instant before which a deletion has expired.
     *
     * Compared against whatever clock is enforcing it — the device's when the app is opened, the
     * server's in the database — and deliberately not against the other's. A note deleted at a
     * given instant is thirty days old by either clock to within the disagreement between them,
     * and thirty days is far wider than any disagreement between two clocks.
     */
    fun expiryCutoff(now: Long): Long = now - RETENTION_MILLIS

    /** When a note deleted at [deletedAt] stops being recoverable. */
    fun expiresAt(deletedAt: Long): Long = deletedAt + RETENTION_MILLIS

    /**
     * How many whole days are left before [deletedAt] expires, or null once it has.
     *
     * Whole days, rounded up, so a note with an hour left says "1 day" rather than "0 days" — the
     * number is meant to answer "how long have I got", and zero would read as "gone".
     */
    fun daysLeft(deletedAt: Long, now: Long): Long? {
        val remaining = expiresAt(deletedAt) - now
        if (remaining <= 0) return null

        val day = 24L * 60L * 60L * 1000L
        return (remaining + day - 1) / day
    }
}
