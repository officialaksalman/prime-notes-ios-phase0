package com.primenotes.bd.data

/**
 * Runs several writes as one atomic unit.
 *
 * Repositories need this for operations that span more than one table — deleting a
 * tag must tombstone the tag *and* drop its links, and deleting a folder must
 * tombstone the folder *and* move its notes out. A partial application of either
 * would leave the database inconsistent.
 *
 * Abstracted so repositories can be driven with a real or no-op implementation.
 */
interface TransactionRunner {
    suspend fun <T> inTransaction(block: suspend () -> T): T
}
