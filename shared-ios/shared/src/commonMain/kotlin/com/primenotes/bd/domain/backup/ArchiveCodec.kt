package com.primenotes.bd.domain.backup

/** One file inside a backup archive. */
data class ArchiveEntry(val name: String, val bytes: ByteArray)

/** What came of asking an archive for one of its entries. */
sealed interface ArchiveRead {

    /** The entry, exactly as it was stored. */
    data class Found(val bytes: ByteArray) : ArchiveRead

    /** There is no such entry — or these bytes are not an archive at all. */
    data object Absent : ArchiveRead

    /** The entry is larger than the caller was willing to read. */
    data object TooLarge : ArchiveRead
}

/**
 * The zip, in and out — the only part of a backup that needs a platform.
 *
 * A backup is one zip holding `notes.json` and a Markdown file per note. Everything about *what*
 * goes in it, what the document means, and what an import would do is plain Kotlin and lives in
 * [NoteArchive] and [planImport]. Only the container is not: Android reads and writes zips with
 * `java.util.zip`, and iOS with the platform's own compression, and neither is available to the
 * other. Both implementations are behind this interface so the format itself is decided in one
 * place.
 *
 * Deliberately *not* `expect`/`actual` on the individual operations: an interface lets each
 * platform keep the implementation it already had, and — because the Android one is
 * `java.util.zip` — keeps this move a move rather than a rewrite of the format's byte layout.
 */
interface ArchiveCodec {

    /** Packs [entries], in order, into a zip and returns its bytes. */
    fun zip(entries: List<ArchiveEntry>): ByteArray

    /** The names of the entries in [bytes], in order, or empty when it is not a zip. */
    fun names(bytes: ByteArray): List<String>

    /**
     * Reads the entry called [name].
     *
     * Answers [ArchiveRead.TooLarge] rather than reading an implausibly large entry, and
     * [ArchiveRead.Absent] for anything that is not a readable zip — because to the caller those
     * are the same answer: the file was not one of ours.
     */
    fun read(bytes: ByteArray, name: String, maxBytes: Long): ArchiveRead
}

/**
 * The platform's zip implementation.
 *
 * The platform seam sits here, at the edge, rather than as `expect` members on [ArchiveCodec]:
 * one `expect` for the whole codec means a new platform has one decision to make — which codec —
 * instead of four.
 */
expect fun archiveCodec(): ArchiveCodec
