package dev.prateekthakur.devprobe.data.apk

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import dev.prateekthakur.devprobe.domain.model.CertificateInfo
import dev.prateekthakur.devprobe.domain.model.SigningInfo
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Extracts signing certificate details and detects which signing schemes (v1/v2/v3/v3.1)
 * protect the APK — all via public, non-privileged APIs (§10, §14 evidence for §10's
 * security findings).
 */
class SigningAnalyzer(private val context: Context) {

    private val blockReader = ApkSigningBlockReader()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    @Suppress("DEPRECATION")
    fun analyze(apkFile: File): SigningInfo {
        val certificates = try {
            extractCertificates(apkFile)
        } catch (_: Exception) {
            emptyList()
        }
        val schemes = blockReader.detectSchemes(apkFile)
        return SigningInfo(
            hasV1 = schemes.v1,
            hasV2 = schemes.v2,
            hasV3 = schemes.v3,
            hasV31 = schemes.v3_1,
            certificates = certificates,
        )
    }

    @Suppress("DEPRECATION")
    private fun extractCertificates(apkFile: File): List<CertificateInfo> {
        val pm = context.packageManager
        val signatures: List<Signature> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            val signingInfo = info?.signingInfo
            when {
                signingInfo == null -> emptyList()
                signingInfo.hasMultipleSigners() -> signingInfo.apkContentsSigners?.toList().orEmpty()
                else -> signingInfo.signingCertificateHistory?.toList()
                    ?: signingInfo.apkContentsSigners?.toList().orEmpty()
            }
        } else {
            val info = pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNATURES)
            info?.signatures?.toList().orEmpty()
        }
        return signatures.mapNotNull { toCertificateInfo(it) }
    }

    private fun toCertificateInfo(signature: Signature): CertificateInfo? {
        val cert = toX509(signature) ?: return null
        val encoded = cert.encoded
        val now = Date()
        val (keyAlgorithm, keyBits) = keySizeOf(cert)
        return CertificateInfo(
            subject = cert.subjectX500Principal.name,
            issuer = cert.issuerX500Principal.name,
            serialNumber = cert.serialNumber.toString(16),
            notBefore = dateFormat.format(cert.notBefore),
            notAfter = dateFormat.format(cert.notAfter),
            signatureAlgorithm = cert.sigAlgName ?: "unknown",
            sha1Fingerprint = fingerprint(encoded, "SHA-1"),
            sha256Fingerprint = fingerprint(encoded, "SHA-256"),
            isExpired = now.after(cert.notAfter),
            isSelfSigned = cert.subjectX500Principal == cert.issuerX500Principal,
            keyAlgorithm = keyAlgorithm,
            keyBits = keyBits,
            isDebugCertificate = isKnownDebugCertificate(cert),
        )
    }

    /** Returns (algorithm name, key size in bits); size is -1 when it can't be determined. */
    private fun keySizeOf(cert: X509Certificate): Pair<String, Int> {
        val key = cert.publicKey
        return when (key) {
            is RSAPublicKey -> "RSA" to key.modulus.bitLength()
            is ECPublicKey -> "EC" to key.params.curve.field.fieldSize
            else -> (key.algorithm ?: "unknown") to -1
        }
    }

    /**
     * The AOSP debug keystore (debug.keystore) is generated with a fixed, publicly known
     * subject ("CN=Android Debug,O=Android,C=US") — any APK signed with that certificate
     * was almost certainly built without a real release signing config.
     */
    private fun isKnownDebugCertificate(cert: X509Certificate): Boolean =
        cert.subjectX500Principal.name.contains("Android Debug", ignoreCase = true)

    private fun toX509(signature: Signature): X509Certificate? = try {
        CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(signature.toByteArray())) as? X509Certificate
    } catch (_: Exception) {
        null
    }

    private fun fingerprint(bytes: ByteArray, algorithm: String): String {
        val digest = MessageDigest.getInstance(algorithm).digest(bytes)
        return digest.joinToString(":") { "%02X".format(it) }
    }
}
