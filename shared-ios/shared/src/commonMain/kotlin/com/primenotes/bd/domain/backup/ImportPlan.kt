package com.primenotes.bd.domain.backup

import com.primenotes.bd.domain.sync.ConflictCopy

/**
 * What an import would do, worked out in full before anything is written.
 *
 * Nothing in it overwrites: [folders], [tags], [notes] and [copies] are all rows to *insert*,
 * with ids that are free or freshly made, and every reference has already been rewritten to
 * point at a row that is about to exist.
 */
data class ImportPlan(
    val folders: List<ArchivedFolder>,
    val tags: List<ArchivedTag>,
    val notes: List<ArchivedNote>,

    /** Versions this device already has something different for, kept rather than replaced. */
    val copies: List<ArchivedNote>,

    val links: List<ArchivedLink>,

    /** Notes the file holds that this device already has, word for word. */
    val unchanged: Int
) {

    val added: Int get() = folders.size + tags.size + notes.size

    val kept: Int get() = copies.size

    /** True when the file holds nothing this device does not already have. */
    val isNoOp: Boolean get() = added == 0 && kept == 0
}

/**
 * What this device already holds, in the two forms the decision needs.
 *
 * [live] is what a person can see, which is what an import compares against. [takenIds] is
 * every id in use at all, **including the ones behind tombstones** — a row this device has
 * deleted still owns its id, and inserting over it would either fail or quietly undo the
 * deletion.
 */
data class ExistingRows(
    val live: ArchiveDocument,
    val takenIds: Set<String>
)

/**
 * What an import of [incoming] would do to [existing].
 *
 * The rule, in full:
 *
 *  * a note the file has and this device does not — inserted, and it will upload;
 *  * a note the file has that this device already has, saying the same thing — nothing at all,
 *    which is what makes importing the same file twice do nothing the second time;
 *  * a note the file has that this device has differently, or has deleted — the file's version
 *    is kept as a **copy** with a fresh id, because a copy has an id nothing else claims and so
 *    cannot replace anything.
 *
 * Folders and tags are matched **by name first, then by id**, and a note's references are
 * rewritten to whichever row it landed on. Matching by name is what makes a restore into a
 * different account sensible — the folders come back with their names rather than arriving as
 * orphans — and what keeps a note's folder pointing at a row that exists.
 *
 * Nothing here carries an owner: an imported note belongs to whoever imported it.
 */
fun planImport(
    incoming: ArchiveDocument,
    existing: ExistingRows,
    newId: () -> String
): ImportPlan {
    val folders = remap(
        incoming = incoming.folders,
        existing = existing.live.folders,
        taken = existing.takenIds,
        id = { folder -> folder.id },
        name = { folder -> folder.name },
        freshId = { folder -> folder.copy(id = newId()) }
    )

    val tags = remap(
        incoming = incoming.tags,
        existing = existing.live.tags,
        taken = existing.takenIds,
        id = { tag -> tag.id },
        name = { tag -> tag.name },
        freshId = { tag -> tag.copy(id = newId()) }
    )

    val existingNotes = existing.live.notes.associateBy { note -> note.id }
    val existingTags = existing.live.links.groupBy({ link -> link.noteId }, { link -> link.tagId })
    val incomingTags = incoming.links.groupBy({ link -> link.noteId }, { link -> link.tagId })

    val notes = mutableListOf<ArchivedNote>()
    val copies = mutableListOf<ArchivedNote>()
    val links = mutableListOf<ArchivedLink>()
    var unchanged = 0

    incoming.notes.forEach { note ->
        val placed = note.copy(folderId = note.folderId?.let { folderId -> folders.ids[folderId] })
        val tagIds = incomingTags[note.id].orEmpty().mapNotNull { tagId -> tags.ids[tagId] }
        val here = existingNotes[note.id]

        when {
            here != null && sameWriting(here, placed, existingTags[note.id].orEmpty(), tagIds) ->
                unchanged++

            here != null || note.id in existing.takenIds -> {
                val copy = placed.copy(
                    id = newId(),
                    title = ConflictCopy.titleFor(placed.title),
                    revision = 0
                )
                copies += copy
                links += tagIds.map { tagId -> ArchivedLink(noteId = copy.id, tagId = tagId) }
            }

            else -> {
                notes += placed
                links += tagIds.map { tagId -> ArchivedLink(noteId = placed.id, tagId = tagId) }
            }
        }
    }

    return ImportPlan(
        folders = folders.added,
        tags = tags.added,
        notes = notes,
        copies = copies,
        links = links,
        unchanged = unchanged
    )
}

/**
 * Whether two versions of a note say the same thing.
 *
 * `updatedAt` and `revision` are deliberately not compared: they move when a pass uploads a
 * row, and a note that reads exactly the same afterwards has not changed. Comparing them would
 * turn every re-import after a sync into a copy. The note's tags **are** compared, because a
 * difference in what a note carries is a difference, and quietly merging it would be a decision
 * nobody asked for.
 *
 * The formatting is compared for the same reason, and as the raw stored string: a note that was
 * retyped differently, or one whose words are the same but are no longer bold, has changed, and
 * treating it as unchanged would leave the file's version of it unimported.
 */
internal fun sameWriting(
    here: ArchivedNote,
    there: ArchivedNote,
    hereTags: List<String>,
    thereTags: List<String>
): Boolean =
    here.title == there.title &&
        here.content == there.content &&
        here.contentDocument == there.contentDocument &&
        here.folderId == there.folderId &&
        here.isPinned == there.isPinned &&
        here.isFavorite == there.isFavorite &&
        here.isLocked == there.isLocked &&
        here.createdAt == there.createdAt &&
        hereTags.sorted() == thereTags.sorted()

/** The rows to insert for one kind, and where each incoming id ended up. */
private class Remap<T>(val ids: Map<String, String>, val added: List<T>)

private fun <T> remap(
    incoming: List<T>,
    existing: List<T>,
    taken: Set<String>,
    id: (T) -> String,
    name: (T) -> String,
    freshId: (T) -> T
): Remap<T> {
    val byName = existing.associateBy { item -> name(item).lowercase() }
    val byId = existing.associateBy { item -> id(item) }

    val ids = mutableMapOf<String, String>()
    val added = mutableListOf<T>()

    incoming.forEach { item ->
        val match = byName[name(item).lowercase()] ?: byId[id(item)]

        when {
            match != null -> ids[id(item)] = id(match)

            // The id is free: the row comes in as it was, so a round trip keeps its identity.
            id(item) !in taken -> {
                ids[id(item)] = id(item)
                added += item
            }

            // A tombstone owns the id and the name is free. The name comes back under a new id
            // rather than over a deletion this account has not finished telling everyone about.
            else -> {
                val fresh = freshId(item)
                ids[id(item)] = id(fresh)
                added += fresh
            }
        }
    }

    return Remap(ids = ids, added = added)
}
