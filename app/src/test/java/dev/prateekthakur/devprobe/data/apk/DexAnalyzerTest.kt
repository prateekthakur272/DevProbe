package dev.prateekthakur.devprobe.data.apk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DexAnalyzerTest {

    private val analyzer = DexAnalyzer()

    /** Builds a minimal, structurally valid DEX file defining the given class descriptors. */
    private fun buildSyntheticDex(descriptors: List<String>): ByteArray {
        val stringBytes = descriptors.map { it.toByteArray(Charsets.UTF_8) }
        val headerSize = 112
        val stringIdsOff = headerSize
        val typeIdsOff = stringIdsOff + descriptors.size * 4
        val classDefsOff = typeIdsOff + descriptors.size * 4
        val stringDataStart = classDefsOff + descriptors.size * 32

        val stringOffsets = mutableListOf<Int>()
        var cursor = stringDataStart
        for (bytes in stringBytes) {
            stringOffsets += cursor
            cursor += 1 + bytes.size + 1 // uleb128 length (1 byte, strings are short) + utf8 + NUL
        }
        val totalSize = cursor

        val out = ByteArrayOutputStream()
        fun writeIntLE(v: Int) {
            out.write(v and 0xFF)
            out.write((v ushr 8) and 0xFF)
            out.write((v ushr 16) and 0xFF)
            out.write((v ushr 24) and 0xFF)
        }
        fun writeByte(v: Int) = out.write(v)

        writeByte('d'.code); writeByte('e'.code); writeByte('x'.code); writeByte('\n'.code)
        writeByte('0'.code); writeByte('3'.code); writeByte('5'.code); writeByte(0) // magic, 8 bytes
        writeIntLE(0) // checksum
        repeat(20) { writeByte(0) } // signature
        writeIntLE(totalSize) // file_size
        writeIntLE(headerSize) // header_size
        writeIntLE(0x12345678) // endian_tag
        writeIntLE(0) // link_size
        writeIntLE(0) // link_off
        writeIntLE(0) // map_off
        writeIntLE(descriptors.size) // string_ids_size
        writeIntLE(stringIdsOff) // string_ids_off
        writeIntLE(descriptors.size) // type_ids_size
        writeIntLE(typeIdsOff) // type_ids_off
        writeIntLE(0) // proto_ids_size
        writeIntLE(0) // proto_ids_off
        writeIntLE(0) // field_ids_size
        writeIntLE(0) // field_ids_off
        writeIntLE(0) // method_ids_size
        writeIntLE(0) // method_ids_off
        writeIntLE(descriptors.size) // class_defs_size
        writeIntLE(classDefsOff) // class_defs_off
        writeIntLE(0) // data_size
        writeIntLE(0) // data_off
        assertEquals(headerSize, out.size())

        // string_ids table
        stringOffsets.forEach { writeIntLE(it) }
        // type_ids table: type[i] -> string[i]
        for (i in descriptors.indices) writeIntLE(i)
        // class_defs table: class[i] -> type[i], rest zeroed
        for (i in descriptors.indices) {
            writeIntLE(i) // class_idx
            repeat(7) { writeIntLE(0) } // remaining 7 u4 fields
        }
        // string data
        for (bytes in stringBytes) {
            writeByte(bytes.size) // uleb128 (single byte, strings here are all < 128 bytes)
            out.write(bytes)
            writeByte(0) // NUL terminator
        }

        val result = out.toByteArray()
        assertEquals(totalSize, result.size)
        return result
    }

    private fun apkWithDex(dexBytes: ByteArray, dexEntryName: String = "classes.dex"): File {
        val file = File.createTempFile("test", ".apk")
        file.deleteOnExit()
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry(dexEntryName))
            zip.write(dexBytes)
            zip.closeEntry()
        }
        return file
    }

    @Test
    fun `parses dex header fields`() {
        val dex = buildSyntheticDex(listOf("Lcom/example/app/MainActivity;"))
        val result = analyzer.analyze(apkWithDex(dex))

        assertEquals(1, result.dexFiles.size)
        val info = result.dexFiles.first()
        assertEquals("035", info.dexVersion)
        assertEquals(1, info.stringCount)
        assertEquals(1, info.typeCount)
        assertEquals(1, info.classCount)
        assertEquals(dex.size.toLong(), info.sizeBytes)
    }

    @Test
    fun `flags a high proportion of short class names as likely obfuscated`() {
        val dex = buildSyntheticDex(
            listOf("Lcom/example/app/MainActivity;", "Lcom/example/app/a;")
        )
        val result = analyzer.analyze(apkWithDex(dex))

        assertEquals(2, result.totalClasses)
        assertEquals(2, result.obfuscation.sampledClassCount)
        assertEquals(1, result.obfuscation.shortNameCount)
        assertEquals(50.0, result.obfuscation.shortNamePercent, 0.01)
        assertTrue(result.obfuscation.likelyObfuscated)
    }

    @Test
    fun `does not flag normal descriptive class names as obfuscated`() {
        val dex = buildSyntheticDex(
            listOf(
                "Lcom/example/app/MainActivity;",
                "Lcom/example/app/PaymentViewModel;",
                "Lcom/example/app/NetworkRepository;",
            )
        )
        val result = analyzer.analyze(apkWithDex(dex))

        assertEquals(0, result.obfuscation.shortNameCount)
        assertTrue(!result.obfuscation.likelyObfuscated)
    }

    @Test
    fun `detects multidex across multiple classes dex entries`() {
        val file = File.createTempFile("test", ".apk")
        file.deleteOnExit()
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("classes.dex"))
            zip.write(buildSyntheticDex(listOf("Lcom/example/app/MainActivity;")))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("classes2.dex"))
            zip.write(buildSyntheticDex(listOf("Lcom/example/app/SecondActivity;")))
            zip.closeEntry()
        }
        val result = analyzer.analyze(file)

        assertEquals(2, result.dexFiles.size)
        assertTrue(result.isMultidex)
        assertEquals(2, result.totalClasses)
    }
}
