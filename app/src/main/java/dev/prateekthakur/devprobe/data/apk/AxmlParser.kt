package dev.prateekthakur.devprobe.data.apk

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal parser for Android's compiled binary XML format (AXML), as used for
 * AndroidManifest.xml inside an APK. PackageManager does not expose intent-filter
 * data for an uninstalled/archived APK, so this reads the manifest directly.
 *
 * Only the subset of the format needed to recover elements/attributes is implemented
 * (no styled-string spans, no namespace resolution beyond ignoring them).
 */
object AxmlParser {

    data class Node(
        val name: String,
        val attributes: Map<String, String>,
        val children: MutableList<Node> = mutableListOf(),
    )

    private const val CHUNK_STRING_POOL = 0x0001
    private const val CHUNK_XML_RESOURCE_MAP = 0x0180
    private const val CHUNK_XML_START_NAMESPACE = 0x0100
    private const val CHUNK_XML_END_NAMESPACE = 0x0101
    private const val CHUNK_XML_START_ELEMENT = 0x0102
    private const val CHUNK_XML_END_ELEMENT = 0x0103
    private const val CHUNK_XML_CDATA = 0x0104

    private const val TYPE_STRING = 0x03
    private const val TYPE_INT_BOOLEAN = 0x12

    fun parse(bytes: ByteArray): Node {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        // Outer XML chunk header.
        buf.short // type
        buf.short // headerSize
        buf.int // chunkSize

        var strings: List<String> = emptyList()
        val root = Node("root", emptyMap())
        val stack = ArrayDeque<Node>()
        stack.addLast(root)

        while (buf.remaining() >= 8) {
            val chunkStart = buf.position()
            val type = buf.short.toInt() and 0xFFFF
            val headerSize = buf.short.toInt() and 0xFFFF
            val chunkSize = buf.int
            if (chunkSize <= 0) break
            when (type) {
                CHUNK_STRING_POOL -> {
                    strings = parseStringPool(buf, chunkStart, chunkSize)
                }
                CHUNK_XML_RESOURCE_MAP -> {
                    // Not needed: we resolve attribute names via the string pool index directly.
                }
                CHUNK_XML_START_NAMESPACE, CHUNK_XML_END_NAMESPACE, CHUNK_XML_CDATA -> {
                    // Skip — not needed for manifest analysis.
                }
                CHUNK_XML_START_ELEMENT -> {
                    buf.int // lineNumber
                    buf.int // comment
                    buf.int // namespaceUri
                    val nameIdx = buf.int
                    val attrStart = buf.short.toInt() and 0xFFFF
                    val attrSize = buf.short.toInt() and 0xFFFF
                    val attrCount = buf.short.toInt() and 0xFFFF
                    buf.short // idIndex
                    buf.short // classIndex
                    buf.short // styleIndex

                    val attrs = LinkedHashMap<String, String>()
                    repeat(attrCount) {
                        val attrChunkPos = buf.position()
                        buf.int // ns
                        val attrNameIdx = buf.int
                        val rawValueIdx = buf.int
                        buf.short // size
                        buf.get() // res0
                        val dataType = buf.get().toInt() and 0xFF
                        val data = buf.int
                        val attrName = strings.getOrNull(attrNameIdx) ?: "attr_$attrNameIdx"
                        val value = when (dataType) {
                            TYPE_STRING -> if (rawValueIdx >= 0) strings.getOrNull(rawValueIdx).orEmpty() else ""
                            TYPE_INT_BOOLEAN -> if (data != 0) "true" else "false"
                            else -> if (rawValueIdx >= 0) strings.getOrNull(rawValueIdx).orEmpty() else data.toString()
                        }
                        attrs[attrName] = value
                        // In case attrSize differs from what we consumed (20 bytes), realign.
                        val consumed = buf.position() - attrChunkPos
                        if (attrSize > consumed) buf.position(buf.position() + (attrSize - consumed))
                    }
                    val elementName = strings.getOrNull(nameIdx) ?: "element_$nameIdx"
                    val node = Node(elementName, attrs)
                    stack.last().children.add(node)
                    stack.addLast(node)
                }
                CHUNK_XML_END_ELEMENT -> {
                    if (stack.size > 1) stack.removeLast()
                }
                else -> {
                    // Unknown chunk: skip via chunkSize below.
                }
            }
            val nextPos = chunkStart + chunkSize
            if (nextPos <= buf.position() || nextPos > bytes.size) {
                // Safety: if we didn't consume as expected, force-seek using chunkSize.
                if (nextPos in (chunkStart + 1)..bytes.size) buf.position(nextPos) else break
            } else {
                buf.position(nextPos)
            }
        }
        return root
    }

    /** Internal: also reused by [ResourcesArscReader] — resources.arsc's global string pool
     * uses this exact same chunk format (RES_STRING_POOL_TYPE = 0x0001). */
    internal fun parseStringPool(buf: ByteBuffer, chunkStart: Int, chunkSize: Int): List<String> {
        val stringCount = buf.int
        buf.int // styleCount
        val flags = buf.int
        val stringsStart = buf.int
        buf.int // stylesStart
        val isUtf8 = (flags and 0x100) != 0

        val offsets = IntArray(stringCount) { buf.int }
        val dataBase = chunkStart + stringsStart
        val result = ArrayList<String>(stringCount)
        for (i in 0 until stringCount) {
            val strPos = dataBase + offsets[i]
            if (strPos < 0 || strPos >= buf.capacity()) {
                result.add("")
                continue
            }
            result.add(if (isUtf8) readUtf8String(buf, strPos) else readUtf16String(buf, strPos))
        }
        buf.position(chunkStart + chunkSize)
        return result
    }

    private fun readUtf16String(buf: ByteBuffer, pos: Int): String {
        var p = pos
        var len = buf.getShort(p).toInt() and 0xFFFF
        p += 2
        if (len and 0x8000 != 0) {
            val hi = len and 0x7FFF
            val lo = buf.getShort(p).toInt() and 0xFFFF
            len = (hi shl 16) or lo
            p += 2
        }
        val chars = CharArray(len)
        for (i in 0 until len) {
            chars[i] = buf.getShort(p + i * 2).toInt().toChar()
        }
        return String(chars)
    }

    private fun readUtf8String(buf: ByteBuffer, pos: Int): String {
        var p = pos
        // utf16 length (skip)
        var b = buf.get(p).toInt() and 0xFF
        p += 1
        if (b and 0x80 != 0) p += 1
        // utf8 length
        b = buf.get(p).toInt() and 0xFF
        p += 1
        var utf8Len = b and 0x7F
        if (b and 0x80 != 0) {
            val lo = buf.get(p).toInt() and 0xFF
            utf8Len = (utf8Len shl 8) or lo
            p += 1
        }
        val bytes = ByteArray(utf8Len)
        for (i in 0 until utf8Len) bytes[i] = buf.get(p + i)
        return String(bytes, Charsets.UTF_8)
    }
}
