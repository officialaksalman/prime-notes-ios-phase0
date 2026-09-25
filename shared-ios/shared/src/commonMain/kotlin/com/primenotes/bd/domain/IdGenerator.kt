package com.primenotes.bd.domain

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Supplies identities for new rows.
 *
 * Prime Notes generates its own primary keys so a note can be created with no
 * internet and still be merged across devices without collisions. Injected so
 * tests can produce deterministic ids.
 */
fun interface IdGenerator {
    fun newId(): String
}

object UuidGenerator : IdGenerator {

    /**
     * The standard library's UUID rather than `java.util.UUID`, because this file is common code
     * now and `java.*` does not exist for every target.
     *
     * It produces the same thing: a random version-4 UUID, lower-case and hyphenated, so ids
     * generated before and after the move are indistinguishable — which matters, because these
     * are primary keys that sync to the server and to other devices.
     */
    @OptIn(ExperimentalUuidApi::class)
    override fun newId(): String = Uuid.random().toString()
}
