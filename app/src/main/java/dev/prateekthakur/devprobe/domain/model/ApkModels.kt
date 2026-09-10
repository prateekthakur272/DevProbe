package dev.prateekthakur.devprobe.domain.model

import kotlinx.serialization.Serializable

/** Metadata extracted from an APK via PackageManager — no root, no install required. */
@Serializable
data class ApkMetadata(
    val appLabel: String,
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val compileSdk: Int?,
    val apkSizeBytes: Long,
    val debuggable: Boolean,
    val nativeArchitectures: List<String>,
    val iconBase64: String? = null,
)

@Serializable
data class ManifestInfo(
    val packageName: String,
    val appLabel: String,
    val debuggable: Boolean,
    val allowBackup: Boolean,
    val usesCleartextTraffic: Boolean?,
    val hasNetworkSecurityConfig: Boolean,
    val hasDataExtractionRules: Boolean,
    val activities: List<ComponentInfo>,
    val services: List<ComponentInfo>,
    val receivers: List<ComponentInfo>,
    val providers: List<ProviderInfo>,
    val compileSdkVersion: Int? = null,
)

@Serializable
enum class ComponentType { ACTIVITY, SERVICE, RECEIVER, PROVIDER }

@Serializable
data class IntentFilterInfo(
    val actions: List<String>,
    val categories: List<String>,
    val dataSchemes: List<String>,
)

@Serializable
data class ComponentInfo(
    val name: String,
    val type: ComponentType,
    val exported: Boolean,
    val permission: String?,
    val intentFilters: List<IntentFilterInfo>,
    val launchMode: String? = null,
)

@Serializable
data class ProviderInfo(
    val name: String,
    val authority: String,
    val exported: Boolean,
    val readPermission: String?,
    val writePermission: String?,
    val grantUriPermissions: Boolean,
)

@Serializable
enum class PermissionCategory { NORMAL, DANGEROUS, SIGNATURE, SPECIAL, UNKNOWN }

@Serializable
enum class RiskLevel { NONE, LOW, MEDIUM, HIGH }

@Serializable
data class PermissionInfo(
    val name: String,
    val category: PermissionCategory,
    val description: String,
    val risk: RiskLevel,
)

@Serializable
enum class Severity { INFO, LOW, MEDIUM, HIGH, CRITICAL }

@Serializable
data class SecurityFinding(
    val severity: Severity,
    val title: String,
    val description: String,
    val evidence: String,
    val recommendation: String,
)

@Serializable
data class ApkSizeBreakdown(
    val totalBytes: Long,
    val fileCount: Int,
    val dexBytes: Long,
    val nativeLibBytes: Long,
    val resourceBytes: Long,
    val assetBytes: Long,
    val otherBytes: Long,
    val largestFiles: List<ApkFileEntry>,
)

@Serializable
data class ApkFileEntry(
    val path: String,
    val sizeBytes: Long,
)

@Serializable
data class CertificateInfo(
    val subject: String,
    val issuer: String,
    val serialNumber: String,
    val notBefore: String,
    val notAfter: String,
    val signatureAlgorithm: String,
    val sha1Fingerprint: String,
    val sha256Fingerprint: String,
    val isExpired: Boolean,
    val isSelfSigned: Boolean,
    val keyAlgorithm: String,
    val keyBits: Int,
    val isDebugCertificate: Boolean,
)

@Serializable
data class SigningInfo(
    val hasV1: Boolean,
    val hasV2: Boolean,
    val hasV3: Boolean,
    val hasV31: Boolean,
    val certificates: List<CertificateInfo>,
)

@Serializable
data class DexFileInfo(
    val name: String,
    val sizeBytes: Long,
    val dexVersion: String,
    val stringCount: Int,
    val typeCount: Int,
    val protoCount: Int,
    val fieldCount: Int,
    val methodCount: Int,
    val classCount: Int,
    val sha1Signature: String,
)

@Serializable
data class ObfuscationSignal(
    val sampledClassCount: Int,
    val shortNameCount: Int,
    val shortNamePercent: Double,
    val likelyObfuscated: Boolean,
)

@Serializable
data class BytecodeAnalysis(
    val dexFiles: List<DexFileInfo>,
    val totalClasses: Int,
    val totalMethods: Int,
    val totalFields: Int,
    val totalStrings: Int,
    val isMultidex: Boolean,
    val obfuscation: ObfuscationSignal,
)

@Serializable
data class ApkAnalysisResult(
    val metadata: ApkMetadata,
    val manifest: ManifestInfo,
    val permissions: List<PermissionInfo>,
    val securityFindings: List<SecurityFinding>,
    val sizeBreakdown: ApkSizeBreakdown,
    val signing: SigningInfo,
    val bytecode: BytecodeAnalysis,
    val stringScan: StringScanResult,
)
