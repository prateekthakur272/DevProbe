package dev.prateekthakur.devprobe.data.apk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ApkSigningBlockReaderTest {

    private val reader = ApkSigningBlockReader()

    private fun writeIntLE(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v ushr 8) and 0xFF)
        out.write((v ushr 16) and 0xFF)
        out.write((v ushr 24) and 0xFF)
    }

    private fun writeLongLE(out: ByteArrayOutputStream, v: Long) {
        for (i in 0 until 8) out.write(((v ushr (8 * i)) and 0xFF).toInt())
    }

    /**
     * Builds a raw byte blob shaped like [prefix][APK Signing Block with the given
     * ID/value pairs][central-directory-ish bytes][EOCD], sufficient for the reader's
     * signing-block scan (it never parses the zip structure itself).
     */
    private fun buildFileWithSigningBlock(pairIds: List<Long>): File {
        val out = ByteArrayOutputStream()
        repeat(50) { out.write(0) } // stand-in for preceding zip local file entries

        val pairsBuf = ByteArrayOutputStream()
        for (id in pairIds) {
            val value = ByteArray(4) // trivial 4-byte value payload
            writeLongLE(pairsBuf, (4 + value.size).toLong()) // pairLen = id(4) + value
            writeIntLE(pairsBuf, id.toInt())
            pairsBuf.write(value)
        }
        val pairsBytes = pairsBuf.toByteArray()
        val blockSizeField = (pairsBytes.size + 24).toLong() // pairs + trailing size(8) + magic(16)

        writeLongLE(out, blockSizeField) // leading size
        out.write(pairsBytes)
        writeLongLE(out, blockSizeField) // trailing size
        out.write("APK Sig Block 42".toByteArray(Charsets.US_ASCII)) // magic (16 bytes)

        val centralDirOffset = out.size()
        repeat(20) { out.write(0) } // stand-in for a "central directory"

        // End Of Central Directory record (exactly 22 bytes, no comment):
        // signature(4) + diskNum+cdDisk(2+2) + cdRecordsThisDisk+totalCdRecords(2+2) + sizeOfCd(4) + cdOffset(4) + commentLen(2)
        out.write(0x50); out.write(0x4b); out.write(0x05); out.write(0x06) // signature
        writeIntLE(out, 0) // disk number (2) + disk where CD starts (2)
        writeIntLE(out, 0) // CD records on this disk (2) + total CD records (2)
        writeIntLE(out, 20) // size of central directory (matches the 20 filler bytes above)
        writeIntLE(out, centralDirOffset) // offset of start of central directory <- what the reader reads
        out.write(0); out.write(0) // comment length = 0

        val file = File.createTempFile("test", ".apk")
        file.deleteOnExit()
        file.writeBytes(out.toByteArray())
        return file
    }

    @Test
    fun `detects v1 signing from META-INF certificate entries`() {
        val file = File.createTempFile("test", ".apk")
        file.deleteOnExit()
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write(byteArrayOf(1, 2, 3))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("META-INF/CERT.RSA"))
            zip.write(byteArrayOf(4, 5, 6))
            zip.closeEntry()
        }

        val schemes = reader.detectSchemes(file)

        assertTrue(schemes.v1)
        assertFalse(schemes.v2)
        assertFalse(schemes.v3)
    }

    @Test
    fun `detects v2 scheme id from the apk signing block`() {
        val file = buildFileWithSigningBlock(listOf(0x7109871aL))

        val schemes = reader.detectSchemes(file)

        assertFalse(schemes.v1)
        assertTrue(schemes.v2)
        assertFalse(schemes.v3)
    }

    @Test
    fun `detects v2 and v3 scheme ids together`() {
        val file = buildFileWithSigningBlock(listOf(0x7109871aL, 0xf05368c0L))

        val schemes = reader.detectSchemes(file)

        assertTrue(schemes.v2)
        assertTrue(schemes.v3)
    }

    @Test
    fun `reports no schemes for a file with no signing block and no v1 entries`() {
        val file = File.createTempFile("test", ".apk")
        file.deleteOnExit()
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write(byteArrayOf(1, 2, 3))
            zip.closeEntry()
        }

        val schemes = reader.detectSchemes(file)

        assertFalse(schemes.v1)
        assertFalse(schemes.v2)
        assertFalse(schemes.v3)
        assertFalse(schemes.v3_1)
    }
}
