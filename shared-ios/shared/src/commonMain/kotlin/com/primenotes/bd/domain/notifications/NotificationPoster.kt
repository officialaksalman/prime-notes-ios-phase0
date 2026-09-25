package com.primenotes.bd.domain.notifications

/**
 * What Prime Notes can say in the notification shade.
 *
 * An interface so that the two things that talk to it — the observer on sync, and the switch in
 * Settings — can be tested without a device, and so that there is one place that decides what a
 * notification says. The platform's own answer to "may I be heard?" is deliberately *not* here: that
 * is a permission and a user's device-level choice, and it is checked where it belongs, at the point
 * something would actually be posted.
 *
 * **Every method is fire-and-forget.** None of them can fail in a way the app must handle: a
 * notification that the system drops is a notification nobody was told about, which is exactly what
 * happens when the permission is refused — and nothing about the notes depends on one arriving.
 */
interface NotificationPoster {

    /**
     * Creates the channel notifications go into, if it is not already there.
     *
     * Called when the setting is turned on rather than at launch, so a category the app has decided
     * nothing about does not appear in the device's list before anybody has asked for it.
     */
    fun ensureChannel()

    /** A pass finished with everything uploaded, and something had been waiting to go. */
    fun syncSynced()

    /** A pass did not finish. */
    fun syncFailed()

    /** A pass finished with [outstanding] rows still to go up. */
    fun syncWaiting(outstanding: Int)

    /** The one notification somebody can ask for outright, to check the switch is not lying. */
    fun test()
}
