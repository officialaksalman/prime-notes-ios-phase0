package com.primenotes.bd.data.cloud

import com.primenotes.bd.domain.device.DeviceListResult
import com.primenotes.bd.domain.device.DeviceRegistration
import com.primenotes.bd.domain.device.DeviceRepository
import com.primenotes.bd.domain.device.DeviceWriteResult
import com.primenotes.bd.domain.model.Device
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One row of `public.user_devices`.
 *
 * `user_id` is deliberately **not** a field here. The column is `not null default auth.uid()`, so the
 * database fills it in from the authenticated session; sending it from the client would mean the app
 * naming the owner of a row, which is precisely the thing a client must never be trusted to do. There
 * is no payload in this file that could carry someone else's account id even if a caller wanted it to.
 */
@Serializable
internal data class DeviceRecord(
    val id: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_name") val deviceName: String,
    val platform: String,
    @SerialName("app_version") val appVersion: String? = null,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("last_seen_at") val lastSeenAt: Long,
    @SerialName("last_sync_at") val lastSyncAt: Long? = null,
    @SerialName("revoked_at") val revokedAt: Long? = null
) {
    fun toDomain(): Device = Device(
        rowId = id,
        deviceId = deviceId,
        name = deviceName,
        platform = platform,
        appVersion = appVersion,
        createdAt = createdAt,
        lastSeenAt = lastSeenAt,
        lastSyncAt = lastSyncAt,
        revokedAt = revokedAt
    )
}

/**
 * The row a new device writes.
 *
 * `created_at` and `user_id` are the database's to fill in: sending either would be the client
 * assigning an owner or a birth time to a row, and neither is its to decide.
 */
@Serializable
internal data class NewDeviceRecord(
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_name") val deviceName: String,
    val platform: String,
    @SerialName("app_version") val appVersion: String? = null,
    @SerialName("last_seen_at") val lastSeenAt: Long,
    @SerialName("last_sync_at") val lastSyncAt: Long? = null
)

/**
 * What a device refreshes about itself.
 *
 * `revoked_at` is absent on purpose, and so is `device_id`: a device coming back to say hello must
 * not be able to clear the account's own mark on it, and must not be able to claim to be a different
 * device. Both are the account's decision, and neither is in this payload.
 */
@Serializable
internal data class DeviceSeenRecord(
    @SerialName("device_name") val deviceName: String,
    val platform: String,
    @SerialName("app_version") val appVersion: String? = null,
    @SerialName("last_seen_at") val lastSeenAt: Long,
    @SerialName("last_sync_at") val lastSyncAt: Long? = null
)

/** The mark a revocation writes. Its own payload, so nothing else about the row is in reach. */
@Serializable
internal data class RevocationRecord(@SerialName("revoked_at") val revokedAt: Long)

/**
 * The mark, cleared.
 *
 * Sent only when a device takes its own row back by signing in again. Null is *written out* rather
 * than left out of the payload — this client encodes defaults and explicit nulls — which is what
 * makes this a statement that the column is empty rather than a payload that says nothing about it.
 */
@Serializable
internal data class RevocationCleared(@SerialName("revoked_at") val revokedAt: Long? = null)

/** The id of a device row, for the read that decides between insert and update. */
@Serializable
internal data class DeviceIdRow(
    val id: String,
    @SerialName("device_id") val deviceId: String
)

/** Just a row's own id, for the read that settles whether a removal actually happened. */
@Serializable
internal data class DeviceRowId(val id: String)

/** Just the mark, for the read a device makes about itself. */
@Serializable
internal data class DeviceRevocationRow(
    @SerialName("revoked_at") val revokedAt: Long? = null
)

/**
 * The device registry, on the real project.
 *
 * Read-then-write rather than an upsert, and deliberately: the account's mark on a device — its
 * revocation — must survive that device coming back and refreshing itself, and an upsert would let a
 * row's whole payload be replaced by whatever the client sent. Two statements say exactly what
 * changes, and nothing else about the row can be reached.
 *
 * Ownership is the database's to enforce and not this side's: every statement runs under the table's
 * policies, which match `auth.uid()` against the row's owner, and the owner is never sent from here.
 * A read therefore returns this account's rows and no others, and a write can only ever touch one of
 * them — there is no user id in this class to get wrong.
 */
class SupabaseDeviceRepository(private val client: SupabaseClient) : DeviceRepository {

    override suspend fun register(
        registration: DeviceRegistration,
        at: Long,
        syncedAt: Long?,
        claimAgain: Boolean
    ): DeviceWriteResult = write {
        if (client.auth.currentUserOrNull() == null) return@write DeviceWriteResult.SignedOut

        val existing = client.postgrest
            .from(DEVICES)
            .select(Columns.list("id", "device_id")) {
                filter { eq("device_id", registration.deviceId) }
                limit(1)
            }
            .decodeList<DeviceIdRow>()
            .firstOrNull()

        if (existing == null) {
            client.postgrest.from(DEVICES).insert(
                NewDeviceRecord(
                    deviceId = registration.deviceId,
                    deviceName = registration.name,
                    platform = registration.platform,
                    appVersion = registration.appVersion,
                    lastSeenAt = at,
                    lastSyncAt = syncedAt
                )
            )
        } else {
            // Taking the row back comes first, so that the refresh below cannot be the thing that
            // left the mark standing. Only a device signing in again asks for this.
            if (claimAgain) {
                client.postgrest.from(DEVICES).update(RevocationCleared()) {
                    filter { eq("id", existing.id) }
                }
            }

            client.postgrest.from(DEVICES).update(
                DeviceSeenRecord(
                    deviceName = registration.name,
                    platform = registration.platform,
                    appVersion = registration.appVersion,
                    lastSeenAt = at,
                    lastSyncAt = syncedAt
                )
            ) {
                filter { eq("id", existing.id) }
            }
        }

        DeviceWriteResult.Done
    }

    override suspend fun devices(): DeviceListResult = list {
        if (client.auth.currentUserOrNull() == null) return@list DeviceListResult.SignedOut

        val rows = client.postgrest
            .from(DEVICES)
            .select(Columns.ALL) {
                order("last_seen_at", Order.DESCENDING)
                order("device_id", Order.ASCENDING)
            }
            .decodeList<DeviceRecord>()

        DeviceListResult.Found(rows.map { row -> row.toDomain() })
    }

    override suspend fun revoke(rowId: String, at: Long): DeviceWriteResult = write {
        if (client.auth.currentUserOrNull() == null) return@write DeviceWriteResult.SignedOut

        client.postgrest.from(DEVICES).update(RevocationRecord(revokedAt = at)) {
            filter { eq("id", rowId) }
        }

        DeviceWriteResult.Done
    }

    /**
     * Deletes the row itself, rather than marking it.
     *
     * The row is named by its id and nothing else, and the table's delete policy is what scopes the
     * statement to this account: a row belonging to somebody else simply is not there as far as this
     * delete is concerned. The user id is still never sent, so there is nothing here that could be
     * made to name another account's device.
     *
     * **The deletion is then looked for, because the delete alone cannot be trusted to say.** A delete
     * that row-level security filters down to nothing answers with exactly the same success as one
     * that removed a row, so "the request was accepted" and "the row is gone" are two different facts
     * — and the screen this serves tells somebody their device has been taken off the list. One read
     * afterwards settles it: the row is gone, or it is still there and this says so rather than
     * reporting a removal that did not happen. A removal the server *refuses* (a missing grant, an
     * expired session) raises and is reported by [write] like any other failed call.
     *
     * A read that cannot be made — the network going between the two calls — is reported as a failure
     * too, and the screen puts the row back. That is the cautious way round on purpose: claiming a
     * device is gone when that is not known would be the one answer that cannot be taken back, and the
     * next refresh settles it either way.
     */
    override suspend fun remove(rowId: String): DeviceWriteResult = write {
        if (client.auth.currentUserOrNull() == null) return@write DeviceWriteResult.SignedOut

        client.postgrest.from(DEVICES).delete {
            filter { eq("id", rowId) }
        }

        val remaining = client.postgrest
            .from(DEVICES)
            .select(Columns.list("id")) {
                filter { eq("id", rowId) }
                limit(1)
            }
            .decodeList<DeviceRowId>()

        if (remaining.isEmpty()) {
            DeviceWriteResult.Done
        } else {
            DeviceWriteResult.Failed(REMOVAL_DID_NOT_LAND)
        }
    }

    /**
     * Whether the account has marked this device as revoked.
     *
     * Null covers both "not revoked" and "could not be read", and the caller must read them the same
     * way: a device that signed itself out because a request failed would be the worst possible
     * consequence of an offline launch. A cancellation is still rethrown, because the caller leaving
     * is not the server answering.
     */
    override suspend fun thisDeviceRevokedAt(deviceId: String): Long? =
        try {
            client.postgrest
                .from(DEVICES)
                .select(Columns.list("revoked_at")) {
                    filter { eq("device_id", deviceId) }
                    limit(1)
                }
                .decodeList<DeviceRevocationRow>()
                .firstOrNull()
                ?.revokedAt
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }

    /** A call that answers with a write's outcome, with every failure turned into one shape. */
    private suspend fun write(block: suspend () -> DeviceWriteResult): DeviceWriteResult =
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            DeviceWriteResult.Failed(error.toCloudReason())
        }

    /** A call that answers with a list, with every failure turned into one shape. */
    private suspend fun list(block: suspend () -> DeviceListResult): DeviceListResult =
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            DeviceListResult.Failed(error.toCloudReason())
        }

    private companion object {
        const val DEVICES = "user_devices"

        /**
         * What is said when the row is still there afterwards.
         *
         * Written out rather than derived from an exception, because nothing threw: the request was
         * accepted and changed nothing, which is the one failure the HTTP status cannot express. The
         * screen shows this sentence, so it says what is true rather than what a network error would
         * have said.
         */
        const val REMOVAL_DID_NOT_LAND = "the device was not removed"
    }
}
