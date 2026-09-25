package com.primenotes.bd.domain.device

import com.primenotes.bd.domain.model.Device
import kotlinx.coroutines.flow.Flow

/**
 * What this device knows about itself, kept on this device.
 *
 * Two unrelated-looking jobs in one place, because they are the same file and the same decision: the
 * app already writes a small preferences file per concern ([com.primenotes.bd.domain.settings.SettingsStore]
 * for the appearance, the recent searches for themselves), and both of these are about *this device*
 * rather than about the account.
 *
 *  1. **Identity.** The id this device registers under. Generated once, on first use, and never
 *     regenerated — regenerating it would make a device look like a new one on every launch, and the
 *     account would collect a row per day.
 *  2. **The last list the server gave.** Kept so the devices screen can show something true offline
 *     rather than an empty page, and labelled as what it is.
 *
 * It is deliberately **not** included in Android's backups, for the same reason the session is not:
 * a device identity that travelled to another phone would make two devices look like one.
 */
interface DeviceStore {

    /** This device's id, generating and persisting one on first call. */
    suspend fun deviceId(): String

    /** When this device last wrote its row, for throttling. Null if it never has. */
    suspend fun lastRegisteredAt(): Long?

    /** Records that this device's row was written at [at]. */
    suspend fun recordRegistered(at: Long)

    /** The last device list the server returned. Empty until one has been read. */
    fun cachedDevices(): Flow<List<Device>>

    /** Remembers a list the server returned, so it can be shown offline. */
    suspend fun rememberDevices(devices: List<Device>)
}
