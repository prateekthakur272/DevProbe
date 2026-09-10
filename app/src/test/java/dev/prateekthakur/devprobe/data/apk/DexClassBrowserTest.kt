package dev.prateekthakur.devprobe.data.apk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DexClassBrowserTest {

    private val browser = DexClassBrowser()

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

    private fun writeUleb128(out: ByteArrayOutputStream, value: Int) {
        var v = value
        do {
            var b = v and 0x7F
            v = v ushr 7
            if (v != 0) b = b or 0x80
            out.write(b)
        } while (v != 0)
    }

    /**
     * Builds a minimal, structurally valid DEX defining one class:
     *   public class MainActivity extends Object {
     *     public static int VERSION;
     *     public <init>();
     *     public String getName();
     *   }
     */
    private fun buildDex(): ByteArray {
        val strings = listOf(
            "Lcom/example/app/MainActivity;", // 0
            "I", // 1
            "V", // 2
            "Ljava/lang/String;", // 3
            "VERSION", // 4
            "<init>", // 5
            "getName", // 6
            "Ljava/lang/Object;", // 7
        )
        val typeIdxToStringIdx = listOf(0, 1, 2, 3, 7) // type 0..4
        val protoReturnTypeIdx = listOf(2, 3) // proto0 -> V, proto1 -> String
        val fieldTypeIdx = 1 // I
        val fieldNameStringIdx = 4 // VERSION
        val methodProtoIdx = listOf(0, 1) // <init>, getName
        val methodNameStringIdx = listOf(5, 6)

        val headerSize = 112
        val stringIdsOff = headerSize
        val typeIdsOff = stringIdsOff + strings.size * 4
        val protoIdsOff = typeIdsOff + typeIdxToStringIdx.size * 4
        val fieldIdsOff = protoIdsOff + protoReturnTypeIdx.size * 12
        val methodIdsOff = fieldIdsOff + 1 * 8
        val classDefsOff = methodIdsOff + methodProtoIdx.size * 8
        val classDataOff = classDefsOff + 1 * 32

        val classData = ByteArrayOutputStream()
        writeUleb128(classData, 1) // static_fields_size
        writeUleb128(classData, 0) // instance_fields_size
        writeUleb128(classData, 1) // direct_methods_size
        writeUleb128(classData, 1) // virtual_methods_size
        writeUleb128(classData, 0) // static field idx diff -> field 0
        writeUleb128(classData, 0x9) // access: public|static
        writeUleb128(classData, 0) // direct method idx diff -> method 0 (<init>)
        writeUleb128(classData, 0x1) // access: public
        writeUleb128(classData, 0) // code_off
        writeUleb128(classData, 1) // virtual method idx diff -> method 1 (getName)
        writeUleb128(classData, 0x1) // access: public
        writeUleb128(classData, 0) // code_off
        val classDataBytes = classData.toByteArray()

        val stringDataStart = classDataOff + classDataBytes.size
        val stringBytesList = strings.map { it.toByteArray(Charsets.UTF_8) }
        val stringOffsets = mutableListOf<Int>()
        var cursor = stringDataStart
        for (b in stringBytesList) {
            stringOffsets += cursor
            cursor += 1 + b.size + 1
        }
        val totalSize = cursor

        val out = ByteArrayOutputStream()
        out.write('d'.code); out.write('e'.code); out.write('x'.code); out.write('\n'.code)
        out.write('0'.code); out.write('3'.code); out.write('5'.code); out.write(0)
        writeIntLE(out, 0) // checksum
        repeat(20) { out.write(0) } // signature
        writeIntLE(out, totalSize)
        writeIntLE(out, headerSize)
        writeIntLE(out, 0x12345678)
        writeIntLE(out, 0); writeIntLE(out, 0) // link_size, link_off
        writeIntLE(out, 0) // map_off
        writeIntLE(out, strings.size); writeIntLE(out, stringIdsOff)
        writeIntLE(out, typeIdxToStringIdx.size); writeIntLE(out, typeIdsOff)
        writeIntLE(out, protoReturnTypeIdx.size); writeIntLE(out, protoIdsOff)
        writeIntLE(out, 1); writeIntLE(out, fieldIdsOff)
        writeIntLE(out, methodProtoIdx.size); writeIntLE(out, methodIdsOff)
        writeIntLE(out, 1); writeIntLE(out, classDefsOff)
        writeIntLE(out, 0); writeIntLE(out, 0) // data_size, data_off
        assertEquals(headerSize, out.size())

        stringOffsets.forEach { writeIntLE(out, it) }
        typeIdxToStringIdx.forEach { writeIntLE(out, it) }
        protoReturnTypeIdx.forEach { returnTypeIdx ->
            writeIntLE(out, 0) // shorty_idx (unused by the browser)
            writeIntLE(out, returnTypeIdx)
            writeIntLE(out, 0) // parameters_off = none
        }
        // field_ids: class_idx(u2), type_idx(u2), name_idx(u4)
        writeShortLE(out, 0)
        writeShortLE(out, fieldTypeIdx)
        writeIntLE(out, fieldNameStringIdx)
        // method_ids
        for (i in methodProtoIdx.indices) {
            writeShortLE(out, 0)
            writeShortLE(out, methodProtoIdx[i])
            writeIntLE(out, methodNameStringIdx[i])
        }
        // class_def_item: class_idx, access_flags, superclass_idx, interfaces_off, source_file_idx, annotations_off, class_data_off, static_values_off
        writeIntLE(out, 0) // class_idx -> type 0 (MainActivity)
        writeIntLE(out, 0x1) // public
        writeIntLE(out, 4) // superclass_idx -> type 4 (Object)
        writeIntLE(out, 0)
        writeIntLE(out, 0)
        writeIntLE(out, 0)
        writeIntLE(out, classDataOff)
        writeIntLE(out, 0)

        out.write(classDataBytes)
        stringBytesList.forEach { b ->
            out.write(b.size)
            out.write(b)
            out.write(0)
        }

        val result = out.toByteArray()
        assertEquals(totalSize, result.size)
        return result
    }

    @Test
    fun `parses class with fields and methods`() {
        val file = File.createTempFile("test", ".apk")
        file.deleteOnExit()
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("classes.dex"))
            zip.write(buildDex())
            zip.closeEntry()
        }

        val classes = browser.browse(file)

        assertEquals(1, classes.size)
        val cls = classes.first()
        assertEquals("com.example.app.MainActivity", cls.name)
        assertEquals("java.lang.Object", cls.superclass)
        assertTrue(cls.accessFlags.contains("public"))
        assertTrue(!cls.isInterface)

        assertEquals(1, cls.fields.size)
        assertEquals("VERSION", cls.fields[0].name)
        assertEquals("int", cls.fields[0].type)
        assertTrue(cls.fields[0].accessFlags.containsAll(listOf("public", "static")))

        assertEquals(2, cls.methods.size)
        val init = cls.methods.first { it.name == "<init>" }
        assertEquals("void", init.returnType)
        assertTrue(init.paramTypes.isEmpty())

        val getName = cls.methods.first { it.name == "getName" }
        assertEquals("java.lang.String", getName.returnType)
        assertTrue(getName.accessFlags.contains("public"))
    }
}
