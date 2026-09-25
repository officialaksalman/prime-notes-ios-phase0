package com.primenotes.bd.domain.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * [ArchiveCodec] on `java.util.zip` — exactly the implementation this archive had before it moved
 * to common code.
 *
 * Kept rather than replaced by a shared zip writer on purpose: the format the app writes has been
 * exercised by real backups and restores, and a move is not the moment to change the bytes.
 */
actual fun archiveCodec(): ArchiveCodec = JvmArchiveCodec

private object JvmArchiveCodec : ArchiveCodec {

    override fun zip(entries: List<ArchiveEntry>): ByteArray {
        val bytes = ByteArrayOutputStream()

        ZipOutputStream(bytes).use { zip ->
            entries.forEach { entry ->
                zip.putNextEntry(ZipEntry(entry.name))
                zip.write(entry.bytes)
                zip.closeEntry()
            }
        }

        return bytes.toByteArray()
    }

    override fun names(bytes: ByteArray): List<String> {
        val names = mutableListOf<String>()

        runCatching {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    names += entry.name
                    entry = zip.nextEntry
                }
            }
        }

        return names
    }

    override fun read(bytes: ByteArray, name: String, maxBytes: Long): ArchiveRead {
        val found = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)

        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == name) {
                        var total = 0L
                        while (true) {
                            val read = zip.read(buffer)
                            if (read <= 0) break
                            total += read
                            if (total > maxBytes) return ArchiveRead.TooLarge
                            found.write(buffer, 0, read)
                        }
                        return ArchiveRead.Found(found.toByteArray())
                    }
                    entry = zip.nextEntry
                }
            }
        } catch (_: Exception) {
            // Not a zip, or not one this reader can follow. Either way: not our file.
            return ArchiveRead.Absent
        }

        return ArchiveRead.Absent
    }
}
