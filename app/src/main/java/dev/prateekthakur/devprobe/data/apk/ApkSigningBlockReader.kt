package dev.prateekthakur.devprobe.data.apk

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile

/**
 * Detects which APK signing schemes were used, without requiring the APK to be
 * installed. v1 (JAR signing) is detected via META-INF entries ending in .RSA,
 * .DSA, or .EC. v2/v3/v3.1 are detected by locating and walking the "APK Signing Block" — a
 * container of length-prefixed ID/value pairs sitting between the zip entries
 * and the central directory (see the Android Signature Scheme v2 spec). Only
 * small, bounded regions near the end of the file are read, so this scales to
 * very large APKs.
 */
class ApkSigningBlockReader {

    data class SchemePresence(val v1: Boolean, val v2: Boolean, val v3: Boolean, val v3_1: Boolean)

    private val magic = "APK Sig Block 42".toByteArray(Charsets.US_ASCII)
    private val idV2 = 0x7109871aL
    private val idV3 = 0xf05368c0L
    private val idV31 = 0x1b93ad61L
    private val eocdSignature = byteArrayOf(0x50, 0x4b, 0x05, 0x06)
    private val eocdSearchWindow = 64 * 1024 + 22

    fun detectSchemes(apkFile: File): SchemePresence {
        val v1 = hasV1SignatureFiles(apkFile)
        val ids = readSigningBlockIds(apkFile)
        return SchemePresence(
            v1 = v1,
            v2 = idV2 in ids,
            v3 = idV3 in ids,
            v3_1 = idV31 in ids,
        )
    }

    private fun hasV1SignatureFiles(apkFile: File): Boolean = try {
        ZipFile(apkFile).use { zip ->
            zip.entries().asSequence().any {
                it.name.startsWith("META-INF/") &&
                    (it.name.endsWith(".RSA") || it.name.endsWith(".DSA") || it.name.endsWith(".EC"))
            }
        }
    } catch (_: Exception) {
        false
    }

    private fun readSigningBlockIds(apkFile: File): Set<Long> {
        return try {
            RandomAccessFile(apkFile, "r").use { raf ->
                val fileLength = raf.length()
                val eocdOffset = findEndOfCentralDirectory(raf, fileLength) ?: return emptySet()

                raf.seek(eocdOffset + 16)
                val cdOffsetBytes = ByteArray(4)
                raf.readFully(cdOffsetBytes)
                val centralDirOffset = ByteBuffer.wrap(cdOffsetBytes).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
                if (centralDirOffset < 24) return emptySet()

                raf.seek(centralDirOffset - 24)
                val trailer = ByteArray(24)
                raf.readFully(trailer)
                val trailerBuf = ByteBuffer.wrap(trailer).order(ByteOrder.LITTLE_ENDIAN)
                val trailingSize = trailerBuf.long
                val magicBytes = ByteArray(16)
                trailerBuf.get(magicBytes)
                if (!magicBytes.contentEquals(magic)) return emptySet()
                if (trailingSize < 24 || trailingSize > centralDirOffset) return emptySet()

                val blockStart = centralDirOffset - (trailingSize + 8)
                if (blockStart < 0) return emptySet()
                raf.seek(blockStart)
                val leadingSizeBytes = ByteArray(8)
                raf.readFully(leadingSizeBytes)
                val leadingSize = ByteBuffer.wrap(leadingSizeBytes).order(ByteOrder.LITTLE_ENDIAN).long
                if (leadingSize != trailingSize) return emptySet()

                val pairsLength = (trailingSize - 24).toInt()
                if (pairsLength <= 0 || pairsLength > 32 * 1024 * 1024) return emptySet()
                val pairs = ByteArray(pairsLength)
                raf.readFully(pairs)

                parsePairIds(pairs)
            }
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun parsePairIds(pairs: ByteArray): Set<Long> {
        val ids = mutableSetOf<Long>()
        val buf = ByteBuffer.wrap(pairs).order(ByteOrder.LITTLE_ENDIAN)
        while (buf.remaining() >= 12) {
            val pairLen = buf.long
            if (pairLen < 4 || pairLen > buf.remaining()) break
            val id = buf.int.toLong() and 0xFFFFFFFFL
            ids += id
            val valueLen = (pairLen - 4).toInt()
            if (valueLen < 0 || valueLen > buf.remaining()) break
            buf.position(buf.position() + valueLen)
        }
        return ids
    }

    private fun findEndOfCentralDirectory(raf: RandomAccessFile, fileLength: Long): Long? {
        val windowSize = minOf(fileLength, eocdSearchWindow.toLong()).toInt()
        if (windowSize < 22) return null
        val windowStart = fileLength - windowSize
        raf.seek(windowStart)
        val window = ByteArray(windowSize)
        raf.readFully(window)
        for (i in windowSize - 22 downTo 0) {
            if (window[i] == eocdSignature[0] && window[i + 1] == eocdSignature[1] &&
                window[i + 2] == eocdSignature[2] && window[i + 3] == eocdSignature[3]
            ) {
                return windowStart + i
            }
        }
        return null
    }
}
