package com.primenotes.bd.domain.sync

/** What a pull does with the cloud's copy of one row. */
enum class MergeDecision {

    /** Write the cloud's row over this device's copy, and record it as uploaded. */
    ApplyRemote,

    /** Leave this device's copy exactly as it is. */
    KeepLocal
}

/**
 * Whether the cloud's copy of a row replaces this device's.
 *
 * The rule, in full:
 *
 *  * this device has never seen the row — take it;
 *  * this device has changes to the row that have not been uploaded — **keep ours**.
 *    It is uploaded instead of being overwritten, so an edit made here is never lost
 *    to a pull. If another device edited the same row, this device's version wins and
 *    becomes the cloud's copy; merging the two is Phase 10, and this is the one place
 *    that decision is made;
 *  * otherwise the newer copy wins, and a tie goes to this device, so a pull never
 *    rewrites a row for no reason.
 *
 * A tombstone is an ordinary row here. It carries a `deletedAt` and a newer
 * timestamp, which is how deleting on one device reaches another — and a local edit
 * that has not been uploaded yet still outranks it.
 *
 * A note thrown away for good is not a case for this function at all: it is deleted rather than
 * sent, so the cloud never returns one to be decided about. See `SyncGateway.deleteNotes`.
 */
fun decideSync(local: LocalCopy?, remote: SyncHead): MergeDecision = when {
    local == null -> MergeDecision.ApplyRemote
    !local.isSynced -> MergeDecision.KeepLocal
    remote.isNewerThan(local.head) -> MergeDecision.ApplyRemote
    else -> MergeDecision.KeepLocal
}

/**
 * Whether a pull is about to discard something the cloud has.
 *
 * This is [`KeepLocal`][MergeDecision.KeepLocal] for a reason worth naming: this device has
 * changes that have not been uploaded, *and* another device has moved on since. Keeping ours
 * is what stops an edit made here from being lost — but unless something is done about it,
 * the other version goes with it. Telling those two apart is what lets the engine keep it.
 *
 * A settled local row never conflicts: the cloud's newer copy simply replaces it.
 */
fun conflictsWithLocal(local: LocalCopy?, remote: SyncHead): Boolean =
    local != null && !local.isSynced && remote.isNewerThan(local.head)
