package com.primenotes.bd.domain.device

import com.primenotes.bd.domain.TimeSource
import com.primenotes.bd.domain.account.AccountRepository
import com.primenotes.bd.domain.sync.SyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps this device in the account's registry, and acts on the one thing the account can say about it.
 *
 * Two jobs, and they are two because they are triggered by two different things:
 *
 *  * **the session** — a signed-in account means this device has a row, and a row the account has
 *    marked revoked means this device must stop;
 *  * **a finished pass** — a pass that completed is when `last_sync_at` is worth writing, which is
 *    the only reason a device's row changes without somebody signing in.
 *
 * **Throttling.** A row is written at most once per [throttleMillis] while a session just sits there,
 * plus once when a pass completes. Passes are at least fifteen minutes apart — that is the platform's
 * shortest periodic interval — so the ceiling is a handful of writes an hour, which is the difference
 * between a registry that tracks devices and one that is a write amplifier.
 *
 * **A failed write records nothing**, so the next trigger tries again: a registry that believed itself
 * up to date because a request never arrived would be worse than one that writes twice.
 *
 * **A revoked device signs itself out**, and that is the whole of what revocation can do. It cannot
 * cancel a token the auth service has already issued — no client-side record can — so the app says
 * exactly that on the screen rather than claiming a remote sign-out it cannot perform.
 *
 * Nothing here runs for a device with no account: a local-first user has no registry to be in.
 *
 * This file is common code, so its clock is a [TimeSource] rather than a `java.time.Clock`. The
 * behaviour is unchanged — both answer in epoch milliseconds — and the composition root still reads
 * one clock for the whole app.
 */
class DevicePresence(
    private val account: AccountRepository,
    private val syncEngine: SyncEngine,
    private val devices: DeviceRepository,
    private val store: DeviceStore,
    private val clock: TimeSource,
    private val describe: suspend (deviceId: String) -> DeviceRegistration,
    private val throttleMillis: Long = REGISTRATION_THROTTLE_MILLIS
) {

    /**
     * One registration at a time.
     *
     * The two watchers below are separate coroutines and both may want to write a row, and the
     * read-then-write inside [register] is not atomic — two of them interleaved could create two rows
     * for one device. The lock is what makes "at most one row" true rather than likely.
     */
    private val guard = Mutex()

    /** Starts both watchers. They live as long as the process does. */
    fun start(scope: CoroutineScope) {
        scope.launch { watchSessions() }
        scope.launch { watchPasses() }
    }

    /**
     * Follows the session: registers this device, and signs it out when the account says so.
     *
     * The revocation check happens once per account per process, and **before** the registration that
     * would otherwise follow it — which is what stops a device refreshing the row it is about to be
     * signed out of, and stops the check from finding the mark cleared by its own write.
     *
     * Signing in *again* on a device that was revoked claims the row back, and is the only thing that
     * does. Without it a revoked device could never be used again; with it on every refresh, the mark
     * would mean nothing.
     */
    private suspend fun watchSessions() {
        var signedOutForRevocation = false
        var checkedAccountId: String? = null

        account.account.collect { signedIn ->
            if (signedIn == null) return@collect

            if (signedOutForRevocation) {
                signedOutForRevocation = false
                checkedAccountId = signedIn.id
                ensureRegistered(claimAgain = true)
                return@collect
            }

            if (checkedAccountId != signedIn.id) {
                checkedAccountId = signedIn.id

                if (isRevoked()) {
                    signedOutForRevocation = true
                    account.signOut()
                    return@collect
                }
            }

            ensureRegistered()
        }
    }

    /**
     * Follows the sync engine, and records when a pass last finished.
     *
     * A pass that failed writes nothing: `last_sync_at` means "this device last got through", and a
     * failure is the absence of that, not a different reading of it.
     */
    private suspend fun watchPasses() {
        var wasRunning = false

        syncEngine.state.collect { state ->
            val running = state.running
            val finished = wasRunning && !running && state.signedIn && state.lastError == null
            wasRunning = running

            if (finished) markSynced()
        }
    }

    /** Whether the account has marked this device as revoked. Unreadable answers "no". */
    private suspend fun isRevoked(): Boolean =
        devices.thisDeviceRevokedAt(store.deviceId()) != null

    /** Writes this device's row, if the throttle has elapsed or the caller says to. */
    private suspend fun ensureRegistered(claimAgain: Boolean = false) {
        guard.withLock {
            if (account.account.first() == null) return

            val now = clock.nowMillis()
            val last = store.lastRegisteredAt()
            val due = claimAgain || last == null || now - last >= throttleMillis
            if (!due) return

            val registration = describe(store.deviceId())

            if (devices.register(registration, at = now, claimAgain = claimAgain) ==
                DeviceWriteResult.Done
            ) {
                store.recordRegistered(now)
            }
        }
    }

    /**
     * Writes this device's row with the moment a pass finished.
     *
     * Deliberately **not** throttled: it is the one write that carries new information, and it can
     * only happen as often as a pass completes — which the platform already bounds at fifteen
     * minutes. What it is not is a write per *change*: the engine emits on every row it touches.
     */
    private suspend fun markSynced() {
        guard.withLock {
            if (account.account.first() == null) return

            val now = clock.nowMillis()
            val registration = describe(store.deviceId())

            if (devices.register(registration, at = now, syncedAt = now) == DeviceWriteResult.Done) {
                store.recordRegistered(now)
            }
        }
    }

    companion object {
        /** How long a device may go without refreshing its row while a session just sits there. */
        const val REGISTRATION_THROTTLE_MILLIS: Long = 15L * 60L * 1000L
    }
}
