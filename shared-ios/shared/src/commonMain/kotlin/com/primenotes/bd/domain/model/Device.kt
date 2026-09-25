package com.primenotes.bd.domain.model

/**
 * One device the account has registered.
 *
 * [rowId] is the database row's identity; [deviceId] is the device's own, generated once and kept on
 * the device. The two are not the same thing, and neither can stand in for the other: the row is
 * replaced if the registry is ever rebuilt, while the device id is what makes *this* device
 * recognisable — which is the whole reason a row can be shown as "This device".
 *
 * [revokedAt] is set when the account has said this device should no longer be used. It is a
 * **record**, not a switch in the auth service: revoking a row cannot reach into Supabase Auth and
 * cancel a token that has already been issued. What it does is authoritative for this app — the
 * revoked device reads its own row, sees the mark, and signs itself out — and it is described that
 * way on the screen rather than claimed to be something it is not.
 */
data class Device(
    val rowId: String,
    val deviceId: String,
    val name: String,
    val platform: String,
    val appVersion: String?,
    val createdAt: Long,
    val lastSeenAt: Long,
    val lastSyncAt: Long?,
    val revokedAt: Long?
) {

    /** Whether the account has told this device to stop being signed in. */
    val isRevoked: Boolean get() = revokedAt != null
}
