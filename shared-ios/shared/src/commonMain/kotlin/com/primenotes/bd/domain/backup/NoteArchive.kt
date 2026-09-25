package com.primenotes.bd.domain.backup

import kotlinx.serialization.Serializable
// Required, and painfully so: without this import the one-argument `encodeToString` is not a
// candidate, the two-argument member is, and the compiler reports the note itself as a
// `SerializationStrategy<uninferred T>` — which reads like a serialization problem and is not one.
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Instant

/**
 * The shape of a backup file.
 *
 * Deliberately neither the database rows nor the wire records: a backup outlives both, and it
 * carries only what someone would want back — no owner, no sync status, no tombstone. An
 * imported note belongs to whoever imported it, which is why the account it came from is not
 * written down here at all.
 */
@Serializable
data class ArchiveDocument(
    val formatVersion: Int,
    val exportedAt: Long,
    val folders: List<ArchivedFolder>,
    val tags: List<ArchivedTag>,
    val notes: List<ArchivedNote>,
    val links: List<ArchivedLink>
) {

    companion object {
        /**
         * The same shape, built from what is on this device.
         *
         * Used twice, on purpose: once for what an export writes, and once as the thing an
         * import compares against. One builder means the two sides of that comparison cannot
         * disagree about what a note is.
         */
        fun of(
            exportedAt: Long,
            folders: List<ArchivedFolder>,
            tags: List<ArchivedTag>,
            notes: List<ArchivedNote>,
            links: List<ArchivedLink>
        ) = ArchiveDocument(
            formatVersion = NoteArchive.FORMAT_VERSION,
            exportedAt = exportedAt,
            folders = folders,
            tags = tags,
            notes = notes,
            links = links
        )
    }
}

@Serializable
data class ArchivedFolder(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class ArchivedTag(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class ArchivedNote(
    val id: String,
    val title: String,
    val content: String,
    val folderId: String?,
    val isPinned: Boolean,
    val isFavorite: Boolean,
    val isLocked: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val revision: Long,
    /**
     * The note's formatting, exactly as the device stored it, or null for plain text.
     *
     * Defaulted so that every backup written before this field existed still reads: a v1 file is a
     * v2 file whose notes happen to have no formatting, which is the truth.
     *
     * Kept as the raw string rather than decoded, so that a body this build cannot read travels
     * through an export and back into an import untouched instead of being flattened on the way.
     */
    val contentDocument: String? = null
)

@Serializable
data class ArchivedLink(
    val noteId: String,
    val tagId: String
)

/** A file that is not a Prime Notes backup, or is one this version cannot read. */
class ArchiveFormatException(message: String) : Exception(message)

/**
 * Reading and writing the file itself.
 *
 * One zip holding two things for two audiences: `notes.json`, which is everything and is what
 * an import reads, and a Markdown file per note under `markdown/`, which is for a person. Both
 * are produced from the same document, so they cannot disagree.
 *
 * This file is common code. The container is not — `java.util.zip` on Android, the platform's own
 * compression on iOS — so it goes through [ArchiveCodec], and everything the format *means* stays
 * here. What that costs is nothing at the call sites: the codec is resolved once, on the platform,
 * and `encode`/`decode` keep the signatures they have always had.
 */
object NoteArchive {

    /** The version this build writes. */
    const val FORMAT_VERSION = 2

    /**
     * Every version this build can read.
     *
     * There is no reason to refuse a v1 file: v2 added one optional field to a note, so a v1
     * document is a v2 document whose notes carry no formatting — which is what they are. A file
     * from a *newer* version is still refused, because that one may have changed a meaning rather
     * than added a field.
     */
    val SUPPORTED_FORMAT_VERSIONS = 1..FORMAT_VERSION

    const val DOCUMENT_NAME = "notes.json"
    const val MARKDOWN_DIR = "markdown"

    /** A note in no folder goes here rather than at the top level. */
    const val UNFILED_DIR = "unfiled"

    /**
     * How much of the document will be read out of a zip.
     *
     * A backup of a personal collection is a few hundred kilobytes. This is not a limit anyone
     * will meet; it is here so that a file chosen by mistake cannot be read into memory for
     * ever.
     */
    private const val MAX_DOCUMENT_BYTES = 32L * 1024 * 1024

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val codec = archiveCodec()

    fun encode(document: ArchiveDocument): ByteArray = codec.zip(
        buildList {
            add(
                ArchiveEntry(
                    name = DOCUMENT_NAME,
                    bytes = json.encodeToString(document).encodeToByteArray()
                )
            )

            markdownEntries(document).forEach { (path, text) ->
                add(ArchiveEntry(name = path, bytes = text.encodeToByteArray()))
            }
        }
    )

    /**
     * Reads the document back.
     *
     * @throws ArchiveFormatException if this is not a zip, has no document in it, was written
     * by a version this one does not know, or is implausibly large.
     */
    fun decode(bytes: ByteArray): ArchiveDocument {
        val document = readDocument(bytes)

        val parsed = try {
            json.decodeFromString<ArchiveDocument>(document)
        } catch (_: Exception) {
            throw ArchiveFormatException("the notes in this file could not be read")
        }

        if (parsed.formatVersion !in SUPPORTED_FORMAT_VERSIONS) {
            throw ArchiveFormatException(
                "this backup was written by a newer version of Prime Notes"
            )
        }

        return parsed
    }

    /**
     * The document, once the codec has found it.
     *
     * Two of the codec's three answers are the same sentence on purpose. A file that is not a zip
     * and a zip with no document in it are one thing to the person who chose it: not a Prime Notes
     * backup. Only "too large" is worth saying differently, because that one is about the file
     * they picked rather than about what it is.
     */
    private fun readDocument(bytes: ByteArray): String =
        when (val read = codec.read(bytes, DOCUMENT_NAME, MAX_DOCUMENT_BYTES)) {
            is ArchiveRead.Found -> read.bytes.decodeToString()
            ArchiveRead.TooLarge -> throw ArchiveFormatException("that backup is too large to read")
            ArchiveRead.Absent -> throw ArchiveFormatException(
                "that file is not a Prime Notes backup"
            )
        }

    /**
     * One file per note, under the name of the folder it is in.
     *
     * Folders become directories because that is what a folder is, and the body is written out
     * exactly as it was typed — a backup that reformatted someone's writing would be a poor
     * backup.
     */
    private fun markdownEntries(document: ArchiveDocument): Map<String, String> {
        val folderNames = document.folders.associate { folder -> folder.id to folder.name }
        val tagNames = document.tags.associate { tag -> tag.id to tag.name }
        val tagsByNote = document.links.groupBy({ link -> link.noteId }, { link -> link.tagId })

        val used = mutableSetOf<String>()

        return document.notes.associate { note ->
            val directory = note.folderId
                ?.let { folderId -> folderNames[folderId] }
                ?.let { name -> pathSegment(name) }
                ?: UNFILED_DIR

            markdownPath(directory, note.title, used) to markdownFor(
                note = note,
                folderName = note.folderId?.let { folderId -> folderNames[folderId] },
                tagNames = tagsByNote[note.id].orEmpty().mapNotNull { tagId -> tagNames[tagId] }
            )
        }
    }

    /** A path that is free, so two notes of the same name both survive the trip. */
    private fun markdownPath(directory: String, title: String, used: MutableSet<String>): String {
        val base = pathSegment(title)
        var candidate = "$MARKDOWN_DIR/$directory/$base.md"
        var suffix = 2

        while (!used.add(candidate)) {
            candidate = "$MARKDOWN_DIR/$directory/$base $suffix.md"
            suffix++
        }

        return candidate
    }

    /**
     * A name that is safe inside a zip and a filesystem, and never empty.
     *
     * Anything that means something to a path is replaced rather than escaped: this file is
     * for a person to open, and a title full of percent signs would not help them.
     */
    internal fun pathSegment(raw: String): String {
        val cleaned = raw
            .replace(Regex("""[\\/:*?"<>|]"""), "-")
            .trim()
            .trim('.')
            .take(MAX_SEGMENT_LENGTH)

        return cleaned.ifBlank { UNTITLED_SEGMENT }
    }

    private fun markdownFor(
        note: ArchivedNote,
        folderName: String?,
        tagNames: List<String>
    ): String = buildString {
        append("# ")
        appendLine(note.title.ifBlank { UNTITLED_SEGMENT })
        appendLine()

        appendLine(
            listOfNotNull(
                folderName?.let { name -> "Folder: $name" },
                tagNames.takeIf { tags -> tags.isNotEmpty() }
                    ?.joinToString(prefix = "Tags: ", separator = ", "),
                "Created ${instant(note.createdAt)}",
                "Updated ${instant(note.updatedAt)}"
            ).joinToString(separator = " · ")
        )

        appendLine()
        appendLine(note.content)
    }

    /**
     * An ISO-8601 instant, spelled exactly as `DateTimeFormatter.ISO_INSTANT` used to spell it.
     *
     * `kotlin.time.Instant` is the standard library's own, and it answers in the same format, so
     * this costs no dependency — which matters here, because this is one timestamp in a file for a
     * person to read, not date arithmetic.
     */
    private fun instant(at: Long): String = Instant.fromEpochMilliseconds(at).toString()

    private const val UNTITLED_SEGMENT = "Untitled"
    private const val MAX_SEGMENT_LENGTH = 80
}
