package dev.prateekthakur.devprobe.data.apk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ElfAnalyzerTest {

    private val analyzer = ElfAnalyzer()

    private fun writeIntLE(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v ushr 8) and 0xFF)
        out.write((v ushr 16) and 0xFF)
        out.write((v ushr 24) and 0xFF)
    }

    private fun writeShortLE(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v ushr 8) and 0xFF)
    }

    /** Builds a minimal, structurally valid 32-bit ELF with one exported and one imported dynamic symbol. */
    private fun buildElf32(): ByteArray {
        val dynsymOff = 52
        val dynsymEntries = 3 // null, exported, imported
        val dynsymSize = dynsymEntries * 16
        val dynstrOff = dynsymOff + dynsymSize
        // dynstr: "\0" + "exported_func\0" + "imported_func\0"
        val exportedNameOff = 1
        val importedNameOff = exportedNameOff + "exported_func".length + 1
        val dynstrBytes = ByteArrayOutputStream().apply {
            write(0)
            write("exported_func".toByteArray()); write(0)
            write("imported_func".toByteArray()); write(0)
        }.toByteArray()
        val shstrtabOff = dynstrOff + dynstrBytes.size
        // shstrtab: "\0" + ".dynsym\0" + ".dynstr\0" + ".shstrtab\0"
        val dynsymNameOff = 1
        val dynstrNameOff = dynsymNameOff + ".dynsym".length + 1
        val shstrtabNameOff = dynstrNameOff + ".dynstr".length + 1
        val shstrtabBytes = ByteArrayOutputStream().apply {
            write(0)
            write(".dynsym".toByteArray()); write(0)
            write(".dynstr".toByteArray()); write(0)
            write(".shstrtab".toByteArray()); write(0)
        }.toByteArray()
        val shoff = shstrtabOff + shstrtabBytes.size

        val out = ByteArrayOutputStream()
        // e_ident
        out.write(0x7F); out.write('E'.code); out.write('L'.code); out.write('F'.code)
        out.write(1) // EI_CLASS = 32-bit
        out.write(1) // EI_DATA = little endian
        out.write(1) // EI_VERSION
        out.write(0) // EI_OSABI
        repeat(8) { out.write(0) } // EI_ABIVERSION + padding, total e_ident = 16 bytes
        assertEquals(16, out.size())
        writeShortLE(out, 3) // e_type = ET_DYN
        writeShortLE(out, 40) // e_machine = EM_ARM
        writeIntLE(out, 1) // e_version
        writeIntLE(out, 0) // e_entry
        writeIntLE(out, 0) // e_phoff
        writeIntLE(out, shoff) // e_shoff
        writeIntLE(out, 0) // e_flags
        writeShortLE(out, 52) // e_ehsize
        writeShortLE(out, 0) // e_phentsize
        writeShortLE(out, 0) // e_phnum
        writeShortLE(out, 40) // e_shentsize
        writeShortLE(out, 4) // e_shnum
        writeShortLE(out, 3) // e_shstrndx
        assertEquals(52, out.size())

        // dynsym: null symbol, exported (shndx != 0), imported (shndx == 0)
        fun writeSym(nameOff: Int, shndx: Int) {
            writeIntLE(out, nameOff) // st_name
            writeIntLE(out, 0) // st_value
            writeIntLE(out, 0) // st_size
            out.write(0x12) // st_info
            out.write(0) // st_other
            writeShortLE(out, shndx)
        }
        writeSym(0, 0)
        writeSym(exportedNameOff, 1)
        writeSym(importedNameOff, 0)
        assertEquals(dynstrOff, out.size())

        out.write(dynstrBytes)
        assertEquals(shstrtabOff, out.size())
        out.write(shstrtabBytes)
        assertEquals(shoff, out.size())

        // section header table: NULL, .dynsym, .dynstr, .shstrtab
        fun writeSection(nameOff: Int, offset: Int, size: Int, entSize: Int) {
            writeIntLE(out, nameOff) // sh_name
            writeIntLE(out, 0) // sh_type
            writeIntLE(out, 0) // sh_flags
            writeIntLE(out, 0) // sh_addr
            writeIntLE(out, offset) // sh_offset
            writeIntLE(out, size) // sh_size
            writeIntLE(out, 0) // sh_link
            writeIntLE(out, 0) // sh_info
            writeIntLE(out, 4) // sh_addralign
            writeIntLE(out, entSize) // sh_entsize
        }
        writeSection(0, 0, 0, 0) // NULL section
        writeSection(dynsymNameOff, dynsymOff, dynsymSize, 16)
        writeSection(dynstrNameOff, dynstrOff, dynstrBytes.size, 0)
        writeSection(shstrtabNameOff, shstrtabOff, shstrtabBytes.size, 0)

        return out.toByteArray()
    }

    private fun apkWithLib(elfBytes: ByteArray, libPath: String = "lib/armeabi-v7a/libtest.so"): File {
        val file = File.createTempFile("test", ".apk")
        file.deleteOnExit()
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry(libPath))
            zip.write(elfBytes)
            zip.closeEntry()
        }
        return file
    }

    @Test
    fun `resolves exported and imported symbols from a 32-bit ELF`() {
        val libs = analyzer.analyzeAll(apkWithLib(buildElf32()))

        assertEquals(1, libs.size)
        val lib = libs.first()
        assertEquals("arm", lib.architecture)
        assertFalse(lib.is64Bit)
        assertTrue(lib.exportedSymbols.contains("exported_func"))
        assertTrue(lib.importedSymbols.contains("imported_func"))
        assertFalse(lib.exportedSymbols.contains("imported_func"))
        assertFalse(lib.importedSymbols.contains("exported_func"))
    }

    @Test
    fun `ignores non-so entries and non-lib paths`() {
        val file = File.createTempFile("test", ".apk")
        file.deleteOnExit()
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write(byteArrayOf(1, 2, 3))
            zip.closeEntry()
        }
        assertTrue(analyzer.analyzeAll(file).isEmpty())
    }
}
