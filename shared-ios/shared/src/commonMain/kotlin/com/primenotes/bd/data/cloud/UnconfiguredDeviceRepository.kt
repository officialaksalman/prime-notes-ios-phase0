package com.primenotes.bd.data.cloud

import com.primenotes.bd.domain.device.DeviceListResult
import com.primenotes.bd.domain.device.DeviceRegistration
import com.primenotes.bd.domain.device.DeviceRepository
import com.primenotes.bd.domain.device.DeviceWriteResult

/**
 * The device registry when there is no cloud to keep one on.
 *
 * A build with no project configured has no account and no devices to register, so every call says
 * exactly that. Nothing is invented, and nothing is written anywhere.
 */
object UnconfiguredDeviceRepository : DeviceRepository {

    override suspend fun register(
        registration: DeviceRegistration,
        at: Long,
        syncedAt: Long?,
        claimAgain: Boolean
    ): DeviceWriteResult = DeviceWriteResult.NotConfigured

    override suspend fun devices(): DeviceListResult = DeviceListResult.NotConfigured

    override suspend fun revoke(rowId: String, at: Long): DeviceWriteResult =
        DeviceWriteResult.NotConfigured

    override suspend fun remove(rowId: String): DeviceWriteResult =
        DeviceWriteResult.NotConfigured

    override suspend fun thisDeviceRevokedAt(deviceId: String): Long? = null
}
