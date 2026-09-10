package dev.prateekthakur.devprobe.data.apk

import dev.prateekthakur.devprobe.data.apk.DexHeaderOffsets.CLASS_DEFS_OFF
import dev.prateekthakur.devprobe.data.apk.DexHeaderOffsets.CLASS_DEFS_SIZE
import dev.prateekthakur.devprobe.data.apk.DexHeaderOffsets.CLASS_DEF_ITEM_SIZE
import dev.prateekthakur.devprobe.data.apk.DexHeaderOffsets.FIELD_IDS_SIZE
import dev.prateekthakur.devprobe.data.apk.DexHeaderOffsets.HEADER_SIZE
import dev.prateekthakur.devprobe.data.apk.DexHeaderOffsets.METHOD_IDS_SIZE
import dev.prateekthakur.devprobe.data.apk.DexHeaderOffsets.PROTO_IDS_SIZE
import dev.prateekthakur.devprobe.data.apk.DexHeaderOffsets.STRING_IDS_OFF
import dev.prateekthakur.devprobe.data.apk.DexHeaderOffsets.STRING_IDS_SIZE
import dev.prateekthakur.devprobe.data.apk.DexHeaderOffsets.TYPE_IDS_OFF
import dev.prateekthakur.devprobe.data.apk.DexHeaderOffsets.TYPE_IDS_SIZE
import dev.prateekthakur.devprobe.domain.model.BytecodeAnalysis
import dev.prateekthakur.devprobe.domain.model.DexFileInfo
import dev.prateekthakur.devprobe.domain.model.ObfuscationSignal
import java.io.File

/**
 * Parses the DEX file header (§7.4 "APK Structure") of each classes*.dex entry —
 * fixed 112-byte layout, no disassembly — to report version, table sizes, and a
 * simple heuristic signal for whether the code looks minified/obfuscated (a very
 * high proportion of 1-2 character class names is typical of ProGuard/R8 output).
 */
class DexAnalyzer {

    fun analyze(apkFile: File): BytecodeAnalysis {
        val dexEntries = DexBinaryReader.readDexEntries(apkFile)

        val dexFiles = dexEntries.mapNotNull { (name, bytes) -> runCatching { parseHeader(name, bytes) }.getOrNull() }
        val classDescriptors = dexEntries.flatMap { (_, bytes) -> runCatching { extractClassDescriptors(bytes) }.getOrDefault(emptyList()) }
        val obfuscation = computeObfuscationSignal(classDescriptors)

        return BytecodeAnalysis(
            dexFiles = dexFiles,
            totalClasses = dexFiles.sumOf { it.classCount },
            totalMethods = dexFiles.sumOf { it.methodCount },
            totalFields = dexFiles.sumOf { it.fieldCount },
            totalStrings = dexFiles.sumOf { it.stringCount },
            isMultidex = dexFiles.size > 1,
            obfuscation = obfuscation,
        )
    }

    private fun parseHeader(name: String, bytes: ByteArray): DexFileInfo {
        require(bytes.size >= HEADER_SIZE) { "File too small to be a valid DEX." }
        val version = String(bytes, 4, 3, Charsets.US_ASCII)
        val signature = bytes.copyOfRange(12, 32)

        return DexFileInfo(
            name = name,
            sizeBytes = bytes.size.toLong(),
            dexVersion = version,
            stringCount = DexBinaryReader.readIntLE(bytes, STRING_IDS_SIZE),
            typeCount = DexBinaryReader.readIntLE(bytes, TYPE_IDS_SIZE),
            protoCount = DexBinaryReader.readIntLE(bytes, PROTO_IDS_SIZE),
            fieldCount = DexBinaryReader.readIntLE(bytes, FIELD_IDS_SIZE),
            methodCount = DexBinaryReader.readIntLE(bytes, METHOD_IDS_SIZE),
            classCount = DexBinaryReader.readIntLE(bytes, CLASS_DEFS_SIZE),
            sha1Signature = signature.joinToString("") { "%02x".format(it) },
        )
    }

    /** Reads type descriptors (e.g. "Lcom/example/app/MainActivity;") for every class DEFINED in this dex. */
    private fun extractClassDescriptors(bytes: ByteArray): List<String> {
        if (bytes.size < HEADER_SIZE) return emptyList()
        val stringIdsSize = DexBinaryReader.readIntLE(bytes, STRING_IDS_SIZE)
        val stringIdsOff = DexBinaryReader.readIntLE(bytes, STRING_IDS_OFF)
        val typeIdsOff = DexBinaryReader.readIntLE(bytes, TYPE_IDS_OFF)
        val classDefsSize = DexBinaryReader.readIntLE(bytes, CLASS_DEFS_SIZE)
        val classDefsOff = DexBinaryReader.readIntLE(bytes, CLASS_DEFS_OFF)

        val descriptors = mutableListOf<String>()
        for (i in 0 until classDefsSize) {
            val classDefOff = classDefsOff + i * CLASS_DEF_ITEM_SIZE
            if (classDefOff < 0 || classDefOff + 4 > bytes.size) continue
            val classIdx = DexBinaryReader.readIntLE(bytes, classDefOff)
            DexBinaryReader.typeDescriptor(bytes, typeIdsOff, stringIdsOff, stringIdsSize, classIdx)?.let { descriptors += it }
        }
        return descriptors
    }

    private fun computeObfuscationSignal(descriptors: List<String>): ObfuscationSignal {
        if (descriptors.isEmpty()) return ObfuscationSignal(0, 0, 0.0, false)
        var shortCount = 0
        descriptors.forEach { descriptor ->
            if (descriptor.startsWith("L") && descriptor.endsWith(";")) {
                val simpleName = descriptor.removePrefix("L").removeSuffix(";").substringAfterLast('/')
                val leaf = simpleName.substringAfterLast('$')
                if (leaf.isNotEmpty() && leaf.length <= 2 && leaf[0].isLetter()) {
                    shortCount++
                }
            }
        }
        val percent = shortCount.toDouble() / descriptors.size * 100.0
        return ObfuscationSignal(
            sampledClassCount = descriptors.size,
            shortNameCount = shortCount,
            shortNamePercent = percent,
            likelyObfuscated = percent >= 40.0,
        )
    }
}
