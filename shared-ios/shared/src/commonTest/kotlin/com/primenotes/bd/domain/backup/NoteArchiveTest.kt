package com.primenotes.bd.domain.backup

import com.primenotes.bd.domain.rich.HeadingLevel
import com.primenotes.bd.domain.rich.RichParagraph
import com.primenotes.bd.domain.rich.RichTextDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The file itself: what goes in, what comes out, and what happens when it is not ours.
 *
 * No device, no database, and now no JVM either — the format is what the rest of a backup rests
 * on, so it is checked in common code and runs wherever there is a test runner. What was
 * `java.util.zip` in the helpers below is now [ArchiveCodec], which is the same reader the
 * production code uses; that is deliberate, because a test that inspected the zip with its own
 * copy of the format could agree with itself while disagreeing with the app.
 */
class NoteArchiveTest {

    private val codec = archiveCodec()

    @Test
    fun `everything the app keeps survives a round trip`() {
        val document = document()

        val decoded = NoteArchive.decode(NoteArchive.encode(document))

        assertEquals(document, decoded)
    }

    @Test
    fun `a backup written before notes could carry formatting still reads`() {
        val fromVersionOne = document().copy(formatVersion = 1)

        val decoded = NoteArchive.decode(NoteArchive.encode(fromVersionOne))

        assertEquals(
            1,
            decoded.formatVersion,
            "a v1 file is a v2 file whose notes happen to have no formatting"
        )
        assertTrue(decoded.notes.all { note -> note.contentDocument == null })
        assertEquals(
            "Oat milk and sourdough",
            decoded.notes.first().content,
            "and their text is exactly what it was"
        )
    }

    @Test
    fun `a note's formatting survives a round trip`() {
        val formatting = RichTextDocument.of(
            listOf(RichParagraph(text = "Oat milk and sourdough", heading = HeadingLevel.Large))
        )
        val archive = document().copy(
            notes = listOf(
                note(id = "n1", title = "Groceries").copy(contentDocument = formatting.encode())
            )
        )

        val decoded = NoteArchive.decode(NoteArchive.encode(archive))

        assertEquals(
            formatting.encode(),
            decoded.notes.single().contentDocument,
            "kept verbatim, so that a body this build cannot read travels too"
        )
    }

    @Test
    fun `a body written by a newer build rides through a backup untouched`() {
        val fromTheFuture = """{"version":99,"paragraphs":[{"text":"Oat milk","sparkle":true}]}"""
        val archive = document().copy(
            notes = listOf(
                note(id = "n1", title = "Groceries").copy(contentDocument = fromTheFuture)
            )
        )

        val decoded = NoteArchive.decode(NoteArchive.encode(archive))

        assertEquals(fromTheFuture, decoded.notes.single().contentDocument)
    }

    @Test
    fun `the zip holds the document and one file per note`() {
        val entries = entriesOf(NoteArchive.encode(document()))

        assertTrue(NoteArchive.DOCUMENT_NAME in entries, "the document is there")
        assertTrue(
            "markdown/Work/Groceries.md" in entries,
            "one file per note, named for the note, under its folder"
        )
        assertTrue("markdown/unfiled/shopping list.md" in entries, "a note with no folder")
    }

    @Test
    fun `a Markdown file reads like the note`() {
        val text = textOf(NoteArchive.encode(document()), "markdown/Work/Groceries.md")

        assertTrue(text.startsWith("# Groceries"), "the title is a heading")
        assertTrue(text.contains("Folder: Work"), "the folder is named")
        assertTrue(text.contains("Tags: urgent, shopping"), "so are the tags")
        assertTrue(text.contains("\nOat milk and sourdough\n"), "the body is verbatim")
    }

    @Test
    fun `two notes of the same name in one folder both survive`() {
        val twice = document().copy(
            notes = listOf(
                note(id = "n1", title = "Notes"),
                note(id = "n2", title = "Notes")
            ),
            links = emptyList()
        )

        val entries = entriesOf(NoteArchive.encode(twice))

        assertTrue("markdown/unfiled/Notes.md" in entries)
        assertTrue("markdown/unfiled/Notes 2.md" in entries)
    }

    @Test
    fun `an untitled note gets a name rather than an empty path`() {
        val untitled = document().copy(notes = listOf(note(id = "n1", title = "   ")))

        val entries = entriesOf(NoteArchive.encode(untitled))

        assertTrue(entries.any { entry -> entry.endsWith("/Untitled.md") })
    }

    @Test
    fun `a title that means something to a path is made safe`() {
        assertEquals("a-b-c", NoteArchive.pathSegment("a/b:c"))
        assertEquals("Untitled", NoteArchive.pathSegment("   "))
        assertEquals(80, NoteArchive.pathSegment("x".repeat(200)).length)
    }

    @Test
    fun `a backup from a newer version is refused`() {
        val fromTheFuture = NoteArchive.encode(
            document().copy(formatVersion = NoteArchive.FORMAT_VERSION + 1)
        )

        assertFailsWith<ArchiveFormatException> { NoteArchive.decode(fromTheFuture) }
    }

    @Test
    fun `a file that is not one of ours is refused`() {
        assertFailsWith<ArchiveFormatException> {
            NoteArchive.decode("not a zip at all".toByteArray())
        }

        assertFailsWith<ArchiveFormatException> {
            NoteArchive.decode(
                codec.zip(
                    listOf(ArchiveEntry(name = "holiday-photo.jpg", bytes = "not ours".toByteArray()))
                )
            )
        }
    }

    private fun entriesOf(bytes: ByteArray): Set<String> = codec.names(bytes).toSet()

    private fun textOf(bytes: ByteArray, entryName: String): String =
        when (val read = codec.read(bytes, entryName, MAX_ENTRY_BYTES)) {
            is ArchiveRead.Found -> read.bytes.decodeToString()
            else -> error("$entryName is not in that archive")
        }

    private fun document(): ArchiveDocument = ArchiveDocument.of(
        exportedAt = 1_700_000_000_000L,
        folders = listOf(
            ArchivedFolder(id = "f1", name = "Work", createdAt = 1L, updatedAt = 2L)
        ),
        tags = listOf(
            ArchivedTag(id = "t1", name = "urgent", createdAt = 1L, updatedAt = 2L),
            ArchivedTag(id = "t2", name = "shopping", createdAt = 1L, updatedAt = 2L)
        ),
        notes = listOf(
            note(id = "n1", title = "Groceries", folderId = "f1"),
            note(id = "n2", title = "shopping list", folderId = null)
        ),
        links = listOf(
            ArchivedLink(noteId = "n1", tagId = "t1"),
            ArchivedLink(noteId = "n1", tagId = "t2")
        )
    )

    private fun note(
        id: String,
        title: String,
        folderId: String? = null
    ) = ArchivedNote(
        id = id,
        title = title,
        content = "Oat milk and sourdough",
        folderId = folderId,
        isPinned = true,
        isFavorite = false,
        isLocked = false,
        createdAt = 1_000L,
        updatedAt = 2_000L,
        revision = 3L
    )

    private companion object {
        /** Far more than a Markdown file of note text needs; the cap is not what is under test. */
        const val MAX_ENTRY_BYTES = 1024L * 1024
    }
}
