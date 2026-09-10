package dev.prateekthakur.devprobe.data.apk

import dev.prateekthakur.devprobe.domain.model.NativeLibraryInfo
import java.io.File
import java.util.zip.ZipFile

/**
 * Parses ELF32/ELF64 headers + section headers of native libraries (the .so files under lib/&lt;abi&gt;/)
 * to resolve the dynamic symbol table (.dynsym/.dynstr) into exported (defined)
 * and imported (undefined) symbol names — the mobile-friendly equivalent of `nm -D`.
 * On-demand only: symbol tables can be large and this isn't needed for every import.
 */
class ElfAnalyzer {

    fun analyzeAll(apkFile: File): List<NativeLibraryInfo> =
        ZipFile(apkFile).use { zip ->
            zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.startsWith("lib/") && it.name.endsWith(".so") }
                .mapNotNull { entry ->
                    val bytes = zip.getInputStream(entry).use { it.readBytes() }
                    runCatching { parse(entry.name, bytes) }.getOrNull()
                }
                .toList()
        }

    private fun readIntLE(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    private fun readShortLE(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun readLongLE(bytes: ByteArray, offset: Int): Long {
        var result = 0L
        for (i in 0 until 8) result = result or ((bytes[offset + i].toLong() and 0xFF) shl (8 * i))
        return result
    }

    private fun machineName(eMachine: Int): String = when (eMachine) {
        3 -> "x86"
        40 -> "arm"
        62 -> "x86_64"
        183 -> "arm64"
        else -> "unknown(0x${eMachine.toString(16)})"
    }

    private fun parse(path: String, bytes: ByteArray): NativeLibraryInfo? {
        if (bytes.size < 20 || bytes[0] != 0x7F.toByte() || bytes[1] != 'E'.code.toByte() ||
            bytes[2] != 'L'.code.toByte() || bytes[3] != 'F'.code.toByte()
        ) return null

        val is64Bit = bytes[4].toInt() == 2
        val eMachine = readShortLE(bytes, 18)

        val shoff: Long
        val shentsize: Int
        val shnum: Int
        val shstrndx: Int
        if (is64Bit) {
            if (bytes.size < 64) return null
            shoff = readLongLE(bytes, 40)
            shentsize = readShortLE(bytes, 58)
            shnum = readShortLE(bytes, 60)
            shstrndx = readShortLE(bytes, 62)
        } else {
            if (bytes.size < 52) return null
            shoff = readIntLE(bytes, 32).toLong() and 0xFFFFFFFFL
            shentsize = readShortLE(bytes, 46)
            shnum = readShortLE(bytes, 48)
            shstrndx = readShortLE(bytes, 50)
        }
        if (shoff <= 0 || shoff >= bytes.size || shnum <= 0 || shentsize <= 0) {
            return NativeLibraryInfo(path, bytes.size.toLong(), machineName(eMachine), is64Bit, emptyList(), emptyList())
        }

        data class Section(val nameOff: Int, val type: Int, val offset: Long, val size: Long, val link: Int, val entSize: Long)

        fun readSection(index: Int): Section? {
            val off = shoff + index.toLong() * shentsize
            if (off < 0 || off + shentsize > bytes.size) return null
            val o = off.toInt()
            return if (is64Bit) {
                Section(
                    nameOff = readIntLE(bytes, o),
                    type = readIntLE(bytes, o + 4),
                    offset = readLongLE(bytes, o + 24),
                    size = readLongLE(bytes, o + 32),
                    link = readIntLE(bytes, o + 40),
                    entSize = readLongLE(bytes, o + 56),
                )
            } else {
                Section(
                    nameOff = readIntLE(bytes, o),
                    type = readIntLE(bytes, o + 4),
                    offset = readIntLE(bytes, o + 16).toLong() and 0xFFFFFFFFL,
                    size = readIntLE(bytes, o + 20).toLong() and 0xFFFFFFFFL,
                    link = readIntLE(bytes, o + 24),
                    entSize = readIntLE(bytes, o + 36).toLong() and 0xFFFFFFFFL,
                )
            }
        }

        val shstrtabSection = readSection(shstrndx)
        fun sectionName(section: Section): String? {
            val strtab = shstrtabSection ?: return null
            val pos = strtab.offset + section.nameOff
            if (pos < 0 || pos >= bytes.size) return null
            var end = pos.toInt()
            while (end < bytes.size && bytes[end] != 0.toByte()) end++
            return String(bytes, pos.toInt(), end - pos.toInt(), Charsets.UTF_8)
        }

        var dynsym: Section? = null
        var dynstr: Section? = null
        for (i in 0 until shnum) {
            val section = readSection(i) ?: continue
            when (sectionName(section)) {
                ".dynsym" -> dynsym = section
                ".dynstr" -> dynstr = section
            }
        }

        if (dynsym == null || dynstr == null || dynsym.entSize <= 0) {
            return NativeLibraryInfo(path, bytes.size.toLong(), machineName(eMachine), is64Bit, emptyList(), emptyList())
        }

        fun symbolNameAt(nameOff: Int): String? {
            val pos = dynstr.offset + nameOff
            if (pos < 0 || pos >= bytes.size) return null
            var end = pos.toInt()
            while (end < bytes.size && bytes[end] != 0.toByte()) end++
            return String(bytes, pos.toInt(), end - pos.toInt(), Charsets.UTF_8).takeIf { it.isNotBlank() }
        }

        val exported = LinkedHashSet<String>()
        val imported = LinkedHashSet<String>()
        val symCount = (dynsym.size / dynsym.entSize).toInt()
        val maxSymbols = 2000
        for (i in 0 until minOf(symCount, maxSymbols)) {
            val symOff = dynsym.offset + i * dynsym.entSize
            if (symOff < 0 || symOff >= bytes.size) continue
            val o = symOff.toInt()
            val nameOff: Int
            val shndx: Int
            if (is64Bit) {
                if (o + 24 > bytes.size) continue
                nameOff = readIntLE(bytes, o)
                shndx = readShortLE(bytes, o + 6)
            } else {
                if (o + 16 > bytes.size) continue
                nameOff = readIntLE(bytes, o)
                shndx = readShortLE(bytes, o + 14)
            }
            val name = symbolNameAt(nameOff) ?: continue
            if (shndx == 0) imported += name else exported += name
        }

        return NativeLibraryInfo(
            path = path,
            sizeBytes = bytes.size.toLong(),
            architecture = machineName(eMachine),
            is64Bit = is64Bit,
            exportedSymbols = exported.toList(),
            importedSymbols = imported.toList(),
        )
    }
}
