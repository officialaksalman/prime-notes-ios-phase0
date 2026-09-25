package com.primenotes.bd.domain.device

import com.primenotes.bd.domain.model.Device

/**
 * What a device says about itself when it registers.
 *
 * [deviceId] is generated once on the device and kept; everything else is read from the platform.
 * None of it is a secret, and none of it identifies a person — which is why it may be written to the
 * account's own rows and nowhere else.
 */
data class DeviceRegistration(
    val deviceId: String,
    val name: String,
    val platform: String,
    val appVersion: String?
)

/**
 * What a screen shows about the account's devices.
 *
 * The same four-way shape as [com.primenotes.bd.domain.account.ProfileResult], and for the same
 * reason: "no cloud", "signed out", "the server did not answer" and "here they are" are four
 * different things, and only one of them is an error.
 */
sealed interface DeviceListResult {

    data class Found(val devices: List<Device>) : DeviceListResult

    data object SignedOut : DeviceListResult

    data object NotConfigured : DeviceListResult

    data class Failed(val reason: String) : DeviceListResult
}

/** What a write to the registry did. */
sealed interface DeviceWriteResult {

    data object Done : DeviceWriteResult

    data object SignedOut : DeviceWriteResult

    data object NotConfigured : DeviceWriteResult

    data class Failed(val reason: String) : DeviceWriteResult
}

/**
 * The account's devices, as the rest of the app sees them.
 *
 * Every operation is scoped to the signed-in account by the **database**, not by this side: the
 * table's policies match `auth.uid()` against the row's owner, and the owner is filled in by the
 * database's own default rather than sent by the client. A client cannot name another account's
 * rows here even if it tries, which is why nothing in this interface takes a user id.
 *
 * **What revoking can and cannot do.** Marking a row revoked is authoritative for this app — the
 * device it names reads its own row on its next launch and signs itself out — but it does not, and
 * cannot, cancel a token the auth service has already issued. Both halves are said on the screen,
 * because a person deciding whether to revoke a device they have lost needs to know which of the two
 * they are getting: the device stops being able to use *this app*, and its session remains valid
 * until the auth service's own token expiry sees to it.
 */
interface DeviceRepository {

    /**
     * Registers this device, or refreshes the row it already has.
     *
     * [at] is when the device was seen, and [syncedAt] — when non-null — is when it last completed a
     * pass. Writing one row per account per device is what keeps this cheap enough to do on every
     * launch, and the caller is what varies *when* it happens; see [DevicePresence].
     *
     * [claimAgain] is how a device takes back a row the account had marked revoked. It is set for
     * exactly one situation — somebody signing in **again** on this device after it had been revoked
     * — because a mark that could be cleared by any refresh would mean nothing, and a mark that
     * could never be cleared would leave a revoked device permanently unable to sign in.
     */
    suspend fun register(
        registration: DeviceRegistration,
        at: Long,
        syncedAt: Long? = null,
        claimAgain: Boolean = false
    ): DeviceWriteResult

    /** Every device this account has registered, most recently seen first. */
    suspend fun devices(): DeviceListResult

    /**
     * Marks one of the account's devices as revoked.
     *
     * Takes the **row** id rather than the device id: a client only knows its own device id, and
     * revoking by the id a screen is holding is what keeps a mistake from naming the wrong row. The
     * database still refuses any row that is not the caller's.
     */
    suspend fun revoke(rowId: String, at: Long): DeviceWriteResult

    /**
     * Takes one of the account's devices off the registry, for good.
     *
     * Not the same thing as [revoke], and deliberately a separate call rather than a flag on it. A
     * revocation is a mark that is *read*: the device it names signs itself out of this app the next
     * time it opens, and the row stays as the account's record of what it has seen. This is the row
     * itself going — how a device that is already signed out is taken off the list rather than left
     * there for ever.
     *
     * Takes the **row** id for the same reason [revoke] does, and the database refuses any row that
     * is not the caller's: the table's own delete policy matches `auth.uid()` against the row's
     * owner, so this can only ever remove a device from the account asking for it.
     */
    suspend fun remove(rowId: String): DeviceWriteResult

    /**
     * When this device's own row was revoked, or null if it has not been.
     *
     * Null covers both "not revoked" and "could not be read", and the caller must treat them the
     * same way — a device that signs itself out because a request failed would be the worst possible
     * reading of an offline launch.
     */
    suspend fun thisDeviceRevokedAt(deviceId: String): Long?
}
