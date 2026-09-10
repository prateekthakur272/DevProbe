package dev.prateekthakur.devprobe.data.security

import dev.prateekthakur.devprobe.domain.model.BytecodeAnalysis
import dev.prateekthakur.devprobe.domain.model.CertificateInfo
import dev.prateekthakur.devprobe.domain.model.ComponentInfo
import dev.prateekthakur.devprobe.domain.model.ManifestInfo
import dev.prateekthakur.devprobe.domain.model.PermissionCategory
import dev.prateekthakur.devprobe.domain.model.PermissionInfo
import dev.prateekthakur.devprobe.domain.model.SecretType
import dev.prateekthakur.devprobe.domain.model.SecurityFinding
import dev.prateekthakur.devprobe.domain.model.Severity
import dev.prateekthakur.devprobe.domain.model.SigningInfo
import dev.prateekthakur.devprobe.domain.model.StringScanResult
import dev.prateekthakur.devprobe.domain.model.WeakCryptoAlgorithm
import dev.prateekthakur.devprobe.domain.model.WeakCryptoUsage

/**
 * Deterministic, rule-based security checks (§10), aligned with common OWASP
 * MASVS-style mobile static-analysis findings (the same class of checks tools
 * like MobSF report). No AI involved — these are facts derivable from the
 * manifest/permissions/signing/bytecode/strings/SDK levels alone.
 */
class SecurityRuleEngine {

    fun evaluate(
        manifest: ManifestInfo,
        permissions: List<PermissionInfo>,
        signing: SigningInfo? = null,
        bytecode: BytecodeAnalysis? = null,
        stringScan: StringScanResult? = null,
        minSdk: Int? = null,
        targetSdk: Int? = null,
    ): List<SecurityFinding> {
        val findings = mutableListOf<SecurityFinding>()

        if (manifest.debuggable) {
            findings += SecurityFinding(
                severity = Severity.CRITICAL,
                title = "Application is debuggable",
                description = "The application declares android:debuggable=\"true\". A debuggable release build allows attaching a debugger and can leak internal state.",
                evidence = "AndroidManifest.xml: <application android:debuggable=\"true\">",
                recommendation = "Ensure android:debuggable is false (or omitted) in release builds.",
            )
        }

        if (manifest.allowBackup && !manifest.hasDataExtractionRules) {
            findings += SecurityFinding(
                severity = Severity.MEDIUM,
                title = "Backup allowed without extraction rules",
                description = "android:allowBackup is true and no dataExtractionRules/fullBackupContent restriction was found, so app data may be included in ADB or cloud backups.",
                evidence = "AndroidManifest.xml: <application android:allowBackup=\"true\">",
                recommendation = "Set android:allowBackup=\"false\", or define android:dataExtractionRules / android:fullBackupContent to exclude sensitive data.",
            )
        }

        if (manifest.usesCleartextTraffic == true) {
            findings += SecurityFinding(
                severity = Severity.HIGH,
                title = "Cleartext traffic permitted",
                description = "android:usesCleartextTraffic is true, allowing unencrypted HTTP traffic app-wide.",
                evidence = "AndroidManifest.xml: <application android:usesCleartextTraffic=\"true\">",
                recommendation = "Disable cleartext traffic and use a Network Security Configuration to allow only the specific domains that require it, if any.",
            )
        } else if (!manifest.hasNetworkSecurityConfig && manifest.usesCleartextTraffic == null) {
            findings += SecurityFinding(
                severity = Severity.INFO,
                title = "No explicit network security configuration",
                description = "The app does not declare a Network Security Configuration. Modern Android defaults (targetSdk 28+) block cleartext traffic, but this cannot be verified without the config.",
                evidence = "AndroidManifest.xml: no android:networkSecurityConfig attribute",
                recommendation = "Consider adding an explicit Network Security Configuration to make TLS/cleartext policy auditable.",
            )
        }

        findings += checkExportedComponents(manifest.activities, "Activity")
        findings += checkExportedComponents(manifest.services, "Service")
        findings += checkExportedComponents(manifest.receivers, "Receiver")

        manifest.providers.forEach { provider ->
            if (provider.exported && provider.grantUriPermissions &&
                provider.readPermission == null && provider.writePermission == null
            ) {
                findings += SecurityFinding(
                    severity = Severity.HIGH,
                    title = "Exported provider grants URI permissions without protection",
                    description = "Content provider '${provider.name}' (authority: ${provider.authority}) is exported, sets grantUriPermissions=true, and declares no read/write permission.",
                    evidence = "AndroidManifest.xml: <provider android:name=\"${provider.name}\" android:exported=\"true\" android:grantUriPermissions=\"true\">",
                    recommendation = "Require a signature-level permission for this provider, or restrict grantUriPermissions to specific <grant-uri-permission> paths.",
                )
            } else if (provider.exported && provider.readPermission == null && provider.writePermission == null) {
                findings += SecurityFinding(
                    severity = Severity.MEDIUM,
                    title = "Exported provider without permission",
                    description = "Content provider '${provider.name}' (authority: ${provider.authority}) is exported and declares no read/write permission, so any app can query it.",
                    evidence = "AndroidManifest.xml: <provider android:name=\"${provider.name}\" android:exported=\"true\">",
                    recommendation = "Add android:readPermission/android:writePermission, or set android:exported=\"false\" if external access is not required.",
                )
            }
        }

        val dangerousPermissions = permissions.filter { it.category == PermissionCategory.DANGEROUS }
        if (dangerousPermissions.size >= 5) {
            findings += SecurityFinding(
                severity = Severity.LOW,
                title = "Large number of dangerous permissions requested",
                description = "The app requests ${dangerousPermissions.size} dangerous permissions: ${dangerousPermissions.joinToString { it.name.substringAfterLast('.') }}.",
                evidence = "AndroidManifest.xml: <uses-permission> entries",
                recommendation = "Review whether each dangerous permission is strictly required; request the minimum necessary and explain usage to users.",
            )
        }

        val specialPermissions = permissions.filter { it.category == PermissionCategory.SPECIAL }
        specialPermissions.forEach { perm ->
            findings += SecurityFinding(
                severity = Severity.MEDIUM,
                title = "Special permission requested: ${perm.name.substringAfterLast('.')}",
                description = perm.description,
                evidence = "AndroidManifest.xml: <uses-permission android:name=\"${perm.name}\">",
                recommendation = "Special permissions require explicit user grant flows and heightened scrutiny; confirm this is intentional.",
            )
        }

        findings += checkPermissionCombinations(permissions)
        findings += checkSdkLevels(minSdk, targetSdk)

        if (signing != null) {
            findings += checkSigning(signing)
        }

        if (bytecode != null && bytecode.obfuscation.likelyObfuscated) {
            findings += SecurityFinding(
                severity = Severity.INFO,
                title = "Code appears minified/obfuscated",
                description = "${bytecode.obfuscation.shortNameCount} of ${bytecode.obfuscation.sampledClassCount} " +
                    "defined classes (${"%.0f".format(bytecode.obfuscation.shortNamePercent)}%) have 1-2 character names, " +
                    "consistent with ProGuard/R8 minification.",
                evidence = "DEX class_defs table: type descriptors with short simple names",
                recommendation = "This is generally good practice for release builds; verify it was intentional and that mapping files are archived for deobfuscating crash reports.",
            )
        }

        if (stringScan != null) {
            findings += checkSecrets(stringScan)
            findings += checkWeakCrypto(stringScan)
        }

        return findings.sortedByDescending { it.severity.ordinal }
    }

    /** Permission pairs that, together, indicate a real (if not necessarily malicious) data-exfiltration risk. */
    private fun checkPermissionCombinations(permissions: List<PermissionInfo>): List<SecurityFinding> {
        val names = permissions.map { it.name }.toSet()
        val hasInternet = "android.permission.INTERNET" in names
        if (!hasInternet) return emptyList()

        data class Combo(val permission: String, val label: String, val severity: Severity, val risk: String)
        val combos = listOf(
            Combo("android.permission.READ_SMS", "read SMS messages", Severity.HIGH, "SMS content (often used for OTP interception) leaving the device"),
            Combo("android.permission.RECEIVE_SMS", "receive SMS messages", Severity.HIGH, "SMS content (often used for OTP interception) leaving the device"),
            Combo("android.permission.READ_CALL_LOG", "read the call log", Severity.HIGH, "call history leaving the device"),
            Combo("android.permission.READ_CONTACTS", "read contacts", Severity.MEDIUM, "the user's contact list leaving the device"),
            Combo("android.permission.ACCESS_FINE_LOCATION", "access precise location", Severity.MEDIUM, "precise location data leaving the device"),
            Combo("android.permission.ACCESS_BACKGROUND_LOCATION", "access background location", Severity.HIGH, "location tracking even while the app isn't in use"),
        )

        val findings = mutableListOf<SecurityFinding>()
        combos.forEach { combo ->
            if (combo.permission in names) {
                findings += SecurityFinding(
                    severity = combo.severity,
                    title = "Network access combined with permission to ${combo.label}",
                    description = "The app requests both INTERNET and ${combo.permission.substringAfterLast('.')}, enabling ${combo.risk}.",
                    evidence = "AndroidManifest.xml: <uses-permission android:name=\"android.permission.INTERNET\"> + <uses-permission android:name=\"${combo.permission}\">",
                    recommendation = "Confirm this data leaves the device only for a purpose the user would expect and consent to; document it in a privacy policy.",
                )
            }
        }

        val hasCamera = "android.permission.CAMERA" in names
        val hasMic = "android.permission.RECORD_AUDIO" in names
        if (hasCamera && hasMic) {
            findings += SecurityFinding(
                severity = Severity.MEDIUM,
                title = "Network access combined with camera and microphone permissions",
                description = "The app requests INTERNET, CAMERA, and RECORD_AUDIO together, which is capable of capturing and exfiltrating audio/video.",
                evidence = "AndroidManifest.xml: <uses-permission> entries for INTERNET, CAMERA, RECORD_AUDIO",
                recommendation = "Confirm capture only happens with clear user intent/indication (e.g. an active recording UI), consistent with platform policy.",
            )
        }
        return findings
    }

    /** Findings driven by the app's declared min/target SDK — each affects a real default security behavior. */
    private fun checkSdkLevels(minSdk: Int?, targetSdk: Int?): List<SecurityFinding> {
        val findings = mutableListOf<SecurityFinding>()

        if (targetSdk != null) {
            if (targetSdk < 23) {
                findings += SecurityFinding(
                    severity = Severity.HIGH,
                    title = "Target SDK predates the runtime permission model",
                    description = "targetSdkVersion is $targetSdk (< 23). Dangerous permissions are granted automatically at install time instead of prompting the user at runtime.",
                    evidence = "AndroidManifest.xml: <uses-sdk android:targetSdkVersion=\"$targetSdk\">",
                    recommendation = "Raise targetSdkVersion to at least 23 (ideally the current Play-required level) and adopt runtime permission requests.",
                )
            } else if (targetSdk < 28) {
                findings += SecurityFinding(
                    severity = Severity.MEDIUM,
                    title = "Target SDK predates default cleartext-traffic blocking",
                    description = "targetSdkVersion is $targetSdk (< 28). Cleartext (HTTP) traffic is allowed by default unless explicitly restricted.",
                    evidence = "AndroidManifest.xml: <uses-sdk android:targetSdkVersion=\"$targetSdk\">",
                    recommendation = "Raise targetSdkVersion to 28+ and/or add a Network Security Configuration that blocks cleartext traffic explicitly.",
                )
            } else if (targetSdk < 29) {
                findings += SecurityFinding(
                    severity = Severity.LOW,
                    title = "Target SDK predates scoped storage",
                    description = "targetSdkVersion is $targetSdk (< 29), so the app is not required to use scoped storage and may have broad external storage access.",
                    evidence = "AndroidManifest.xml: <uses-sdk android:targetSdkVersion=\"$targetSdk\">",
                    recommendation = "Raise targetSdkVersion to 29+ and migrate to scoped storage / the Storage Access Framework where applicable.",
                )
            }
        }

        if (minSdk != null && minSdk < 21) {
            findings += SecurityFinding(
                severity = Severity.INFO,
                title = "Very low minimum SDK version",
                description = "minSdkVersion is $minSdk (< 21/Lollipop), which limits the platform security mitigations (ART, APK signature scheme v2+, etc.) that can be relied upon for all installs.",
                evidence = "AndroidManifest.xml: <uses-sdk android:minSdkVersion=\"$minSdk\">",
                recommendation = "Raise minSdkVersion if the very old device support is not required, to benefit from modern platform security defaults.",
            )
        }

        return findings
    }

    private fun checkSecrets(stringScan: StringScanResult): List<SecurityFinding> {
        val bySeverityWorthySecrets = stringScan.secrets.filter { it.type != SecretType.URL && it.type != SecretType.EMAIL }
        return bySeverityWorthySecrets.take(10).map { secret ->
            val label = when (secret.type) {
                SecretType.AWS_KEY -> "Possible AWS access key"
                SecretType.GOOGLE_API_KEY -> "Possible Google API key"
                SecretType.JWT -> "Possible JWT token"
                SecretType.PRIVATE_KEY -> "Embedded private key material"
                SecretType.GENERIC_SECRET_ASSIGNMENT -> "Possible hardcoded credential"
                SecretType.URL, SecretType.EMAIL -> "Embedded string"
            }
            SecurityFinding(
                severity = secret.severity,
                title = "$label found in ${secret.source.name.lowercase()} strings",
                description = "A string matching a $label pattern was found: \"${secret.matchedText.take(60)}\".",
                evidence = secret.context,
                recommendation = "Hardcoded secrets in an APK can be extracted by anyone. Move this to a secure backend, environment-specific config, or the Android Keystore.",
            )
        }
    }

    private fun checkWeakCrypto(stringScan: StringScanResult): List<SecurityFinding> {
        val byAlgorithm = stringScan.weakCryptoUsages.groupBy { it.algorithm }
        return byAlgorithm.entries.take(10).map { (algorithm, usages) ->
            val severity = when (algorithm) {
                WeakCryptoAlgorithm.DES, WeakCryptoAlgorithm.TRIPLE_DES,
                WeakCryptoAlgorithm.RC2, WeakCryptoAlgorithm.RC4,
                WeakCryptoAlgorithm.ECB_MODE -> Severity.HIGH
                WeakCryptoAlgorithm.MD5, WeakCryptoAlgorithm.SHA1 -> Severity.MEDIUM
            }
            val label = weakCryptoLabel(algorithm)
            val examples = usages.take(3).joinToString { "\"${it.matchedText}\"" }
            SecurityFinding(
                severity = severity,
                title = "Possible use of weak cryptography: $label",
                description = "Found ${usages.size} string literal(s) matching $label transformation/algorithm names, consistent with a call to Cipher.getInstance()/MessageDigest.getInstance() using this algorithm: $examples.",
                evidence = "DEX/resource strings: $examples",
                recommendation = weakCryptoRecommendation(algorithm),
            )
        }
    }

    private fun weakCryptoLabel(algorithm: WeakCryptoAlgorithm): String = when (algorithm) {
        WeakCryptoAlgorithm.DES -> "DES"
        WeakCryptoAlgorithm.TRIPLE_DES -> "3DES/DESede"
        WeakCryptoAlgorithm.RC2 -> "RC2"
        WeakCryptoAlgorithm.RC4 -> "RC4"
        WeakCryptoAlgorithm.MD5 -> "MD5"
        WeakCryptoAlgorithm.SHA1 -> "SHA-1"
        WeakCryptoAlgorithm.ECB_MODE -> "ECB cipher mode"
    }

    private fun weakCryptoRecommendation(algorithm: WeakCryptoAlgorithm): String = when (algorithm) {
        WeakCryptoAlgorithm.DES, WeakCryptoAlgorithm.TRIPLE_DES, WeakCryptoAlgorithm.RC2, WeakCryptoAlgorithm.RC4 ->
            "Replace with a modern authenticated cipher such as AES-GCM (e.g. \"AES/GCM/NoPadding\")."
        WeakCryptoAlgorithm.ECB_MODE ->
            "ECB mode does not use an IV and leaks patterns in the plaintext; switch to GCM or CBC with a random IV."
        WeakCryptoAlgorithm.MD5, WeakCryptoAlgorithm.SHA1 ->
            "Use SHA-256 or better for hashing; for password storage use a dedicated KDF (Argon2/bcrypt/PBKDF2), never a bare hash."
    }

    private fun checkSigning(signing: SigningInfo): List<SecurityFinding> {
        val findings = mutableListOf<SecurityFinding>()

        if (signing.certificates.isEmpty()) {
            findings += SecurityFinding(
                severity = Severity.INFO,
                title = "Unable to determine signing certificate",
                description = "No signing certificate could be extracted from this APK via PackageManager.",
                evidence = "PackageManager.getPackageArchiveInfo returned no signing certificates.",
                recommendation = "Verify the APK is properly signed; unsigned or corrupted APKs cannot be installed on most devices.",
            )
        }

        signing.certificates.forEach { cert ->
            findings += checkCertificateQuality(cert)
        }

        if (!signing.hasV2 && !signing.hasV3 && signing.hasV1) {
            findings += SecurityFinding(
                severity = Severity.MEDIUM,
                title = "Signed only with legacy v1 (JAR) signing",
                description = "No APK Signature Scheme v2 or v3 block was found — only the older v1/JAR signing scheme protects this APK. v1-only signing does not protect all APK bytes and is more susceptible to tampering (e.g. the Janus vulnerability class).",
                evidence = "APK Signing Block: v2=false, v3=false; META-INF/*.RSA|DSA|EC present",
                recommendation = "Re-sign with APK Signature Scheme v2/v3 (default with modern Android Gradle Plugin + apksigner) in addition to v1 for compatibility.",
            )
        } else if (!signing.hasV1 && !signing.hasV2 && !signing.hasV3) {
            findings += SecurityFinding(
                severity = Severity.HIGH,
                title = "No recognized signing scheme detected",
                description = "Neither v1 (JAR) signature files nor a v2/v3 APK Signing Block were found.",
                evidence = "META-INF/*.RSA|DSA|EC: absent; APK Signing Block: absent",
                recommendation = "Confirm this APK was exported/signed correctly before distribution.",
            )
        }

        return findings
    }

    private fun checkCertificateQuality(cert: CertificateInfo): List<SecurityFinding> {
        val findings = mutableListOf<SecurityFinding>()

        if (cert.isExpired) {
            findings += SecurityFinding(
                severity = Severity.HIGH,
                title = "Signing certificate has expired",
                description = "The signing certificate (serial ${cert.serialNumber}) expired on ${cert.notAfter}.",
                evidence = "Certificate subject: ${cert.subject}",
                recommendation = "Apps signed with an expired certificate cannot be updated once installed past expiry on some Android versions; renew and re-sign with a valid certificate.",
            )
        }

        if (cert.isDebugCertificate) {
            findings += SecurityFinding(
                severity = Severity.CRITICAL,
                title = "Signed with the default Android debug certificate",
                description = "The signing certificate subject (\"${cert.subject}\") matches the well-known AOSP debug.keystore identity, meaning this build was almost certainly not produced with a real release signing config.",
                evidence = "Certificate subject: ${cert.subject}",
                recommendation = "Never distribute a build signed with the debug keystore. Configure a dedicated release signing key and keep it secret.",
            )
        }

        val weakSigAlgo = listOf("MD5", "SHA1", "SHA-1").any { cert.signatureAlgorithm.contains(it, ignoreCase = true) }
        if (weakSigAlgo) {
            findings += SecurityFinding(
                severity = Severity.HIGH,
                title = "Certificate uses a weak signature algorithm",
                description = "The signing certificate's signature algorithm is \"${cert.signatureAlgorithm}\", which relies on a broken/weak hash function.",
                evidence = "Certificate signature algorithm: ${cert.signatureAlgorithm}",
                recommendation = "Re-sign with a modern algorithm such as SHA256withRSA or SHA256withECDSA.",
            )
        }

        if (cert.keyBits in 1 until 2048 && cert.keyAlgorithm == "RSA") {
            findings += SecurityFinding(
                severity = Severity.HIGH,
                title = "Signing key size is too small",
                description = "The certificate's RSA public key is ${cert.keyBits} bits. Modern guidance requires at least 2048 bits for RSA.",
                evidence = "Certificate public key: RSA-${cert.keyBits}",
                recommendation = "Generate a new signing key with at least RSA-2048 (or an EC key) and re-sign.",
            )
        } else if (cert.keyBits in 1 until 224 && cert.keyAlgorithm == "EC") {
            findings += SecurityFinding(
                severity = Severity.MEDIUM,
                title = "Signing key size is too small",
                description = "The certificate's EC public key field size is ${cert.keyBits} bits, below the commonly recommended 224-bit minimum.",
                evidence = "Certificate public key: EC-${cert.keyBits}",
                recommendation = "Generate a new EC signing key with at least a 256-bit curve (e.g. secp256r1) and re-sign.",
            )
        }

        return findings
    }

    private fun checkExportedComponents(components: List<ComponentInfo>, kind: String): List<SecurityFinding> {
        return components.filter { it.exported && it.permission == null && it.intentFilters.isNotEmpty() }
            .map { component ->
                SecurityFinding(
                    severity = Severity.MEDIUM,
                    title = "Exported $kind without permission",
                    description = "$kind '${component.name}' is exported (has an intent-filter and is not explicitly protected) but declares no permission, so any app can invoke it.",
                    evidence = "AndroidManifest.xml: <${kind.lowercase()} android:name=\"${component.name}\" android:exported=\"true\">",
                    recommendation = "Add android:permission, restrict the intent-filter, or set android:exported=\"false\" if external access is not required.",
                )
            }
    }
}
