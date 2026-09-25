package com.primenotes.bd.domain.sync

import kotlinx.coroutines.flow.Flow

/**
 * Sync, as the rest of the app sees it.
 *
 * Signing in, editing notes and reading them all work without ever calling this. It
 * exists so that what has been written here also reaches the account's other
 * devices — and so the app can say honestly how far that has got.
 */
interface SyncEngine {

    /**
     * What the Sync card draws, live.
     *
     * Emits on any of its parts changing: the session, a row being written, a pass
     * starting or finishing.
     */
    val state: Flow<SyncState>

    /**
     * Runs one pass: bring down what is new, send up what is waiting.
     *
     * Safe to call from anywhere at any time. Concurrent callers do not interleave —
     * the second one is told [SyncResult.AlreadyRunning] rather than reading and
     * writing the same rows as the first.
     */
    suspend fun syncNow(): SyncResult
}
