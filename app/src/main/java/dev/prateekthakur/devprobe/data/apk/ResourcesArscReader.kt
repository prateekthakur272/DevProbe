package dev.prateekthakur.devprobe.data.apk

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile

/**
 * Reads resources.arsc's leading global string pool. The ResTable_header is
 * followed immediately by a string-pool chunk in the exact same binary format
 * used inside AndroidManifest.xml, so this reuses [AxmlParser.parseStringPool]
 * rather than re-implementing the string-pool format.
 */
object ResourcesArscReader {

    private const val CHUNK_STRING_POOL = 0x0001
    private const val RES_TABLE_HEADER_SIZE = 12

    fun readGlobalStringPool(apkFile: File): List<String> {
        val bytes = try {
            ZipFile(apkFile).use { zip ->
                val entry = zip.getEntry("resources.arsc") ?: return emptyList()
                zip.getInputStream(entry).use { it.readBytes() }
            }
        } catch (_: Exception) {
            return emptyList()
        }
        return try {
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            if (bytes.size < RES_TABLE_HEADER_SIZE + 8) return emptyList()
            buf.position(RES_TABLE_HEADER_SIZE)
            val chunkStart = buf.position()
            val type = buf.short.toInt() and 0xFFFF
            buf.short // headerSize
            val chunkSize = buf.int
            if (type != CHUNK_STRING_POOL) return emptyList()
            AxmlParser.parseStringPool(buf, chunkStart, chunkSize)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
