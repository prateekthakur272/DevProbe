package dev.prateekthakur.devprobe.data.apk

import dev.prateekthakur.devprobe.data.apk.DexHeaderOffsets.CLASS_DEFS_OFF
import dev.prateekthakur.devprobe.data.apk.DexHeaderOffsets.CLASS_DEFS_SIZE
import dev.prateekthakur.devprobe.data.apk.DexHeaderOffsets.CLASS_DEF_ITEM_SIZE
import dev.prateekthakur.devprobe.data.apk.DexHeaderOffsets.FIELD_IDS_OFF
import dev.prateekthakur.devprobe.data.apk.DexHeaderOffsets.FIELD_ID_ITEM_SIZE
import dev.prateekthakur.devprobe.data.apk.DexHeaderOffsets.HEADER_SIZE
import dev.prateekthakur.devprobe.data.apk.DexHeaderOffsets.METHOD_IDS_OFF
import dev.prateekthakur.devprobe.data.apk.DexHeaderOffsets.METHOD_ID_ITEM_SIZE
import dev.prateekthakur.devprobe.data.apk.DexHeaderOffsets.PROTO_IDS_OFF
import dev.prateekthakur.devprobe.data.apk.DexHeaderOffsets.PROTO_ID_ITEM_SIZE
import dev.prateekthakur.devprobe.data.apk.DexHeaderOffsets.STRING_IDS_OFF
import dev.prateekthakur.devprobe.data.apk.DexHeaderOffsets.STRING_IDS_SIZE
import dev.prateekthakur.devprobe.data.apk.DexHeaderOffsets.TYPE_IDS_OFF
import dev.prateekthakur.devprobe.domain.model.DexClassInfo
import dev.prateekthakur.devprobe.domain.model.DexFieldInfo
import dev.prateekthakur.devprobe.domain.model.DexMethodInfo
import java.io.File

private const val ACC_PUBLIC = 0x1
private const val ACC_PRIVATE = 0x2
private const val ACC_PROTECTED = 0x4
private const val ACC_STATIC = 0x8
private const val ACC_FINAL = 0x10
private const val ACC_SYNCHRONIZED = 0x20
private const val ACC_INTERFACE = 0x200
private const val ACC_ABSTRACT = 0x400
private const val ACC_SYNTHETIC = 0x1000
private const val ACC_ENUM = 0x4000
private const val ACC_NATIVE = 0x100
private const val NO_INDEX = -1 // 0xFFFFFFFF as a signed Int

/**
 * Structural (not disassembled) DEX class browser — "smali-lite". Parses
 * class_def_item/class_data_item (ULEB128-encoded field/method tables) plus
 * method_id/field_id/proto_id tables to recover every class's declared fields
 * and methods with resolved, human-readable signatures. On-demand only (not
 * part of the eager analysis pipeline) since results can be very large.
 */
class DexClassBrowser {

    fun browse(apkFile: File): List<DexClassInfo> =
        DexBinaryReader.readDexEntries(apkFile)
            .flatMap { (_, bytes) -> runCatching { parseClasses(bytes) }.getOrDefault(emptyList()) }

    private fun parseClasses(bytes: ByteArray): List<DexClassInfo> {
        if (bytes.size < HEADER_SIZE) return emptyList()
        val r = DexBinaryReader
        val stringIdsSize = r.readIntLE(bytes, STRING_IDS_SIZE)
        val stringIdsOff = r.readIntLE(bytes, STRING_IDS_OFF)
        val typeIdsOff = r.readIntLE(bytes, TYPE_IDS_OFF)
        val protoIdsOff = r.readIntLE(bytes, PROTO_IDS_OFF)
        val fieldIdsOff = r.readIntLE(bytes, FIELD_IDS_OFF)
        val methodIdsOff = r.readIntLE(bytes, METHOD_IDS_OFF)
        val classDefsSize = r.readIntLE(bytes, CLASS_DEFS_SIZE)
        val classDefsOff = r.readIntLE(bytes, CLASS_DEFS_OFF)

        fun typeName(typeIdx: Int): String =
            r.typeDescriptor(bytes, typeIdsOff, stringIdsOff, stringIdsSize, typeIdx)
                ?.let { r.readableTypeName(it) } ?: "?"

        fun fieldInfo(fieldIdx: Int, accessFlags: Int): DexFieldInfo? {
            val off = fieldIdsOff + fieldIdx * FIELD_ID_ITEM_SIZE
            if (off !in 0 until (bytes.size - 8 + 1)) return null
            val typeIdx = r.readShortLE(bytes, off + 2)
            val nameIdx = r.readIntLE(bytes, off + 4)
            val name = r.readStringAt(bytes, stringIdsOff, stringIdsSize, nameIdx) ?: return null
            return DexFieldInfo(name, typeName(typeIdx), decodeAccessFlags(accessFlags, isMethod = false))
        }

        fun methodInfo(methodIdx: Int, accessFlags: Int): DexMethodInfo? {
            val off = methodIdsOff + methodIdx * METHOD_ID_ITEM_SIZE
            if (off !in 0 until (bytes.size - 8 + 1)) return null
            val protoIdx = r.readShortLE(bytes, off + 2)
            val nameIdx = r.readIntLE(bytes, off + 4)
            val name = r.readStringAt(bytes, stringIdsOff, stringIdsSize, nameIdx) ?: return null

            var returnType = "?"
            var paramTypes: List<String> = emptyList()
            val protoOff = protoIdsOff + protoIdx * PROTO_ID_ITEM_SIZE
            if (protoOff in 0 until (bytes.size - PROTO_ID_ITEM_SIZE + 1)) {
                val returnTypeIdx = r.readIntLE(bytes, protoOff + 4)
                returnType = typeName(returnTypeIdx)
                val paramsOff = r.readIntLE(bytes, protoOff + 8)
                paramTypes = r.readTypeList(bytes, paramsOff, typeIdsOff, stringIdsOff, stringIdsSize)
                    .map { r.readableTypeName(it) }
            }
            val isAbstract = (accessFlags and ACC_ABSTRACT) != 0 || (accessFlags and ACC_NATIVE) != 0
            return DexMethodInfo(name, returnType, paramTypes, decodeAccessFlags(accessFlags, isMethod = true), isAbstract)
        }

        val classes = mutableListOf<DexClassInfo>()
        for (i in 0 until classDefsSize) {
            val off = classDefsOff + i * CLASS_DEF_ITEM_SIZE
            if (off !in 0 until (bytes.size - CLASS_DEF_ITEM_SIZE + 1)) continue
            val classIdx = r.readIntLE(bytes, off)
            val classAccessFlags = r.readIntLE(bytes, off + 4)
            val superclassIdx = r.readIntLE(bytes, off + 8)
            val classDataOff = r.readIntLE(bytes, off + 24)

            val className = r.typeDescriptor(bytes, typeIdsOff, stringIdsOff, stringIdsSize, classIdx)
                ?.let { r.readableTypeName(it) } ?: continue
            val superclassName = if (superclassIdx != NO_INDEX) {
                r.typeDescriptor(bytes, typeIdsOff, stringIdsOff, stringIdsSize, superclassIdx)?.let { r.readableTypeName(it) }
            } else null

            val fields = mutableListOf<DexFieldInfo>()
            val methods = mutableListOf<DexMethodInfo>()

            if (classDataOff in 1 until bytes.size) {
                var pos = classDataOff
                fun uleb(): Int {
                    val (v, c) = r.readUleb128(bytes, pos)
                    pos += c
                    return v
                }
                val staticFieldsSize = uleb()
                val instanceFieldsSize = uleb()
                val directMethodsSize = uleb()
                val virtualMethodsSize = uleb()

                var idx = 0
                repeat(staticFieldsSize) {
                    idx += uleb()
                    val af = uleb()
                    fieldInfo(idx, af)?.let { fields += it }
                }
                idx = 0
                repeat(instanceFieldsSize) {
                    idx += uleb()
                    val af = uleb()
                    fieldInfo(idx, af)?.let { fields += it }
                }
                idx = 0
                repeat(directMethodsSize) {
                    idx += uleb()
                    val af = uleb()
                    uleb() // code_off, unused (no disassembly)
                    methodInfo(idx, af)?.let { methods += it }
                }
                idx = 0
                repeat(virtualMethodsSize) {
                    idx += uleb()
                    val af = uleb()
                    uleb()
                    methodInfo(idx, af)?.let { methods += it }
                }
            }

            classes += DexClassInfo(
                name = className,
                superclass = superclassName,
                accessFlags = decodeAccessFlags(classAccessFlags, isMethod = false),
                isInterface = (classAccessFlags and ACC_INTERFACE) != 0,
                fields = fields,
                methods = methods,
            )
        }
        return classes
    }

    private fun decodeAccessFlags(flags: Int, isMethod: Boolean): List<String> {
        val result = mutableListOf<String>()
        if (flags and ACC_PUBLIC != 0) result += "public"
        if (flags and ACC_PRIVATE != 0) result += "private"
        if (flags and ACC_PROTECTED != 0) result += "protected"
        if (flags and ACC_STATIC != 0) result += "static"
        if (flags and ACC_FINAL != 0) result += "final"
        if (isMethod) {
            if (flags and ACC_NATIVE != 0) result += "native"
            if (flags and ACC_ABSTRACT != 0) result += "abstract"
            if (flags and ACC_SYNCHRONIZED != 0) result += "synchronized"
        } else {
            if (flags and ACC_INTERFACE != 0) result += "interface"
            if (flags and ACC_ABSTRACT != 0) result += "abstract"
            if (flags and ACC_ENUM != 0) result += "enum"
        }
        if (flags and ACC_SYNTHETIC != 0) result += "synthetic"
        return result
    }
}
