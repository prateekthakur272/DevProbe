package dev.prateekthakur.devprobe.data.report

import dev.prateekthakur.devprobe.domain.model.ApkAnalysisResult

/** Maps a completed [ApkAnalysisResult] into report content — no rendering concerns here. */
fun buildApkReportBlocks(result: ApkAnalysisResult): List<ReportBlock> {
    val blocks = mutableListOf<ReportBlock>()
    val metadata = result.metadata

    blocks += ReportBlock.Section("Overview")
    blocks += ReportBlock.KeyValue(
        listOf(
            "Package" to metadata.packageName,
            "App label" to metadata.appLabel,
            "Version" to "${metadata.versionName ?: "—"} (code ${metadata.versionCode})",
            "SDK" to "min ${metadata.minSdk} · target ${metadata.targetSdk}" + (metadata.compileSdk?.let { " · compile $it" } ?: ""),
            "APK size" to formatReportBytes(metadata.apkSizeBytes),
            "Debuggable" to if (metadata.debuggable) "Yes" else "No",
            "Native ABIs" to metadata.nativeArchitectures.takeIf { it.isNotEmpty() }?.joinToString(", ").orEmpty().ifBlank { "None" },
        ),
    )

    val findings = result.securityFindings.sortedByDescending { it.severity.ordinal }
    blocks += ReportBlock.Spacer()
    blocks += ReportBlock.Section("Security Findings (${findings.size})")
    if (findings.isEmpty()) {
        blocks += ReportBlock.Paragraph("No security findings detected.")
    } else {
        findings.forEach { finding ->
            blocks += ReportBlock.Finding(
                severityLabel = finding.severity.name,
                title = finding.title,
                description = finding.description,
                evidence = finding.evidence.takeIf { it.isNotBlank() },
                recommendation = finding.recommendation.takeIf { it.isNotBlank() },
            )
        }
    }

    blocks += ReportBlock.Spacer()
    blocks += ReportBlock.Section("Permissions (${result.permissions.size})")
    if (result.permissions.isEmpty()) {
        blocks += ReportBlock.Paragraph("No permissions declared.")
    } else {
        blocks += ReportBlock.Bullets(
            result.permissions.sortedByDescending { it.risk.ordinal }.map {
                "${it.name} — ${it.category.name.lowercase()} · risk: ${it.risk.name.lowercase()}"
            },
        )
    }

    blocks += ReportBlock.Spacer()
    blocks += ReportBlock.Section("Signing & Certificate")
    val signing = result.signing
    blocks += ReportBlock.KeyValue(
        listOf(
            "Signature schemes" to listOfNotNull(
                "v1".takeIf { signing.hasV1 },
                "v2".takeIf { signing.hasV2 },
                "v3".takeIf { signing.hasV3 },
                "v3.1".takeIf { signing.hasV31 },
            ).ifEmpty { listOf("None detected") }.joinToString(", "),
        ),
    )
    signing.certificates.forEach { cert ->
        blocks += ReportBlock.Spacer(6f)
        blocks += ReportBlock.KeyValue(
            listOf(
                "Subject" to cert.subject,
                "Issuer" to cert.issuer,
                "Serial" to cert.serialNumber,
                "Validity" to "${cert.notBefore} → ${cert.notAfter}" + if (cert.isExpired) "  (EXPIRED)" else "",
                "Signature algorithm" to cert.signatureAlgorithm,
                "Key" to "${cert.keyAlgorithm} ${cert.keyBits}-bit",
                "SHA-1" to cert.sha1Fingerprint,
                "SHA-256" to cert.sha256Fingerprint,
                "Flags" to listOfNotNull(
                    "self-signed".takeIf { cert.isSelfSigned },
                    "debug certificate".takeIf { cert.isDebugCertificate },
                ).ifEmpty { listOf("none") }.joinToString(", "),
            ),
        )
    }

    blocks += ReportBlock.Spacer()
    blocks += ReportBlock.Section("Size Breakdown")
    val size = result.sizeBreakdown
    blocks += ReportBlock.KeyValue(
        listOf(
            "Total size" to formatReportBytes(size.totalBytes),
            "File count" to size.fileCount.toString(),
            "DEX" to formatReportBytes(size.dexBytes),
            "Native libraries" to formatReportBytes(size.nativeLibBytes),
            "Resources" to formatReportBytes(size.resourceBytes),
            "Assets" to formatReportBytes(size.assetBytes),
            "Other" to formatReportBytes(size.otherBytes),
        ),
    )
    if (size.largestFiles.isNotEmpty()) {
        blocks += ReportBlock.Paragraph("Largest files:")
        blocks += ReportBlock.Bullets(size.largestFiles.take(10).map { "${it.path} — ${formatReportBytes(it.sizeBytes)}" })
    }

    blocks += ReportBlock.Spacer()
    blocks += ReportBlock.Section("Bytecode")
    val bytecode = result.bytecode
    blocks += ReportBlock.KeyValue(
        listOf(
            "Classes" to bytecode.totalClasses.toString(),
            "Methods" to bytecode.totalMethods.toString(),
            "Fields" to bytecode.totalFields.toString(),
            "Strings" to bytecode.totalStrings.toString(),
            "Multidex" to if (bytecode.isMultidex) "Yes (${bytecode.dexFiles.size} DEX files)" else "No",
            "Obfuscation signal" to (
                (if (bytecode.obfuscation.likelyObfuscated) "Likely obfuscated" else "Not obviously obfuscated") +
                    " (${"%.1f".format(bytecode.obfuscation.shortNamePercent)}% short class names)"
                ),
        ),
    )

    blocks += ReportBlock.Spacer()
    blocks += ReportBlock.Section("Strings & Secrets")
    val scan = result.stringScan
    blocks += ReportBlock.KeyValue(
        listOf(
            "Strings scanned" to scan.totalStringsScanned.toString(),
            "Possible secrets" to scan.secrets.size.toString(),
            "Weak crypto usages" to scan.weakCryptoUsages.size.toString(),
        ),
    )
    if (scan.secrets.isNotEmpty()) {
        blocks += ReportBlock.Paragraph("Detected secrets, URLs & emails:")
        blocks += ReportBlock.Bullets(scan.secrets.take(30).map { "[${it.type.name}] ${it.matchedText.take(80)}" })
    }
    if (scan.weakCryptoUsages.isNotEmpty()) {
        blocks += ReportBlock.Paragraph("Weak cryptography usage:")
        blocks += ReportBlock.Bullets(scan.weakCryptoUsages.take(20).map { "${it.algorithm.name} — ${it.matchedText.take(60)}" })
    }

    return blocks
}
