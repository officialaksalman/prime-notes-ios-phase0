package com.primenotes.bd.domain.backup

import com.primenotes.bd.domain.rich.RichParagraph
import com.primenotes.bd.domain.rich.RichTextDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What an import would do, decided before anything is written.
 *
 * This is the rule that makes an import safe to offer at all, so every case is pinned down
 * here rather than discovered by running it against a real collection.
 */
class ImportPlanTest {

    @Test
    fun `a note this device has never seen is added with its folder and tags`() {
        val plan = plan(incoming = file())

        assertEquals(listOf("n2"), plan.notes.map { note -> note.id })
        assertEquals(
            listOf("t1"),
            plan.links.map { link -> link.tagId },
            "the tag comes in with it, matched to the one already here"
        )
        assertEquals(1, plan.added)
        assertFalse(plan.isNoOp)
    }

    @Test
    fun `a note this device already has word for word is left alone`() {
        val plan = plan(
            incoming = file(
                notes = listOf(note("n1", title = "Groceries")),
                links = listOf(ArchivedLink("n1", "t1"))
            ),
            existing = here()
        )

        assertTrue(plan.notes.isEmpty(), "nothing is inserted")
        assertTrue(plan.copies.isEmpty(), "nothing is kept")
        assertEquals(1, plan.unchanged)
    }

    @Test
    fun `a note this device has differently is kept as a copy`() {
        val plan = plan(
            incoming = file(notes = listOf(note("n1", title = "Groceries", content = "From the file"))),
            existing = here()
        )

        val copy = plan.copies.single()
        assertEquals("Groceries (conflict)", copy.title)
        assertEquals("From the file", copy.content)
        assertTrue(copy.id != "n1", "with an id of its own")
        assertEquals(0L, copy.revision, "and no history of its own")
    }

    @Test
    fun `the plan never holds a row that would replace one this device has`() {
        val plan = plan(
            incoming = file(notes = listOf(note("n1", title = "Different"))),
            existing = here()
        )

        val idsBeingWritten = (plan.notes + plan.copies).map { note -> note.id }
        assertFalse("n1" in idsBeingWritten, "n1 is taken and must not be written over")
    }

    @Test
    fun `a note this device has deleted comes back as a copy`() {
        val plan = plan(
            incoming = file(notes = listOf(note("n1"))),
            existing = ExistingRows(live = emptyDocument(), takenIds = setOf("n1"))
        )

        val copy = plan.copies.single()
        assertFalse(copy.id == "n1", "not over the tombstone's id")
        assertEquals(1, plan.kept)
        assertTrue(plan.notes.isEmpty(), "the tombstone keeps its id")
    }

    @Test
    fun `a folder is matched by name before id so its notes follow it`() {
        val plan = plan(
            incoming = file(
                folders = listOf(ArchivedFolder("incoming-folder", "work", 1L, 2L)),
                notes = listOf(note("n2", folderId = "incoming-folder"))
            )
        )

        assertTrue(plan.folders.isEmpty(), "no second folder with that name")
        assertEquals(
            "f1",
            plan.notes.single().folderId,
            "and the note points at the folder that is already here"
        )
    }

    @Test
    fun `a folder whose id is taken and whose name is free comes back under a new id`() {
        val plan = plan(
            incoming = file(folders = listOf(ArchivedFolder("f1", "Personal", 1L, 2L))),
            existing = ExistingRows(live = emptyDocument(), takenIds = setOf("f1"))
        )

        val added = plan.folders.single()
        assertEquals("Personal", added.name)
        assertFalse(added.id == "f1", "not over the tombstone's id")
    }

    @Test
    fun `importing the same file twice does nothing the second time`() {
        val document = file()

        val second = planImport(
            incoming = document,
            existing = ExistingRows(
                live = document,
                takenIds = (document.folders.map { it.id } + document.tags.map { it.id } +
                    document.notes.map { it.id }).toSet()
            ),
            newId = { "copy" }
        )

        assertTrue(second.isNoOp)
        assertEquals(document.notes.size, second.unchanged)
    }

    @Test
    fun `the same writing with a different revision is still the same writing`() {
        val plan = plan(
            incoming = file(
                notes = listOf(note("n1", title = "Groceries", revision = 9L, updatedAt = 99L)),
                links = listOf(ArchivedLink("n1", "t1"))
            ),
            existing = here()
        )

        assertEquals(
            1,
            plan.unchanged,
            "a pass uploading a row does not make it a different note"
        )
        assertTrue(plan.copies.isEmpty())
    }

    @Test
    fun `a difference in tags alone is a difference`() {
        val plan = plan(
            incoming = file(notes = listOf(note("n1", title = "Groceries")), links = emptyList()),
            existing = here()
        )

        assertEquals(
            1,
            plan.kept,
            "quietly merging what a note carries would be a decision nobody asked for"
        )
    }

    @Test
    fun `a difference in formatting alone is a difference`() {
        val formatting = RichTextDocument.of(listOf(RichParagraph(text = "Oat milk")))

        val plan = plan(
            incoming = file(
                notes = listOf(
                    note("n1", title = "Groceries", contentDocument = formatting.encode())
                ),
                links = listOf(ArchivedLink("n1", "t1"))
            ),
            existing = here()
        )

        assertEquals(
            1,
            plan.kept,
            "the words match but the note does not, so the file's version must not be dropped"
        )
    }

    @Test
    fun `the same formatting is the same note`() {
        val formatting = RichTextDocument.of(listOf(RichParagraph(text = "Oat milk")))
        val stored = note("n1", title = "Groceries", contentDocument = formatting.encode())

        val plan = plan(
            incoming = file(notes = listOf(stored), links = listOf(ArchivedLink("n1", "t1"))),
            existing = ExistingRows(
                live = ArchiveDocument.of(
                    exportedAt = 0L,
                    folders = emptyList(),
                    tags = listOf(ArchivedTag("t1", "urgent", 1L, 2L)),
                    notes = listOf(stored),
                    links = listOf(ArchivedLink("n1", "t1"))
                ),
                takenIds = setOf("t1", "n1")
            )
        )

        assertEquals(1, plan.unchanged, "importing the same file twice does nothing")
    }

    // -----------------------------------------------------------------------

    private fun plan(
        incoming: ArchiveDocument,
        existing: ExistingRows = here()
    ) = planImport(incoming = incoming, existing = existing, newId = { "fresh-${nextId++}" })

    private var nextId = 0

    private fun here() = ExistingRows(
        live = ArchiveDocument.of(
            exportedAt = 0L,
            folders = listOf(ArchivedFolder("f1", "Work", 1L, 2L)),
            tags = listOf(ArchivedTag("t1", "urgent", 1L, 2L)),
            notes = listOf(note("n1", title = "Groceries")),
            links = listOf(ArchivedLink("n1", "t1"))
        ),
        takenIds = setOf("f1", "t1", "n1")
    )

    private fun emptyDocument() = ArchiveDocument.of(
        exportedAt = 0L,
        folders = emptyList(),
        tags = emptyList(),
        notes = emptyList(),
        links = emptyList()
    )

    private fun file(
        folders: List<ArchivedFolder> = emptyList(),
        tags: List<ArchivedTag> = listOf(ArchivedTag("t1", "urgent", 1L, 2L)),
        notes: List<ArchivedNote> = listOf(note("n2")),
        links: List<ArchivedLink> = listOf(ArchivedLink("n2", "t1"))
    ) = ArchiveDocument.of(
        exportedAt = 0L,
        folders = folders,
        tags = tags,
        notes = notes,
        links = links
    )

    private fun note(
        id: String,
        title: String = "From the file",
        content: String = "Oat milk",
        folderId: String? = null,
        revision: Long = 0L,
        updatedAt: Long = 2_000L,
        contentDocument: String? = null
    ) = ArchivedNote(
        id = id,
        title = title,
        content = content,
        folderId = folderId,
        isPinned = false,
        isFavorite = false,
        isLocked = false,
        createdAt = 1_000L,
        updatedAt = updatedAt,
        revision = revision,
        contentDocument = contentDocument
    )
}
