package com.primenotes.bd.domain.backup

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.toCValues
import platform.darwin.COMPRESSION_ZLIB
import platform.darwin.compression_decode_buffer
import platform.darwin.compression_encode_buffer

/**
 * [ArchiveCodec] on Apple's own compression.
 *
 * iOS has no `java.util.zip`, and Foundation has no zip of its own — `NSData.compressed(using:)`
 * is macOS-only and, in any case, not a zip. So the container is assembled here while the
 * compression itself comes from the platform: `compression_encode_buffer`/`_decode_buffer` with
 * `COMPRESSION_ZLIB`, which despite the name is **raw DEFLATE** (RFC 1951, no zlib header and no
 * Adler-32 trailer) — exactly the stream a zip entry of method 8 holds, and exactly what
 * `java.util.zip` produces with a `Deflater(..., nowrap = true)`. The two platforms therefore
 * write archives each other can read.
 *
 * Two details of the format are load-bearing, and both were measured against real `java.util.zip`
 * output rather than assumed:
 *
 *  * **Reading follows the central directory, not the local headers.** `java.util.zip`'s
 *    `ZipOutputStream` streams its entries, so it sets the data-descriptor flag and leaves the
 *    sizes and CRC *zero* in each local file header — they arrive afterwards. The central
 *    directory always carries the real values, so that is what is read. Local headers are used
 *    only for the one thing they do own: where the entry's data starts, since their name and
 *    extra-field lengths need not match the central directory's.
 *  * **Writing states the sizes up front** and sets no data-descriptor flag, because every entry
 *    is deflated whole before it is written. `java.util.zip`'s `ZipInputStream` reads that
 *    without complaint.
 *
 * The entry names are always UTF-8 with the archive's UTF-8 flag set: a note's title becomes a
 * Markdown file name, and titles are not ASCII.
 */
actual fun archiveCodec(): ArchiveCodec = FoundationArchiveCodec

private object FoundationArchiveCodec : ArchiveCodec {

    override fun zip(entries: List<ArchiveEntry>): ByteArray {
        val out = ByteSink()
        val central = mutableListOf<Record>()

        for (entry in entries) {
            val name = entry.name.encodeToByteArray()
            val data = entry.bytes

            // Small and empty entries are stored rather than deflated: an empty one has nothing to
            // compress, and a short one commonly comes out longer. Both are valid zip entries, and
            // any reader — ours included — takes either.
            val deflated = if (data.isEmpty()) null else deflate(data)
            val method = if (deflated != null && deflated.size < data.size) DEFLATED else STORED
            val payload = if (method == DEFLATED) requireNotNull(deflated) else data
            val crc = Crc32.of(data)

            val offset = out.size

            out.u32(LOCAL_HEADER)
            out.u16(VERSION_NEEDED)
            out.u16(UTF8_FLAG)
            out.u16(method)
            out.u16(0)
            out.u16(0)
            out.u32(crc)
            out.u32(payload.size.toLong())
            out.u32(data.size.toLong())
            out.u16(name.size)
            out.u16(0)
            out.bytes(name)
            out.bytes(payload)

            central += Record(name, method, crc, payload.size.toLong(), data.size.toLong(), offset)
        }

        val centralOffset = out.size

        for (record in central) {
            out.u32(CENTRAL_HEADER)
            out.u16(VERSION_MADE_BY)
            out.u16(VERSION_NEEDED)
            out.u16(UTF8_FLAG)
            out.u16(record.method)
            out.u16(0)
            out.u16(0)
            out.u32(record.crc)
            out.u32(record.compressedSize)
            out.u32(record.uncompressedSize)
            out.u16(record.name.size)
            out.u16(0)
            out.u16(0)
            out.u16(0)
            out.u16(0)
            out.u32(0)
            out.u32(record.localOffset.toLong())
            out.bytes(record.name)
        }

        val centralSize = out.size - centralOffset

        out.u32(END_OF_CENTRAL_DIRECTORY)
        out.u16(0)
        out.u16(0)
        out.u16(central.size)
        out.u16(central.size)
        out.u32(centralSize.toLong())
        out.u32(centralOffset.toLong())
        out.u16(0)

        return out.toByteArray()
    }

    override fun names(bytes: ByteArray): List<String> =
        bytes.centralDirectory().orEmpty().map { record -> record.name.decodeToString() }

    override fun read(bytes: ByteArray, name: String, maxBytes: Long): ArchiveRead {
        val record = bytes.centralDirectory()
            ?.firstOrNull { candidate -> candidate.name.decodeToString() == name }
            ?: return ArchiveRead.Absent

        if (record.uncompressedSize > maxBytes) return ArchiveRead.TooLarge

        val local = record.localOffset
        if (local + LOCAL_HEADER_SIZE > bytes.size) return ArchiveRead.Absent
        if (bytes.u32(local) != LOCAL_HEADER) return ArchiveRead.Absent

        val dataStart = local + LOCAL_HEADER_SIZE + bytes.u16(local + 26) + bytes.u16(local + 28)
        val dataEnd = dataStart + record.compressedSize
        if (dataEnd > bytes.size) return ArchiveRead.Absent

        val payload = bytes.copyOfRange(dataStart, dataEnd.toInt())
        val plain = when (record.method) {
            STORED -> payload
            DEFLATED -> inflate(payload, record.uncompressedSize.toInt()) ?: return ArchiveRead.Absent
            else -> return ArchiveRead.Absent
        }

        // Checked rather than trusted: a backup whose bytes do not match its own directory is not
        // a backup, and a wrong entry read into the database would be worse than a refusal.
        if (plain.size.toLong() != record.uncompressedSize) return ArchiveRead.Absent
        if (Crc32.of(plain) != record.crc) return ArchiveRead.Absent

        return ArchiveRead.Found(plain)
    }
}

/** Where one entry lives and what it holds, as the central directory states it. */
private class Record(
    val name: ByteArray,
    val method: Int,
    val crc: Long,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val localOffset: Int
)

/** One entry of a zip's central directory per call, or null when the bytes are not a zip. */
private fun ByteArray.centralDirectory(): List<Record>? {
    val end = endOfCentralDirectory()
    if (end < 0) return null

    var at = u32(end + 16).toInt()
    val count = u16(end + 10)
    val records = ArrayList<Record>(count)

    repeat(count) {
        if (at + CENTRAL_HEADER_SIZE > size) return null
        if (u32(at) != CENTRAL_HEADER) return null

        val method = u16(at + 10)
        val crc = u32(at + 16)
        val compressedSize = u32(at + 20)
        val uncompressedSize = u32(at + 24)
        val nameLength = u16(at + 28)
        val extraLength = u16(at + 30)
        val commentLength = u16(at + 32)
        val localOffset = u32(at + 42).toInt()

        if (at + CENTRAL_HEADER_SIZE + nameLength > size) return null

        records += Record(
            name = copyOfRange(at + CENTRAL_HEADER_SIZE, at + CENTRAL_HEADER_SIZE + nameLength),
            method = method,
            crc = crc,
            compressedSize = compressedSize,
            uncompressedSize = uncompressedSize,
            localOffset = localOffset
        )

        at += CENTRAL_HEADER_SIZE + nameLength + extraLength + commentLength
    }

    return records
}

/** Where the end-of-central-directory record starts, or -1 when there is none. */
private fun ByteArray.endOfCentralDirectory(): Int {
    val earliest = maxOf(0, size - END_OF_CENTRAL_DIRECTORY_SIZE - MAX_COMMENT_LENGTH)

    for (at in size - END_OF_CENTRAL_DIRECTORY_SIZE downTo earliest) {
        if (u32(at) == END_OF_CENTRAL_DIRECTORY) return at
    }

    return -1
}

private fun ByteArray.u16(at: Int): Int =
    (this[at].toInt() and 0xFF) or ((this[at + 1].toInt() and 0xFF) shl 8)

private fun ByteArray.u32(at: Int): Long =
    (this[at].toLong() and 0xFF) or
        ((this[at + 1].toLong() and 0xFF) shl 8) or
        ((this[at + 2].toLong() and 0xFF) shl 16) or
        ((this[at + 3].toLong() and 0xFF) shl 24)

/**
 * Raw DEFLATE, which is what a zip entry of method 8 is.
 *
 * The destination is sized by zlib's own worst-case bound — an incompressible entry comes out
 * slightly longer than it went in — so a single call is always enough and no retry loop is needed.
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalUnsignedTypes::class)
private fun deflate(data: ByteArray): ByteArray = memScoped {
    val capacity = data.size + (data.size shr 12) + (data.size shr 14) + (data.size shr 25) + 13
    val output = allocArray<UByteVar>(capacity)

    val produced = compression_encode_buffer(
        output,
        capacity.convert(),
        data.toUByteArray().toCValues(),
        data.size.convert(),
        null,
        COMPRESSION_ZLIB
    )

    check(produced > 0uL) { "the platform compressor refused a ${data.size}-byte entry" }

    output.readBytes(produced.toInt())
}

/** Raw DEFLATE back to [size] bytes, or null when the stream is not exactly that. */
@OptIn(ExperimentalForeignApi::class, ExperimentalUnsignedTypes::class)
private fun inflate(data: ByteArray, size: Int): ByteArray? {
    if (size == 0) return ByteArray(0)

    return memScoped {
        val output = allocArray<UByteVar>(size)

        val produced = compression_decode_buffer(
            output,
            size.convert(),
            data.toUByteArray().toCValues(),
            data.size.convert(),
            null,
            COMPRESSION_ZLIB
        )

        if (produced.toInt() != size) null else output.readBytes(size)
    }
}

/**
 * The standard reflected CRC-32, as zip stores it.
 *
 * `java.util.zip` computes this itself; on this side it has to be written out, because a zip's
 * directory has to carry it and [FoundationArchiveCodec.read] verifies it.
 */
private object Crc32 {

    private val table = IntArray(256) { index ->
        var value = index
        repeat(8) {
            value = if (value and 1 != 0) (value ushr 1) xor POLYNOMIAL else value ushr 1
        }
        value
    }

    fun of(bytes: ByteArray): Long {
        var crc = -1

        for (byte in bytes) {
            crc = (crc ushr 8) xor table[(crc xor byte.toInt()) and 0xFF]
        }

        return (crc.inv().toLong()) and 0xFFFFFFFFL
    }

    private const val POLYNOMIAL = -306674912 // 0xEDB88320 as a signed Int
}

/** A growable byte buffer: common Kotlin has no appender for `ByteArray`. */
private class ByteSink {

    private var bytes = ByteArray(INITIAL_CAPACITY)
    private var length = 0

    val size: Int get() = length

    fun u16(value: Int) {
        ensure(2)
        bytes[length++] = (value and 0xFF).toByte()
        bytes[length++] = ((value ushr 8) and 0xFF).toByte()
    }

    fun u32(value: Long) {
        ensure(4)
        bytes[length++] = (value and 0xFF).toByte()
        bytes[length++] = ((value ushr 8) and 0xFF).toByte()
        bytes[length++] = ((value ushr 16) and 0xFF).toByte()
        bytes[length++] = ((value ushr 24) and 0xFF).toByte()
    }

    fun bytes(source: ByteArray) {
        ensure(source.size)
        source.copyInto(bytes, length)
        length += source.size
    }

    fun toByteArray(): ByteArray = bytes.copyOf(length)

    private fun ensure(extra: Int) {
        if (length + extra <= bytes.size) return

        var capacity = bytes.size
        while (capacity < length + extra) capacity *= 2

        bytes = bytes.copyOf(capacity)
    }

    private companion object {
        const val INITIAL_CAPACITY = 1024
    }
}

private const val LOCAL_HEADER = 0x04034b50L
private const val CENTRAL_HEADER = 0x02014b50L
private const val END_OF_CENTRAL_DIRECTORY = 0x06054b50L
private const val LOCAL_HEADER_SIZE = 30
private const val CENTRAL_HEADER_SIZE = 46
private const val END_OF_CENTRAL_DIRECTORY_SIZE = 22
private const val MAX_COMMENT_LENGTH = 0xFFFF
private const val VERSION_MADE_BY = 20
private const val VERSION_NEEDED = 20
private const val UTF8_FLAG = 0x0800
private const val STORED = 0
private const val DEFLATED = 8
