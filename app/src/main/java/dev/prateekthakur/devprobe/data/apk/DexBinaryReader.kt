package dev.prateekthakur.devprobe.data.apk

import java.io.File
import java.util.zip.ZipFile

/** Fixed byte offsets into the 112-byte DEX header (all fields are u4 little-endian unless noted). */
internal object DexHeaderOffsets {
    const val STRING_IDS_SIZE = 56
    const val STRING_IDS_OFF = 60
    const val TYPE_IDS_SIZE = 64
    const val TYPE_IDS_OFF = 68
    const val PROTO_IDS_SIZE = 72
    const val PROTO_IDS_OFF = 76
    const val FIELD_IDS_SIZE = 80
    const val FIELD_IDS_OFF = 84
    const val METHOD_IDS_SIZE = 88
    const val METHOD_IDS_OFF = 92
    const val CLASS_DEFS_SIZE = 96
    const val CLASS_DEFS_OFF = 100
    const val HEADER_SIZE = 112
    const val CLASS_DEF_ITEM_SIZE = 32
    const val METHOD_ID_ITEM_SIZE = 8
    const val FIELD_ID_ITEM_SIZE = 8
    const val PROTO_ID_ITEM_SIZE = 12
}

/**
 * Low-level DEX binary readers shared by [DexAnalyzer], [DexClassBrowser], and
 * [StringScanner] so the byte-format knowledge (uleb128, string table lookup,
 * type lists) lives in exactly one place.
 */
internal object DexBinaryReader {

    private val dexEntryRegex = Regex("""classes\d*\.dex""")

    /** Reads every classes*.dex entry's raw bytes, keyed by zip entry name. */
    fun readDexEntries(apkFile: File): List<Pair<String, ByteArray>> =
        ZipFile(apkFile).use { zip ->
            zip.entries().asSequence()
                .filter { !it.isDirectory && dexEntryRegex.matches(it.name.substringAfterLast('/')) }
                .map { entry -> entry.name to zip.getInputStream(entry).use { it.readBytes() } }
                .toList()
        }

    fun readIntLE(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    fun readShortLE(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    /** Reads a ULEB128 value starting at [offset]. Returns (value, bytesConsumed). */
    fun readUleb128(bytes: ByteArray, offset: Int): Pair<Int, Int> {
        var result = 0
        var shift = 0
        var pos = offset
        var consumed = 0
        while (consumed < 5 && pos < bytes.size) {
            val b = bytes[pos].toInt() and 0xFF
            pos++
            consumed++
            result = result or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) break
            shift += 7
        }
        return result to consumed
    }

    /** Reads a SLEB128 (signed) value starting at [offset]. Returns (value, bytesConsumed). */
    fun readSleb128(bytes: ByteArray, offset: Int): Pair<Int, Int> {
        var result = 0
        var shift = 0
        var pos = offset
        var consumed = 0
        var b: Int
        do {
            if (pos >= bytes.size) return result to consumed
            b = bytes[pos].toInt() and 0xFF
            pos++
            consumed++
            result = result or ((b and 0x7F) shl shift)
            shift += 7
        } while (b and 0x80 != 0 && consumed < 5)
        if (shift < 32 && (b and 0x40) != 0) {
            result = result or (-1 shl shift)
        }
        return result to consumed
    }

    /** Resolves string_ids[stringIdx] to its decoded (approximate MUTF-8 as UTF-8) text. */
    fun readStringAt(bytes: ByteArray, stringIdsOff: Int, stringIdsSize: Int, stringIdx: Int): String? {
        if (stringIdx < 0 || stringIdx >= stringIdsSize) return null
        val stringDataOff = readIntLE(bytes, stringIdsOff + stringIdx * 4)
        if (stringDataOff < 0 || stringDataOff >= bytes.size) return null
        val (_, consumed) = readUleb128(bytes, stringDataOff)
        val start = stringDataOff + consumed
        var end = start
        while (end < bytes.size && bytes[end] != 0.toByte()) end++
        if (end > bytes.size || start > bytes.size) return null
        return String(bytes, start, end - start, Charsets.UTF_8)
    }

    /** Reads every entry in string_ids (i.e. every literal string this dex file references). */
    fun readAllStrings(bytes: ByteArray): List<String> {
        if (bytes.size < DexHeaderOffsets.HEADER_SIZE) return emptyList()
        val stringIdsSize = readIntLE(bytes, DexHeaderOffsets.STRING_IDS_SIZE)
        val stringIdsOff = readIntLE(bytes, DexHeaderOffsets.STRING_IDS_OFF)
        val result = ArrayList<String>(stringIdsSize)
        for (i in 0 until stringIdsSize) {
            readStringAt(bytes, stringIdsOff, stringIdsSize, i)?.let { result += it }
        }
        return result
    }

    /** Resolves type_ids[typeIdx] to its descriptor string (e.g. "Lcom/example/App;" or "I"). */
    fun typeDescriptor(bytes: ByteArray, typeIdsOff: Int, stringIdsOff: Int, stringIdsSize: Int, typeIdx: Int): String? {
        val typeIdOff = typeIdsOff + typeIdx * 4
        if (typeIdOff < 0 || typeIdOff + 4 > bytes.size) return null
        val stringIdx = readIntLE(bytes, typeIdOff)
        return readStringAt(bytes, stringIdsOff, stringIdsSize, stringIdx)
    }

    /** Reads a type_list at [offset] (u4 size + size*u2 type indices), resolving each to a descriptor. */
    fun readTypeList(bytes: ByteArray, offset: Int, typeIdsOff: Int, stringIdsOff: Int, stringIdsSize: Int): List<String> {
        if (offset <= 0 || offset + 4 > bytes.size) return emptyList()
        val size = readIntLE(bytes, offset)
        val result = ArrayList<String>(size)
        for (i in 0 until size) {
            val entryOff = offset + 4 + i * 2
            if (entryOff + 2 > bytes.size) break
            val typeIdx = readShortLE(bytes, entryOff)
            typeDescriptor(bytes, typeIdsOff, stringIdsOff, stringIdsSize, typeIdx)?.let { result += it }
        }
        return result
    }

    /** Converts a JVM/DEX type descriptor (e.g. "Lcom/example/App;", "[I", "I") into a readable name. */
    fun readableTypeName(descriptor: String): String {
        var d = descriptor
        var arrayDepth = 0
        while (d.startsWith("[")) {
            arrayDepth++
            d = d.substring(1)
        }
        val base = when {
            d.startsWith("L") && d.endsWith(";") -> d.removePrefix("L").removeSuffix(";").replace('/', '.')
            d == "V" -> "void"
            d == "Z" -> "boolean"
            d == "B" -> "byte"
            d == "S" -> "short"
            d == "C" -> "char"
            d == "I" -> "int"
            d == "J" -> "long"
            d == "F" -> "float"
            d == "D" -> "double"
            else -> d
        }
        return base + "[]".repeat(arrayDepth)
    }
}
