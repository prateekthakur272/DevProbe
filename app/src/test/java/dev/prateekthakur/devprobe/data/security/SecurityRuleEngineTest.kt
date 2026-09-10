package dev.prateekthakur.devprobe.data.security

import dev.prateekthakur.devprobe.domain.model.CertificateInfo
import dev.prateekthakur.devprobe.domain.model.ComponentInfo
import dev.prateekthakur.devprobe.domain.model.ComponentType
import dev.prateekthakur.devprobe.domain.model.DetectedSecret
import dev.prateekthakur.devprobe.domain.model.IntentFilterInfo
import dev.prateekthakur.devprobe.domain.model.ManifestInfo
import dev.prateekthakur.devprobe.domain.model.PermissionCategory
import dev.prateekthakur.devprobe.domain.model.PermissionInfo
import dev.prateekthakur.devprobe.domain.model.RiskLevel
import dev.prateekthakur.devprobe.domain.model.SecretType
import dev.prateekthakur.devprobe.domain.model.Severity
import dev.prateekthakur.devprobe.domain.model.SigningInfo
import dev.prateekthakur.devprobe.domain.model.StringScanResult
import dev.prateekthakur.devprobe.domain.model.StringSource
import dev.prateekthakur.devprobe.domain.model.WeakCryptoAlgorithm
import dev.prateekthakur.devprobe.domain.model.WeakCryptoUsage
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityRuleEngineTest {

    private val engine = SecurityRuleEngine()

    private fun baseManifest(
        debuggable: Boolean = false,
        allowBackup: Boolean = false,
        hasDataExtractionRules: Boolean = true,
        cleartext: Boolean? = false,
        activities: List<ComponentInfo> = emptyList(),
    ) = ManifestInfo(
        packageName = "com.example.app",
        appLabel = "Example",
        debuggable = debuggable,
        allowBackup = allowBackup,
        usesCleartextTraffic = cleartext,
        hasNetworkSecurityConfig = true,
        hasDataExtractionRules = hasDataExtractionRules,
        activities = activities,
        services = emptyList(),
        receivers = emptyList(),
        providers = emptyList(),
    )

    @Test
    fun `flags debuggable apps as critical`() {
        val findings = engine.evaluate(baseManifest(debuggable = true), emptyList())
        assertTrue(findings.any { it.severity == Severity.CRITICAL && it.title.contains("debuggable", ignoreCase = true) })
    }

    @Test
    fun `flags allowBackup without extraction rules`() {
        val findings = engine.evaluate(baseManifest(allowBackup = true, hasDataExtractionRules = false), emptyList())
        assertTrue(findings.any { it.title.contains("Backup", ignoreCase = true) })
    }

    @Test
    fun `does not flag backup when extraction rules present`() {
        val findings = engine.evaluate(baseManifest(allowBackup = true, hasDataExtractionRules = true), emptyList())
        assertTrue(findings.none { it.title.contains("Backup", ignoreCase = true) })
    }

    @Test
    fun `flags cleartext traffic as high severity`() {
        val findings = engine.evaluate(baseManifest(cleartext = true), emptyList())
        assertTrue(findings.any { it.severity == Severity.HIGH && it.title.contains("Cleartext", ignoreCase = true) })
    }

    @Test
    fun `flags exported activity with intent filter and no permission`() {
        val activity = ComponentInfo(
            name = "com.example.app.ShareActivity",
            type = ComponentType.ACTIVITY,
            exported = true,
            permission = null,
            intentFilters = listOf(IntentFilterInfo(actions = listOf("android.intent.action.VIEW"), categories = emptyList(), dataSchemes = emptyList())),
        )
        val findings = engine.evaluate(baseManifest(activities = listOf(activity)), emptyList())
        assertTrue(findings.any { it.title.contains("Exported Activity", ignoreCase = true) })
    }

    @Test
    fun `does not flag exported activity that declares a permission`() {
        val activity = ComponentInfo(
            name = "com.example.app.ShareActivity",
            type = ComponentType.ACTIVITY,
            exported = true,
            permission = "com.example.app.permission.SHARE",
            intentFilters = listOf(IntentFilterInfo(actions = listOf("android.intent.action.VIEW"), categories = emptyList(), dataSchemes = emptyList())),
        )
        val findings = engine.evaluate(baseManifest(activities = listOf(activity)), emptyList())
        assertTrue(findings.none { it.title.contains("Exported Activity", ignoreCase = true) })
    }

    private fun permission(name: String, category: PermissionCategory = PermissionCategory.DANGEROUS) =
        PermissionInfo(name = name, category = category, description = "", risk = RiskLevel.MEDIUM)

    @Test
    fun `flags internet plus sms permission combination as high`() {
        val permissions = listOf(permission("android.permission.INTERNET", PermissionCategory.NORMAL), permission("android.permission.READ_SMS"))
        val findings = engine.evaluate(baseManifest(), permissions)
        assertTrue(findings.any { it.severity == Severity.HIGH && it.title.contains("read SMS", ignoreCase = true) })
    }

    @Test
    fun `does not flag sms permission without internet`() {
        val permissions = listOf(permission("android.permission.READ_SMS"))
        val findings = engine.evaluate(baseManifest(), permissions)
        assertTrue(findings.none { it.title.contains("SMS", ignoreCase = true) })
    }

    @Test
    fun `flags target sdk below runtime permission model`() {
        val findings = engine.evaluate(baseManifest(), emptyList(), targetSdk = 19)
        assertTrue(findings.any { it.severity == Severity.HIGH && it.title.contains("runtime permission", ignoreCase = true) })
    }

    @Test
    fun `flags target sdk below cleartext blocking threshold`() {
        val findings = engine.evaluate(baseManifest(), emptyList(), targetSdk = 26)
        assertTrue(findings.any { it.severity == Severity.MEDIUM && it.title.contains("cleartext", ignoreCase = true) })
    }

    @Test
    fun `does not flag a modern target sdk`() {
        val findings = engine.evaluate(baseManifest(), emptyList(), targetSdk = 34)
        assertTrue(findings.none { it.title.contains("runtime permission", ignoreCase = true) || it.title.contains("cleartext-traffic blocking", ignoreCase = true) })
    }

    @Test
    fun `flags very low min sdk as info`() {
        val findings = engine.evaluate(baseManifest(), emptyList(), minSdk = 16)
        assertTrue(findings.any { it.severity == Severity.INFO && it.title.contains("minimum SDK", ignoreCase = true) })
    }

    private fun baseCertificate(
        isExpired: Boolean = false,
        isDebugCertificate: Boolean = false,
        signatureAlgorithm: String = "SHA256withRSA",
        keyAlgorithm: String = "RSA",
        keyBits: Int = 2048,
    ) = CertificateInfo(
        subject = "CN=Example,O=Example,C=US",
        issuer = "CN=Example,O=Example,C=US",
        serialNumber = "1",
        notBefore = "2024-01-01",
        notAfter = "2034-01-01",
        signatureAlgorithm = signatureAlgorithm,
        sha1Fingerprint = "AA",
        sha256Fingerprint = "BB",
        isExpired = isExpired,
        isSelfSigned = true,
        keyAlgorithm = keyAlgorithm,
        keyBits = keyBits,
        isDebugCertificate = isDebugCertificate,
    )

    private fun baseSigning(vararg certificates: CertificateInfo) = SigningInfo(
        hasV1 = false,
        hasV2 = true,
        hasV3 = true,
        hasV31 = false,
        certificates = certificates.toList(),
    )

    @Test
    fun `flags signing with the android debug certificate as critical`() {
        val findings = engine.evaluate(baseManifest(), emptyList(), signing = baseSigning(baseCertificate(isDebugCertificate = true)))
        assertTrue(findings.any { it.severity == Severity.CRITICAL && it.title.contains("debug certificate", ignoreCase = true) })
    }

    @Test
    fun `flags weak certificate signature algorithm`() {
        val findings = engine.evaluate(baseManifest(), emptyList(), signing = baseSigning(baseCertificate(signatureAlgorithm = "SHA1withRSA")))
        assertTrue(findings.any { it.severity == Severity.HIGH && it.title.contains("weak signature algorithm", ignoreCase = true) })
    }

    @Test
    fun `flags undersized rsa signing key`() {
        val findings = engine.evaluate(baseManifest(), emptyList(), signing = baseSigning(baseCertificate(keyBits = 1024)))
        assertTrue(findings.any { it.severity == Severity.HIGH && it.title.contains("key size", ignoreCase = true) })
    }

    @Test
    fun `does not flag a modern signing certificate`() {
        val findings = engine.evaluate(baseManifest(), emptyList(), signing = baseSigning(baseCertificate()))
        assertTrue(findings.none { it.title.contains("debug certificate", ignoreCase = true) || it.title.contains("weak signature", ignoreCase = true) || it.title.contains("key size", ignoreCase = true) })
    }

    @Test
    fun `flags weak crypto usage found in strings`() {
        val stringScan = StringScanResult(
            totalStringsScanned = 10,
            secrets = emptyList(),
            weakCryptoUsages = listOf(WeakCryptoUsage(WeakCryptoAlgorithm.DES, "DES", StringSource.DEX)),
        )
        val findings = engine.evaluate(baseManifest(), emptyList(), stringScan = stringScan)
        assertTrue(findings.any { it.severity == Severity.HIGH && it.title.contains("weak cryptography", ignoreCase = true) })
    }

    @Test
    fun `flags secrets found in string scan`() {
        val stringScan = StringScanResult(
            totalStringsScanned = 10,
            secrets = listOf(DetectedSecret(SecretType.AWS_KEY, "AKIAIOSFODNN7EXAMPLE", "context", StringSource.DEX, Severity.CRITICAL)),
        )
        val findings = engine.evaluate(baseManifest(), emptyList(), stringScan = stringScan)
        assertTrue(findings.any { it.severity == Severity.CRITICAL && it.title.contains("AWS access key", ignoreCase = true) })
    }
}
