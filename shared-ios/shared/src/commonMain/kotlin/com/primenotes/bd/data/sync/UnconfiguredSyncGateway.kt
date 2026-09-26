package com.primenotes.bd.data.sync

import com.primenotes.bd.domain.model.Folder
import com.primenotes.bd.domain.model.Note
import com.primenotes.bd.domain.model.Tag
import com.primenotes.bd.domain.sync.GatewayResult
import com.primenotes.bd.domain.sync.RemoteLink
import com.primenotes.bd.domain.sync.RemoteRow
import com.primenotes.bd.domain.sync.SyncEntity
import com.primenotes.bd.domain.sync.SyncGateway

/**
 * The cloud, when this build has none configured.
 *
 * A build with no project has no client at all, so there is nothing to reach. Every
 * call says so rather than failing obscurely, and the engine reports the same thing
 * before it ever gets here â€” this exists so that nothing in the app can accidentally
 * start talking to somewhere that was never set up.
 */
object UnconfiguredSyncGateway : SyncGateway {

    private const val NO_CLOUD = "no cloud project is configured"

    override suspend fun serverTime(): GatewayResult<Long> =
        GatewayResult.Failed(NO_CLOUD, refused = false)

    override suspend fun pull(
        entity: SyncEntity,
        since: Long,
        offset: Int,
        limit: Int
    ): GatewayResult<List<RemoteRow>> = GatewayResult.Failed(NO_CLOUD, refused = false)

    override suspend fun pullLinks(noteIds: Collection<String>): GatewayResult<List<RemoteLink>> =
        GatewayResult.Failed(NO_CLOUD, refused = false)

    override suspend fun pushFolders(rows: List<Folder>): GatewayResult<Unit> =
        GatewayResult.Failed(NO_CLOUD, refused = false)

    override suspend fun pushTags(rows: List<Tag>): GatewayResult<Unit> =
        GatewayResult.Failed(NO_CLOUD, refused = false)

    override suspend fun pushNotes(rows: List<Note>): GatewayResult<Unit> =
        GatewayResult.Failed(NO_CLOUD, refused = false)

    override suspend fun deleteNotes(ids: List<String>): GatewayResult<Unit> =
        GatewayResult.Failed(NO_CLOUD, refused = false)

    override suspend fun replaceLinks(
        linksByNote: Map<String, Collection<String>>
    ): GatewayResult<Unit> = GatewayResult.Failed(NO_CLOUD, refused = false)
}
