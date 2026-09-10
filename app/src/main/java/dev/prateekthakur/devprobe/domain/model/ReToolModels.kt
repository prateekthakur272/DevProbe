package dev.prateekthakur.devprobe.domain.model

import kotlinx.serialization.Serializable

// --- File explorer (on-demand, not persisted) ---

data class ApkFileEntryDetail(
    val path: String,
    val sizeBytes: Long,
    val compressedSizeBytes: Long,
    val crc32: Long,
    val isDirectory: Boolean,
)

// --- Strings & secrets (eager, persisted) ---

@Serializable
enum class StringSource { DEX, RESOURCES, NATIVE_LIB }

@Serializable
enum class SecretType {
    AWS_KEY, GOOGLE_API_KEY, JWT, PRIVATE_KEY, GENERIC_SECRET_ASSIGNMENT, URL, EMAIL
}

@Serializable
data class DetectedSecret(
    val type: SecretType,
    val matchedText: String,
    val context: String,
    val source: StringSource,
    val severity: Severity,
)

@Serializable
enum class WeakCryptoAlgorithm { DES, TRIPLE_DES, RC2, RC4, MD5, SHA1, ECB_MODE }

@Serializable
data class WeakCryptoUsage(
    val algorithm: WeakCryptoAlgorithm,
    val matchedText: String,
    val source: StringSource,
)

@Serializable
data class StringScanResult(
    val totalStringsScanned: Int,
    val secrets: List<DetectedSecret>,
    val weakCryptoUsages: List<WeakCryptoUsage> = emptyList(),
)

// --- Class/method browser (on-demand, not persisted) ---

data class DexFieldInfo(
    val name: String,
    val type: String,
    val accessFlags: List<String>,
)

data class DexMethodInfo(
    val name: String,
    val returnType: String,
    val paramTypes: List<String>,
    val accessFlags: List<String>,
    val isAbstract: Boolean,
) {
    val signature: String get() = "$name(${paramTypes.joinToString(", ")}): $returnType"
}

data class DexClassInfo(
    val name: String,
    val superclass: String?,
    val accessFlags: List<String>,
    val isInterface: Boolean,
    val fields: List<DexFieldInfo>,
    val methods: List<DexMethodInfo>,
)

// --- Native library analysis (on-demand, not persisted) ---

data class NativeLibraryInfo(
    val path: String,
    val sizeBytes: Long,
    val architecture: String,
    val is64Bit: Boolean,
    val exportedSymbols: List<String>,
    val importedSymbols: List<String>,
)
